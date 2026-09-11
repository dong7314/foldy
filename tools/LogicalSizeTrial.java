import android.os.*;
import android.graphics.*;
import android.view.*;
import java.util.concurrent.*;
import java.lang.reflect.*;

/** Short non-persistent size trial. Requires an already prepared outer-primary display. */
public final class LogicalSizeTrial {
    static Object wm;static Class<?> api;static Point original;static int density;
    static void set(int width,int height)throws Exception {
        Class<?> builder=Class.forName("com.samsung.android.view.MultiResolutionChangeRequestInfo$Builder");
        Object b=builder.getConstructor(int.class).newInstance(0);
        builder.getMethod("setWidth",int.class).invoke(b,width);builder.getMethod("setHeight",int.class).invoke(b,height);
        builder.getMethod("setDensity",int.class).invoke(b,density);builder.getMethod("setSaveToSettings",boolean.class).invoke(b,false);
        Object request=builder.getMethod("build").invoke(b);
        api.getMethod("setForcedDisplaySizeDensityWithInfo",request.getClass()).invoke(wm,request);
        Point actual=new Point();api.getMethod("getBaseDisplaySize",int.class,Point.class).invoke(wm,0,actual);
        System.out.println("SIZE requested="+width+"x"+height+" actual="+actual+" save=false epoch="+System.currentTimeMillis());
    }
    static void waitUnlocked(long millis)throws Exception {
        long end=SystemClock.elapsedRealtime()+millis;
        while(SystemClock.elapsedRealtime()<end){if((boolean)api.getMethod("isKeyguardLocked").invoke(wm))throw new IllegalStateException("Locked during trial");Thread.sleep(40);}
    }
    public static void main(String[] args) {
        try {
            if(!Build.MODEL.equals("SM-F971N"))throw new IllegalStateException("Unverified model");
            wm=LogicalCaptureProbe.window();api=Class.forName("android.view.IWindowManager");
            if((boolean)api.getMethod("isKeyguardLocked").invoke(wm))throw new IllegalStateException("Locked");
            original=new Point();api.getMethod("getBaseDisplaySize",int.class,Point.class).invoke(wm,0,original);
            density=(int)api.getMethod("getBaseDisplayDensity",int.class).invoke(wm,0);
            if(original.x!=1248||original.y!=1972)throw new IllegalStateException("Unexpected initial size "+original);
            IBinder displayBinder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"display");
            Object dm=Class.forName("android.hardware.display.IDisplayManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,displayBinder);
            Method power=Class.forName("android.hardware.display.IDisplayManager").getMethod("setDisplayStateOverrideWithDisplayId",IBinder.class,int.class,int.class,int.class);
            Binder[] powerTokens={new Binder(),new Binder()};
            for(int i=0;i<2;i++)power.invoke(dm,powerTokens[i],2,i,7000);
            SurfaceControl testLayer=new SurfaceControl.Builder().setName("Poldy logical resize test").setBufferSize(1248,1972).build();
            Surface testSurface=new Surface(testLayer);Canvas canvas=testSurface.lockHardwareCanvas();canvas.drawColor(Color.rgb(36,150,160));
            for(int y=200;y<1972;y+=200)for(int x=0;x<1248;x+=200){canvas.save();canvas.clipRect(x,y,x+100,y+100);canvas.drawColor(Color.WHITE);canvas.restore();}
            testSurface.unlockCanvasAndPost(canvas);
            try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
                SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,testLayer,0);
                t.setLayer(testLayer,2000000000).setVisibility(testLayer,true).apply();
            }
            ScheduledExecutorService projector=Executors.newSingleThreadScheduledExecutor();
            SurfaceControl live=null;
            SurfaceControl liveParent=null;
            if(args.length==1&&args[0].equals("live")) {
                liveParent=new SurfaceControl.Builder().setName("Poldy logical live parent").setBufferSize(2448,1848).build();
                live=SurfaceControl.class.getConstructor().newInstance();
                if(!(boolean)api.getMethod("mirrorDisplay",int.class,SurfaceControl.class).invoke(wm,0,live))throw new IllegalStateException("Mirror failed");
                try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()) {
                    SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,liveParent,1);
                    t.setLayer(liveParent,1900000000).setVisibility(liveParent,true).setAlpha(liveParent,1);
                    t.reparent(live,liveParent).setLayer(live,1).setVisibility(live,true).setAlpha(live,1).apply();
                }
            }
            if(args.length==1&&(args[0].equals("projection")||args[0].equals("live"))) {
                IBinder token=(IBinder)SurfaceControl.class.getMethod("getPhysicalDisplayToken",long.class).invoke(null,4630947123231501204L);
                Method project=SurfaceControl.Transaction.class.getMethod("setDisplayProjection",IBinder.class,int.class,Rect.class,Rect.class);
                projector.scheduleAtFixedRate(()->{try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
                    project.invoke(t,token,0,new Rect(0,0,1248,1972),new Rect(0,0,1248,1972));t.apply();
                }catch(Exception e){e.printStackTrace();}},0,2,TimeUnit.MILLISECONDS);
            }
            System.out.println("READY original="+original+" density="+density+" epoch="+System.currentTimeMillis());
            try {
                waitUnlocked(1200);set(2448,1848);waitUnlocked(400);LogicalCaptureProbe.capture();
                waitUnlocked(1200);set(original.x,original.y);waitUnlocked(400);LogicalCaptureProbe.capture();waitUnlocked(500);
            }finally{
                projector.shutdownNow();projector.awaitTermination(300,TimeUnit.MILLISECONDS);
                set(original.x,original.y);
                try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setVisibility(testLayer,false).apply();}
                testSurface.release();testLayer.release();System.out.println("RESTORED");
                if(live!=null)live.release();
                if(liveParent!=null)liveParent.release();
                for(int i=0;i<2;i++)power.invoke(dm,powerTokens[i],0,i,0);
            }
        }catch(Throwable e){e.printStackTrace();System.exit(1);}System.exit(0);
    }
}
