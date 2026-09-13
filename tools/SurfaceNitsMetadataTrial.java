import android.os.IBinder;
import java.lang.reflect.Method;

/** One-shot SM-F971N trial: preserve current luminance metadata without a backlight value. */
public final class SurfaceNitsMetadataTrial {
    private static final long INNER=4630947004648141459L, OUTER=4630947123231501204L;
    public static void main(String[] args)throws Exception {
        IBinder displayBinder=(IBinder)Class.forName("android.os.ServiceManager")
            .getMethod("getService",String.class).invoke(null,"display");
        Class<?> displayApi=Class.forName("android.hardware.display.IDisplayManager");
        Object displays=Class.forName("android.hardware.display.IDisplayManager$Stub")
            .getMethod("asInterface",IBinder.class).invoke(null,displayBinder);
        Object info=displayApi.getMethod("getDisplayInfo",int.class).invoke(displays,0);
        String unique=(String)info.getClass().getField("uniqueId").get(info);
        long physical=unique.equals("local:"+INNER)?INNER:unique.equals("local:"+OUTER)?OUTER:0;
        if(physical==0)throw new IllegalStateException("Unexpected display "+unique);
        float nits=(float)displayApi.getMethod("getBrightnessByUnit",int.class,int.class)
            .invoke(displays,0,2);
        if(!Float.isFinite(nits)||nits<=1)throw new IllegalStateException("Invalid nits "+nits);
        Class<?> surface=Class.forName("android.view.SurfaceControl");
        IBinder token=(IBinder)surface.getMethod("getPhysicalDisplayToken",long.class).invoke(null,physical);
        Method set=surface.getMethod("setDisplayBrightness",IBinder.class,float.class,float.class,float.class,float.class);
        boolean accepted=(boolean)set.invoke(null,token,-1f,nits,-1f,nits);
        System.out.println("physical="+physical+" nits="+nits+" accepted="+accepted);
    }
}
