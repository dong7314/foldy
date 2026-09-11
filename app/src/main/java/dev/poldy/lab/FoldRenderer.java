package dev.poldy.lab;

import android.graphics.*;

/** Shared hardware renderer for physical panels and the angle scrubber. */
final class FoldRenderer implements AutoCloseable {
    private final RenderNode content=new RenderNode("Poldy sharp content");
    private final RenderNode near=new RenderNode("Poldy near diffusion");
    private final RenderNode far=new RenderNode("Poldy far diffusion");
    private final Paint imagePaint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint maskPaint=new Paint(),shadePaint=new Paint(),boundaryPaint=new Paint();
    private final Paint backgroundPaint=new Paint();
    private final Matrix transform=new Matrix();
    private final boolean optimized;
    private final Matrix plane=new Matrix(),projected=new Matrix();
    private final float[] sourceQuad=new float[8],targetQuad=new float[8];
    private final Path movingClip=new Path();
    private final float[] boundaryLine=new float[3];
    private boolean projectedPlane;
    private float pitch,yaw,roll;
    void attitude(float p,float y,float r){pitch=p;yaw=y;roll=r;}
    private float gravityX,gravityY=1,flatness;
    void orientation(float x,float y,float flat){gravityX=x;gravityY=y;flatness=flat;}
    private final RuntimeShader mask=new RuntimeShader("""
        uniform float2 size;
        uniform float strength;
        uniform float edge;
        uniform float feather;
        uniform float inner;
        uniform float veil;
        uniform float band;
        half4 main(float2 p) {
            float x=p.x/size.x;
            float ramp=smoothstep(edge-feather*0.5,edge+feather*0.5,x);
            float spatial=mix(ramp,1.0-ramp,inner);
            float local=mix(strength*spatial,1.0,veil);
            // Let fine detail dissolve before the broad colour diffusion takes over.
            float a=band<0.5?smoothstep(0.0,0.42,local):smoothstep(0.20,1.0,local);
            return half4(half(a));
        }
        """);
    private final RuntimeShader shade=new RuntimeShader("""
        uniform float2 size;
        uniform float strength;
        uniform float edge;
        uniform float feather;
        uniform float inner;
        uniform float darkness;
        uniform float planeShade;
        half4 main(float2 p) {
            float x=p.x/size.x;
            float ramp=smoothstep(edge-feather*0.5,edge+feather*0.5,x);
            float spatial=mix(ramp,1.0-ramp,inner);
            float depth=clamp(strength*spatial,0.0,1.0);
            // The moving face darkens most at its free edge; the stationary face stays untouched.
            float distance=mix(x,1.0-x,inner);
            float a=darkness*depth*depth*(0.66+0.34*distance);
            float freeEdge=inner>0.5?clamp(1.0-2.0*x,0.0,1.0):x;
            // View-dependent falloff sits on the moving plane, not a full-screen black filter.
            a=1.0-(1.0-a)*(1.0-planeShade*pow(freeEdge,1.2));
            return half4(0.0,0.0,0.0,half(a));
        }
        """);
    private final RuntimeShader boundary=new RuntimeShader("""
        uniform float2 size;
        uniform float3 topLine;
        uniform float3 bottomLine;
        uniform float3 sideLine;
        uniform float softness;
        uniform float inner;
        half4 main(float2 p) {
            float freeEdge=inner>0.5?clamp(1.0-2.0*p.x/size.x,0.0,1.0):clamp(p.x/size.x,0.0,1.0);
            // Taper the feather into the hinge so it cannot darken the stationary face or
            // leave a seam where the projected and stationary content meet.
            float width=max(0.001,softness*smoothstep(0.0,0.18,freeEdge));
            float3 point=float3(p,1.0);
            float coverage=smoothstep(0.0,width,dot(topLine,point))
                *smoothstep(0.0,width,dot(bottomLine,point))
                *smoothstep(0.0,width,dot(sideLine,point));
            float a=1.0-coverage;
            // This feather is part of the source plane and is diffused with its colours.
            return half4(half3(3.0/255.0,5.0/255.0,8.0/255.0)*half(a),half(a));
        }
        """);
    private float radius=-1;
    FoldRenderer(){this(true);}
    FoldRenderer(boolean optimized){
        this.optimized=optimized;
        backgroundPaint.setColor(Color.rgb(3,5,8));
        maskPaint.setShader(mask);maskPaint.setBlendMode(BlendMode.DST_IN);shadePaint.setShader(shade);
        boundaryPaint.setShader(boundary);
    }

