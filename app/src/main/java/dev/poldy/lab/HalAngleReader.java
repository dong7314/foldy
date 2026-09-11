package dev.poldy.lab;

import android.os.IBinder;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Shell-only, bounded reader of existing sensor logs. Never changes logging or permissions. */
final class HalAngleReader implements AutoCloseable {
    private final IHingeAngleListener listener;
    private final Process process;
    private final long started=SystemClock.elapsedRealtimeNanos();
    private final IBinder.DeathRecipient death=this::close;
    private volatile boolean closed;
    private int logcatPid;

    HalAngleReader(IHingeAngleListener listener) throws Exception {
        if(android.os.Process.myUid()!=2000)throw new IllegalStateException("Shell UID required");
        this.listener=listener;
        // The child prints its own PID, then exec replaces it with logcat. timeout outlives
        // a killed controller and bounds even an idle orphan. No caller text enters the shell.
        process=new ProcessBuilder("/system/bin/timeout","300s","/system/bin/sh","-c",
            "echo $$; exec /system/bin/logcat -b main -v raw -T 1 -s sensors-hal:I '*:S'")
            .redirectErrorStream(true).start();
        try {listener.asBinder().linkToDeath(death,0);}
        catch(Exception e){closed=true;new Thread(this::read,"poldy-hal-cleanup").start();throw e;}
        new Thread(this::read,"poldy-hal-angles").start();
    }
    private void read(){
        long previous=0;int count=0;String failure="각도 로그 읽기가 종료되었습니다.";
        try(BufferedReader input=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){
            String pidLine=input.readLine();
            synchronized(this){
                logcatPid=Integer.parseInt(pidLine);
                if(logcatPid<=1)throw new IllegalStateException("Invalid reader PID");
                if(closed)killReader();
            }
            Log.i("PoldyAngle","reader_started:pid="+logcatPid);
            String line;
            while(!closed&&(line=input.readLine())!=null){
                HalAngleSample sample=HalAngleSample.parse(line,SystemClock.elapsedRealtimeNanos(),previous,started);
                if(sample==null)continue;
                previous=sample.sensorNanos;count++;
                listener.onAngle(sample.sensorNanos,sample.angle,sample.source);
            }
        }catch(Exception e){failure="각도 로그 연결 실패: "+e.getClass().getSimpleName();Log.w("PoldyAngle",failure,e);}
        finally {
            boolean notify;
            synchronized(this){notify=!closed;killReader();closed=true;logcatPid=0;}
            try{listener.asBinder().unlinkToDeath(death,0);}catch(RuntimeException ignored){}
            Log.i("PoldyAngle","reader_stopped:samples="+count);
            if(notify)try{listener.onStopped(failure);}catch(Exception ignored){}
        }
    }
    private void killReader(){
        if(logcatPid>1&&process.isAlive())try{Os.kill(logcatPid,OsConstants.SIGTERM);}catch(Exception ignored){}
    }
    @Override public synchronized void close(){
        closed=true;
        // Signalling the OS process unblocks readLine without taking its Java pipe lock.
        killReader();
    }
}
