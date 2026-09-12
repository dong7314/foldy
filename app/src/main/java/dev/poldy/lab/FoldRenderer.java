package dev.poldy.lab;

import android.graphics.*;

/** Hardware renderer that ports Apple's Wipe material to an already physical folding display. */
final class FoldRenderer implements AutoCloseable {
    private final RenderNode content=new RenderNode("Poldy sharp content");
    private final RenderNode fine=new RenderNode("Poldy mip diffusion fine");
    private final RenderNode medium=new RenderNode("Poldy mip diffusion medium");
    private final RenderNode deep=new RenderNode("Poldy mip diffusion deep");
    private final Paint imagePaint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint maskPaint=new Paint(),shadePaint=new Paint(),backgroundPaint=new Paint();
    private final Matrix transform=new Matrix();
    private final boolean optimized;

    /*
     * Apple's WebGL material samples an 8-level mip chain twice with a continuous LOD.
     * Android's RenderEffect has no variable-LOD sampler, so three successively smaller
     * buffers reproduce its fine, medium, and deep diffusion without a giant Gaussian halo.
     */
    private final RuntimeShader mask=new RuntimeShader("""
        uniform float2 size;
        uniform float wipeAmount;
        uniform float inner;
        uniform float envelope;
        uniform float veil;
        uniform float band;
        half4 main(float2 p) {
            float x=clamp(p.x/size.x,0.0,1.0);
            float distanceToWipe=inner>0.5?1.0-x:x;
            float low=inner>0.5?0.45:0.0;
            float high=inner>0.5?1.0:0.9;
            float source=clamp(((distanceToWipe-low)/(high-low))
                *wipeAmount*envelope*2.5,0.0,1.0);
            float blurArea=max(source/0.75,veil*0.72);
            float a=band<0.5?smoothstep(0.015,0.20,blurArea)
                :(band<1.5?smoothstep(0.18,0.68,blurArea)
                :smoothstep(0.62,1.12,blurArea));
            return half4(half(a));
        }
        """);

    private final RuntimeShader shade=new RuntimeShader("""
        uniform float2 size;
        uniform float wipeAmount;
        uniform float brightness;
        uniform float inner;
        uniform float envelope;
        half4 main(float2 p) {
            float x=clamp(p.x/size.x,0.0,1.0);
            float y=clamp(p.y/size.y,0.0,1.0);
            float distanceToWipe=inner>0.5?1.0-x:x;
            float shadeLow=inner>0.5?0.5:0.0;
            float wipe=1.0-clamp(smoothstep(shadeLow,1.0,distanceToWipe)
                *wipeAmount*1.5,0.0,1.0);
            float blurLow=inner>0.5?0.45:0.0;
            float blurHigh=inner>0.5?1.0:0.9;
            float source=clamp(((distanceToWipe-blurLow)/(blurHigh-blurLow))
                *wipeAmount*2.5,0.0,1.0);
            float blurArea=source/0.75;
            float horizontal=1.0-smoothstep(0.9,1.3,blurArea);
            float vertical=1.0-smoothstep(0.9,1.0,abs(y-0.5)*2.0);
            // The real device already supplies the 3D screen edge. Fade the reference's
            // texture-edge shading in only where its diffusion is active.
            float edges=mix(1.0,horizontal*vertical,clamp(blurArea,0.0,1.0));
            float emitted=mix(1.0,brightness*wipe*edges,envelope);
            return half4(0.0,0.0,0.0,half(1.0-emitted));
        }
        """);

    FoldRenderer(){this(true);}
    FoldRenderer(boolean optimized){
        this.optimized=optimized;
        backgroundPaint.setColor(Color.rgb(3,5,8));
        maskPaint.setShader(mask);maskPaint.setBlendMode(BlendMode.DST_IN);
        shadePaint.setShader(shade);
        fine.setRenderEffect(RenderEffect.createBlurEffect(1,1,Shader.TileMode.CLAMP));
        medium.setRenderEffect(RenderEffect.createBlurEffect(1.5f,1.5f,Shader.TileMode.CLAMP));
        deep.setRenderEffect(RenderEffect.createBlurEffect(2,2,Shader.TileMode.CLAMP));
    }

