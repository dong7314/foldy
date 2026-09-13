package dev.poldy.lab;

import android.os.Handler;
import android.os.SystemClock;
import android.view.Choreographer;
import java.util.function.Consumer;

/** Advances on the physical display's frame clock, with a bounded service fallback. */
final class FrameTween implements Runnable,Choreographer.FrameCallback {
    private final Handler handler;private final long start,duration;
    private final Choreographer choreographer;
    private final Consumer<Float> update;private final Runnable finished;
    private boolean cancelled;
    FrameTween(Handler handler,long duration,Consumer<Float> update,Runnable finished){
        this.handler=handler;this.duration=duration;this.update=update;this.finished=finished;
        choreographer=Choreographer.getInstance();start=SystemClock.uptimeMillis();
    }
    void start(){schedule();}
    void cancel(){cancelled=true;handler.removeCallbacks(this);choreographer.removeFrameCallback(this);}
    private void schedule(){
        choreographer.postFrameCallback(this);
        // Display remapping can briefly pause vsync delivery. Keep the handoff moving
        // without imposing a fixed 60 fps timer during normal 120 Hz operation.
        handler.postDelayed(this,18);
    }
    @Override public void doFrame(long frameTimeNanos){handler.removeCallbacks(this);advance();}
    @Override public void run(){choreographer.removeFrameCallback(this);advance();}
    private void advance(){
        if(cancelled)return;float linear=Math.min(1,(SystemClock.uptimeMillis()-start)/(float)duration);
        update.accept((float)(.5-.5*Math.cos(Math.PI*linear)));
        if(cancelled)return;if(linear>=1)finished.run();else schedule();
    }
}
