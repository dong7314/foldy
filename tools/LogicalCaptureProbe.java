import android.os.*;
import android.graphics.*;
import android.view.SurfaceControl;
import android.hardware.HardwareBuffer;

/** Invisible logical-display mirror; reports capture dimensions only. */
public final class LogicalCaptureProbe {
    public static Object window() throws Exception {
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"window");
        return Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
    }
    public static void main(String[] args) {
        try {capture();}catch(Throwable e){e.printStackTrace();System.exit(1);}System.exit(0);
    }
    public static void capture()throws Exception {
        Object wm=window();Class<?> api=Class.forName("android.view.IWindowManager");
        if((boolean)api.getMethod("isKeyguardLocked").invoke(wm))throw new IllegalStateException("Locked");
        Point size=new Point();api.getMethod("getBaseDisplaySize",int.class,Point.class).invoke(wm,0,size);
        SurfaceControl root=SurfaceControl.class.getConstructor().newInstance();
        try {
            boolean mirrored=(boolean)api.getMethod("mirrorDisplay",int.class,SurfaceControl.class).invoke(wm,0,root);
            if(!mirrored||!root.isValid())throw new IllegalStateException("Mirror unavailable");
            System.out.println("MIRROR valid="+root.isValid()+" size="+size);
            try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
                SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,root,2147483000);
                t.setVisibility(root,true).setAlpha(root,1).apply();
            }
            Thread.sleep(100);
            Class<?> builder=Class.forName("android.window.ScreenCaptureInternal$LayerCaptureArgs$Builder");
            Object b=builder.getConstructor(SurfaceControl.class).newInstance(root);
            builder.getMethod("setSourceCrop",Rect.class).invoke(b,new Rect(0,0,size.x,size.y));
            builder.getMethod("setFrameScale",float.class).invoke(b,1f);
            builder.getMethod("setSecureContentPolicy",int.class).invoke(b,0);
            builder.getMethod("setProtectedContentPolicy",int.class).invoke(b,0);
            Class<?> capture=Class.forName("android.window.ScreenCaptureInternal");
            Object result=capture.getMethod("captureLayers",Class.forName("android.window.ScreenCaptureInternal$LayerCaptureArgs")).invoke(null,builder.getMethod("build").invoke(b));
            if(result==null)throw new IllegalStateException("No captured buffer");
            try(HardwareBuffer buffer=(HardwareBuffer)result.getClass().getMethod("getHardwareBuffer").invoke(result)) {
                System.out.println("LOGICAL_CAPTURE "+buffer.getWidth()+"x"+buffer.getHeight()+" base="+size+" secure_policy=REDACT protected_policy=REDACT");
            }
        }finally{root.release();}
    }
}
