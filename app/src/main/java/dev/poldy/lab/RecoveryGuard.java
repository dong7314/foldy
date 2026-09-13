package dev.poldy.lab;

import android.os.SystemClock;
import java.io.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Independent shell process: a missing heartbeat or closed pipe restores only this trial. */
public final class RecoveryGuard {
    private static volatile long until=SystemClock.elapsedRealtime()+6000;
    private static volatile boolean disarmed;
    private static volatile String innerRotation,outerRotation;
    private static String run(String... args)throws Exception {
        Process p=new ProcessBuilder(args).redirectErrorStream(true).start();
        if(!p.waitFor(3,TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("Timeout");}
        return new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
    }
    private static String rotation(String encoded) {
        return PanelRotation.normalize(encoded.replace(':',' '));
    }
    private static void restoreRotation(int displayId,String mode)throws Exception {
        String[] parts=PanelRotation.normalize(mode).split(" ");
        if(parts[0].equals("free"))run("/system/bin/wm","user-rotation","-d",Integer.toString(displayId),"free");
        else run("/system/bin/wm","user-rotation","-d",Integer.toString(displayId),"lock",parts[1]);
    }
    public static void main(String[] args)throws Exception {
        if(args.length!=3||!args[0].matches("[0-9]+"))System.exit(2);
        String owner=args[0];
        innerRotation=rotation(args[1]);outerRotation=rotation(args[2]);
        Thread reader=new Thread(()->{
            try(BufferedReader input=new BufferedReader(new InputStreamReader(System.in))){
                String line;while((line=input.readLine())!=null){
                    if(line.equals("DISARM")){disarmed=true;return;}
                    if(line.equals("BEAT"))until=SystemClock.elapsedRealtime()+6000;
                    else if(line.matches("ROTATION (free|lock:[0-3]) (free|lock:[0-3])")) {
                        String[] values=line.split(" ");innerRotation=rotation(values[1]);outerRotation=rotation(values[2]);
                    }
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
                        new PhysicalPanels().routeLogical(t,inner?0:1,inner?1:0);t.apply();
                    }
                    restoreRotation(0,inner?innerRotation:outerRotation);
                    restoreRotation(1,inner?outerRotation:innerRotation);
                }
            }
        }
        System.exit(0);
    }
}