    void draw(Canvas out,int w,int h,Bitmap before,Bitmap current,float mix,
              FoldOptics.Pose pose,float envelope,float veil) {
        if(current==null||current.isRecycled())return;
        float strength=pose.strength()*FoldOptics.clamp(envelope,0,1);
        float protection=FoldOptics.clamp(veil*envelope,0,1);
        FoldPlane.Shape shape=FoldPlane.at(pose,gravityX,gravityY,flatness,pitch,yaw,roll);
        preparePlane(pose,shape,w,h,envelope);
        content.setPosition(0,0,w,h);RecordingCanvas c=content.beginRecording(w,h);
        c.drawColor(Color.rgb(3,5,8));
        if(before!=null&&!before.isRecycled()&&mix<1){drawImage(c,before,w,h,255,pose.inner());drawImage(c,current,w,h,Math.round(255*mix),pose.inner());}
        else drawImage(c,current,w,h,255,pose.inner());
        if(projectedPlane){
            boundary.setFloatUniform("size",w,h);boundary.setFloatUniform("inner",pose.inner()?1:0);
            boundary.setFloatUniform("softness",boundarySoftness(w,h,pose,envelope));
            setBoundaryLine("topLine",0);setBoundaryLine("bottomLine",2);
            setBoundaryLine("sideLine",pose.inner()?3:1);
            c.drawRect(0,0,pose.inner()?w*.5f:w,h,boundaryPaint);
        }
        content.endRecording();
        // Diffusion can now carry the plane's colours across the feathered boundary.
        // Never repaint a dark polygon over this result: that makes the wedge a visible band.
        out.drawRect(0,0,w,h,backgroundPaint);
        out.drawRenderNode(content);
        if(strength<=0&&protection<=0)return;
        float r=Math.max(1,Math.min(w,h)*Math.max(pose.radiusFraction(),.065f*protection));
        r=Math.round(r*2)/2f;
        // Only Gaussian intermediates use fewer pixels; projected content and masks are native.
        int divisor=optimized?(pose.inner()?4:2):1;
        // The inner stationary face has zero diffusion mask. Avoid full-width intermediate
        // layers there, with three radii of source padding so the blur boundary is unchanged.
        int effectWidth=optimized&&pose.inner()&&protection==0?
            Math.min(w,(int)Math.ceil(w*(pose.edge()+pose.feather()*.5f))):w;
        int sourceWidth=Math.min(w,effectWidth+(int)Math.ceil(3*r));
        int bw=(sourceWidth+divisor-1)/divisor,bh=(h+divisor-1)/divisor;
        float sx=divisor,sy=h/(float)bh;
        float kernel=r/divisor;
        if(radius!=kernel){radius=kernel;near.setRenderEffect(RenderEffect.createBlurEffect(kernel*.40f,kernel*.40f,Shader.TileMode.CLAMP));far.setRenderEffect(RenderEffect.createBlurEffect(kernel,kernel,Shader.TileMode.CLAMP));}
        for(RenderNode node:new RenderNode[]{near,far}){
            node.setPosition(0,0,bw,bh);RecordingCanvas b=node.beginRecording(bw,bh);
            b.scale(1/sx,1/sy);b.drawRenderNode(content);node.endRecording();
        }
        uniforms(mask,w,h,pose,strength);mask.setFloatUniform("veil",protection);
        composite(out,near,effectWidth,h,0,sx,sy);composite(out,far,effectWidth,h,1,sx,sy);
        uniforms(shade,w,h,pose,strength);shade.setFloatUniform("darkness",pose.shade());
        shade.setFloatUniform("planeShade",shape.darkness()*envelope);
        out.drawRect(0,0,w,h,shadePaint);
    }
    static float boundarySoftness(int w,int h,FoldOptics.Pose pose,float envelope){
        return Math.min(w,h)*.05f*(float)Math.pow(pose.obliqueness(),.65)*envelope;
    }
    private void composite(Canvas out,RenderNode node,int w,int h,int band,float sx,float sy){
        mask.setFloatUniform("band",band);int layer=out.saveLayer(0,0,w,h,null);
        int scaled=out.save();out.scale(sx,sy);out.drawRenderNode(node);out.restoreToCount(scaled);
        out.drawRect(0,0,w,h,maskPaint);out.restoreToCount(layer);
    }
    private void uniforms(RuntimeShader shader,int w,int h,FoldOptics.Pose pose,float strength){
        shader.setFloatUniform("size",w,h);shader.setFloatUniform("strength",strength);
        shader.setFloatUniform("edge",pose.edge());shader.setFloatUniform("feather",pose.feather());shader.setFloatUniform("inner",pose.inner()?1:0);
    }
    private void drawBitmap(Canvas canvas,Bitmap frame,int w,int h,int alpha){
        FrameFit fit=new FrameFit(frame.getWidth(),frame.getHeight(),w,h);
        transform.setScale(fit.scale,fit.scale);transform.postTranslate(fit.x,fit.y);
        imagePaint.setAlpha(alpha);canvas.drawBitmap(frame,transform,imagePaint);imagePaint.setAlpha(255);
    }
    private void preparePlane(FoldOptics.Pose pose,FoldPlane.Shape shape,int w,int h,float envelope){
        projectedPlane=pose.obliqueness()*envelope>.00001f;
        if(!projectedPlane)return;
        float edge=pose.inner()?w*.5f:w;
        sourceQuad[0]=0;sourceQuad[1]=0;sourceQuad[2]=edge;sourceQuad[3]=0;
        sourceQuad[4]=edge;sourceQuad[5]=h;sourceQuad[6]=0;sourceQuad[7]=h;
        FoldPlane.quad(pose.inner(),shape,w,h,envelope,targetQuad);
        if(!plane.setPolyToPoly(sourceQuad,0,targetQuad,0,4))throw new IllegalStateException("Degenerate content plane");
        movingClip.reset();movingClip.moveTo(targetQuad[0],targetQuad[1]);
        for(int i=2;i<8;i+=2)movingClip.lineTo(targetQuad[i],targetQuad[i+1]);movingClip.close();
    }
    private void setBoundaryLine(String name,int edge){
        int next=(edge+1)%4;float x=targetQuad[edge*2],y=targetQuad[edge*2+1];
        float dx=targetQuad[next*2]-x,dy=targetQuad[next*2+1]-y;
        float length=(float)Math.hypot(dx,dy);
        boundaryLine[0]=-dy/length;boundaryLine[1]=dx/length;boundaryLine[2]=(dy*x-dx*y)/length;
        boundary.setFloatUniform(name,boundaryLine);
    }
    private void drawImage(Canvas canvas,Bitmap frame,int w,int h,int alpha,boolean inner){
        if(!projectedPlane){drawBitmap(canvas,frame,w,h,alpha);return;}
        if(inner){
            int stable=canvas.save();canvas.clipRect(w*.5f,0,w,h);
            drawBitmap(canvas,frame,w,h,alpha);canvas.restoreToCount(stable);
        }
        FrameFit fit=new FrameFit(frame.getWidth(),frame.getHeight(),w,h);
        transform.setScale(fit.scale,fit.scale);transform.postTranslate(fit.x,fit.y);
        projected.setConcat(plane,transform);
        int moving=canvas.save();canvas.clipPath(movingClip);
        imagePaint.setAlpha(alpha);canvas.drawBitmap(frame,projected,imagePaint);imagePaint.setAlpha(255);
        canvas.restoreToCount(moving);
    }
    @Override public void close(){content.discardDisplayList();near.discardDisplayList();far.discardDisplayList();}
}
