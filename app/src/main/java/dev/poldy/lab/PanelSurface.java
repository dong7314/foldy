package dev.poldy.lab;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Native-resolution layers; blur effects never retain a per-frame capture shader. */
final class PanelSurface extends SurfaceView implements SurfaceHolder.Callback {
    private final RenderNode content=new RenderNode("Poldy native content");
    private final RenderNode blurred=new RenderNode("Poldy cached Gaussian blur");
    private final Paint imagePaint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint maskPaint=new Paint();
    private final Matrix transform=new Matrix();
    private final RuntimeShader mask=new RuntimeShader("""
        uniform float2 size;
        uniform float amount;
        uniform float inner;
        uniform float veil;
        half4 main(float2 p) {
            float x=p.x/size.x;
            float spatial=inner>0.5 ? 1.0-smoothstep(0.38,0.56,x) : smoothstep(0.08,0.78,x);
            half a=half(clamp(amount*mix(spatial,1.0,veil),0.0,1.0));
            return half4(a,a,a,a);
        }
        """);
    private Bitmap bitmap,previousBitmap;
    private float contentMix=1;
    private float amount,veil;
    private boolean inner,ready;
    private int effectWidth,effectHeight;
    private Integer solid;
    private Runnable onReady;
    PanelSurface(Context context) {
        super(context);setZOrderOnTop(true);getHolder().setFormat(PixelFormat.TRANSLUCENT);
        getHolder().addCallback(this);maskPaint.setShader(mask);maskPaint.setBlendMode(BlendMode.DST_IN);
    }
    void whenReady(Runnable callback){onReady=callback;if(ready)callback.run();}
    void frame(Bitmap bitmap,boolean inner,float amount,float veil) {
        blend(null,bitmap,1,inner,amount,veil);
    }
    void blend(Bitmap before,Bitmap current,float mix,boolean inner,float amount,float veil) {
        previousBitmap=before;bitmap=current;contentMix=mix;
        this.inner=inner;this.amount=amount;this.veil=veil;solid=null;drawFrame();
    }
    void solid(int color){solid=color;drawFrame();}
    void clear(){if(bitmap==null&&solid==null)return;bitmap=null;previousBitmap=null;solid=null;releaseContent();drawFrame();}
    SurfaceControl layer(){SurfaceControl sc=getSurfaceControl();return ready && sc!=null && sc.isValid()?sc:null;}
    private void releaseContent(){content.discardDisplayList();blurred.discardDisplayList();}
    private void drawFrame() {
        if(!ready || getWidth()==0 || getHeight()==0)return;
        Canvas out=null;
        try {
            out=getHolder().lockHardwareCanvas();out.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
            if(solid!=null){out.drawColor(solid);return;}
            if(bitmap==null || bitmap.isRecycled())return;
            int w=getWidth(),h=getHeight();
            content.setPosition(0,0,w,h);RecordingCanvas recording=content.beginRecording(w,h);
            recording.drawColor(Color.rgb(17,26,32));
            if(previousBitmap!=null&&!previousBitmap.isRecycled()&&contentMix<1) {
                drawBitmap(recording,previousBitmap,w,h,255);
                drawBitmap(recording,bitmap,w,h,Math.round(255*contentMix));
            } else drawBitmap(recording,bitmap,w,h,255);
            content.endRecording();
            blurred.setPosition(0,0,w,h);
            if(effectWidth!=w||effectHeight!=h){
                effectWidth=w;effectHeight=h;float radius=Math.max(1,Math.min(w,h)*.027f);
                blurred.setRenderEffect(RenderEffect.createBlurEffect(radius,radius,Shader.TileMode.CLAMP));
            }
            RecordingCanvas blurContent=blurred.beginRecording(w,h);blurContent.drawRenderNode(content);blurred.endRecording();
            out.drawRenderNode(content);
            if(amount>0){
                mask.setFloatUniform("size",w,h);mask.setFloatUniform("amount",amount);
                mask.setFloatUniform("inner",inner?1:0);mask.setFloatUniform("veil",veil);
                int layer=out.saveLayer(0,0,w,h,null);
                out.drawRenderNode(blurred);out.drawRect(0,0,w,h,maskPaint);out.restoreToCount(layer);
            }
        } catch(RuntimeException e){android.util.Log.e("PoldyCapture","surface_draw_failed",e);}
        finally {if(out!=null)try{getHolder().unlockCanvasAndPost(out);}catch(RuntimeException e){android.util.Log.e("PoldyCapture","surface_post_failed",e);}}
    }
    private void drawBitmap(Canvas canvas,Bitmap frame,int w,int h,int alpha) {
        FrameFit fit=new FrameFit(frame.getWidth(),frame.getHeight(),w,h);
        transform.setScale(fit.scale,fit.scale);transform.postTranslate(fit.x,fit.y);
        imagePaint.setAlpha(alpha);canvas.drawBitmap(frame,transform,imagePaint);imagePaint.setAlpha(255);
    }
    @Override public void surfaceCreated(SurfaceHolder holder){ready=true;drawFrame();if(onReady!=null)onReady.run();}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){drawFrame();}
    @Override public void surfaceDestroyed(SurfaceHolder holder){ready=false;releaseContent();}
}
