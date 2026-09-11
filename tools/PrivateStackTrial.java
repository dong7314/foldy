import android.os.*;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.lang.reflect.*;
import java.util.concurrent.*;

/** Eight-second, unlocked SM-F971N-only diagnostic; draws generated test pixels, never reads apps. */
public final class PrivateStackTrial {
    static boolean locked() throws Exception {
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"window");
        Object wm=Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        return (boolean)Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked").invoke(wm);
    }
    static Object dm;static Method getInfo,setStack,setDisplayStack;
    static SurfaceControl[] layers=new SurfaceControl[2];static Surface[] surfaces=new Surface[2];
    static IBinder[] tokens=new IBinder[2];static boolean pin;
    static synchronized void route() throws Exception {
        Object d0=getInfo.invoke(dm,0);if(d0==null)return;
        String unique=(String)d0.getClass().getField("uniqueId").get(d0);
        if(!unique.equals("local:4630947004648141459")&&!unique.equals("local:4630947123231501204"))throw new IllegalStateException("Unexpected primary");
        boolean inner=unique.equals("local:4630947004648141459");
        try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()) {
            for(int i=0;i<2;i++) {
                int stack=2100000000+i;
                setStack.invoke(t,layers[i],stack);
                if(pin){
                    setDisplayStack.invoke(t,tokens[i],stack);
                    int w=i==0?2448:1248,h=i==0?1848:1972;
                    SurfaceControl.Transaction.class.getMethod("setDisplayProjection",IBinder.class,int.class,Rect.class,Rect.class).invoke(t,tokens[i],0,new Rect(0,0,w,h),new Rect(0,0,w,h));
                }
            }
            t.apply();
        }
    }
    public static void main(String[] args)throws Exception {
        if(!Build.MODEL.equals("SM-F971N"))throw new IllegalStateException("Unverified model");
        System.out.println("BEGIN");pin=args.length==1&&args[0].equals("pin");Looper.prepareMainLooper();
        System.out.println("LOOPER");Object thread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
        Context context=(Context)thread.getClass().getMethod("getSystemContext").invoke(thread);
        System.out.println("CONTEXT");if(locked())throw new IllegalStateException("Locked");
        System.out.println("UNLOCKED");if(args.length==1&&args[0].equals("probe"))System.exit(0);Object states=context.getSystemService("device_state");Class<?> stateApi=Class.forName("android.hardware.devicestate.IDeviceStateManager");
        Method service=Class.forName("android.os.ServiceManager").getMethod("getService",String.class);
        Object stateService=Class.forName("android.hardware.devicestate.IDeviceStateManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,service.invoke(null,"device_state"));
        Object info=stateApi.getMethod("getDeviceStateInfo").invoke(stateService);
        if(!info.getClass().getField("baseState").get(info).equals(info.getClass().getField("currentState").get(info)))throw new IllegalStateException("Existing override");
        dm=Class.forName("android.hardware.display.IDisplayManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,service.invoke(null,"display"));
        Class<?> displayApi=Class.forName("android.hardware.display.IDisplayManager");getInfo=displayApi.getMethod("getDisplayInfo",int.class);
        Method power=displayApi.getMethod("setDisplayStateOverrideWithDisplayId",IBinder.class,int.class,int.class,int.class);
        Binder[] leases={new Binder(),new Binder()};
        Class<?> request=Class.forName("android.hardware.devicestate.DeviceStateRequest");
        Method requestState=states.getClass().getMethod("requestState",request,java.util.concurrent.Executor.class,Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback"));
        setStack=SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class);
        setDisplayStack=SurfaceControl.Transaction.class.getMethod("setDisplayLayerStack",IBinder.class,int.class);
        ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
        java.util.Timer timer=new java.util.Timer(true);timer.schedule(new java.util.TimerTask(){public void run(){System.exit(3);}},8000);
        try {
            for(int i=0;i<2;i++)power.invoke(dm,leases[i],2,i,7000);
            PanelPowerTrial.set(states,requestState,request,5);Thread.sleep(500);
            int[] widths={2448,1248},heights={1848,1972};long[] ids={4630947004648141459L,4630947123231501204L};
            for(int i=0;i<2;i++) {
                tokens[i]=(IBinder)SurfaceControl.class.getMethod("getPhysicalDisplayToken",long.class).invoke(null,ids[i]);
                layers[i]=new SurfaceControl.Builder().setName("Poldy route trial "+i).setBufferSize(widths[i],heights[i]).build();
                surfaces[i]=new Surface(layers[i]);Canvas canvas=surfaces[i].lockHardwareCanvas();
                canvas.drawColor(i==0?Color.rgb(36,150,160):Color.rgb(180,90,36));
                for(int y=300;y<heights[i];y+=200)for(int x=0;x<widths[i];x+=200)if(((x+y)/200)%2==0){canvas.save();canvas.clipRect(x,y,x+100,y+100);canvas.drawColor(Color.WHITE);canvas.restore();}
                surfaces[i].unlockCanvasAndPost(canvas);
            }
            route();try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){for(SurfaceControl layer:layers)t.setLayer(layer,2000000000).setVisibility(layer,true);t.apply();}
            System.out.println("READY pin="+pin+" epoch="+System.currentTimeMillis());
            worker.scheduleAtFixedRate(()->{try {
                if(locked()){System.exit(4);return;}route();
            }catch(Exception e){System.err.println(e);}},0,2,TimeUnit.MILLISECONDS);
            Thread.sleep(1200);PanelPowerTrial.set(states,requestState,request,4);
            Thread.sleep(1700);PanelPowerTrial.set(states,requestState,request,5);
            Thread.sleep(1700);
        } finally {
            worker.shutdownNow();worker.awaitTermination(300,TimeUnit.MILLISECONDS);
            try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){for(SurfaceControl layer:layers)if(layer!=null)t.setVisibility(layer,false);t.apply();}
            for(Surface s:surfaces)if(s!=null)s.release();for(SurfaceControl s:layers)if(s!=null)s.release();
            for(int i=0;i<2;i++)try{power.invoke(dm,leases[i],0,i,0);}catch(Exception ignored){}
            states.getClass().getMethod("cancelStateRequest").invoke(states);
            System.out.println("RESET epoch="+System.currentTimeMillis());
        }
        System.exit(0);
    }
}
