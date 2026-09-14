package dev.poldy.lab;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.hardware.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.util.Log;
import android.view.*;
import java.util.ArrayList;

/** Both panels stay prepared; native capture excludes our GPU layers. */
public final class FoldService extends Service implements SensorEventListener,DisplayManager.DisplayListener {
    public static final String STOP="dev.poldy.lab.FOLD_STOP";
    public static volatile boolean running;
    public static volatile boolean prepared;
    public static volatile boolean suspended;
    public static volatile String status="애니메이션 대기";
    private final Handler main=new Handler(Looper.getMainLooper());
    private FoldSignals signals=new FoldSignals();
    private final TransferGate transfer=new TransferGate();
    private DisplayManager displays;private SensorManager sensors;
    private NativePanel innerPanel,outerPanel;
    private float primaryOpacity;
    private Bitmap latest,outgoingFrame,heldTarget;
    private final FrameRetirement<Bitmap> retiredFrames=new FrameRetirement<>();
    private final PanelFrameCache<Bitmap> nativeFrames=new PanelFrameCache<>();
    private long contentEpoch;
    private boolean privacyBlocked=true;
    private long privacyEpoch=-1;
    private final java.util.IdentityHashMap<Bitmap,Integer> snapshotSources=new java.util.IdentityHashMap<>();
    private final java.util.Set<Bitmap> discardAfterSnapshot=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final IPrivacyListener privacyListener=new IPrivacyListener.Stub(){
        @Override public void onPrivacyChanged(boolean blocked,long epoch){main.post(()->{
            if(!active||epoch<privacyEpoch)return;
            privacyEpoch=epoch;privacyBlocked=blocked;
            if(blocked)pausePipeline("보호 중인 화면 · 다른 앱으로 이동하면 자동 재개");
            else scheduleResume();
        });}
    };
    private long invalidProfileSince;
    private boolean outputReleased=true,outputReleasePending,latestNativeReady;
    private boolean endpointPowerSettled;
    private boolean snapshotPending,pendingReveal,releasePending;
    private boolean active,paused,initializing,resumePending,moving,curtain,capturing;
    private float frost=1,contentMix;
    private long transitionAt;
    private int preparationFrames;
    private int frameCount;
    private int startupCaptureRetries;
    private FrameTween finish,handoff;
    private boolean logicalInner;
    private Boolean deferredTransfer;
    private long lastAngleNanos;
    private float measuredAngle;
    private int angleSamples;
    private FoldAttitude attitude=new FoldAttitude();
    private float opticalAngle;
    private AngleSmoother visual=new AngleSmoother();
    private HingeProgress progress=new HingeProgress();
    private long pipelineEpoch;
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
        @Override public void onStopped(String reason){main.post(()->{if(active&&!paused)fail(reason);});}
    };
    private final BroadcastReceiver screenReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            String action=intent.getAction();
            if(Intent.ACTION_SCREEN_OFF.equals(action))pauseForLock();
            else if(Intent.ACTION_SCREEN_ON.equals(action)||Intent.ACTION_USER_PRESENT.equals(action))scheduleResume();
        }
    };
    private final Runnable lease=new Runnable(){
        @Override public void run(){
            if(!active)return;
            if(locked()){pauseForLock();main.postDelayed(this,500);return;}
            if(paused){scheduleResume();main.postDelayed(this,500);return;}
            ControlBridge.renew();main.postDelayed(this,1000);
        }
    };
    @Override public void onCreate(){
        super.onCreate();choreographer=Choreographer.getInstance();displays=getSystemService(DisplayManager.class);
        sensors=getSystemService(SensorManager.class);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("fold","접힘 애니메이션",NotificationManager.IMPORTANCE_LOW));
        IntentFilter screen=new IntentFilter();screen.addAction(Intent.ACTION_SCREEN_OFF);
        screen.addAction(Intent.ACTION_SCREEN_ON);screen.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver,screen,Context.RECEIVER_NOT_EXPORTED);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null||STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(active)return START_NOT_STICKY;
        PendingIntent stop=PendingIntent.getService(this,3,new Intent(this,FoldService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE);
        startForeground(2,new Notification.Builder(this,"fold").setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Foldy · 접힘 애니메이션").setContentText("접힘 각도에 맞춰 두 패널의 화면을 연결합니다.")
            .setOngoing(true).addAction(new Notification.Action.Builder(null,"중지",stop).build())
            .setContentIntent(PendingIntent.getActivity(this,4,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE)).build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        try{
            if(!ControlBridge.ready())throw new IllegalStateException("화면 제어 연결을 확인해 주세요.");
            active=true;running=true;prepared=false;suspended=false;paused=true;displays.registerDisplayListener(this,main);
            main.post(lease);ControlBridge.watchPrivacy(privacyListener,error->{if(active&&error!=null)fail(error);});
        }catch(RuntimeException e){fail(e.toString());}
        return START_NOT_STICKY;
    }
    private void startPipeline(){
        if(!active||initializing||!paused||privacyBlocked)return;
        if(locked()){status="화면 잠금 중 · 잠금 해제 후 자동 재개";return;}
        try{
            Display display=displays.getDisplay(Display.DEFAULT_DISPLAY);
            if(display==null)throw new IllegalStateException("활성 화면을 찾지 못했습니다.");
            int width=display.getMode().getPhysicalWidth();
            if(width!=1248&&width!=2448)throw new IllegalStateException("활성 패널 크기를 확인하지 못했습니다: "+width);
            logicalInner=width==2448;
            transfer.inner=logicalInner;transfer.phase=TransferGate.Phase.READY;
            signals=new FoldSignals();progress=new HingeProgress();visual=new AngleSmoother();attitude=new FoldAttitude();
            measuredAngle=logicalInner?180:0;visual.reset(measuredAngle);opticalAngle=measuredAngle;lastAngleNanos=0;angleSamples=0;
            pendingOpticsAnchor=Float.NaN;moving=false;curtain=false;capturing=false;snapshotPending=false;
            pendingReveal=false;releasePending=false;outputReleased=true;outputReleasePending=false;
            deferredTransfer=null;
            latestNativeReady=false;endpointPowerSettled=false;invalidProfileSince=0;startupCaptureRetries=0;
            primaryOpacity=0;frost=1;contentMix=0;renderedFrames=0;renderCost=0;
            Sensor sensor=sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE);
            if(sensor==null||!sensors.registerListener(this,sensor,20_000,main))
                throw new IllegalStateException("접힘 신호를 읽지 못했습니다.");
            Sensor gyro=sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if(gyro!=null)sensors.registerListener(this,gyro,20_000,main);
            Sensor gravity=sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
            if(gravity!=null)sensors.registerListener(this,gravity,40_000,main);
            paused=false;suspended=false;initializing=true;long epoch=++pipelineEpoch;
            status="화면 잠금 해제됨 · 두 화면 다시 준비 중";
            main.postDelayed(()->{
                if(active&&!paused&&epoch==pipelineEpoch&&latest==null)
                    fail("두 화면의 첫 프레임을 준비하지 못했습니다.");
            },3500);
            ControlBridge.switchTo(isInner(),transfer.generation,result->{
                if(!active||paused||epoch!=pipelineEpoch)return;
                if("WAIT_ENDPOINT".equals(result)){pausePipeline("완전히 접거나 펼치면 자동 재개");return;}
                if("PRIVACY_BLOCKED".equals(result)){privacyBlocked=true;pausePipeline("보호 중인 화면 · 다른 앱으로 이동하면 자동 재개");return;}
                if(!"OK".equals(result)){fail(result);return;}
                ControlBridge.createScene(isInner(),(panels,error)->{
                    if(!active||paused||epoch!=pipelineEpoch){
                        if(panels!=null)for(SurfaceControl p:panels)p.release();return;
                    }
                    if(panels==null||panels.length!=2){fail("GPU 화면 준비 실패: "+error);return;}
                    try{
                        innerPanel=new NativePanel(panels[0],2448,1848);outerPanel=new NativePanel(panels[1],1248,1972);
                        initializing=false;captureNext();status="두 화면 준비됨 · 접힘 각도 수신 대기";
                        ControlBridge.startAngles(angleListener,resultAngle->{
                            if(active&&!paused&&epoch==pipelineEpoch&&!"OK".equals(resultAngle))fail(resultAngle);
                        });
                        Log.i("PoldyFold","native_started:native_profiles, resumed="+(epoch>1));
                    }catch(RuntimeException e){for(SurfaceControl p:panels)p.release();fail(e.toString());}
                });
            });
        }catch(RuntimeException e){fail(e.toString());}
    }
    private void pauseForLock(){pausePipeline("화면 잠금 중 · 잠금 해제 후 자동 재개");}
    private void pausePipeline(String reason){
        if(!active)return;
        status=reason;suspended=true;
        if(paused)return;
        paused=true;prepared=false;initializing=false;resumePending=false;pipelineEpoch++;transfer.generation++;
        moving=false;capturing=false;snapshotPending=false;cancelAnimations();
        choreographer.removeFrameCallback(vsync);main.removeCallbacks(renderFallback);renderQueued=false;
        contentEpoch++;
        // Hide and discard before waiting for any remote display restoration.
        releasePipeline();
        // A sleep teardown releases every power token so the service cannot keep a
        // physical panel awake. The foreground service remains available to resume.
        ControlBridge.sleepBlocking();
        sensors.unregisterListener(this);
        Log.i("PoldyPrivacy","pipeline_paused:cache_cleared, privacy="+privacyBlocked);
    }
    private void scheduleResume(){
        if(!active||!paused||resumePending||privacyBlocked)return;
        resumePending=true;
        main.postDelayed(()->{
            resumePending=false;
            if(active&&paused&&!locked())startPipeline();
        },250);
    }
    private void releasePipeline(){
        if(innerPanel!=null){innerPanel.close();innerPanel=null;}
        if(outerPanel!=null){outerPanel.close();outerPanel=null;}
        nativeFrames.clear();
        deferredTransfer=null;
        releaseFrame(outgoingFrame);outgoingFrame=null;releaseFrame(heldTarget);heldTarget=null;releaseFrame(latest);latest=null;
        retiredFrames.close(this::releaseFrame);
    }
    private boolean locked(){return getSystemService(KeyguardManager.class).isKeyguardLocked();}
    private boolean isInner(){return logicalInner;}
    private void acceptAngle(long stamp,float angle,String source){
        long now=SystemClock.elapsedRealtimeNanos();
        if(!active||paused||stamp<=lastAngleNanos||stamp>now||now-stamp>HalAngleSample.MAX_AGE_NANOS
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
        if(!active||paused||capturing)return;if(locked()){pauseForLock();return;}
        if(innerPanel==null||outerPanel==null)return;
        ArrayList<SurfaceControl> layers=new ArrayList<>();layers.add(innerPanel.control);layers.add(outerPanel.control);
        boolean inner=isInner();long generation=transfer.generation,epoch=pipelineEpoch;capturing=true;long captureStarted=SystemClock.uptimeMillis();
        ControlBridge.capture(inner,layers.toArray(new SurfaceControl[0]),(bitmap,info,error)->{
            if(epoch!=pipelineEpoch){recycle(bitmap);return;}
            capturing=false;if(!active||paused){recycle(bitmap);return;}
            if("PRIVACY_BLOCKED".equals(error)){pausePipeline("보호 중인 화면 · 다른 앱으로 이동하면 자동 재개");return;}
            if(bitmap==null){
                // The mirror's first Surface transaction can finish after createScene returns.
                if(latest==null&&error!=null&&error.contains("No logical buffer")&&startupCaptureRetries++<12){
                    Log.i("PoldyCapture","waiting_for_first_buffer:"+startupCaptureRetries);
                    main.postDelayed(this::captureNext,75);return;
                }
                fail("화면 캡처 실패: "+error);return;
            }
            if(generation!=transfer.generation||inner!=isInner()){bitmap.recycle();main.post(this::captureNext);return;}
            if(info==null||info.generation()!=generation||info.inner()!=inner){bitmap.recycle();main.postDelayed(this::captureNext,16);return;}
            if(nativeFrames.owner(info.owner())){
                contentEpoch++;
                Bitmap oldOutgoing=outgoingFrame,oldHold=heldTarget;outgoingFrame=null;heldTarget=null;
                retire(oldOutgoing);retire(oldHold);
                if(curtain){cancelAnimations();contentMix=0;transfer.invalidateFrames();}
                Log.i("PoldyFold","native_cache_invalidated:epoch="+contentEpoch);
            }
            latestNativeReady=info.nativeReady();
            if(!latestNativeReady){
                if(curtain)transfer.invalidateFrames();bitmap.recycle();
                if(invalidProfileSince==0)invalidProfileSince=SystemClock.uptimeMillis();
                if(SystemClock.uptimeMillis()-invalidProfileSince>1800){fail("현재 앱의 네이티브 전체 화면 구성을 확인하지 못해 시험을 중지했습니다.");return;}
                render();main.postDelayed(this::captureNext,32);return;
            }
            invalidProfileSince=0;
            Bitmap old=latest;latest=bitmap;retire(old);
            boolean correctSize=TaskProfileReader.nativeBounds(inner,bitmap.getWidth(),bitmap.getHeight());
            if(correctSize){prepared=true;primary().resize(bitmap.getWidth(),bitmap.getHeight());nativeFrames.put(bitmap,inner,info.owner(),SystemClock.uptimeMillis());}
            if(correctSize&&deferredTransfer!=null){
                boolean target=deferredTransfer;deferredTransfer=null;
                if(target!=inner||moving)beginTransfer(target);
            }
            if(correctSize&&!curtain&&!moving&&!endpointPowerSettled){
                endpointPowerSettled=true;ControlBridge.settle(inner,transfer.generation);
            }
            if(correctSize&&SystemClock.uptimeMillis()-transitionAt>=280&&transfer.frame(generation,inner)){
                Log.i("PoldyFold","destination_ready:"+bitmap.getWidth()+"x"+bitmap.getHeight()+", elapsed="+(SystemClock.uptimeMillis()-transitionAt));
                startHandoff();
            }
            render();
            if(++frameCount%30==1)Log.i("PoldyCapture","frame:"+bitmap.getWidth()+"x"+bitmap.getHeight()+", gpu="+bitmap.getConfig()+", capture_ms="+info.elapsed()+", excluded="+layers.size());
            FoldSignals.Action early=signals.acceptBaseState(info.baseState());
            if(early!=FoldSignals.Action.NONE){
                Log.i("PoldyFold","base_signal:"+info.baseState()+", action="+early);
                handleSignal(early,false);
            }
            long cadence=moving||curtain?16:240;
            main.postDelayed(this::captureNext,Math.max(0,cadence-(SystemClock.uptimeMillis()-captureStarted)));
        });
    }
    private NativePanel primary(){return isInner()?innerPanel:outerPanel;}
    private NativePanel secondary(){return isInner()?outerPanel:innerPanel;}
    private void render(){
        if(!active||paused||renderQueued)return;renderQueued=true;
        choreographer.postFrameCallback(vsync);main.postDelayed(renderFallback,18);
    }
    private void renderNow(){
        if(!active||paused||latest==null||innerPanel==null||outerPanel==null)return;
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
        if(snapshotPending){
            // Only the destination is waiting on a GPU snapshot. Keep the other
            // panel moving, without overwriting a same-panel reversal's hold.
            if(transfer.canAnimateOutgoing(inner)&&primary.matches(outgoingFrame)){
                primary.frame(outgoingFrame,inner,1,0);
                setPrimaryOpacity(1);
                if(++preparationFrames==1)Log.i("PoldyFold","source_motion_during_prepare:panel="+(inner?"inner":"outer")
                    +", elapsed="+(renderStarted-transitionAt)+", measured="+measuredAngle+", visual="+opticalAngle);
                if(!primary.healthy){fail("GPU 화면 표시 오류로 시험을 중지했습니다.");return;}
            }
            if(visual.animating())render();
            return;
        }
        if(curtain){
            if(inner!=transfer.inner){
                primary.frame(primary.matches(outgoingFrame)?outgoingFrame:null,inner,frost,0);
            }else if(transfer.phase!=TransferGate.Phase.READY||!latestNativeReady||!primary.matches(latest))primary.frame(heldTarget,inner,frost,0);
            else primary.blend(heldTarget,latest,contentMix,inner,frost,0);
        } else primary.frame(nativeFrames.get(inner,renderStarted),inner,0,0);
        if(curtain){
            if(inner!=transfer.inner)secondary.frame(heldTarget,!inner,frost,0);
            else secondary.frame(secondary.matches(outgoingFrame)?outgoingFrame:null,!inner,1,0);
        }else secondary.frame(nativeFrames.get(!inner,renderStarted),!inner,1,0);
        primary.opacity(primaryOpacity);secondary.opacity(1);
        if(!primary.healthy||!secondary.healthy)fail("GPU 화면 표시 오류로 시험을 중지했습니다.");
        renderCost+=SystemClock.uptimeMillis()-renderStarted;
        if(++renderedFrames%60==0){Log.i("PoldyCapture","render_mean_ms:"+(renderCost/60f));renderCost=0;}
        if(curtain&&transfer.phase==TransferGate.Phase.READY&&latestNativeReady&&!outputReleased&&!outputReleasePending)
            releaseOutput(transfer.generation);
        if(pendingReveal&&outputReleased&&!moving&&!releasePending&&visual.settled()&&latestNativeReady&&primary.matches(latest)){
            releasePending=true;long token=transfer.generation,epoch=contentEpoch;
            primary.whenVisible(getMainExecutor(),()->{
                if(!active||token!=transfer.generation)return;
                if(epoch==contentEpoch&&pendingReveal&&outputReleased&&latestNativeReady
                    &&transfer.phase==TransferGate.Phase.READY&&!moving&&handoff==null)completeReveal();
                else{releasePending=false;render();}
            });
        }
        if(visual.animating())render();
        retiredFrames.collect(SystemClock.uptimeMillis(),frame->frame==latest||frame==outgoingFrame||frame==heldTarget
            ||snapshotSources.containsKey(frame)||nativeFrames.references(frame)
            ||(innerPanel!=null&&innerPanel.references(frame))||(outerPanel!=null&&outerPanel.references(frame)),FoldService::recycle);
    }
    private void retire(Bitmap frame){retiredFrames.retire(frame,SystemClock.uptimeMillis());}
    private void releaseFrame(Bitmap frame){
        if(frame!=null&&snapshotSources.containsKey(frame))discardAfterSnapshot.add(frame);
        else recycle(frame);
    }
    private void snapshotFinished(Bitmap source){
        if(source==null)return;
        int refs=snapshotSources.getOrDefault(source,1)-1;
        if(refs>0)snapshotSources.put(source,refs);
        else {snapshotSources.remove(source);if(discardAfterSnapshot.remove(source))recycle(source);}
    }
    private static void recycle(Bitmap frame){if(frame!=null&&!frame.isRecycled())frame.recycle();}
    @Override public void onSensorChanged(SensorEvent event){
        if(!active||paused||event.values.length==0||!Float.isFinite(event.values[0]))return;
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
            Log.i("PoldyAngle","measured_departure:"+change+", angle="+measuredAngle);
            startGesture(change==HingeProgress.Change.START_OPEN);
        }else if(change==HingeProgress.Change.FINISH_OPEN||change==HingeProgress.Change.FINISH_CLOSE){
            moving=false;boolean inner=change==HingeProgress.Change.FINISH_OPEN;
            attitude.rest();anchorOptics(inner);
            if(transfer.phase==TransferGate.Phase.READY)finishReveal();
        }
    }
    private void beginTransfer(boolean inner){
        if(locked()){pauseForLock();return;}
        if(latest==null){
            // A posture callback can beat the first capture after unlock. Replay only the
            // newest requested panel once a validated native frame can cover the change.
            deferredTransfer=inner;return;
        }
        if(!curtain)renderNow();
        cancelAnimations();
        Bitmap held=nativeFrames.get(isInner(),SystemClock.uptimeMillis());
        Bitmap retired=outgoingFrame;outgoingFrame=held;
        retire(retired);
        long token=transfer.begin(inner);ControlBridge.markTransition(token);
        transitionAt=SystemClock.uptimeMillis();snapshotPending=true;preparationFrames=0;
        endpointPowerSettled=false;
        outputReleased=false;outputReleasePending=false;latestNativeReady=false;
        invalidProfileSince=0;
        long epoch=contentEpoch;
        NativePanel target=inner?innerPanel:outerPanel;
        // Prepare raw content off-screen. Optical deformation remains live on every vsync,
        // including the time spent waiting for Samsung's native app layout.
        Bitmap nativeTarget=nativeFrames.get(inner,transitionAt);
        Bitmap source=nativeTarget!=null?nativeTarget:target.matches(heldTarget)?heldTarget:held;
        if(source!=null)snapshotSources.merge(source,1,Integer::sum);
        target.snapshotRaw(source,main,snapshot->{
            snapshotFinished(source);
            if(!active||token!=transfer.generation){if(snapshot!=null)snapshot.recycle();return;}
            if(epoch!=contentEpoch){recycle(snapshot);snapshotPending=false;beginTransfer(inner);return;}
            snapshotPending=false;
            if(snapshot==null){fail("현재 패널 화면을 보관하지 못해 시험을 중지했습니다.");return;}
            Bitmap oldHold=heldTarget;heldTarget=snapshot;
            retire(oldHold);
            curtain=true;frost=1;contentMix=0;setPrimaryOpacity(1);
            status=inner?"펼침 · 내부 화면 연결 중":"접힘 · 외부 화면 연결 중";renderNow();
            Log.i("PoldyFold","panel_hold_ready:"+snapshot.getWidth()+"x"+snapshot.getHeight()+", elapsed="+(SystemClock.uptimeMillis()-transitionAt)
                +", source_frames="+preparationFrames+", visual="+opticalAngle);
            // Keep each panel's own presented image until its new native-size layout is ready.
            secondary().whenVisible(getMainExecutor(),()->{
              if(!active||token!=transfer.generation)return;
              primary().whenVisible(getMainExecutor(),()->{
                if(!active||!transfer.covered(token))return;
                Log.i("PoldyFold","curtain_presented:target="+(inner?"inner":"outer")+", elapsed="+(SystemClock.uptimeMillis()-transitionAt));
                ControlBridge.switchTo(inner,token,result->{
                    if(!active||token!=transfer.generation)return;
                    if(!"OK".equals(result)){fail(result);return;}
                    logicalInner=inner;render();captureNext();
                });
              });
            });
        });
        render();
        main.postDelayed(()->{if(active&&token==transfer.generation&&(transfer.phase!=TransferGate.Phase.READY||!outputReleased))fail("새 화면을 준비하지 못해 시험을 중지했습니다.");},3000);
    }
    private void releaseOutput(long token){
        outputReleasePending=true;boolean inner=isInner();long epoch=contentEpoch;
        secondary().whenVisible(getMainExecutor(),()->{
            if(!active||token!=transfer.generation)return;
            primary().whenVisible(getMainExecutor(),()->{
                if(!active||token!=transfer.generation)return;
                if(epoch!=contentEpoch||!latestNativeReady){outputReleasePending=false;render();return;}
                ControlBridge.finishMode(inner,token,result->{
                    if(!active||token!=transfer.generation)return;
                    outputReleasePending=false;
                    if("WAIT".equals(result)){main.postDelayed(this::render,16);return;}
                    if(!"OK".equals(result)){fail(result);return;}
                    outputReleased=true;render();
                    Log.i("PoldyFold","output_ready:elapsed="+(SystemClock.uptimeMillis()-transitionAt));
                });
            });
        });
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
        handoff=new FrameTween(main,280,t->{contentMix=t;render();},()->{
            handoff=null;contentMix=1;Log.i("PoldyFold","handoff_finished");if(!moving)finishReveal();
        });handoff.start();
    }
    private void finishReveal(){
        if(!curtain||handoff!=null||finish!=null)return;
        pendingReveal=true;render();
    }
    private void completeReveal(){
        pendingReveal=false;releasePending=false;curtain=false;finish=null;
        long endpointGeneration=transfer.generation;
        status="센서 각도로 화면 연결 중 · 다음 움직임 대기";
        Bitmap retired=outgoingFrame,retiredHold=heldTarget;outgoingFrame=null;heldTarget=null;render();
        retire(retired);retire(retiredHold);
        // Keep the already presented native-size frame over Samsung's endpoint
        // remap. The normal app output becomes visible only after panel power and
        // device-state policy have settled, preventing a short all-black gap.
        endpointPowerSettled=true;ControlBridge.settle(isInner(),endpointGeneration);
        main.postDelayed(()->{
            if(active&&endpointGeneration==transfer.generation&&endpointPowerSettled&&!curtain&&!moving)
                setPrimaryOpacity(0);
        },500);
        Log.i("PoldyFold","reveal_finished:panel="+(isInner()?"inner":"outer")+", visual_angle="+opticalAngle);
    }
    private void fail(String message){status=message;Log.e("PoldyFold",message);stopSelf();}
    @Override public void onDisplayAdded(int id){onDisplayChanged(id);}
    @Override public void onDisplayChanged(int id){
        if(!active||paused)return;
        ControlBridge.routeScene(false);render();
        Log.i("PoldyFold","display_changed:primary="+(isInner()?"inner":"outer")+", persistent=true");
    }
    @Override public void onDisplayRemoved(int id){onDisplayChanged(id);}
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){
        boolean sleeping=paused||locked();
        active=false;paused=true;running=false;prepared=false;suspended=false;main.removeCallbacksAndMessages(null);transfer.generation++;pipelineEpoch++;
        cancelAnimations();
        choreographer.removeFrameCallback(vsync);main.removeCallbacks(renderFallback);renderQueued=false;
        // Restore the system display state while our last opaque frame still covers
        // the physical outputs. Closing these layers first exposes an empty stack.
        if(sleeping){releasePipeline();ControlBridge.sleepBlocking();}
        else {ControlBridge.resetBlocking();releasePipeline();}
        ControlBridge.watchPrivacy(null,error->{});
        sensors.unregisterListener(this);displays.unregisterDisplayListener(this);
        try{unregisterReceiver(screenReceiver);}catch(IllegalArgumentException ignored){}
        stopForeground(STOP_FOREGROUND_REMOVE);Log.i("PoldyFold","native_stopped:state_reset");super.onDestroy();
    }
}
