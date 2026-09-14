package dev.poldy.lab;

import java.util.concurrent.atomic.AtomicInteger;

/** Read-only shell check using the production readers with accelerated child deadlines. */
public final class LogcatLifecycleProbe {
    public static void main(String[] args)throws Exception {
        AtomicInteger failures=new AtomicInteger(),lines=new AtomicInteger();
        RenewingLogcat.Listener listener=new RenewingLogcat.Listener(){
            public void line(String line){lines.incrementAndGet();}
            public void stopped(String reason){System.out.println("FAILED "+reason);failures.incrementAndGet();}
        };
        RenewingLogcat a=new RenewingLogcat(RenewingLogcat.Stream.ANGLE,listener,2);
        RenewingLogcat b=new RenewingLogcat(RenewingLogcat.Stream.LUMINANCE,listener,2);
        try {
            a.start();b.start();Thread.sleep(7200);
            int ac=a.leases(),bc=b.leases();
            if(ac<3||bc<3||failures.get()!=0)throw new AssertionError("Lease renewal failed: "+ac+"/"+bc);
            a.close();b.close();Thread.sleep(2500);
            if(a.leases()!=ac||b.leases()!=bc||a.running()||b.running()||failures.get()!=0)
                throw new AssertionError("Reader restarted after close");
            System.out.println("PASS angle_leases="+ac+" luminance_leases="+bc+" lines="+lines+" closed_without_restart=true");
        }finally {a.close();b.close();}
    }
}
