package dev.poldy.lab;

import android.graphics.Rect;
import android.os.IBinder;
import android.view.SurfaceControl;
import java.lang.reflect.Method;

/** Verified physical identities, independent of their current logical display IDs. */
final class PhysicalPanels {
    static final long INNER=4630947004648141459L, OUTER=4630947123231501204L;
    private final IBinder[] tokens=new IBinder[2];
    private final Method stack=SurfaceControl.Transaction.class.getMethod("setDisplayLayerStack",IBinder.class,int.class);
    private final Method projection=SurfaceControl.Transaction.class.getMethod("setDisplayProjection",IBinder.class,int.class,Rect.class,Rect.class);
    private final Rect[] bounds={new Rect(0,0,2448,1848),new Rect(0,0,1248,1972)};
    PhysicalPanels()throws Exception {
        tokens[0]=(IBinder)SurfaceControl.class.getMethod("getPhysicalDisplayToken",long.class).invoke(null,INNER);
        tokens[1]=(IBinder)SurfaceControl.class.getMethod("getPhysicalDisplayToken",long.class).invoke(null,OUTER);
    }
    void route(SurfaceControl.Transaction t,int innerStack,int outerStack)throws Exception {
        stack.invoke(t,tokens[0],innerStack);stack.invoke(t,tokens[1],outerStack);
        for(int i=0;i<2;i++)projection.invoke(t,tokens[i],0,bounds[i],bounds[i]);
    }
    static boolean primaryIsInner()throws Exception {
        IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"display");
        Object dm=Class.forName("android.hardware.display.IDisplayManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,b);
        Object info=Class.forName("android.hardware.display.IDisplayManager").getMethod("getDisplayInfo",int.class).invoke(dm,0);
        if(info==null)throw new IllegalStateException("No default display");
        String id=(String)info.getClass().getField("uniqueId").get(info);
        if(id.equals("local:"+INNER))return true;
        if(id.equals("local:"+OUTER))return false;
        throw new IllegalStateException("Unexpected default display "+id);
    }
}
