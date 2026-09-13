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
    private final Method setStack=SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class);
    private final PhysicalPanels physical=new PhysicalPanels();
    private final ScheduledExecutorService worker=Executors.newScheduledThreadPool(3);
    private final ScheduledFuture<?>[] outputPins=new ScheduledFuture<?>[2];
    private final AtomicBoolean outputFailureLogged=new AtomicBoolean();
    private ScheduledFuture<?> routePin;
    private volatile boolean closed,shielded;
    private boolean restoreFailed;
    private boolean innerPrimary;
    private float pinnedNits;
    private PhysicalPanels.ProjectionSpec innerShieldProjection,outerShieldProjection;
    NativeScene(boolean inner)throws Exception {
        try {
            panels[0]=new SurfaceControl.Builder().setName("Poldy persistent inner").setBufferSize(2448,1848).build();
            panels[1]=new SurfaceControl.Builder().setName("Poldy persistent outer").setBufferSize(1248,1972).build();
            try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()) {
                for(SurfaceControl p:panels)t.setLayer(p,2000000000).setAlpha(p,0).setVisibility(p,true);
                layers(t,inner?0:1,inner?1:0);t.apply();
            }
            innerPrimary=inner;
        }catch(Exception e){close();throw e;}
    }
    private void layers(SurfaceControl.Transaction t,int inner,int outer)throws Exception {
        setStack.invoke(t,panels[0],inner);setStack.invoke(t,panels[1],outer);
    }
    synchronized void shield()throws Exception {
        if(closed)throw new IllegalStateException("Scene closed");
        if(shielded)return;
        // FoldService has already fenced the opaque buffers on both panels.
        // Keep the physical outputs powered while Samsung swaps the logical displays.
        // The two-argument brightness overload is unsafe on this firmware: it maps an
        // ordinary logical level to 500 nits. Preserve only the current system nits
        // through the five-argument API while leaving both backlight arguments at -1.
        // Freeze the current logical orientation before the display IDs swap. The
        // private shield stacks then keep exactly the same portrait/landscape space.
        innerShieldProjection=physical.logicalProjection(innerPrimary?0:1);
        outerShieldProjection=physical.logicalProjection(innerPrimary?1:0);
        pinnedNits=physical.visibleNits();
        android.util.Log.i("PoldyControl","physical_luminance_metadata_hold:nits="+pinnedNits);
        shielded=true;pinOnce();
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
        try{physical.keepLuminanceMetadata(panel,pinnedNits);}
        catch(Exception e){if(outputFailureLogged.compareAndSet(false,true))android.util.Log.e("PoldyControl","physical_output_pin_failed",e);}
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
        innerPrimary=inner;shielded=false;pinnedNits=0;innerShieldProjection=null;outerShieldProjection=null;
    }
    private void cancelPins(){
        if(routePin!=null){routePin.cancel(false);routePin=null;}
        for(int i=0;i<outputPins.length;i++)if(outputPins[i]!=null){outputPins[i].cancel(false);outputPins[i]=null;}
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
            }finally{for(int i=0;i<2;i++)if(panels[i]!=null){panels[i].release();panels[i]=null;}}
        }
    }
    boolean closeRestoring(){close();return !restoreFailed;}
}
