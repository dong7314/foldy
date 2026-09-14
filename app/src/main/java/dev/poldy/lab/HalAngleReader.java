package dev.poldy.lab;

import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

/** Shell-only sensor log reader; renewals preserve sample ordering across child processes. */
final class HalAngleReader implements AutoCloseable {
    private final IHingeAngleListener listener;
    private final RenewingLogcat reader;
    private final long started=SystemClock.elapsedRealtimeNanos();
    private final IBinder.DeathRecipient death=this::close;
    private long previous;
    private int count;

    HalAngleReader(IHingeAngleListener listener)throws Exception {
        this.listener=listener;
        reader=new RenewingLogcat(RenewingLogcat.Stream.ANGLE,new RenewingLogcat.Listener(){
            public void line(String line)throws Exception {
                HalAngleSample sample=HalAngleSample.parse(line,SystemClock.elapsedRealtimeNanos(),previous,started);
                if(sample==null)return;
                previous=sample.sensorNanos;count++;
                listener.onAngle(sample.sensorNanos,sample.angle,sample.source);
            }
            public void stopped(String reason){
                unlink();Log.i("PoldyAngle","reader_stopped:samples="+count);
                try{listener.onStopped(reason);}catch(Exception ignored){}
            }
        });
        // Link before spawning: a dead app must never start an orphan reader.
        try{listener.asBinder().linkToDeath(death,0);reader.start();}
        catch(Exception e){close();throw e;}
    }
    private void unlink(){try{listener.asBinder().unlinkToDeath(death,0);}catch(RuntimeException ignored){}}
    @Override public void close(){reader.close();unlink();}
}
