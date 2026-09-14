package dev.poldy.lab;

import android.os.*;
import java.lang.reflect.Method;

/** Bounded replay of an already measured SDR output; does not switch profiles or settings. */
public final class MeasuredLuminanceTrial {
    public static void main(String[] args)throws Exception {
        try {run(args);System.exit(0);}
        catch(Throwable error){System.out.println("ABORT "+error);System.exit(2);}
    }
    private static void run(String[] args)throws Exception {
        if(!Build.MODEL.equals("SM-F971N"))throw new IllegalStateException("Unverified model");
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"window");
        Object wm=Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        Method locked=Class.forName("android.view.IWindowManager").getMethod("isKeyguardLocked");
        PhysicalPanels physical=new PhysicalPanels();
        try(PanelLuminanceReader reader=new PanelLuminanceReader()) {
            boolean inner=PhysicalPanels.primaryIsInner();
            PanelLuminance sample=null;long until=SystemClock.elapsedRealtime()+1800;
            while(sample==null&&SystemClock.elapsedRealtime()<until){Thread.sleep(50);sample=reader.snapshot(inner,physical.currentNits());}
            if(sample==null)throw new IllegalStateException("No current SDR sample; no write performed");
            System.out.println("BEFORE "+sample);
            if(args.length>0&&args[0].equals("replay")) {
                until=SystemClock.elapsedRealtime()+500;int writes=0;
                while(SystemClock.elapsedRealtime()<until) {
                    if((boolean)locked.invoke(wm)||PhysicalPanels.primaryIsInner()!=inner)throw new IllegalStateException("Screen locked or changed");
                    physical.keepLuminance(inner?0:1,sample);writes++;Thread.sleep(8);
                }
                System.out.println("REPLAY writes="+writes);
            }
            System.out.println("AFTER nits="+physical.currentNits());
        }
    }
}
