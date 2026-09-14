package dev.poldy.lab;

import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Continuous reader with bounded children, so a dead controller cannot leave idle logcats. */
final class RenewingLogcat implements AutoCloseable {
    enum Stream {
        ANGLE("-v raw -T 500 -e 'folding_angle|lid_angle_fusion' -s sensors-hal:I '*:S'"),
        LUMINANCE("-v epoch -e 'setDisplayBrightness' -s SurfaceFlinger:I '*:S'");
        final String args;
        Stream(String args){this.args=args;}
    }
    interface Listener {
        void line(String line)throws Exception;
        void stopped(String reason);
    }
    private final Stream stream;
    private final Listener listener;
    private final int leaseSeconds;
    private final Thread worker;
    private volatile boolean closed;
    private Process process;
    private int pid;
    private volatile int leases;

    RenewingLogcat(Stream stream,Listener listener){this(stream,listener,300);}
    // Short leases are used by the on-device lifecycle probe, never by the app UI.
    RenewingLogcat(Stream stream,Listener listener,int leaseSeconds){
        if(android.os.Process.myUid()!=2000)throw new IllegalStateException("Shell UID required");
        if(leaseSeconds<1||leaseSeconds>300)throw new IllegalArgumentException("Invalid lease");
        this.stream=stream;this.listener=listener;this.leaseSeconds=leaseSeconds;
        worker=new Thread(this::read,"poldy-log-"+stream.name().toLowerCase(java.util.Locale.ROOT));
    }
    synchronized void start(){if(!closed)worker.start();}
    int leases(){return leases;}
    boolean running(){return !closed;}

    private void read(){
        LogcatRestart restart=new LogcatRestart();
        String failure=stream+" 로그 수집이 종료되었습니다.";
        try {
            while(!closed){
                long began=SystemClock.elapsedRealtime();boolean readFailed=false;
                try {
                    synchronized(this){
                        if(closed)break;
                        // Only fixed enum text enters the shell. timeout survives a controller
                        // crash; its child prints its PID before exec so close can unblock readLine.
                        process=new ProcessBuilder("/system/bin/timeout",leaseSeconds+"s","/system/bin/sh","-c",
                            "echo $$; exec /system/bin/logcat -b main "+stream.args).redirectErrorStream(true).start();
                    }
                    try(BufferedReader input=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8))){
                        String pidLine=input.readLine();
                        synchronized(this){
                            pid=Integer.parseInt(pidLine);
                            if(pid<=1)throw new IllegalStateException("Invalid logcat PID");
                            if(closed)kill();else leases++;
                        }
                        Log.i("PoldyLog","lease_started:stream="+stream+",generation="+leases+",pid="+pid);
                        String line;
                        while(!closed&&(line=input.readLine())!=null)listener.line(line);
                    }
                }catch(Exception e){
                    readFailed=true;failure=stream+" 수집 연결 실패: "+e.getClass().getSimpleName();
                    if(!closed)Log.w("PoldyLog",failure,e);
                }finally {
                    synchronized(this){kill();pid=0;process=null;}
                }
                long delay=restart.delay(closed,SystemClock.elapsedRealtime()-began,leaseSeconds*1000L,readFailed);
                if(delay<0)break;
                Log.i("PoldyLog","lease_renew:stream="+stream+",delay_ms="+delay);
                // close wakes a retry immediately and the loop checks closed before launching.
                if(delay>0)synchronized(this){if(!closed)wait(delay);}
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        finally {
            boolean notify;
            synchronized(this){notify=!closed;closed=true;kill();pid=0;process=null;}
            Log.i("PoldyLog","reader_closed:stream="+stream+",leases="+leases);
            if(notify)listener.stopped(failure);
        }
    }
    private void kill(){
        if(pid>1&&process!=null&&process.isAlive())try{Os.kill(pid,OsConstants.SIGTERM);}catch(Exception ignored){}
    }
    @Override public synchronized void close(){closed=true;kill();notifyAll();}
}
