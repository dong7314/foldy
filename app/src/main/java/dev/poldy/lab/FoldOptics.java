package dev.poldy.lab;

/** Stateless screen-material pose derived from the official iPhone Duo Wipe shader. */
final class FoldOptics {
    record Pose(boolean inner,float progress,float wipeAmount,float brightness) {}

    static Pose at(boolean inner,float degrees) {
        if(!Float.isFinite(degrees))throw new IllegalArgumentException("A finite angle is required");
        float progress=clamp(degrees,0,180)/180f;
        // The landscape inner material uses hinge * -1.2 + 1.2. The portrait cover
        // overrides that with the exact triangular pulse used by Wipe.onLoop().
        float wipe=inner?clamp(1.2f*(1-progress),0,1)
            :.5f*clamp(1-2*Math.abs(progress-.5f),0,1);
        return new Pose(inner,progress,wipe,inner?AppleDuoMotion.innerScreenBrightness(degrees):1);
    }

    static Pose posture(boolean inner,boolean affected) {
        if(!affected)return at(inner,inner?180:0);
        return at(inner,inner?0:90);
    }

    /** Exact blur-area input to the official two-pass mipmap sampler, before layer quantizing. */
    static float blurArea(Pose pose,float x) {
        float distance=pose.inner()?1-clamp(x,0,1):clamp(x,0,1);
        float low=pose.inner()?.45f:0,high=pose.inner()?1:.9f;
        float source=clamp((distance-low)/(high-low)*pose.wipeAmount()*2.5f,0,1);
        return source/.75f;
    }

    /** Screen-light multiplier before the model's spatial wipe and edge shading. */
    static float emissiveBrightness(Pose pose) {
        return pose.inner()?smooth(.1f,1,pose.brightness()):1;
    }

    static float shadeWipe(Pose pose,float x) {
        float distance=pose.inner()?1-clamp(x,0,1):clamp(x,0,1);
        float low=pose.inner()?.5f:0;
        return 1-clamp(smooth(low,1,distance)*pose.wipeAmount()*1.5f,0,1);
    }

    static float smooth(float low,float high,float value) {
        float t=clamp((value-low)/(high-low),0,1);return t*t*(3-2*t);
    }
    static float clamp(float value,float low,float high){return Math.max(low,Math.min(high,value));}
    private FoldOptics(){}
}
