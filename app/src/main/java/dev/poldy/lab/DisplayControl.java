package dev.poldy.lab;

import android.os.SystemClock;
import android.content.Context;
import android.annotation.SuppressLint;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.regex.*;

/** Runs as the Shizuku shell user. Only exposes bounded display-state operations. */
public final class DisplayControl extends IDisplayControl.Stub {
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    private int innerId = -1, outerId = -1, openedId = -1, closedId = -1, ownedState = -1;
    private long leaseUntil;
    private boolean settling;
    private final Object stateManager;
    private NativeFrameCapture frameCapture;
    private PanelPower panelPower;
    private volatile NativeScene scene;
    private final int ownerUid;
    private final WindowPrivacyObserver privacy;
    private volatile IPrivacyListener privacyListener;
    @Override public boolean onTransact(int code,android.os.Parcel data,android.os.Parcel reply,int flags)
            throws android.os.RemoteException {
        if(code!=android.os.IBinder.INTERFACE_TRANSACTION&&!ControlCaller.allowed(
            android.os.Binder.getCallingUid(),ownerUid,android.os.Process.myUid(),code))
            throw new SecurityException("Caller is not the Foldy owner");
        return super.onTransact(code,data,reply,flags);
    }
    private void privacyChanged(boolean blocked,long epoch){
        NativeScene current=scene;if(blocked&&current!=null)current.suppress();
        android.util.Log.i("PoldyPrivacy","window_policy:blocked="+blocked+", epoch="+epoch);
        IPrivacyListener listener=privacyListener;
        if(listener!=null)try{listener.onPrivacyChanged(blocked,epoch);}catch(android.os.RemoteException ignored){}
    }
    @Override public void watchPrivacy(IPrivacyListener listener){
        synchronized(privacy){
            privacyListener=listener;
            if(listener!=null)try{listener.onPrivacyChanged(privacy.blocked(),privacy.epoch());}catch(android.os.RemoteException ignored){}
        }
    }
    private Object stateCallback;
    private StableDisplay stable;
    private HalAngleReader angles;
    private PanelLuminanceReader luminance;
    private TaskProfileReader tasks;
    private final ProfileSwitchGate switching=new ProfileSwitchGate();
    private final PanelRotation rotations=new PanelRotation();

