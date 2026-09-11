package dev.poldy.lab;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.hardware.*;
import android.hardware.display.DisplayManager;
import android.media.projection.*;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.view.*;
import java.util.ArrayList;

/** Both panels stay prepared; native capture excludes our GPU layers. */
public final class FoldService extends Service implements SensorEventListener,DisplayManager.DisplayListener {
    public static final String STOP="dev.poldy.lab.FOLD_STOP";
    static final String DIAGNOSE="dev.poldy.lab.FOLD_DIAGNOSE";
    public static volatile boolean running;
    public static volatile String status="애니메이션 대기";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final FoldSignals signals=new FoldSignals();
    private final TransferGate transfer=new TransferGate();
    private DisplayManager displays;private SensorManager sensors;
    private MediaProjection projection;
    private NativePanel innerPanel,outerPanel;
    private float primaryOpacity;
    private Bitmap latest,outgoingFrame,heldTarget;
    private final FrameRetirement<Bitmap> retiredFrames=new FrameRetirement<>();
    private boolean snapshotPending,pendingReveal,releasePending;
    private boolean active,moving,curtain,capturing;
    private float frost=1,contentMix;
    private long transitionAt;
    private int frameCount;
    private int startupCaptureRetries;
    private long diagnosticGeneration;
    private FrameTween finish,handoff;
    private boolean logicalInner;
    private long lastAngleNanos;
    private float measuredAngle;
    private int angleSamples;
    private final FoldAttitude attitude=new FoldAttitude();
    private float opticalAngle;
    private final AngleSmoother visual=new AngleSmoother();
    private final HingeProgress progress=new HingeProgress();
    private float pendingOpticsAnchor=Float.NaN;
    private Choreographer choreographer;
    private boolean renderQueued;
    private final Choreographer.FrameCallback vsync=time->{if(renderQueued){renderQueued=false;main.removeCallbacks(this.renderFallback);renderNow();}};
    private final Runnable renderFallback=()->{if(renderQueued){renderQueued=false;choreographer.removeFrameCallback(vsync);renderNow();}};
    private int renderedFrames;
    private long renderCost;
    private float gravityX,gravityY=1,flatness;
    private final IHingeAngleListener angleListener=new IHingeAngleListener.Stub(){
        @Override public void onAngle(long stamp,float angle,String source){main.post(()->acceptAngle(stamp,angle,source));}
        @Override public void onStopped(String reason){main.post(()->{if(active)fail(reason);});}
    };
    private final MediaProjection.Callback projectionCallback=new MediaProjection.Callback(){
        @Override public void onStop(){stopSelf();}
    };
    private final Runnable lease=new Runnable(){
        @Override public void run(){
            if(!active)return;if(locked()){stopSelf();return;}
            ControlBridge.renew();main.postDelayed(this,1000);
        }
    };
    @Override public void onCreate(){
        super.onCreate();choreographer=Choreographer.getInstance();displays=getSystemService(DisplayManager.class);
        sensors=getSystemService(SensorManager.class);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("fold","접힘 애니메이션",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(DIAGNOSE.equals(intent.getAction())) {
            if(active)diagnoseTransition(Math.max(3000,Math.min(20000,intent.getLongExtra("holdMillis",3000))),intent.getBooleanExtra("reverse",false));
            else stopSelf();
            return START_NOT_STICKY;
        }
        if(active)return START_NOT_STICKY;
        PendingIntent stop=PendingIntent.getService(this,3,new Intent(this,FoldService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE);
        startForeground(2,new Notification.Builder(this,"fold").setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Poldy · 두 화면 연결 시험").setContentText("두 패널을 준비하고 화면을 기기 안에서 처리합니다. 최대 5분.")
            .setOngoing(true).addAction(new Notification.Action.Builder(null,"중지",stop).build())
            .setContentIntent(PendingIntent.getActivity(this,4,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE)).build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        try{
            if(!Settings.canDrawOverlays(this)||!ControlBridge.ready())throw new IllegalStateException("화면 제어와 표시 권한을 확인해 주세요.");
            Intent consent=intent.getParcelableExtra("consent",Intent.class);
            if(consent==null)throw new IllegalStateException("화면 공유 동의가 필요합니다.");
            // The approved sharing session gates the lifetime of shell capture.
            projection=getSystemService(MediaProjectionManager.class).getMediaProjection(intent.getIntExtra("result",Activity.RESULT_CANCELED),consent);
            projection.registerCallback(projectionCallback,main);
            active=true;running=true;logicalInner=false;transfer.inner=false;displays.registerDisplayListener(this,main);
            Sensor sensor=sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE);
            if(sensor==null||!sensors.registerListener(this,sensor,20_000,main))throw new IllegalStateException("접힘 신호를 읽지 못했습니다.");
            Sensor gyro=sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if(gyro!=null)sensors.registerListener(this,gyro,20_000,main);
            Sensor gravity=sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
            if(gravity!=null)sensors.registerListener(this,gravity,40_000,main);
            main.post(lease);main.postDelayed(this::stopSelf,300_000);
            main.postDelayed(()->{if(active&&latest==null)fail("두 화면의 첫 프레임을 준비하지 못했습니다.");},3500);
            ControlBridge.switchTo(isInner(),result->{
                if(!active)return;if(!"OK".equals(result)){fail(result);return;}
                ControlBridge.createScene(isInner(),(panels,error)->{
                    if(!active){if(panels!=null)for(SurfaceControl p:panels)p.release();return;}
                    if(panels==null||panels.length!=2){fail("GPU 화면 준비 실패: "+error);return;}
                    try {
                        innerPanel=new NativePanel(panels[0],2448,1848);outerPanel=new NativePanel(panels[1],1248,1972);
                        captureNext();status="두 화면 준비됨 · 접힘 각도 수신 대기";
                        ControlBridge.startAngles(angleListener,resultAngle->{if(active&&!"OK".equals(resultAngle))fail(resultAngle);});
                        Log.i("PoldyFold","native_started:native_profiles");
                    } catch(RuntimeException e){for(SurfaceControl p:panels)p.release();fail(e.toString());}
                });
            });
        }catch(RuntimeException e){fail(e.toString());}
        return START_NOT_STICKY;
    }
    private boolean locked(){return getSystemService(KeyguardManager.class).isKeyguardLocked();}
    private boolean isInner(){return logicalInner;}
    private void acceptAngle(long stamp,float angle,String source){
        long now=SystemClock.elapsedRealtimeNanos();
        if(!active||stamp<=lastAngleNanos||stamp>now||now-stamp>HalAngleSample.MAX_AGE_NANOS
            ||!Float.isFinite(angle)||angle<0||angle>180)return;
        lastAngleNanos=stamp;measuredAngle=angle;angleSamples++;
        HingeProgress.Change change=progress.sample(angle,SystemClock.uptimeMillis());
        applyProgress(change);
        if(progress.active()){moving=true;pendingOpticsAnchor=Float.NaN;visual.measuredTarget(angle,stamp);render();}
        status="센서 각도 "+Math.round(angle)+"° · "+(moving?"움직임에 따라 효과 적용 중":"다음 움직임 대기");
        Log.i("PoldyAngle","sample:angle="+angle+", sensor_ns="+stamp+", age_ms="+((now-stamp)/1e6)
            +", visual="+opticalAngle+", moving="+moving+", source="+source);
    }
    private void renderMotion(){render();}
    private void anchorOptics(boolean inner){
        Log.i("PoldyFold","endpoint_correction:target="+(inner?180:0)+", measured="+measuredAngle+", visual="+opticalAngle
            +", sample_age_ms="+(lastAngleNanos==0?-1:(SystemClock.elapsedRealtimeNanos()-lastAngleNanos)/1e6));
        pendingOpticsAnchor=inner?180:0;
        if(inner==isInner()&&transfer.phase==TransferGate.Phase.READY&&handoff==null){
            visual.target(pendingOpticsAnchor,true);pendingOpticsAnchor=Float.NaN;
        }
        render();
    }
    private void captureNext(){
        if(!active||capturing)return;if(locked()){stopSelf();return;}
        if(innerPanel==null||outerPanel==null)return;
        ArrayList<SurfaceControl> layers=new ArrayList<>();layers.add(innerPanel.control);layers.add(outerPanel.control);
        boolean inner=isInner();long generation=transfer.generation;capturing=true;long captureStarted=SystemClock.uptimeMillis();
        ControlBridge.capture(inner,layers.toArray(new SurfaceControl[0]),(bitmap,elapsed,baseState,error)->{
            capturing=false;if(!active){if(bitmap!=null)bitmap.recycle();return;}
            if(bitmap==null){
                // The mirror's first Surface transaction can finish after createScene returns.
                if(latest==null&&error!=null&&error.contains("No logical buffer")&&startupCaptureRetries++<12){
                    Log.i("PoldyCapture","waiting_for_first_buffer:"+startupCaptureRetries);
                    main.postDelayed(this::captureNext,75);return;
                }
                fail("화면 캡처 실패: "+error);return;
            }
            if(generation!=transfer.generation||inner!=isInner()){bitmap.recycle();main.post(this::captureNext);return;}
            Bitmap old=latest;latest=bitmap;retire(old);
            boolean correctSize=bitmap.getWidth()==(inner?2448:1248)&&bitmap.getHeight()==(inner?1848:1972);
            if(correctSize&&SystemClock.uptimeMillis()-transitionAt>=280&&transfer.frame(generation,inner)){
                Log.i("PoldyFold","destination_ready:"+bitmap.getWidth()+"x"+bitmap.getHeight()+", elapsed="+(SystemClock.uptimeMillis()-transitionAt));
                startHandoff();
            }
            render();
            if(++frameCount%30==1)Log.i("PoldyCapture","frame:"+bitmap.getWidth()+"x"+bitmap.getHeight()+", gpu="+bitmap.getConfig()+", capture_ms="+elapsed+", excluded="+layers.size());
            FoldSignals.Action early=signals.acceptBaseState(baseState);
            if(early!=FoldSignals.Action.NONE){
                Log.i("PoldyFold","base_signal:"+baseState+", action="+early);
                handleSignal(early,false);
            }
            long cadence=moving||curtain?16:80;
            main.postDelayed(this::captureNext,Math.max(0,cadence-(SystemClock.uptimeMillis()-captureStarted)));
        });
    }
    private NativePanel primary(){return isInner()?innerPanel:outerPanel;}
    private NativePanel secondary(){return isInner()?outerPanel:innerPanel;}
    private void render(){
        if(!active||renderQueued)return;renderQueued=true;
        choreographer.postFrameCallback(vsync);main.postDelayed(renderFallback,24);
    }
    private void renderNow(){
        if(!active||snapshotPending||latest==null||innerPanel==null||outerPanel==null)return;
        long renderStarted=SystemClock.uptimeMillis();
        applyProgress(progress.tick(renderStarted));
        opticalAngle=visual.step(renderStarted);
        attitude.step(renderStarted);
        NativePanel primary=primary(),secondary=secondary();boolean inner=isInner();
        innerPanel.optics(FoldOptics.at(true,opticalAngle));
        outerPanel.optics(FoldOptics.at(false,opticalAngle));
        innerPanel.orientation(gravityX,gravityY,flatness);outerPanel.orientation(gravityX,gravityY,flatness);
        innerPanel.attitude(attitude.pitch(),attitude.yaw(),attitude.roll());
        outerPanel.attitude(attitude.pitch(),attitude.yaw(),attitude.roll());
        if(curtain){
            if(inner!=transfer.inner){
                if(primary.matches(outgoingFrame))primary.frame(outgoingFrame,inner,frost,0);
            }else if(transfer.phase!=TransferGate.Phase.READY||!primary.matches(latest))primary.hold(heldTarget);
            else primary.reveal(heldTarget,latest,contentMix,inner,frost,0);
        } else primary.frame(latest,inner,0,0); // Keep a valid buffer even while its opacity is zero.
        if(curtain){
            if(inner!=transfer.inner)secondary.hold(heldTarget);
            else if(secondary.matches(outgoingFrame))secondary.frame(outgoingFrame,!inner,1,0);
        }else secondary.frame(latest,!inner,1,secondary.matches(latest)?0:1);
        primary.opacity(primaryOpacity);secondary.opacity(1);
        if(!primary.healthy||!secondary.healthy)fail("GPU 화면 표시 오류로 시험을 중지했습니다.");
        renderCost+=SystemClock.uptimeMillis()-renderStarted;
        if(++renderedFrames%60==0){Log.i("PoldyCapture","render_mean_ms:"+(renderCost/60f));renderCost=0;}
        if(pendingReveal&&!moving&&!releasePending&&visual.settled()&&primary.matches(latest)){
            releasePending=true;long token=transfer.generation;
            primary.whenVisible(getMainExecutor(),()->{if(active&&token==transfer.generation&&!moving)completeReveal();});
        }
        if(visual.animating())render();
        retiredFrames.collect(SystemClock.uptimeMillis(),frame->frame==latest||frame==outgoingFrame||frame==heldTarget
            ||(innerPanel!=null&&innerPanel.references(frame))||(outerPanel!=null&&outerPanel.references(frame)),FoldService::recycle);
    }
    private void retire(Bitmap frame){retiredFrames.retire(frame,SystemClock.uptimeMillis());}
    private static void recycle(Bitmap frame){if(frame!=null&&!frame.isRecycled())frame.recycle();}
    @Override public void onSensorChanged(SensorEvent event){
        if(!active||event.values.length==0||!Float.isFinite(event.values[0]))return;
        if(event.sensor.getType()==Sensor.TYPE_GRAVITY){
            if(event.values.length<3||!Float.isFinite(event.values[1])||!Float.isFinite(event.values[2]))return;
            float x=event.values[0]/SensorManager.GRAVITY_EARTH,y=event.values[1]/SensorManager.GRAVITY_EARTH,z=Math.abs(event.values[2])/SensorManager.GRAVITY_EARTH;
            gravityX+=(x-gravityX)*.2f;gravityY+=(y-gravityY)*.2f;flatness+=(z-flatness)*.2f;
            if(moving||curtain)renderMotion();return;
        }
        if(event.sensor.getType()==Sensor.TYPE_GYROSCOPE){
            if(event.values.length<3||!Float.isFinite(event.values[1])||!Float.isFinite(event.values[2]))return;
            attitude.gyro(event.timestamp,event.values[0],event.values[1],event.values[2]);
            if(moving||curtain)renderMotion();
            return;
        }
        handleSignal(signals.accept(event.values[0]),event.values[0]>=178);
    }
    private void handleSignal(FoldSignals.Action action,boolean endpointInner){
        if(action!=FoldSignals.Action.NONE){
            diagnosticGeneration++;
            Log.i("PoldyFold","posture_action:"+action+", endpoint_inner="+endpointInner);
        }
        if(action==FoldSignals.Action.OPEN||action==FoldSignals.Action.CLOSE){
            progress.begin();
            startGesture(action==FoldSignals.Action.OPEN);
        }
        else if(action==FoldSignals.Action.REST){
            HingeProgress.Change change=progress.posture(endpointInner,SystemClock.uptimeMillis());
            if(change==HingeProgress.Change.NONE)
                Log.i("PoldyAngle","endpoint_deferred:target="+(endpointInner?180:0)+", measured="+measuredAngle);
            applyProgress(change);
            // A real coarse endpoint can repair a reversed viewport while HAL optics keep moving.
            if(endpointInner!=isInner()||(transfer.phase!=TransferGate.Phase.READY&&transfer.inner!=endpointInner))beginTransfer(endpointInner);
        }
    }
    private void startGesture(boolean inner){
        boolean alreadyMoving=moving;moving=true;
        if(!alreadyMoving)attitude.begin();
        pendingOpticsAnchor=Float.NaN;
        if(lastAngleNanos>0&&SystemClock.elapsedRealtimeNanos()-lastAngleNanos<=HalAngleSample.MAX_AGE_NANOS)
            visual.target(measuredAngle,false);
        main.postDelayed(()->{if(active&&moving&&angleSamples==0)fail("접힘 각도 값을 받지 못해 시험을 중지했습니다.");},6000);
        // A later 0/90/180 event acknowledges an early HAL departure, not a second transfer.
        if(!alreadyMoving||transfer.inner!=inner)beginTransfer(inner);
    }
    private void applyProgress(HingeProgress.Change change){
        if(change==HingeProgress.Change.START_OPEN||change==HingeProgress.Change.START_CLOSE){
            diagnosticGeneration++;
            Log.i("PoldyAngle","measured_departure:"+change+", angle="+measuredAngle);
            startGesture(change==HingeProgress.Change.START_OPEN);
        }else if(change==HingeProgress.Change.FINISH_OPEN||change==HingeProgress.Change.FINISH_CLOSE){
            moving=false;boolean inner=change==HingeProgress.Change.FINISH_OPEN;
            attitude.rest();anchorOptics(inner);
            if(transfer.phase==TransferGate.Phase.READY)finishReveal();
        }
    }
    private void diagnoseTransition(long holdMillis,boolean reverse) {
        if(latest==null||moving||curtain)return;
        boolean initialInner=isInner();long token=++diagnosticGeneration;
        moving=false;anchorOptics(!initialInner);beginTransfer(!initialInner);
        Log.i("PoldyFold","diagnostic_transition:initial="+initialInner);
        if(reverse){
            main.postDelayed(()->{
                if(!active||token!=diagnosticGeneration)return;
                moving=false;anchorOptics(initialInner);beginTransfer(initialInner);
                Log.i("PoldyFold","diagnostic_reverse:160ms");
            },160);
            main.postDelayed(()->{
                if(!active||token!=diagnosticGeneration)return;
                moving=false;anchorOptics(!initialInner);beginTransfer(!initialInner);
                Log.i("PoldyFold","diagnostic_reverse:380ms");
            },380);
        }
        main.postDelayed(()->{
            if(!active||token!=diagnosticGeneration)return;
            moving=false;anchorOptics(initialInner);beginTransfer(initialInner);
        },holdMillis);
    }
    private void beginTransfer(boolean inner){
        if(locked()){stopSelf();return;}
        if(latest==null){fail("전환 전 화면이 준비되지 않았습니다. 다시 시작해 주세요.");return;}
        if(!curtain)renderNow();
        cancelAnimations();
        Bitmap held=primary().matches(latest)?latest:primary().matches(outgoingFrame)?outgoingFrame:null;
        Bitmap retired=outgoingFrame;outgoingFrame=held;
        retire(retired);
        long token=transfer.begin(inner);transitionAt=SystemClock.uptimeMillis();snapshotPending=true;
        NativePanel target=inner?innerPanel:outerPanel;
        target.snapshot(main,snapshot->{
            if(!active||token!=transfer.generation){if(snapshot!=null)snapshot.recycle();return;}
            snapshotPending=false;
            if(snapshot==null){fail("현재 패널 화면을 보관하지 못해 시험을 중지했습니다.");return;}
            Bitmap oldHold=heldTarget;heldTarget=snapshot;
            retire(oldHold);
            curtain=true;frost=1;contentMix=0;setPrimaryOpacity(1);
            status=inner?"펼침 · 내부 화면 연결 중":"접힘 · 외부 화면 연결 중";renderNow();
            Log.i("PoldyFold","panel_hold_ready:"+snapshot.getWidth()+"x"+snapshot.getHeight()+", elapsed="+(SystemClock.uptimeMillis()-transitionAt));
            // Keep each panel's own presented image until its new native-size layout is ready.
            secondary().whenVisible(getMainExecutor(),()->primary().whenVisible(getMainExecutor(),()->{
                if(!active||!transfer.covered(token))return;
                Log.i("PoldyFold","curtain_presented:target="+(inner?"inner":"outer")+", elapsed="+(SystemClock.uptimeMillis()-transitionAt));
                ControlBridge.switchTo(inner,result->{
                    if(!active||token!=transfer.generation)return;
                    if(!"OK".equals(result)){fail(result);return;}
                    logicalInner=inner;render();captureNext();
                });
            }));
        });
        main.postDelayed(()->{if(active&&token==transfer.generation&&transfer.phase!=TransferGate.Phase.READY)fail("새 화면을 준비하지 못해 시험을 중지했습니다.");},2500);
    }
    private void setPrimaryOpacity(float opacity){
        primaryOpacity=opacity;if(innerPanel!=null&&outerPanel!=null)primary().opacity(opacity);
    }
    private void cancelAnimations(){
        pendingReveal=false;releasePending=false;
        if(finish!=null){finish.cancel();finish=null;}
        if(handoff!=null){handoff.cancel();handoff=null;}
    }
    private void startHandoff(){
        if(handoff!=null)handoff.cancel();
        if(Float.isFinite(pendingOpticsAnchor)){visual.target(pendingOpticsAnchor,true);pendingOpticsAnchor=Float.NaN;}
        handoff=new FrameTween(main,240,t->{contentMix=t;render();},()->{
            handoff=null;contentMix=1;Log.i("PoldyFold","handoff_finished");if(!moving)finishReveal();
        });handoff.start();
    }
    private void finishReveal(){
        if(!curtain||handoff!=null||finish!=null)return;
        pendingReveal=true;render();
    }
    private void completeReveal(){
        pendingReveal=false;releasePending=false;setPrimaryOpacity(0);curtain=false;finish=null;
        status="센서 각도로 화면 연결 중 · 다음 움직임 대기";
        Bitmap retired=outgoingFrame,retiredHold=heldTarget;outgoingFrame=null;heldTarget=null;render();
        retire(retired);retire(retiredHold);
        Log.i("PoldyFold","reveal_finished:panel="+(isInner()?"inner":"outer")+", visual_angle="+opticalAngle);
    }
    private void fail(String message){status=message;Log.e("PoldyFold",message);stopSelf();}
    @Override public void onDisplayAdded(int id){onDisplayChanged(id);}
    @Override public void onDisplayChanged(int id){
        if(!active)return;
        ControlBridge.routeScene(false);render();
        Log.i("PoldyFold","display_changed:primary="+(isInner()?"inner":"outer")+", persistent=true");
    }
    @Override public void onDisplayRemoved(int id){onDisplayChanged(id);}
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){
        active=false;running=false;main.removeCallbacksAndMessages(null);transfer.generation++;
        cancelAnimations();
        choreographer.removeFrameCallback(vsync);renderQueued=false;
        if(innerPanel!=null){innerPanel.close();innerPanel=null;}
        if(outerPanel!=null){outerPanel.close();outerPanel=null;}
        recycle(outgoingFrame);outgoingFrame=null;recycle(heldTarget);heldTarget=null;recycle(latest);latest=null;
        retiredFrames.close(FoldService::recycle);
        ControlBridge.stopAngles();ControlBridge.reset();sensors.unregisterListener(this);displays.unregisterDisplayListener(this);
        if(projection!=null){projection.unregisterCallback(projectionCallback);projection.stop();}
        stopForeground(STOP_FOREGROUND_REMOVE);Log.i("PoldyFold","native_stopped:state_reset");super.onDestroy();
    }
}
