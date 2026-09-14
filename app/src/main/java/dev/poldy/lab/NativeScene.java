package dev.poldy.lab;

import android.os.Parcel;
import android.view.SurfaceControl;
import java.lang.reflect.Method;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native-size buffers shield a profile change, then return to system routing for normal input. */
final class NativeScene implements AutoCloseable {
    private static final int INNER_SHIELD=2100000000, OUTER_SHIELD=2100000001;
    private final SurfaceControl[] panels=new SurfaceControl[2];
    private final SurfaceControl[] privacyRoots=new SurfaceControl[2];
    private final Object privacyLock=new Object();
    private boolean suppressed;
    private final Method setStack=SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class);
    private final PhysicalPanels physical=new PhysicalPanels();
    private final ScheduledExecutorService worker=Executors.newScheduledThreadPool(3);
    private final ScheduledFuture<?>[] outputPins=new ScheduledFuture<?>[2];
    private final AtomicBoolean outputFailureLogged=new AtomicBoolean();
    private ScheduledFuture<?> routePin;
    private volatile boolean closed,shielded;
    private final Object[] outputLocks={new Object(),new Object()};
    private volatile boolean pinOutputs;
    private volatile long luminanceUntil;
    private volatile LuminanceGuard luminanceGuard;
    private int outgoingPanel;
    private final PanelLuminanceReader luminance;
    private boolean restoreFailed;
    private boolean innerPrimary;
    private PhysicalPanels.ProjectionSpec innerShieldProjection,outerShieldProjection;
    NativeScene(boolean inner)throws Exception {
        this(inner,null);
    }
    NativeScene(boolean inner,PanelLuminanceReader luminance)throws Exception {
        this.luminance=luminance;
        try {
            for(int i=0;i<2;i++) {
                SurfaceControl.Builder builder=new SurfaceControl.Builder().setName("Foldy privacy root "+i);
                SurfaceControl.Builder.class.getMethod("setContainerLayer").invoke(builder);
                SurfaceControl.Builder.class.getMethod("setSecure",boolean.class).invoke(builder,true);
                privacyRoots[i]=builder.build();
            }
            panels[0]=new SurfaceControl.Builder().setName("Poldy persistent inner").setParent(privacyRoots[0]).setBufferSize(2448,1848).build();
            panels[1]=new SurfaceControl.Builder().setName("Poldy persistent outer").setParent(privacyRoots[1]).setBufferSize(1248,1972).build();
            try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()) {
                for(SurfaceControl p:privacyRoots)t.setLayer(p,2000000000).setVisibility(p,true);
                for(SurfaceControl p:panels)t.setLayer(p,0).setAlpha(p,0).setVisibility(p,true);
                layers(t,inner?0:1,inner?1:0);t.apply();
            }
            innerPrimary=inner;
        }catch(Exception e){close();throw e;}
    }
    private void layers(SurfaceControl.Transaction t,int inner,int outer)throws Exception {
        setStack.invoke(t,privacyRoots[0],inner);setStack.invoke(t,privacyRoots[1],outer);
    }
    /** One-way latch owned by shell. App-side child alpha changes cannot undo this hide. */
    void suppress() {
        synchronized(privacyLock){
            if(suppressed)return;suppressed=true;
            try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
                for(SurfaceControl p:privacyRoots)if(p!=null&&p.isValid())t.setVisibility(p,false);
                t.apply();
            }
        }
    }
    synchronized void shield()throws Exception {
        if(closed)throw new IllegalStateException("Scene closed");
        if(shielded)return;
        // FoldService has already fenced the opaque buffers on both panels.
        // Keep the physical outputs powered while Samsung swaps the logical displays.
        // Replay only a measured, current SDR output on the outgoing physical panel.
        // The firmware's logical brightness and -1 metadata sentinel are not suitable.
        outgoingPanel=innerPrimary?0:1;
        luminanceGuard=luminance==null?null:luminance.beginHold(innerPrimary,physical.currentNits());
        luminanceUntil=android.os.SystemClock.elapsedRealtime()+1500;
        android.util.Log.i("PoldyControl","luminance_guard:"+(luminanceGuard==null?"unavailable":"armed")+",outgoing="+outgoingPanel);
        // Freeze the current logical orientation before the display IDs swap. The
        // private shield stacks then keep exactly the same portrait/landscape space.
        innerShieldProjection=physical.logicalProjection(innerPrimary?0:1);
        outerShieldProjection=physical.logicalProjection(innerPrimary?1:0);
        shielded=true;pinOutputs=true;pinOnce();
        Future<?> innerOn=worker.submit(()->{outputOnce(0);return null;});
        Future<?> outerOn=worker.submit(()->{outputOnce(1);return null;});
        awaitOutput(innerOn);awaitOutput(outerOn);
        routePin=worker.scheduleAtFixedRate(()->{
            synchronized(this){if(!shielded||closed)return;try{pinOnce();}catch(Exception e){android.util.Log.e("PoldyControl","shield_failed",e);}}
        },1,1,TimeUnit.MILLISECONDS);
        for(int i=0;i<2;i++){
            int panel=i;
            outputPins[i]=worker.scheduleWithFixedDelay(()->{
                if(shielded&&!closed)outputOnce(panel);
            },1,1,TimeUnit.MILLISECONDS);
        }
    }
    private static void awaitOutput(Future<?> result)throws Exception {
        try{result.get();}
        catch(ExecutionException e){
            Throwable cause=e.getCause();
            if(cause instanceof Exception exception)throw exception;
            throw new RuntimeException(cause);
        }
    }
    private void outputOnce(int panel) {
        synchronized(outputLocks[panel]) {
            if(!pinOutputs||closed)return;
            try {
                physical.keepPowered(panel);
                LuminanceGuard guard=luminanceGuard;
                if(panel==outgoingPanel&&guard!=null&&android.os.SystemClock.elapsedRealtime()<luminanceUntil){
                    PanelLuminance sample=guard.takeRecovery(System.currentTimeMillis());
                    if(sample!=null){
                        physical.keepLuminance(panel,sample);
                        android.util.Log.i("PoldyControl","luminance_recovered:panel="+panel+",nits="+sample.nits());
                    }
                }
            }catch(Exception e){if(outputFailureLogged.compareAndSet(false,true))android.util.Log.e("PoldyControl","physical_output_pin_failed",e);}
        }
    }
    private void pinOnce()throws Exception {
        try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()) {
            layers(t,INNER_SHIELD,OUTER_SHIELD);
            physical.routeNative(t,INNER_SHIELD,OUTER_SHIELD,innerShieldProjection,outerShieldProjection);t.apply();
        }
    }
    synchronized void finishSwitch(boolean inner)throws Exception {
        if(closed)return;
        cancelPins();
        // Both layer ownership and physical projection change atomically.
        try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()) {
            layers(t,inner?0:1,inner?1:0);physical.routeLogical(t,inner?0:1,inner?1:0);t.apply();
        }
        innerPrimary=inner;shielded=false;innerShieldProjection=null;outerShieldProjection=null;
    }
    private void cancelPins(){
        pinOutputs=false;
        if(routePin!=null){routePin.cancel(false);routePin=null;}
        for(int i=0;i<outputPins.length;i++)if(outputPins[i]!=null){outputPins[i].cancel(false);outputPins[i]=null;}
        // An in-flight ON/brightness call must finish before normal routing or sleep.
        for(Object lock:outputLocks)synchronized(lock){}
        LuminanceGuard guard=luminanceGuard;luminanceGuard=null;if(guard!=null)guard.close();
    }
    synchronized void route(boolean inner)throws Exception {
        if(closed||shielded||innerPrimary==inner)return;
        try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){layers(t,inner?0:1,inner?1:0);t.apply();}
        innerPrimary=inner;
    }
    SurfaceControl[] copies() {
        SurfaceControl[] result=new SurfaceControl[2];
        try {
            for(int i=0;i<2;i++) {
                Parcel p=Parcel.obtain();try{panels[i].writeToParcel(p,0);p.setDataPosition(0);result[i]=SurfaceControl.CREATOR.createFromParcel(p);}finally{p.recycle();}
            }return result;
        }catch(RuntimeException e){for(SurfaceControl p:result)if(p!=null)p.release();throw e;}
    }
    @Override public void close() {
        synchronized(this){
            if(closed)return;
            try{if(shielded)finishSwitch(PhysicalPanels.primaryIsInner());}
            catch(Exception e){restoreFailed=true;android.util.Log.e("PoldyControl","shield_restore_failed",e);}
            closed=true;cancelPins();worker.shutdownNow();
        }
        try{if(!worker.awaitTermination(500,TimeUnit.MILLISECONDS))android.util.Log.w("PoldyControl","physical_output_pin_stop_timeout");}
        catch(InterruptedException e){Thread.currentThread().interrupt();}
        synchronized(this){
            try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()) {
                for(SurfaceControl p:panels)if(p!=null&&p.isValid())t.setVisibility(p,false).setAlpha(p,0);t.apply();
            }finally{
                synchronized(privacyLock){
                    for(int i=0;i<2;i++){
                        if(panels[i]!=null){panels[i].release();panels[i]=null;}
                        if(privacyRoots[i]!=null){privacyRoots[i].release();privacyRoots[i]=null;}
                    }
                }
            }
        }
    }
    boolean closeRestoring(){close();return !restoreFailed;}
}