    @SuppressLint("WrongConstant") // Hidden service constant; this class runs in Shizuku's shell process.
    public DisplayControl(Context context) {
        try{ownerUid=context.getPackageManager().getApplicationInfo("dev.poldy.lab",0).uid;}
        catch(android.content.pm.PackageManager.NameNotFoundException e){throw new SecurityException("Foldy owner unavailable",e);}
        stateManager = context.getSystemService("device_state");
        privacy=new WindowPrivacyObserver(context,ownerUid,this::privacyChanged);
        watchClosedCancellation();
        watchdog.scheduleAtFixedRate(() -> {
            synchronized (this) {
                if (ownedState >= 0 && SystemClock.elapsedRealtime() > leaseUntil) reset();
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
    }
    private void watchClosedCancellation() {
        try {
            Class<?> callbackClass=Class.forName("android.hardware.devicestate.DeviceStateManager$DeviceStateCallback");
            stateCallback=java.lang.reflect.Proxy.newProxyInstance(callbackClass.getClassLoader(),new Class<?>[]{callbackClass},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class) {
                    if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
                    if(method.getName().equals("equals"))return proxy==args[0];
                    return "Poldy closed-state observer";
                }
                if(method.getName().equals("onDeviceStateChanged")&&args!=null&&args.length>0) {
                    int id=(int)args[0].getClass().getMethod("getIdentifier").invoke(args[0]);
                    boolean restore;boolean target;long token;
                    synchronized(this) {
                        // The firmware cancels a concurrent override when the base becomes CLOSED.
                        // Restore cover-primary preparation only while this trial still owns a lease.
                        restore=id==closedId&&ownedState>=0&&!settling&&SystemClock.elapsedRealtime()<leaseUntil;
                        target=switching.inner;token=switching.generation;
                    }
                    if(restore)android.util.Log.i("PoldyControl","closed_prewarm_restore:"+requestMode(target,token,true));
                }
                return null;
            });
            stateManager.getClass().getMethod("registerCallback",Executor.class,callbackClass).invoke(stateManager,watchdog,stateCallback);
        } catch(Exception e){android.util.Log.e("PoldyControl","closed_observer_unavailable",e);}
    }
    private String command(String... args) throws Exception {
        Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
        if (!process.waitFor(3, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new Exception("command_timeout"); }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) throw new Exception(output.trim());
        return output;
    }
    private String rotationMode(int displayId) throws Exception {
        return PanelRotation.normalize(command("/system/bin/wm", "user-rotation", "-d", Integer.toString(displayId)));
    }
    private void observeRotationPreferences() throws Exception {
        boolean primaryInner = PhysicalPanels.primaryIsInner();
        String active = rotationMode(0);
        if (rotations.ready()) rotations.observeActive(primaryInner, active);
        else rotations.capture(primaryInner, active, rotationMode(1));
        if(stable!=null)stable.updateRecoveryRotations(rotations.panelMode(true),rotations.panelMode(false));
    }
    private void writeRotationMode(int displayId, String mode) throws Exception {
        String[] parts = PanelRotation.normalize(mode).split(" ");
        if (parts[0].equals("free"))
            command("/system/bin/wm", "user-rotation", "-d", Integer.toString(displayId), "free");
        else
            command("/system/bin/wm", "user-rotation", "-d", Integer.toString(displayId), "lock", parts[1]);
    }
    private boolean restoreRotationPreferences(boolean primaryInner) {
        if (!rotations.ready()) return true;
        try {
            String display0=rotations.mode(primaryInner,0),display1=rotations.mode(primaryInner,1);
            writeRotationMode(0,display0);writeRotationMode(1,display1);
            if(!rotationMode(0).equals(display0)||!rotationMode(1).equals(display1))
                throw new IllegalStateException("Rotation policy verification failed");
            android.util.Log.i("PoldyControl", "panel_rotation_restored:inner=" + primaryInner);
            return true;
        } catch (Exception e) {
            android.util.Log.e("PoldyControl", "panel_rotation_restore_failed", e);
            return false;
        }
    }
    @Override public synchronized String capabilities() {
        try {
            // Only enable automatic switching on the model physically verified in this project.
            String model = command("/system/bin/getprop", "ro.product.model").trim();
            if (!"SM-F971N".equals(model)) return "검증하지 않은 기종: " + model;
            if (stateManager == null) return "기기 상태 서비스에 연결하지 못했습니다.";
            String states = command("/system/bin/cmd", "device_state", "print-states");
            Matcher m = Pattern.compile("identifier=(\\d+), name='([^']+)'").matcher(states);
            while (m.find()) {
                if ("CONCURRENT_INNER_DEFAULT".equals(m.group(2))) innerId = Integer.parseInt(m.group(1));
                if ("CONCURRENT_OUTER_DEFAULT".equals(m.group(2))) outerId = Integer.parseInt(m.group(1));
                if ("OPENED".equals(m.group(2))) openedId = Integer.parseInt(m.group(1));
                if ("CLOSED".equals(m.group(2))) closedId = Integer.parseInt(m.group(1));
            }
            return innerId >= 0 && outerId >= 0 && openedId >= 0 && closedId >= 0 ? "READY" : "지원 상태를 찾지 못했습니다.";
        } catch (Exception e) { return e.getMessage(); }
    }
    @Override public String requestMode(boolean inner,long generation) {return requestMode(inner,generation,false);}
    private String requestMode(boolean inner,long generation,boolean reassert) {
        try {
          synchronized(this) {
            if(privacy.blocked())return "PRIVACY_BLOCKED";
            if(reassert&&!switching.matches(generation,inner))return "STALE";
            if(!reassert&&generation<=switching.generation)return "STALE";
            if (innerId < 0 || outerId < 0) return "화면 상태 검증이 필요합니다.";
            String state = command("/system/bin/cmd", "device_state", "state");
            if (state.contains("Override state:") && (ownedState < 0 || !hasOwnOverride(state)))
                return "다른 화면 상태 요청이 있어 중단했습니다.";
            int desired = inner ? innerId : outerId;
            if(ownedState<0&&stable==null) {
                if(!state.contains("name='CLOSED'")&&!state.contains("name='OPENED'"))return "WAIT_ENDPOINT";
                if(PhysicalPanels.primaryIsInner()!=inner)return "WAIT_ENDPOINT";
                if(command("/system/bin/wm","size","-d","0").contains("Override size:"))return "기존 화면 크기 설정이 있어 시험을 중지했습니다.";
            }
            // CLOSED may arrive before the app's covered reversal reaches Binder.
            // Leave its scene and lease intact; that next generation owns the remap.
            if(reassert&&scene!=null&&!switching.canReassert(PhysicalPanels.primaryIsInner()))return "WAIT_COVER";
            observeRotationPreferences();
            settling = false;
            leaseUntil = SystemClock.elapsedRealtime() + 4000;
            if(stable==null) {
                if(luminance==null)luminance=new PanelLuminanceReader();
                String apk=command("/system/bin/pm","path","dev.poldy.lab").trim();
                if(!apk.startsWith("package:")||apk.contains("\n"))throw new IllegalStateException("Unexpected APK path");
                stable=new StableDisplay(apk.substring(8),rotations.panelMode(true),rotations.panelMode(false));
            }
            // CLOSED can cancel the concurrent request while the cover is already primary.
            // Reasserting that same profile must not move an idle transparent panel to a private stack.
            boolean shielding=scene!=null&&(switching.pending||PhysicalPanels.primaryIsInner()!=inner);
            if(reassert&&shielding&&!switching.pending)return "WAIT_COVER";
            if(!reassert)switching.begin(generation,inner,SystemClock.elapsedRealtime(),shielding);
            if(panelPower==null)panelPower=new PanelPower();
            // The endpoint lease keeps logical display 1 OFF. Turn both logical
            // outputs on before routing the already-presented shield layers, or a
            // fast reversal can expose an unpowered/wrong profile for one frame.
            panelPower.hold();
            if(shielding)scene.shield();
            Class<?> requestClass = Class.forName("android.hardware.devicestate.DeviceStateRequest");
            Object builder = requestClass.getMethod("newBuilder",int.class).invoke(null,desired);
            Object request = builder.getClass().getMethod("build").invoke(builder);
            // This firmware can retain a dead client request; an independent guard is required.
            if(ownedState!=desired||!hasOwnOverride(state))
                stateManager.getClass().getMethod("requestState",requestClass,Executor.class,
                    Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback"))
                    .invoke(stateManager,request,null,null);
            ownedState = desired;
          }
            // Never hold the capture/lease/reset lock while waiting for a display traversal.
            long until=SystemClock.elapsedRealtime()+1800;
            while(SystemClock.elapsedRealtime()<until) {
                synchronized(this) {
                    if(!switching.matches(generation,inner)||stable==null)return "STALE";
                    if(privacy.blocked())return "PRIVACY_BLOCKED";
                    if(!stable.unlocked())throw new IllegalStateException("Locked during profile change");
                    boolean ready=stable.profileReady(inner);switching.observe(ready,SystemClock.elapsedRealtime());
                    if(ready){
                        if(!restoreRotationPreferences(inner))throw new IllegalStateException("Panel rotation restore failed");
                        android.util.Log.i("PoldyControl","native_profile_ready:generation="+generation+", inner="+inner);return "OK";
                    }
                    stable.renew();
                }
                Thread.sleep(12);
            }
            throw new IllegalStateException("Native display profile did not settle");
        } catch (Exception e) {
            synchronized(this){if(switching.matches(generation,inner))reset();}
            Throwable reason=e;
            while (reason.getCause()!=null) reason=reason.getCause();
            return reason.getClass().getSimpleName()+": "+reason.getMessage();
        }
    }
    @Override public synchronized String finishMode(boolean inner,long generation) {
        if(!switching.matches(generation,inner)||stable==null||ownedState<0)return "STALE";
        try {
            if(SystemClock.elapsedRealtime()>=leaseUntil||!stable.unlocked())throw new IllegalStateException("Display lease ended or locked");
            boolean ready=stable.profileReady(inner);switching.observe(ready,SystemClock.elapsedRealtime());
            if(!ready||!switching.canFinish(generation,SystemClock.elapsedRealtime()))return "WAIT";
            if(scene!=null&&switching.pending)scene.finishSwitch(inner);
            switching.finished();
            android.util.Log.i("PoldyControl","native_output_released:generation="+generation+", inner="+inner);
            return "OK";
        }catch(Exception e){reset();return e.toString();}
    }
    private boolean hasOwnOverride(String state) {
        Matcher m = Pattern.compile("Override state: DeviceState\\{identifier=(\\d+)").matcher(state);
        return m.find() && Integer.parseInt(m.group(1)) == ownedState;
    }
    @Override public synchronized void renew() {
        if(stable==null)return;
        leaseUntil=SystemClock.elapsedRealtime()+4000;
        try {
            if(panelPower!=null){if(ownedState>=0)panelPower.hold();else panelPower.keepEndpoint();}
            stable.renew();
        }
        catch(Exception e){reset();android.util.Log.e("PoldyControl","panel_power_lease_failed",e);}
    }
    @Override public CapturedFrame captureFrame(boolean inner,android.view.SurfaceControl[] excluded) {
        try {
            synchronized(this) {
                if(innerId<0 || outerId<0)throw new IllegalStateException("Model verification required");
                if(privacy.blocked())return privacyFrame();
                if(scene!=null)scene.route(PhysicalPanels.primaryIsInner());
                if(frameCapture==null)frameCapture=new NativeFrameCapture();
            }
            synchronized(this) {
                if(stable==null||privacy.blocked())return privacyFrame();
                long privacyEpoch=privacy.epoch();
                if(!stable.unlocked())throw new IllegalStateException("Display locked");
                if(tasks==null)tasks=new TaskProfileReader();
                boolean profileReady=stable.profileReady(switching.inner);
                switching.observe(profileReady,SystemClock.elapsedRealtime());
                TaskProfileReader.Profile before=tasks.read();
                // Capture the composed physical panel so a landscape task is returned
                // in the panel's native pixel orientation (the animation buffers stay
                // 2448x1848 and 1248x1972 in every app orientation).
                CapturedFrame result=frameCapture.capture(stable.isInner(),excluded);
                try {
                    if(privacy.blocked()||privacyEpoch!=privacy.epoch()){result.close();return privacyFrame();}
                    TaskProfileReader.Profile after=tasks.read();
                    result.generation=switching.generation;result.inner=stable.isInner();
                    boolean same=before.owner()!=null&&before.equals(after);
                    result.owner=same?after.owner():null;
                    result.nativeReady=profileReady&&same&&after.ready()&&after.inner()==result.inner;
                    if(privacy.blocked()||privacyEpoch!=privacy.epoch()){result.close();return privacyFrame();}
                    return result;
                }catch(Exception e){result.close();throw e;}
            }
        } catch(Exception e) {
            CapturedFrame result=new CapturedFrame();result.error=e.toString();return result;
        } finally {
            // The incoming Binder parcels own native references independent of the app's originals.
            if(excluded!=null)for(android.view.SurfaceControl layer:excluded)if(layer!=null)layer.release();
        }
    }
    private CapturedFrame privacyFrame(){
        NativeScene current=scene;if(current!=null)current.suppress();
        CapturedFrame result=new CapturedFrame();result.error="PRIVACY_BLOCKED";return result;
    }
    @Override public synchronized android.view.SurfaceControl[] createScene(boolean inner) {
        if(privacy.blocked()||ownedState<0||SystemClock.elapsedRealtime()>=leaseUntil)return null;
        try {if(scene!=null)scene.close();scene=new NativeScene(PhysicalPanels.primaryIsInner(),luminance);
            if(privacy.blocked()){scene.suppress();scene.close();scene=null;return null;}return scene.copies();}
        catch(Exception e){if(scene!=null)scene.close();scene=null;android.util.Log.e("PoldyControl","scene_create_failed",e);return null;}
    }
    @Override public synchronized void routeScene(boolean inner) {
        if(scene==null||ownedState<0||SystemClock.elapsedRealtime()>=leaseUntil)return;
        try {scene.route(PhysicalPanels.primaryIsInner());}catch(Exception e){reset();}
    }
    @Override public synchronized String startAngles(IHingeAngleListener listener){
        if(ownedState<0||scene==null||SystemClock.elapsedRealtime()>=leaseUntil)return "화면 시험을 먼저 시작해 주세요.";
        if(listener==null)return "각도 수신 연결이 없습니다.";
        stopAngles();
        try {angles=new HalAngleReader(listener);return "OK";}
        catch(Exception e){return "각도 읽기 시작 실패: "+e.getClass().getSimpleName();}
    }
    @Override public synchronized void stopAngles(){if(angles!=null){angles.close();angles=null;}}
    @Override public synchronized void settle(boolean fullyOpen) {
        if(ownedState>=0) {
            // Profile changes require FoldService's two-panel cover and generation.
            // A legacy settle call must not start an unfenced private shield.
            if(switching.inner!=fullyOpen||switching.pending){reset();return;}
            try {
                // The transition has been revealed on logical display 0. Let the
                // firmware return to its physical endpoint policy so the other
                // panel powers off, while keeping the prepared scene for the next fold.
                settling=true;
                if(panelPower!=null)panelPower.settle();
                stateManager.getClass().getMethod("cancelStateRequest").invoke(stateManager);
                ownedState=-1;
                long until=SystemClock.elapsedRealtime()+700;
                while(SystemClock.elapsedRealtime()<until) {
                    if(!command("/system/bin/cmd","device_state","state").contains("Override state:"))break;
                    Thread.sleep(12);
                }
                if(!restoreRotationPreferences(fullyOpen))throw new IllegalStateException("Panel rotation restore failed");
                if(stable!=null)stable.renew();
                android.util.Log.i("PoldyControl","endpoint_power_released:inner="+fullyOpen);
            } catch(Exception e) {
                android.util.Log.e("PoldyControl","endpoint_power_release_failed",e);
                reset();
            }
        }
    }
    @Override public synchronized void reset() {resetInternal(true);}
    @Override public synchronized void sleep() {if(scene!=null)scene.suppress();resetInternal(false);}
    private void resetInternal(boolean keepActivePanelAwake) {
        switching.reset();
        stopAngles();
        StableDisplay retiring=stable;
        boolean stateRestored=true;
        if(ownedState>=0) {
            try {
                // Keep the native scene covering both physical panels until the
                // firmware has actually dropped our concurrent-display override.
                stateManager.getClass().getMethod("cancelStateRequest").invoke(stateManager);
                ownedState=-1;settling=false;
                long until=SystemClock.elapsedRealtime()+700;
                while(SystemClock.elapsedRealtime()<until) {
                    if(!command("/system/bin/cmd","device_state","state").contains("Override state:"))break;
                    Thread.sleep(12);
                }
                if(command("/system/bin/cmd","device_state","state").contains("Override state:"))
                    throw new IllegalStateException("Device-state override remained after cancellation");
            } catch(Exception e) {
                stateRestored=false;
                android.util.Log.e("PoldyControl","state_restore_failed",e);
            }
        }
        boolean rotationRestored=false;
        try { rotationRestored=restoreRotationPreferences(PhysicalPanels.primaryIsInner()); }
        catch(Exception e) { android.util.Log.e("PoldyControl", "panel_rotation_endpoint_unknown", e); }
        stable=null;
        if(retiring!=null)retiring.close();
        boolean routeRestored=true;
        if(scene!=null){routeRestored=scene.closeRestoring();scene=null;}
        if(luminance!=null){luminance.close();luminance=null;}
        if(panelPower!=null){if(keepActivePanelAwake)panelPower.releaseGracefully();else panelPower.release();}
        if(retiring!=null){
            if(stateRestored&&rotationRestored&&routeRestored)retiring.disarm();
            else retiring.triggerRecovery();
        }
        if(keepActivePanelAwake)rotations.clear();
    }
    @Override public void destroy() {
        privacy.close();reset(); watchdog.shutdownNow(); System.exit(0);
    }
}
