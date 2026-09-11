package dev.poldy.lab;
import android.content.Context;
import android.os.*;
import android.view.*;
import android.graphics.*;
import java.util.concurrent.Executor;

/** Bounded real-profile smoke test, with the same capture/recovery/shield classes as the APK. */
public final class NativeProfileProbe {
    static Object states;static StableDisplay stable;static NativeScene scene;static PanelPower power;
    static SurfaceControl[] controls;static Surface[] surfaces;
    static void set(boolean inner)throws Exception {
        scene.shield();long started=SystemClock.elapsedRealtime();power.hold();
        Class<?> r=Class.forName("android.hardware.devicestate.DeviceStateRequest");
        Object b=r.getMethod("newBuilder",int.class).invoke(null,inner?4:5);
        states.getClass().getMethod("requestState",r,Executor.class,Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback")).invoke(states,b.getClass().getMethod("build").invoke(b),null,null);
        stable.resize(inner);
        while(SystemClock.elapsedRealtime()-started<650){stable.renew();Thread.sleep(10);}
        stable.resize(inner);scene.finishSwitch(inner);
        System.out.println("PROFILE "+(inner?"INNER":"OUTER"));System.out.flush();
    }
    static void hold(long ms)throws Exception {
        long end=SystemClock.elapsedRealtime()+ms;
        while(SystemClock.elapsedRealtime()<end){if(!stable.unlocked())throw new IllegalStateException("Locked");stable.renew();power.hold();Thread.sleep(100);}
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1||!Build.MODEL.equals("SM-F971N"))throw new IllegalStateException("Unverified");
        Looper.prepareMainLooper();Object thread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
        Context context=(Context)thread.getClass().getMethod("getSystemContext").invoke(thread);states=context.getSystemService("device_state");
        android.os.IBinder stateBinder=(android.os.IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"device_state");
        Object service=Class.forName("android.hardware.devicestate.IDeviceStateManager$Stub").getMethod("asInterface",android.os.IBinder.class).invoke(null,stateBinder);
        Object info=Class.forName("android.hardware.devicestate.IDeviceStateManager").getMethod("getDeviceStateInfo").invoke(service);
        Object base=info.getClass().getField("baseState").get(info),current=info.getClass().getField("currentState").get(info);
        if(!base.equals(current)||!PhysicalPanels.primaryIsInner())throw new IllegalStateException("Requires normal opened home without override");
        java.util.Timer timer=new java.util.Timer(true);timer.schedule(new java.util.TimerTask(){public void run(){System.exit(3);}},18000);
        try {
            stable=new StableDisplay(args[0]);if(!stable.unlocked())throw new IllegalStateException("Locked");
            power=new PanelPower();power.hold();scene=new NativeScene(true);controls=scene.copies();surfaces=new Surface[2];
            for(int i=0;i<2;i++){
                surfaces[i]=new Surface(controls[i]);Canvas c=surfaces[i].lockHardwareCanvas();c.drawColor(i==0?Color.rgb(36,150,160):Color.rgb(180,90,36));
                surfaces[i].unlockCanvasAndPost(c);
            }
            try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){for(SurfaceControl s:controls)t.setAlpha(s,1);t.apply();}
            hold(200);set(false);System.out.println("READY");System.out.flush();hold(1600);
            set(true);hold(150);
            try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setAlpha(controls[0],0).apply();}
            System.out.println("INNER_VISIBLE");System.out.flush();hold(3000);
            try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setAlpha(controls[0],1).apply();}
            hold(100);set(false);hold(150);
            try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setAlpha(controls[1],0).apply();}
            System.out.println("OUTER_VISIBLE");System.out.flush();hold(2600);
        }finally {
            if(scene!=null)scene.close();if(stable!=null)stable.close();if(power!=null)power.release();
            states.getClass().getMethod("cancelStateRequest").invoke(states);if(stable!=null)stable.disarm();
            if(surfaces!=null)for(Surface s:surfaces)if(s!=null)s.release();if(controls!=null)for(SurfaceControl s:controls)if(s!=null)s.release();timer.cancel();
        }
        System.out.println("RESTORED");System.exit(0);
    }
}