    // Perspective and crease geometry are already produced by the physical folding panel.
    // Applying the web model's camera projection again caused the artificial black wedges.
    void attitude(float pitch,float yaw,float roll){}
    void orientation(float x,float y,float flat){}

    void draw(Canvas out,int w,int h,Bitmap before,Bitmap current,float mix,
              FoldOptics.Pose pose,float envelope,float veil) {
        if(current==null||current.isRecycled())return;
        float effect=FoldOptics.clamp(envelope,0,1);
        float protection=FoldOptics.clamp(veil*effect,0,1);
        content.setPosition(0,0,w,h);RecordingCanvas c=content.beginRecording(w,h);
        c.drawColor(Color.rgb(3,5,8));
        if(before!=null&&!before.isRecycled()&&mix<1){
            drawBitmap(c,before,w,h,255);drawBitmap(c,current,w,h,Math.round(255*mix));
        }else drawBitmap(c,current,w,h,255);
        content.endRecording();

        out.drawRect(0,0,w,h,backgroundPaint);out.drawRenderNode(content);
        if(effect<=0&&protection<=0)return;

        int[] divisors=optimized?new int[]{2,8,32}:new int[]{1,4,16};
        RenderNode[] nodes={fine,medium,deep};
        uniforms(mask,w,h,pose,effect);mask.setFloatUniform("veil",protection);
        for(int band=0;band<nodes.length;band++){
            recordDiffuse(nodes[band],divisors[band],w,h);
            mask.setFloatUniform("band",band);
            composite(out,nodes[band],divisors[band],w,h);
        }

        shade.setFloatUniform("size",w,h);shade.setFloatUniform("wipeAmount",pose.wipeAmount());
        shade.setFloatUniform("inner",pose.inner()?1:0);shade.setFloatUniform("envelope",effect);
        shade.setFloatUniform("brightness",FoldOptics.emissiveBrightness(pose));
        out.drawRect(0,0,w,h,shadePaint);
    }

    private void recordDiffuse(RenderNode node,int divisor,int w,int h){
        int bw=(w+divisor-1)/divisor,bh=(h+divisor-1)/divisor;
        node.setPosition(0,0,bw,bh);RecordingCanvas canvas=node.beginRecording(bw,bh);
        canvas.scale(1f/divisor,1f/divisor);canvas.drawRenderNode(content);node.endRecording();
    }
    private void composite(Canvas out,RenderNode node,int divisor,int w,int h){
        int layer=out.saveLayer(0,0,w,h,null);int scaled=out.save();
        out.scale(divisor,divisor);out.drawRenderNode(node);out.restoreToCount(scaled);
        out.drawRect(0,0,w,h,maskPaint);out.restoreToCount(layer);
    }
    private void uniforms(RuntimeShader shader,int w,int h,FoldOptics.Pose pose,float envelope){
        shader.setFloatUniform("size",w,h);shader.setFloatUniform("wipeAmount",pose.wipeAmount());
        shader.setFloatUniform("inner",pose.inner()?1:0);shader.setFloatUniform("envelope",envelope);
    }
    private void drawBitmap(Canvas canvas,Bitmap frame,int w,int h,int alpha){
        FrameFit fit=new FrameFit(frame.getWidth(),frame.getHeight(),w,h);
        transform.setScale(fit.scale,fit.scale);transform.postTranslate(fit.x,fit.y);
        imagePaint.setAlpha(alpha);canvas.drawBitmap(frame,transform,imagePaint);imagePaint.setAlpha(255);
    }
    @Override public void close(){
        content.discardDisplayList();fine.discardDisplayList();medium.discardDisplayList();deep.discardDisplayList();
    }
}
