import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceControl;

/** Invisible, temporary compositor-layer capability check. Does not change display state. */
public final class NativeLayerProbe {
    public static void main(String[] args)throws Exception {
        Looper.prepareMainLooper();
        SurfaceControl sc=new SurfaceControl.Builder().setName("Poldy invisible layer probe").setBufferSize(64,64).build();
        Surface surface=null;
        try {
            surface=new Surface(sc);
            Canvas c=surface.lockHardwareCanvas();c.drawColor(Color.MAGENTA);surface.unlockCanvasAndPost(c);
            try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()) {
                SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,sc,0);
                t.setAlpha(sc,0).setLayer(sc,2000000000).apply();
            }
            System.out.println("VALID="+sc.isValid()+", SURFACE="+surface.isValid()+", GPU_DRAW=OK, invisible=true");
        } finally {
            try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(sc,false).setAlpha(sc,0).apply();}
            if(surface!=null)surface.release();sc.release();
        }
        System.exit(0);
    }
}
