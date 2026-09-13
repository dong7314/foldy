package dev.poldy.lab;

import android.graphics.*;
import android.view.Surface;
import android.view.SurfaceControl;

/** A fixed physical-panel buffer, independent of Activity/Presentation window lifetimes. */
final class NativePanel implements AutoCloseable {
    private final FoldRenderer renderer=new FoldRenderer();
    private FoldOptics.Pose optics;
    private float gravityX,gravityY=1,flatness;
    private float pitch,yaw,roll;
    void attitude(float p,float y,float r){pitch=p;yaw=y;roll=r;renderer.attitude(p,y,r);}
    void optics(FoldOptics.Pose pose){optics=pose;}
    void orientation(float x,float y,float flat){gravityX=x;gravityY=y;flatness=flat;renderer.orientation(x,y,flat);}
    boolean matches(Bitmap frame){return frame!=null&&frame.getWidth()==width&&frame.getHeight()==height;}
    boolean references(Bitmap frame){return frame!=null&&(frame==bitmap||frame==previousBitmap||frame==bakedBefore);}
    final SurfaceControl control;
    private final Surface surface;
    private int width,height;
    private Bitmap bitmap,previousBitmap,bakedBefore;
    private final Paint bakedPaint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint preparationPaint=new Paint();
    private final Matrix bakedTransform=new Matrix();
    private float amount,veil,contentMix=1,opacity=-1;
    private boolean inner,closed;
    boolean healthy=true;
    NativePanel(SurfaceControl control,int width,int height) {
        this.control=control;this.width=width;this.height=height;surface=new Surface(control);
        android.util.Log.i("PoldyCapture","root_panel_created:"+width+"x"+height);
    }
    void frame(Bitmap bitmap,boolean inner,float amount,float veil){blend(null,bitmap,1,inner,amount,veil);}
    void transitionPlaceholder(Bitmap source,boolean inner) {
        if(source==null||source.isRecycled())throw new IllegalArgumentException("A live source frame is required");
        // FoldRenderer preserves aspect ratio when source and panel sizes differ.
        // This path is used only until the new app's native target layout arrives.
        bakedBefore=null;previousBitmap=null;bitmap=source;contentMix=1;
        this.inner=inner;this.amount=1;this.veil=0;drawFrame();
    }
    void resize(int width,int height) {
        if(closed||this.width==width&&this.height==height)return;
        if(width<=0||height<=0)throw new IllegalArgumentException("Invalid panel buffer size");
        try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()) {
            t.setBufferSize(control,width,height).apply();
        }
        this.width=width;this.height=height;opacity=-1;
        android.util.Log.i("PoldyCapture","root_panel_resized:"+width+"x"+height);
    }
    void blend(Bitmap before,Bitmap current,float mix,boolean inner,float amount,float veil) {
        requireNative(before);requireNative(current);
        bakedBefore=null;previousBitmap=before;bitmap=current;contentMix=mix;
        this.inner=inner;this.amount=amount;this.veil=veil;drawFrame();
    }
    void hold(Bitmap nativeFrame){reveal(nativeFrame,null,0,inner,0,0);}
    void reveal(Bitmap nativeBefore,Bitmap current,float mix,boolean inner,float amount,float veil){
        requireNative(current);
        if(nativeBefore!=null&&!compatible(nativeBefore))throw new IllegalArgumentException("A held frame must belong to this panel");
        bakedBefore=nativeBefore;previousBitmap=null;bitmap=current;contentMix=mix;
        this.inner=inner;this.amount=amount;this.veil=veil;drawFrame();
    }
    private void requireNative(Bitmap frame){if(frame!=null&&!matches(frame))throw new IllegalArgumentException("Content must use this panel's native layout");}
    private boolean compatible(Bitmap frame) {
        return matches(frame)||(frame!=null&&frame.getWidth()==height&&frame.getHeight()==width);
    }
    void opacity(float alpha) {
        if(closed||Math.abs(opacity-alpha)<.001f)return;opacity=alpha;
        try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){t.setAlpha(control,alpha).apply();}
        catch(RuntimeException e){healthy=false;android.util.Log.e("PoldyCapture","root_alpha_failed",e);}
    }
    void whenVisible(java.util.concurrent.Executor executor,Runnable callback) {
        if(closed||!healthy)return;
        try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()) {
            t.setAlpha(control,1);
            if(android.os.Build.VERSION.SDK_INT>=35)
                t.addTransactionCompletedListener(executor,stats->callback.run());
            else t.addTransactionCommittedListener(executor,callback::run);
            opacity=1;t.apply();
        } catch(RuntimeException e){healthy=false;android.util.Log.e("PoldyCapture","root_present_failed",e);}
    }
    private void drawFrame() {
        if(closed)return;
        Canvas out=null;
        try {
            out=surface.lockHardwareCanvas();out.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
            drawInto(out,renderer);
        } catch(RuntimeException e){healthy=false;android.util.Log.e("PoldyCapture","root_draw_failed",e);}
        finally {if(out!=null)try{surface.unlockCanvasAndPost(out);}catch(RuntimeException e){healthy=false;android.util.Log.e("PoldyCapture","root_post_failed",e);}}
    }
    private void drawInto(Canvas out,FoldRenderer painter){
        // An opaque, content-free preparation surface is also snapshot-able. It never
        // stretches the opposite layout or exposes an old task while a native cache is absent.
        if(bakedBefore==null&&previousBitmap==null&&(bitmap==null||contentMix<1)){
            preparationPaint.setShader(new LinearGradient(0,0,width,height,
                Color.rgb(49,57,67),Color.rgb(25,31,40),Shader.TileMode.CLAMP));
            out.drawRect(0,0,width,height,preparationPaint);
            if(bitmap!=null&&contentMix>0){
                int layer=out.saveLayerAlpha(0,0,width,height,Math.round(255*contentMix));
                painter.draw(out,width,height,null,bitmap,1,optics!=null?optics:FoldOptics.posture(inner,true),amount,veil);
                out.restoreToCount(layer);
            }
            return;
        }
        if(bakedBefore!=null&&bakedBefore.isRecycled())throw new IllegalStateException("Panel hold was released while still referenced");
        if(bakedBefore!=null&&!bakedBefore.isRecycled()){
            FrameFit fit=new FrameFit(bakedBefore.getWidth(),bakedBefore.getHeight(),width,height);
            bakedTransform.setScale(fit.scale,fit.scale);bakedTransform.postTranslate(fit.x,fit.y);
            bakedPaint.setAlpha(255);out.drawBitmap(bakedBefore,bakedTransform,bakedPaint);
            if(bitmap==null||contentMix<=0)return;
            int layer=out.saveLayerAlpha(0,0,width,height,Math.round(255*contentMix));
            painter.draw(out,width,height,null,bitmap,1,optics!=null?optics:FoldOptics.posture(inner,true),amount,veil);
            out.restoreToCount(layer);
        }else painter.draw(out,width,height,previousBitmap,bitmap,contentMix,
            optics!=null?optics:FoldOptics.posture(inner,true),amount,veil);
    }
    private static final java.util.concurrent.ExecutorService SNAPSHOTS=java.util.concurrent.Executors.newSingleThreadExecutor();
    /** GPU snapshot of what this physical panel showed, including its own layout and effects. */
    void snapshot(android.os.Handler main,java.util.function.Consumer<Bitmap> callback){
        if(closed||android.os.Build.VERSION.SDK_INT<34){callback.accept(null);return;}
        snapshot34(main,callback);
    }
    @android.annotation.TargetApi(34)
    private void snapshot34(android.os.Handler main,java.util.function.Consumer<Bitmap> callback){
        android.hardware.HardwareBuffer buffer=null;HardwareBufferRenderer capture=null;
        FoldRenderer painter=new FoldRenderer();RenderNode root=new RenderNode("Poldy panel hold");
        try {
            buffer=android.hardware.HardwareBuffer.create(width,height,android.hardware.HardwareBuffer.RGBA_8888,1,
                android.hardware.HardwareBuffer.USAGE_GPU_COLOR_OUTPUT|android.hardware.HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
            capture=new HardwareBufferRenderer(buffer);root.setPosition(0,0,width,height);
            painter.orientation(gravityX,gravityY,flatness);
            painter.attitude(pitch,yaw,roll);
            RecordingCanvas c=root.beginRecording(width,height);c.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);drawInto(c,painter);root.endRecording();
            capture.setContentRoot(root);
            final android.hardware.HardwareBuffer ownedBuffer=buffer;final HardwareBufferRenderer ownedCapture=capture;
            capture.obtainRenderRequest().setColorSpace(ColorSpace.get(ColorSpace.Named.SRGB)).draw(SNAPSHOTS,result->{
                Bitmap snapshot=null;
                try(android.hardware.SyncFence fence=result.getFence()){
                    if(result.getStatus()==HardwareBufferRenderer.RenderResult.SUCCESS&&fence.await(java.time.Duration.ofMillis(400)))
                        snapshot=Bitmap.wrapHardwareBuffer(ownedBuffer,ColorSpace.get(ColorSpace.Named.SRGB));
                }catch(RuntimeException e){android.util.Log.e("PoldyCapture","hold_capture_failed",e);}
                finally {ownedCapture.close();ownedBuffer.close();painter.close();root.discardDisplayList();}
                Bitmap answer=snapshot;main.post(()->callback.accept(answer));
            });
        }catch(RuntimeException e){
            if(capture!=null)capture.close();if(buffer!=null)buffer.close();painter.close();root.discardDisplayList();
            android.util.Log.e("PoldyCapture","hold_setup_failed",e);callback.accept(null);
        }
    }
    @Override public void close(){
        if(closed)return;opacity(0);closed=true;bitmap=null;previousBitmap=null;bakedBefore=null;
        renderer.close();surface.release();control.release();
    }
}
