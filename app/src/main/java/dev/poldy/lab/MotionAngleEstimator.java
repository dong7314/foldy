package dev.poldy.lab;

import java.util.ArrayDeque;

/** Experimental single-IMU estimate; a whole-device yaw is indistinguishable from folding. */
final class MotionAngleEstimator {
    private record Delta(long at,float radians) {}
    private final ArrayDeque<Delta> recent=new ArrayDeque<>();
    private final float openingGain,closingGain;
    private long previousTime;
    private float previousRate,angle,gain;
    private boolean active;
    MotionAngleEstimator(float openingGain,float closingGain){
        if(!Float.isFinite(openingGain)||!Float.isFinite(closingGain)||openingGain<.5f||openingGain>8||closingGain<.5f||closingGain>8)
            throw new IllegalArgumentException("Invalid calibrated gains");
        this.openingGain=openingGain;this.closingGain=closingGain;gain=openingGain;
    }
    float angle(){return angle;}
    boolean active(){return active;}
    void anchor(boolean inner){angle=inner?180:0;gain=inner?closingGain:openingGain;active=false;recent.clear();}
    void begin(){
        if(active)return;active=true;
        float early=0;for(Delta d:recent)early+=degrees(d.radians());
        angle=FoldOptics.clamp(angle+FoldOptics.clamp(early,-25,25),2,178);
        recent.clear();
    }
    boolean gyro(long timestampNanos,float hingeAxisRate){
        if(!Float.isFinite(hingeAxisRate)||timestampNanos<=previousTime)return false;
        float rate=Math.abs(hingeAxisRate)<.008f?0:hingeAxisRate;
        float seconds=(timestampNanos-previousTime)*1e-9f;
        long oldTime=previousTime;previousTime=timestampNanos;
        if(oldTime==0||seconds>.12f){previousRate=rate;recent.clear();return false;}
        float delta=(previousRate+rate)*.5f*seconds;previousRate=rate;
        recent.addLast(new Delta(timestampNanos,delta));
        while(!recent.isEmpty()&&timestampNanos-recent.peekFirst().at()>600_000_000L)recent.removeFirst();
        if(!active)return false;
        float next=FoldOptics.clamp(angle+degrees(delta),2,178);
        boolean changed=Math.abs(next-angle)>.0001f;angle=next;return changed;
    }
    // Keep one calibrated scale until the next physical endpoint. Choosing a different gain
    // for every sign change introduced artificial drift in an otherwise exact out-and-back.
    private float degrees(float radians){return (float)Math.toDegrees(radians)*gain;}
}
