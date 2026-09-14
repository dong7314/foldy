package dev.poldy.lab;

/** Smooths measured targets without extrapolating past a received angle. */
final class AngleSmoother {
    private float value,target,velocity;
    private boolean endpoint;
    private long last;
    private long cadenceNanos;
    private float cadenceAngle;
    private float motionTau=.12f;
    void reset(float initial){
        value=target=FoldOptics.clamp(initial,0,180);velocity=0;last=0;cadenceNanos=0;endpoint=false;motionTau=.12f;
    }
    void target(float value,boolean endpoint){
        if(!Float.isFinite(value))return;
        target=FoldOptics.clamp(value,0,180);this.endpoint=endpoint;
        if(endpoint)cadenceNanos=0;
    }
    void measuredTarget(float value,long sensorNanos){
        if(!Float.isFinite(value)||sensorNanos<=cadenceNanos)return;
        if(cadenceNanos==0){cadenceNanos=sensorNanos;cadenceAngle=value;}
        else if(Math.abs(value-cadenceAngle)>=8){
            float seconds=(sensorNanos-cadenceNanos)/1e9f;
            // Ignore near-duplicate fusion records when estimating the sparse log cadence.
            // A long physical pause must not turn the next movement into a long animation.
            if(seconds<.7f){
                // Samsung reports hinge changes in uneven bursts (roughly 80-400 ms
                // in the physical trial). Spread a sparse step across most of its
                // observed interval, and adapt quickly enough to avoid stop-go motion.
                float desired=FoldOptics.clamp(seconds*.85f,.10f,.34f);
                motionTau+=(desired-motionTau)*.6f;
            }
            cadenceNanos=sensorNanos;cadenceAngle=value;
        }
        target(value,false);
    }
    float value(){return value;}
    boolean settled(){return Math.abs(target-value)<.6f;}
    boolean animating(){return Math.abs(target-value)>.025f;}
    float step(long nowMillis){
        if(last==0){last=nowMillis;return value;}
        float remaining=FoldOptics.clamp((nowMillis-last)/1000f,0,.06f);last=nowMillis;
        // Bound endpoint catch-up independently of its distance. Large single-IMU errors must
        // not turn a late 0/180 posture notification into a high-speed sweep. Substeps keep
        // acceleration consistent at different display refresh rates and during a slow frame.
        while(remaining>0){
            float dt=Math.min(remaining,.008f);remaining-=dt;
            float distance=target-value;
            float desired=FoldOptics.clamp(distance/(endpoint?.14f:motionTau),endpoint?-140:-480,endpoint?140:480);
            float acceleration=endpoint?600:1800;
            velocity+=FoldOptics.clamp(desired-velocity,-acceleration*dt,acceleration*dt);
            float delta=velocity*dt;
            if(Math.signum(delta)==Math.signum(distance)&&Math.abs(delta)>=Math.abs(distance)){
                value=target;velocity=0;
            }else value=FoldOptics.clamp(value+delta,0,180);
        }
        if(Math.abs(target-value)<.025f){value=target;velocity=0;}
        return value;
    }
}
