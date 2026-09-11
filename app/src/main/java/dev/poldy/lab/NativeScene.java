package dev.poldy.lab;

import android.os.Parcel;
import android.view.SurfaceControl;
import java.lang.reflect.Method;
import java.util.concurrent.*;

/** Native-size buffers shield a profile change, then return to system routing for normal input. */
final class NativeScene implements AutoCloseable {
    private static final int INNER_SHIELD=2100000000, OUTER_SHIELD=2100000001;
    private final SurfaceControl[] panels=new SurfaceControl[2];
    private final Method setStack=SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class);
    private final PhysicalPanels physical=new PhysicalPanels();
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pin;
    private boolean closed,shielded,innerPrimary;
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
        shielded=true;pinOnce();
        pin=worker.scheduleAtFixedRate(()->{
            synchronized(this){if(!shielded||closed)return;try{pinOnce();}catch(Exception e){android.util.Log.e("PoldyControl","shield_failed",e);}}
        },1,1,TimeUnit.MILLISECONDS);
    }
    private void pinOnce()throws Exception {
        try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()) {
            layers(t,INNER_SHIELD,OUTER_SHIELD);
            physical.route(t,INNER_SHIELD,OUTER_SHIELD);t.apply();
        }
    }
    synchronized void finishSwitch(boolean inner)throws Exception {
        if(closed)return;
        if(pin!=null){pin.cancel(false);pin=null;}
        // Both layer ownership and physical projection change atomically.
        try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()) {
            layers(t,inner?0:1,inner?1:0);physical.route(t,inner?0:1,inner?1:0);t.apply();
        }
        innerPrimary=inner;shielded=false;
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
    @Override public synchronized void close() {
        if(closed)return;
        try{if(shielded)finishSwitch(PhysicalPanels.primaryIsInner());}catch(Exception e){android.util.Log.e("PoldyControl","shield_restore_failed",e);}
        closed=true;worker.shutdownNow();
        try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()) {
            for(SurfaceControl p:panels)if(p!=null&&p.isValid())t.setVisibility(p,false).setAlpha(p,0);t.apply();
        }finally{for(int i=0;i<2;i++)if(panels[i]!=null){panels[i].release();panels[i]=null;}}
    }
}
