package dev.poldy.lab;

import android.content.Context;
import android.os.*;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/** Ten-second comparison of the shipped fixed mapping. Does not edit launcher data/settings. */
public final class HomeLayoutProbe {
    private static String command(String... args)throws Exception {
        java.lang.Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
        String result=new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        if(p.waitFor()!=0)throw new IllegalStateException(result);
        return result;
    }
    public static void main(String[] args)throws Exception {
        if((args.length!=1&&args.length!=2)||!Build.MODEL.equals("SM-F971N"))throw new IllegalStateException("Unverified invocation");
        Looper.prepareMainLooper();
        Object thread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
        Context context=(Context)thread.getClass().getMethod("getSystemContext").invoke(thread);
        String state=command("/system/bin/cmd","device_state","state");
        if(state.contains("Override state:")||!state.contains("name='OPENED'"))throw new IllegalStateException("Requires normal OPENED");
        if(command("/system/bin/wm","size").contains("Override size:"))throw new IllegalStateException("Existing override");
        Object states=context.getSystemService("device_state");
        Class<?> request=Class.forName("android.hardware.devicestate.DeviceStateRequest");
        Method set=states.getClass().getMethod("requestState",request,Executor.class,Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback"));
        StableDisplay stable=null; NativeScene scene=null; PanelPower power=null;
        // The independent StableDisplay guard starts before any state request, and restores on exit.
        java.util.Timer deadline=new java.util.Timer(true);
        deadline.schedule(new java.util.TimerTask(){public void run(){System.exit(3);}},15000);
        try {
            stable=new StableDisplay(args[0]);
            if(!stable.unlocked())throw new IllegalStateException("Locked");
            power=new PanelPower();power.hold();
            Object b=request.getMethod("newBuilder",int.class).invoke(null,5);
            set.invoke(states,b.getClass().getMethod("build").invoke(b),null,null);
            Thread.sleep(500);
            stable.resize(true);
            scene=new NativeScene(false);stable.attachLive(scene.liveParent());
            if(args.length==2&&args[1].equals("profile")) {
                android.content.res.Configuration change=new android.content.res.Configuration();
                change.getClass().getField("semDisplayDeviceType").setInt(change,0);
                Object atm=Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
                Object result=Class.forName("android.app.IActivityTaskManager").getMethod("updateConfiguration",change.getClass()).invoke(atm,change);
                System.out.println("PROFILE configuration request returned "+result);
            }
            System.out.println("READY fixed outer mapping, wide logical viewport");System.out.flush();
            long until=SystemClock.elapsedRealtime()+9000;
            while(SystemClock.elapsedRealtime()<until) {
                if(!stable.unlocked())throw new IllegalStateException("Locked");
                stable.renew();power.hold();Thread.sleep(500);
            }
        } finally {
            if(stable!=null)stable.close();
            if(scene!=null)scene.close();
            if(power!=null)power.release();
            states.getClass().getMethod("cancelStateRequest").invoke(states);
            if(stable!=null)stable.disarm();
            deadline.cancel();
        }
        System.out.println("RESTORED");System.exit(0);
    }
}
