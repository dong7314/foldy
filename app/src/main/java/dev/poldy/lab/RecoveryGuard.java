package dev.poldy.lab;

import android.os.SystemClock;
import java.io.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Independent shell process: a missing heartbeat or closed pipe restores only this trial. */
public final class RecoveryGuard {
    private static volatile long until=SystemClock.elapsedRealtime()+6000;
    private static volatile boolean disarmed;
    private static String run(String... args)throws Exception {
        Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
        if(!p.waitFor(3,TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("Timeout");}
        return new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=1||!args[0].matches("[0-9]+"))System.exit(2);
        String owner=args[0];
        Thread reader=new Thread(()->{
            try(BufferedReader input=new BufferedReader(new InputStreamReader(System.in))){
                String line;while((line=input.readLine())!=null){
                    if(line.equals("DISARM")){disarmed=true;return;}
                    if(line.equals("BEAT"))until=SystemClock.elapsedRealtime()+6000;
                }
            }catch(IOException ignored){}until=0;
        });reader.setDaemon(true);reader.start();System.out.println("READY");System.out.flush();
        while(!disarmed&&SystemClock.elapsedRealtime()<until)Thread.sleep(100);
        if(!disarmed){
            String states=run("/system/bin/dumpsys","device_state");
            Matcher pid=Pattern.compile("Request: mPid=(\\d+),").matcher(states);
            boolean owns=pid.find()&&pid.group(1).equals(owner);
            // CLOSED can cancel our request before a crash. Do not touch another owner's override.
            if(owns||states.contains("mOverrideState=Optional.empty")){
                if(run("/system/bin/wm","size","-d","0").contains("Override size: 2448x1848"))
                    run("/system/bin/wm","size","reset","-d","0");
                if(owns){
                    Matcher base=Pattern.compile("mBaseState=Optional\\[DeviceState\\{identifier=([0-3]),").matcher(states);
                    if(base.find()){run("/system/bin/cmd","device_state","state",base.group(1));run("/system/bin/cmd","device_state","state","reset");}
                }
                // The compositor caches its normal stack IDs. A dead client may have replaced
                // those with private shield stacks without changing that system-side cache.
                Thread.sleep(600);
                if(run("/system/bin/dumpsys","device_state").contains("mOverrideState=Optional.empty")) {
                    boolean inner=PhysicalPanels.primaryIsInner();
                    try(android.view.SurfaceControl.Transaction t=new android.view.SurfaceControl.Transaction()) {
                        new PhysicalPanels().route(t,inner?0:1,inner?1:0);t.apply();
                    }
                }
            }
        }
        System.exit(0);
    }
}
