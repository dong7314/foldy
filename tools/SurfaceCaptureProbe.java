import android.hardware.HardwareBuffer;
import android.os.IBinder;
import java.lang.reflect.*;

/** Shell-only, read-only capability probe. Prints metadata, never writes captured pixels. */
public final class SurfaceCaptureProbe {
    public static void main(String[] args) throws Exception {
        if(args.length>0&&args[0].equals("window-api")) {
            for(String name:new String[]{"android.view.IWindowManager","android.view.SurfaceControl","android.hardware.input.IInputManager","android.window.ScreenCaptureInternal$LayerCaptureArgs$Builder","com.samsung.android.view.MultiResolutionChangeRequestInfo$Builder"}) {
                try {
                    Class<?> c=Class.forName(name);
                    for(Constructor<?> constructor:c.getDeclaredConstructors())System.out.println(name+": "+constructor);
                    for(Method method:c.getDeclaredMethods()) {
                        String n=method.getName().toLowerCase();
                        if(n.contains("mirror")||n.contains("forced")||n.contains("inject")||n.contains("displayid")||n.contains("save")||n.contains("capture")||n.contains("size")||n.contains("density")||n.contains("build"))System.out.println(name+": "+method);
                    }
                }catch(Exception e){System.out.println(name+": "+e);}
            }
            return;
        }
        if(args.length>0&&args[0].equals("display-api")) {
            for(String name:new String[]{"android.hardware.display.IDisplayManager","android.hardware.display.DisplayManager","android.view.SurfaceControl","android.view.SurfaceControl$Transaction","android.view.SurfaceControl$Builder"}) {
                for(Method method:Class.forName(name).getDeclaredMethods()) {
                    String n=method.getName().toLowerCase();
                    if(n.contains("state")||n.contains("power")||n.contains("override")||n.contains("displaytoken")||n.contains("layerstack")||n.contains("buffer")||n.contains("parent")||n.contains("layer")||n.equals("build"))System.out.println(name+": "+method);
                }
            }
            return;
        }
        Class<?> sc=Class.forName("android.view.SurfaceControl");
        long[] ids=(long[])sc.getMethod("getPhysicalDisplayIds").invoke(null);
        Class<?> capture=Class.forName("android.window.ScreenCaptureInternal");
        for(Class<?> type:capture.getDeclaredClasses()) {
            System.out.println("TYPE "+type.getName());
            for(Constructor<?> ctor:type.getDeclaredConstructors())System.out.println("  "+ctor);
            for(Method method:type.getDeclaredMethods())System.out.println("  "+method);
        }
        for(Method method:capture.getDeclaredMethods())System.out.println("METHOD "+method);
        if(args.length==0)return;
        Class<?> builder=Class.forName("android.window.ScreenCaptureInternal$DisplayCaptureArgs$Builder");
        Class<?> captureArgs=Class.forName("android.window.ScreenCaptureInternal$DisplayCaptureArgs");
        for (long id:ids) {
            long start=System.nanoTime();
            try {
                IBinder token=(IBinder)sc.getMethod("getPhysicalDisplayToken",long.class).invoke(null,id);
                Object b=builder.getConstructor(IBinder.class).newInstance(token);
                builder.getMethod("setSecureContentPolicy",int.class).invoke(b,0);
                builder.getMethod("setProtectedContentPolicy",int.class).invoke(b,0);
                builder.getMethod("setFrameScale",float.class).invoke(b,1f);
                Object result=capture.getMethod("captureDisplay",captureArgs).invoke(null,builder.getMethod("build").invoke(b));
                if(result==null){System.out.println(id+": no buffer");continue;}
                try(HardwareBuffer buffer=(HardwareBuffer)result.getClass().getMethod("getHardwareBuffer").invoke(result)) {
                    System.out.println(id+": "+buffer.getWidth()+"x"+buffer.getHeight()+", format="+buffer.getFormat()
                        +", elapsed_ms="+(System.nanoTime()-start)/1_000_000);
                }
            } catch(Exception e) {
                Throwable root=e;while(root.getCause()!=null)root=root.getCause();
                System.out.println(id+": "+root);
            }
        }
    }
}
