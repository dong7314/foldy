package dev.poldy.lab;

/** 61 authored Slider samples extracted from Apple's public iPhone Duo glTF scene. */
final class AppleDuoMotion {
    static final int LAST_FRAME=60;
    private static final float[] RELEASE={
        0.000000f,-0.006835f,-0.012000f,-0.014995f,-0.011402f,0.001823f,0.021817f,0.041901f,
        0.062080f,0.082330f,0.102637f,0.122986f,0.143349f,0.163700f,0.184063f,0.204396f,
        0.224716f,0.245022f,0.265264f,0.285405f,0.305430f,0.325361f,0.345207f,0.364939f,
        0.384546f,0.404004f,0.423304f,0.442462f,0.461464f,0.480326f,0.499040f,0.516707f,
        0.534108f,0.551267f,0.568207f,0.584951f,0.601542f,0.618002f,0.634339f,0.650574f,
        0.666722f,0.682821f,0.698878f,0.714931f,0.730979f,0.747032f,0.763110f,0.779222f,
        0.795382f,0.811607f,0.827912f,0.844346f,0.860870f,0.877546f,0.894359f,0.911362f,
        0.928576f,0.945981f,0.963724f,0.981731f,1.000000f
    };
    static float authoredRelease(int frame){return RELEASE[Math.max(0,Math.min(LAST_FRAME,frame))];}
    static float release(float degrees){
        float position=FoldOptics.clamp(degrees,0,180)/3f;
        int lower=(int)Math.floor(position),upper=Math.min(LAST_FRAME,lower+1);
        float value=RELEASE[lower]+(RELEASE[upper]-RELEASE[lower])*(position-lower);
        return FoldOptics.clamp(value,0,1);
    }
    static float innerScreenBrightness(float degrees){
        float progress=FoldOptics.clamp(degrees,0,180)/180f;
        if(progress<=1f/3f)return .15f+.30f*progress;
        return .25f+1.125f*(progress-1f/3f);
    }
    private AppleDuoMotion(){}
}
