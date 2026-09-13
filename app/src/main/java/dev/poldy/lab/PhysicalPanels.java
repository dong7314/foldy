package dev.poldy.lab;

import android.graphics.Rect;
import android.os.IBinder;
import android.view.SurfaceControl;
import java.lang.reflect.Method;

/** Verified physical identities, independent of their current logical display IDs. */
final class PhysicalPanels {
    static final long INNER=4630947004648141459L, OUTER=4630947123231501204L;
    private static final int POWER_MODE_NORMAL=2;
    private final IBinder[] tokens=new IBinder[2];
    private final Method stack=SurfaceControl.Transaction.class.getMethod("setDisplayLayerStack",IBinder.class,int.class);
    private final Method projection=SurfaceControl.Transaction.class.getMethod("setDisplayProjection",IBinder.class,int.class,Rect.class,Rect.class);
    private final Method power=SurfaceControl.class.getMethod("setDisplayPowerMode",IBinder.class,int.class);
    private final Object displays;
    private final Method getDisplayInfo;
    private final Rect[] bounds={new Rect(0,0,2448,1848),new Rect(0,0,1248,1972)};
    static final class ProjectionSpec {
        final int rotation,width,height;
        ProjectionSpec(int rotation,int width,int height) {
            this.rotation=rotation;this.width=width;this.height=height;
        }
    }
    PhysicalPanels()throws Exception {
        tokens[0]=(IBinder)SurfaceControl.class.getMethod("getPhysicalDisplayToken",long.class).invoke(null,INNER);
        tokens[1]=(IBinder)SurfaceControl.class.getMethod("getPhysicalDisplayToken",long.class).invoke(null,OUTER);
        IBinder displayBinder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"display");
        Class<?> displayApi=Class.forName("android.hardware.display.IDisplayManager");
        displays=Class.forName("android.hardware.display.IDisplayManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,displayBinder);
        getDisplayInfo=displayApi.getMethod("getDisplayInfo",int.class);
    }
    ProjectionSpec logicalProjection(int displayId)throws Exception {
        Object info=getDisplayInfo.invoke(displays,displayId);
        if(info==null)throw new IllegalStateException("No logical display "+displayId);
        Class<?> type=info.getClass();
        int rotation=type.getField("rotation").getInt(info);
        int width=type.getField("logicalWidth").getInt(info);
        int height=type.getField("logicalHeight").getInt(info);
        if(width<=0||height<=0)throw new IllegalStateException("Invalid logical bounds for display "+displayId);
        return new ProjectionSpec(rotation,width,height);
    }
    void routeNative(SurfaceControl.Transaction t,int innerStack,int outerStack,
                     ProjectionSpec innerProjection,ProjectionSpec outerProjection)throws Exception {
        stack.invoke(t,tokens[0],innerStack);stack.invoke(t,tokens[1],outerStack);
        ProjectionSpec[] specs={innerProjection,outerProjection};
        for(int i=0;i<2;i++) {
            applyProjection(t,i,specs[i]);
        }
    }
    void routeLogical(SurfaceControl.Transaction t,int innerDisplay,int outerDisplay)throws Exception {
        int[] displayIds={innerDisplay,outerDisplay};
        for(int panel=0;panel<tokens.length;panel++) {
            int displayId=displayIds[panel];
            ProjectionSpec spec=logicalProjection(displayId);
            stack.invoke(t,tokens[panel],displayId);
            applyProjection(t,panel,spec);
        }
    }
    private void applyProjection(SurfaceControl.Transaction t,int panel,ProjectionSpec spec)throws Exception {
        Rect nativeBounds=bounds[panel];
        // SurfaceFlinger applies rotation after mapping the layer-stack viewport.
        // Its output rectangle therefore belongs to the oriented display space.
        // Passing the natural portrait rectangle for a 90/270-degree projection
        // makes SurfaceFlinger scale each axis by the opposite aspect ratio and
        // crops fullscreen video after a fold transition.
        boolean quarterTurn=(spec.rotation&1)!=0;
        Rect output=quarterTurn
            ?new Rect(0,0,nativeBounds.height(),nativeBounds.width())
            :new Rect(nativeBounds);
        projection.invoke(t,tokens[panel],spec.rotation,
            new Rect(0,0,spec.width,spec.height),output);
    }
    void keepPowered(int index)throws Exception {
        if(index<0||index>=tokens.length)throw new IllegalArgumentException("Unknown physical panel "+index);
        power.invoke(null,tokens[index],POWER_MODE_NORMAL);
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
