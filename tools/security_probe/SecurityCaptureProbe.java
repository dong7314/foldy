package dev.poldy.lab;

import android.graphics.*;
import android.os.*;
import android.view.*;
import java.lang.reflect.*;

/** Shell audit: refuses pixels unless our synthetic activity owns the foreground task. */
public final class SecurityCaptureProbe {
    private static boolean synthetic()throws Exception {
        String owner=new TaskProfileReader().read().owner();
        if(owner==null)return false;
        android.content.ComponentName component=android.content.ComponentName.unflattenFromString(owner.substring(owner.lastIndexOf(':')+1));
        return component!=null&&component.getPackageName().equals("dev.foldy.securityprobe")
            &&component.getClassName().equals("dev.foldy.securityprobe.ProbeActivity");
    }
    private static boolean unlocked()throws Exception {
        IBinder b=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"window");
        Object wm=Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,b);
        return !(boolean)Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked").invoke(wm);
    }
    private static void constants(Class<?> type)throws Exception {
        for(Field field:type.getDeclaredFields()){
            String n=field.getName().toLowerCase();
            if(Modifier.isStatic(field.getModifiers())&&field.getType()==int.class&&(n.contains("secure")||n.contains("protected"))){
                field.setAccessible(true);System.out.println(type.getName()+"."+field.getName()+"="+field.get(null));
            }
        }
        for(Class<?> nested:type.getDeclaredClasses())constants(nested);
    }
    public static void main(String[] args)throws Exception {
        if(android.os.Process.myUid()!=2000)throw new SecurityException("Shell audit only");
        if(args.length==0){constants(Class.forName("android.window.ScreenCaptureInternal"));constants(Class.forName("android.window.ScreenCapture"));return;}
        if(!unlocked()||!synthetic()){System.out.println("SKIP: synthetic test activity is not unlocked and foreground");return;}
        SurfaceControl overlay=null;Surface surface=null;
        try {
            if(args[0].equals("overlay")){
                // Same root/layer mechanism as NativeScene; never uses a captured image.
                overlay=new SurfaceControl.Builder().setName("Foldy synthetic security audit").setBufferSize(400,400).build();
                surface=new Surface(overlay);Canvas canvas=surface.lockHardwareCanvas();canvas.drawColor(Color.rgb(224,32,64));surface.unlockCanvasAndPost(canvas);
                try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
                    SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,overlay,0);
                    t.setLayer(overlay,2000000000).setPosition(overlay,300,400).setAlpha(overlay,1).setVisibility(overlay,true).apply();
                }
                Thread.sleep(200);
            }
            if(!unlocked()||!synthetic()){System.out.println("SKIP: foreground changed");return;}
            try(CapturedFrame frame=new NativeFrameCapture().capture(PhysicalPanels.primaryIsInner(),new SurfaceControl[0])){
                if(frame.error!=null){System.out.println("CAPTURE_ERROR:"+frame.error);return;}
                Bitmap bitmap=frame.takeBitmap();
                if(bitmap==null){System.out.println("CAPTURE_EMPTY");return;}
                try {
                    if(!unlocked()||!synthetic()){System.out.println("DISCARDED: foreground changed");return;}
                    Bitmap pixels=bitmap.copy(Bitmap.Config.ARGB_8888,false);
                    try {
                        int green=0,red=0,black=0,total=0;
                        for(int y=450;y<750;y+=10)for(int x=350;x<650;x+=10){
                            int c=pixels.getPixel(x,y);total++;
                            if(Math.abs(Color.red(c)-24)<8&&Math.abs(Color.green(c)-184)<8&&Math.abs(Color.blue(c)-120)<8)green++;
                            if(Math.abs(Color.red(c)-224)<8&&Math.abs(Color.green(c)-32)<8&&Math.abs(Color.blue(c)-64)<8)red++;
                            if(Color.red(c)<8&&Color.green(c)<8&&Color.blue(c)<8)black++;
                        }
                        System.out.println("SYNTHETIC_SAMPLE total="+total+",green="+green+",red="+red+",black="+black);
                    }finally{pixels.recycle();}
                }finally{bitmap.recycle();}
            }
        }finally {
            if(overlay!=null){try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(overlay,false).setAlpha(overlay,0).apply();}}
            if(surface!=null)surface.release();if(overlay!=null)overlay.release();
        }
    }
}
