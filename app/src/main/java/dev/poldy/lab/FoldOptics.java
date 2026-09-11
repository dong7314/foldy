package dev.poldy.lab;

/** Stateless optical pose. Degrees are an input, never inferred from elapsed time. */
final class FoldOptics {
    record Pose(boolean inner, float strength, float edge, float feather,
                float radiusFraction, float shade,float obliqueness) {}
    static Pose at(boolean inner,float degrees) {
        if(!Float.isFinite(degrees))throw new IllegalArgumentException("A finite angle is required");
        float angle=clamp(degrees,0,180);
        // These are tunable visual curves inferred from the reference, not measured sensor thresholds.
        // Both panels respond throughout the gesture. A half-range plateau amplified gyro error:
        // the last endpoint correction used to perform most of the visible animation at once.
        float away=inner?180-angle:angle;
        // A cubic angle curve followed by the diffusion mask's cubic response was almost
        // invisible at departure. Give the first 3 visual degrees a continuous frost entry,
        // then keep the rest of the response spread over the gesture (also reversible).
        float depth=.26f*smooth(0,3,away)+.74f*smooth(0,180,away);
        return pose(inner,depth,angle==0||angle==180?0:(float)Math.sin(Math.PI*angle/180));
    }
    static Pose posture(boolean inner,boolean affected) {
        // A discrete posture has no precise angle. Do not label this preset as measured degrees.
        return pose(inner,affected?1:0,affected?1:0);
    }
    private static Pose pose(boolean inner,float depth,float obliqueness) {
        // The cover's diffusion front passes its left border by mid-gesture. Strength, radius,
        // displacement and shade continue changing after the front has covered the whole panel.
        return new Pose(inner,depth,inner?.36f+.17f*depth:.96f-1.24f*smooth(0,.5f,depth),
            inner?.11f+.09f*depth:.17f+.39f*depth,
            .0027f+.04536f*depth,inner?.50f:.30f,obliqueness);
    }
    static float spatial(Pose pose,float x) {
        float ramp=smooth(pose.edge()-pose.feather()*.5f,pose.edge()+pose.feather()*.5f,x);
        return pose.inner()?1-ramp:ramp;
    }
    static float smooth(float low,float high,float value) {
        float t=clamp((value-low)/(high-low),0,1);return t*t*(3-2*t);
    }
    static float clamp(float value,float low,float high){return Math.max(low,Math.min(high,value));}
}
