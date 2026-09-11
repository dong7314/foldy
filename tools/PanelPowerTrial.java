import android.os.*;
import android.content.Context;
import java.lang.reflect.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;

/** SM-F971N-only seven-second display power experiment. No persistent settings. */
public final class PanelPowerTrial {
    public static void main(String[] args) throws Exception {
        if(!Build.MODEL.equals("SM-F971N"))throw new IllegalStateException("Unverified model");
        Looper.prepareMainLooper();
        Object thread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
        Context context=(Context)thread.getClass().getMethod("getSystemContext").invoke(thread);
        Object states=context.getSystemService("device_state");
        Class<?> stateApi=Class.forName("android.hardware.devicestate.IDeviceStateManager");
        IBinder stateBinder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"device_state");
        Object stateService=Class.forName("android.hardware.devicestate.IDeviceStateManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,stateBinder);
        Object info=stateApi.getMethod("getDeviceStateInfo").invoke(stateService);
        Object base=info.getClass().getField("baseState").get(info),current=info.getClass().getField("currentState").get(info);
        if(!base.equals(current))throw new IllegalStateException("Existing state override; refusing trial");
        Class<?> request=Class.forName("android.hardware.devicestate.DeviceStateRequest");
        Method requestState=states.getClass().getMethod("requestState",request,Executor.class,Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback"));
        IBinder displayBinder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"display");
        Object displays=Class.forName("android.hardware.display.IDisplayManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,displayBinder);
        Method power=Class.forName("android.hardware.display.IDisplayManager").getMethod("setDisplayStateOverrideWithDisplayId",IBinder.class,int.class,int.class,int.class);
        Binder[] leases={new Binder(),new Binder()};
        new Timer(true).schedule(new TimerTask(){public void run(){System.exit(2);}},7000);
        try {
            set(states,requestState,request,5);Thread.sleep(700);
            for(int i=0;i<2;i++)power.invoke(displays,leases[i],2,i,5500);
            System.out.println("POWER_HOLD_ON "+SystemClock.elapsedRealtime());Thread.sleep(200);
            set(states,requestState,request,4);Thread.sleep(1400);
            set(states,requestState,request,5);Thread.sleep(1400);
        } finally {
            for(int i=0;i<2;i++)try{power.invoke(displays,leases[i],0,i,0);}catch(Exception ignored){}
            states.getClass().getMethod("cancelStateRequest").invoke(states);
            System.out.println("RESET "+SystemClock.elapsedRealtime());
        }
        System.exit(0);
    }
    static void set(Object states,Method method,Class<?> request,int id)throws Exception {
        Object builder=request.getMethod("newBuilder",int.class).invoke(null,id);
        method.invoke(states,builder.getClass().getMethod("build").invoke(builder),null,null);
        System.out.println("REQUEST "+id+" "+SystemClock.elapsedRealtime());
    }
}
