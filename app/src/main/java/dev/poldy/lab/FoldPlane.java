package dev.poldy.lab;

/** A content plane inside the physical panel, inferred from the reference's black wedges. */
final class FoldPlane {
    record Shape(float top,float bottom,float side,float darkness) {}
    static Shape at(FoldOptics.Pose pose,float gx,float gy,float flat,float pitch,float yaw,float roll){
        float turn=pose.obliqueness();
        float facing=FoldOptics.clamp(flat,0,1);
        float bias=FoldOptics.clamp(.24f*gy+.60f*pitch+.34f*roll,-.6f,.6f);
        // Keep the reference's anchored hinge, with a gentler free-edge contraction.
        // User preference calls for less pronounced wedges than the earlier visual model.
        float inset=turn*(.072f+.033f*facing);
        float side=turn*FoldOptics.clamp(.013f+.026f*yaw+.013f*gx,0,.037f);
        float shade=FoldOptics.clamp((float)Math.pow(turn,1.4)*(.56f+.09f*facing+.075f*yaw),0,.72f);
        return new Shape(inset*(1+bias),inset*(1-bias),side,shade);
    }
    /** Clockwise panel-local quad. The inner hinge and stationary right face remain fixed. */
    static void quad(boolean inner,Shape s,float w,float h,float envelope,float[] points){
        float top=s.top()*h*envelope,bottom=s.bottom()*h*envelope,side=s.side()*w*envelope;
        if(inner){
            points[0]=side;points[1]=top;points[2]=w*.5f;points[3]=0;
            points[4]=w*.5f;points[5]=h;points[6]=side;points[7]=h-bottom;
        }else{
            points[0]=0;points[1]=0;points[2]=w-side;points[3]=top;
            points[4]=w-side;points[5]=h-bottom;points[6]=0;points[7]=h;
        }
    }
}
