package dev.poldy.lab;

/** Small material/parallax response to the primary IMU; never estimates the hinge angle. */
final class FoldViewMotion {
    record Pose(float x,float y,float dx,float dy,float scale){}
    private float pitch,yaw,roll,gx,gy=1,flat;
    void attitude(float pitch,float yaw,float roll){
        this.pitch=finite(pitch,-.65f,.65f);this.yaw=finite(yaw,-.65f,.65f);this.roll=finite(roll,-.65f,.65f);
    }
    void orientation(float x,float y,float flat){
        gx=finite(x,-1,1);gy=finite(y,-1,1);this.flat=finite(flat,0,1);
    }
    Pose at(FoldOptics.Pose optics,float envelope){
        float p=optics.progress();
        // Exact zero at both endpoints, including when relative attitude remains nonzero.
        float activity=4*p*(1-p)*FoldOptics.clamp(envelope,0,1);
        float x=FoldOptics.clamp(yaw*.8f+roll*.22f+gx*.24f,-1,1)*activity;
        float y=FoldOptics.clamp(pitch*.8f+flat*.32f+(1-Math.abs(gy))*.08f,-1,1)*activity;
        float dx=x*.004f,dy=y*.005f;
        // Cover the translated rectangle with a sub-percent overscan. No trapezoid,
        // exposed triangle, transparent strip, or screen-aspect-ratio change is introduced.
        float scale=1+2*Math.max(Math.abs(dx),Math.abs(dy));
        return new Pose(x,y,dx,dy,scale);
    }
    private static float finite(float value,float low,float high){return Float.isFinite(value)?FoldOptics.clamp(value,low,high):0;}
}
