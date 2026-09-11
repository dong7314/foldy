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
    private int settlingBase = -1;
    private final Object stateManager;
    private NativeFrameCapture frameCapture;
    private PanelPower panelPower;
    private NativeScene scene;
    private Object stateCallback;
    private StableDisplay stable;
    private HalAngleReader angles;

    @SuppressLint("WrongConstant") // Hidden service constant; this class runs in Shizuku's shell process.
    public DisplayControl(Context context) {
        stateManager = context.getSystemService("device_state");
        watchClosedCancellation();
        watchdog.scheduleAtFixedRate(() -> {
            synchronized (this) {
                if (ownedState >= 0 && settling) {
                    try {
                        String state=command("/system/bin/cmd","device_state","state");
                        Matcher base=Pattern.compile("Base state: DeviceState\\{identifier=(\\d+)").matcher(state);
                        if (base.find() && Integer.parseInt(base.group(1))==settlingBase) reset();
                    } catch (Exception ignored) {}
                }
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
                    synchronized(this) {
                        // The firmware cancels a concurrent override when the base becomes CLOSED.
                        // Restore cover-primary preparation only while this trial still owns a lease.
                        if(id==closedId&&ownedState>=0&&!settling&&SystemClock.elapsedRealtime()<leaseUntil) {
                            String result=requestMode(stable!=null&&stable.isInner());
                            android.util.Log.i("PoldyControl","closed_prewarm_restore:"+result);
                        }
                    }
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
    @Override public synchronized String requestMode(boolean inner) {
        try {
            if (innerId < 0 || outerId < 0) return "화면 상태 검증이 필요합니다.";
            String state = command("/system/bin/cmd", "device_state", "state");
            if (state.contains("Override state:") && (ownedState < 0 || !hasOwnOverride(state)))
                return "다른 화면 상태 요청이 있어 중단했습니다.";
            int desired = inner ? innerId : outerId;
            if(ownedState<0) {
                if(!state.contains("name='CLOSED'"))return "이번 시험은 완전히 접은 상태에서 시작해 주세요.";
                if(command("/system/bin/wm","size","-d","0").contains("Override size:"))return "기존 화면 크기 설정이 있어 시험을 중지했습니다.";
            }
            settling = false;
            leaseUntil = SystemClock.elapsedRealtime() + 4000;
            if(ownedState==desired && hasOwnOverride(state)) {
                if(stable!=null)stable.resize(inner);
                return "OK";
            }
            if(stable==null) {
                String apk=command("/system/bin/pm","path","dev.poldy.lab").trim();
                if(!apk.startsWith("package:")||apk.contains("\n"))throw new IllegalStateException("Unexpected APK path");
                stable=new StableDisplay(apk.substring(8));
            }
            // CLOSED can cancel the concurrent request while the cover is already primary.
            // Reasserting that same profile must not move an idle transparent panel to a private stack.
            boolean shielding=scene!=null&&PhysicalPanels.primaryIsInner()!=inner;
            if(shielding)scene.shield();
            long switchStarted=SystemClock.elapsedRealtime();
            if(panelPower==null)panelPower=new PanelPower();
            panelPower.hold();
            Class<?> requestClass = Class.forName("android.hardware.devicestate.DeviceStateRequest");
            Object builder = requestClass.getMethod("newBuilder",int.class).invoke(null,desired);
            Object request = builder.getClass().getMethod("build").invoke(builder);
            // This firmware can retain a dead client request; an independent guard is required.
            stateManager.getClass().getMethod("requestState",requestClass,Executor.class,
                Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback"))
                .invoke(stateManager,request,null,null);
            ownedState = desired;
            stable.resize(inner);
            if(shielding) {
                // DMS can perform a late traversal after its new DisplayInfo becomes visible.
                while(SystemClock.elapsedRealtime()-switchStarted<650){stable.renew();Thread.sleep(10);}
                stable.resize(inner);scene.finishSwitch(inner);
            }
            return "OK";
        } catch (Exception e) {
            reset();
            Throwable reason=e;
            while (reason.getCause()!=null) reason=reason.getCause();
            return reason.getClass().getSimpleName()+": "+reason.getMessage();
        }
    }
    private boolean hasOwnOverride(String state) {
        Matcher m = Pattern.compile("Override state: DeviceState\\{identifier=(\\d+)").matcher(state);
        return m.find() && Integer.parseInt(m.group(1)) == ownedState;
    }
    @Override public synchronized void renew() {
        if(ownedState<0)return;
        leaseUntil=SystemClock.elapsedRealtime()+4000;
        try {if(panelPower!=null)panelPower.hold();if(stable!=null)stable.renew();}
        catch(Exception e){reset();android.util.Log.e("PoldyControl","panel_power_lease_failed",e);}
    }
    @Override public CapturedFrame captureFrame(boolean inner,android.view.SurfaceControl[] excluded) {
        try {
            synchronized(this) {
                if(innerId<0 || outerId<0)throw new IllegalStateException("Model verification required");
                if(scene!=null)scene.route(PhysicalPanels.primaryIsInner());
                if(frameCapture==null)frameCapture=new NativeFrameCapture();
            }
            synchronized(this) {
                if(stable!=null)return stable.capture(frameCapture);
                return frameCapture.capture(inner,excluded);
            }
        } catch(Exception e) {
            CapturedFrame result=new CapturedFrame();result.error=e.toString();return result;
        } finally {
            // The incoming Binder parcels own native references independent of the app's originals.
            if(excluded!=null)for(android.view.SurfaceControl layer:excluded)if(layer!=null)layer.release();
        }
    }
    @Override public synchronized android.view.SurfaceControl[] createScene(boolean inner) {
        if(ownedState<0||SystemClock.elapsedRealtime()>=leaseUntil)return null;
        try {if(scene!=null)scene.close();scene=new NativeScene(PhysicalPanels.primaryIsInner());return scene.copies();}
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
        if (ownedState >= 0) {
            // The actual endpoint wins even after an undetectable reversal inside the 90 posture.
            settlingBase=fullyOpen?openedId:closedId;
            int desired=fullyOpen?innerId:outerId;
            if (ownedState!=desired) requestMode(fullyOpen);
            settling=true; leaseUntil=SystemClock.elapsedRealtime()+4000;
        }
    }
    @Override public synchronized void reset() {
        stopAngles();
        StableDisplay retiring=stable;stable=null;
        if(retiring!=null)retiring.close();
        if(scene!=null){scene.close();scene=null;}
        if(panelPower!=null)panelPower.release();
        if (ownedState < 0) {if(retiring!=null)retiring.disarm();return;}
        try {
            stateManager.getClass().getMethod("cancelStateRequest").invoke(stateManager);
            ownedState = -1;
            settling = false;
            settlingBase = -1;
            if(retiring!=null)retiring.disarm();
        } catch (Exception ignored) { /* Watchdog retries; do not forget ownership on failure. */ }
    }
    @Override public void destroy() {
        reset(); watchdog.shutdownNow(); System.exit(0);
    }
}
