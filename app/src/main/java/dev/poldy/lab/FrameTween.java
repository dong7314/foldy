package dev.poldy.lab;

import android.os.Handler;
import android.os.SystemClock;
import java.util.function.Consumer;

/** Service animations advance even when the activity's Choreographer is not producing frames. */
final class FrameTween implements Runnable {
    private final Handler handler;private final long start,duration;
    private final Consumer<Float> update;private final Runnable finished;
    private boolean cancelled;
    FrameTween(Handler handler,long duration,Consumer<Float> update,Runnable finished){
        this.handler=handler;this.duration=duration;this.update=update;this.finished=finished;start=SystemClock.uptimeMillis();
    }
    void start(){handler.post(this);}
    void cancel(){cancelled=true;handler.removeCallbacks(this);}
    @Override public void run(){
        if(cancelled)return;float linear=Math.min(1,(SystemClock.uptimeMillis()-start)/(float)duration);
        update.accept((float)(.5-.5*Math.cos(Math.PI*linear)));
        if(cancelled)return;if(linear>=1)finished.run();else handler.postDelayed(this,16);
    }
}
