import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Short SM-F971N trial: keep physical power and luminance across a logical-display swap. */
public final class SurfaceBrightnessPinTrial {
    private static final long INNER=4630947004648141459L, OUTER=4630947123231501204L;

    public static void main(String[] args)throws Exception {
        if(!Build.MODEL.equals("SM-F971N"))throw new IllegalStateException("Unverified model");
        Looper.prepareMainLooper();
        Object thread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
        Context context=(Context)thread.getClass().getMethod("getSystemContext").invoke(thread);
        Object states=context.getSystemService("device_state");
        Class<?> stateApi=Class.forName("android.hardware.devicestate.IDeviceStateManager");
        IBinder stateBinder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"device_state");
        Object stateService=Class.forName("android.hardware.devicestate.IDeviceStateManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,stateBinder);
        Object info=stateApi.getMethod("getDeviceStateInfo").invoke(stateService);
        if(!info.getClass().getField("baseState").get(info).equals(info.getClass().getField("currentState").get(info)))
            throw new IllegalStateException("Existing state override; refusing trial");
        Class<?> request=Class.forName("android.hardware.devicestate.DeviceStateRequest");
        Method requestState=states.getClass().getMethod("requestState",request,Executor.class,
            Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback"));

        IBinder displayBinder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"display");
        Class<?> displayApi=Class.forName("android.hardware.display.IDisplayManager");
        Object displays=Class.forName("android.hardware.display.IDisplayManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,displayBinder);
        Method override=displayApi.getMethod("setDisplayStateOverrideWithDisplayId",IBinder.class,int.class,int.class,int.class);
        Method getBrightness=displayApi.getMethod("getBrightness",int.class);
        Binder[] leases={new Binder(),new Binder()};

        Class<?> surface=Class.forName("android.view.SurfaceControl");
        Method tokenApi=surface.getMethod("getPhysicalDisplayToken",long.class);
        Method power=surface.getMethod("setDisplayPowerMode",IBinder.class,int.class);
        Method brightness=surface.getMethod("setDisplayBrightness",IBinder.class,float.class);
        IBinder[] panels={(IBinder)tokenApi.invoke(null,INNER),(IBinder)tokenApi.invoke(null,OUTER)};
        if(panels[0]==null||panels[1]==null)throw new IllegalStateException("Missing panel token");

        ScheduledExecutorService pin=Executors.newScheduledThreadPool(2);
        AtomicLong writes=new AtomicLong();AtomicReference<Throwable> failure=new AtomicReference<>();
        new Timer(true).schedule(new TimerTask(){public void run(){System.exit(2);}},10_000);
        try {
            set(states,requestState,request,5);Thread.sleep(900);
            float level=Math.max(.02f,(float)getBrightness.invoke(displays,0));
            System.out.println("BRIGHTNESS "+level);
            for(int i=0;i<2;i++)override.invoke(displays,leases[i],2,i,6500);
            for(IBinder panel:panels)pin.scheduleWithFixedDelay(()->{
                try {
                    power.invoke(null,panel,2);
                    if(!((boolean)brightness.invoke(null,panel,level)))throw new IllegalStateException("Brightness rejected");
                    writes.incrementAndGet();
                }catch(Throwable error){failure.compareAndSet(null,error);}
            },0,1,TimeUnit.MILLISECONDS);
            Thread.sleep(100);set(states,requestState,request,4);Thread.sleep(1800);
            set(states,requestState,request,5);Thread.sleep(1800);
            System.out.println("WRITES "+writes.get()+" FAILURE "+failure.get());
        } finally {
            pin.shutdownNow();pin.awaitTermination(600,TimeUnit.MILLISECONDS);
            for(int i=0;i<2;i++)try{override.invoke(displays,leases[i],0,i,0);}catch(Exception ignored){}
            states.getClass().getMethod("cancelStateRequest").invoke(states);
            System.out.println("RESET "+SystemClock.elapsedRealtime());
        }
        System.exit(0);
    }
    private static void set(Object states,Method method,Class<?> request,int id)throws Exception {
        Object builder=request.getMethod("newBuilder",int.class).invoke(null,id);
        method.invoke(states,builder.getClass().getMethod("build").invoke(builder),null,null);
        System.out.println("REQUEST "+id+" "+SystemClock.elapsedRealtime());
    }
}
