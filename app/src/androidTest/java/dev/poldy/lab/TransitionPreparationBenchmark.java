package dev.poldy.lab;

import android.graphics.*;
import android.hardware.*;
import android.os.*;
import android.view.SurfaceControl;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.*;

/** Uses generated pixels and invisible surfaces; never captures the user's screen. */
final class TransitionPreparationBenchmark {
    static String run()throws Exception {
        StringBuilder report=new StringBuilder();
        Handler main=new Handler(Looper.getMainLooper());
        ExecutorService gpu=Executors.newSingleThreadExecutor();
        try {
            for(boolean inner:new boolean[]{false,true}){
                int w=inner?2448:1248,h=inner?1848:1972;
                Bitmap source=Bitmap.createBitmap(inner?1248:2448,inner?1972:1848,Bitmap.Config.ARGB_8888);
                source.eraseColor(Color.rgb(24,184,120));
                SurfaceControl control=new SurfaceControl.Builder().setName("Foldy invisible synthetic test").setBufferSize(w,h).build();
                NativePanel panel=new NativePanel(control,w,h);panel.opacity(0);
                Bitmap[] raw={null};CountDownLatch prepared=new CountDownLatch(1);
                main.post(()->{panel.optics(FoldOptics.at(inner,90));panel.snapshotRaw(source,main,b->{raw[0]=b;prepared.countDown();});});
                if(!prepared.await(3,TimeUnit.SECONDS))throw new AssertionError("Raw preparation timeout");
                if(raw[0]==null||raw[0].getWidth()!=w||raw[0].getHeight()!=h)throw new AssertionError("Wrong native layout");
                Bitmap readable=raw[0].copy(Bitmap.Config.ARGB_8888,false);
                int color=readable.getPixel(w/2,h/2);readable.recycle();
                if(Color.green(color)!=184||Color.red(color)!=24)throw new AssertionError("Effects were baked into raw content");
                HardwareBuffer buffer=HardwareBuffer.create(w,h,HardwareBuffer.RGBA_8888,1,
                    HardwareBuffer.USAGE_GPU_COLOR_OUTPUT|HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
                try(HardwareBufferRenderer output=new HardwareBufferRenderer(buffer);FoldRenderer renderer=new FoldRenderer()){
                    RenderNode node=new RenderNode("Foldy synthetic closing");node.setPosition(0,0,w,h);output.setContentRoot(node);
                    double[] times=new double[72];long first=0,last=0;
                    for(int i=-12;i<72;i++){
                        float angle=180-Math.max(0,i)*180f/71;
                        long start=System.nanoTime();
                        RecordingCanvas canvas=node.beginRecording(w,h);
                        renderer.draw(canvas,w,h,null,raw[0],1,FoldOptics.at(inner,angle),1,0);node.endRecording();
                        CountDownLatch frame=new CountDownLatch(1);boolean[] ok={false};
                        output.obtainRenderRequest().setColorSpace(ColorSpace.get(ColorSpace.Named.SRGB)).draw(gpu,result->{
                            try(SyncFence fence=result.getFence()){
                                ok[0]=result.getStatus()==HardwareBufferRenderer.RenderResult.SUCCESS&&fence.await(Duration.ofMillis(800));
                            }finally{frame.countDown();}
                        });
                        if(!frame.await(2,TimeUnit.SECONDS)||!ok[0])throw new AssertionError("GPU frame failed");
                        if(i>=0)times[i]=(System.nanoTime()-start)/1e6;
                        if(i==35||i==71){
                            Bitmap hardware=Bitmap.wrapHardwareBuffer(buffer,ColorSpace.get(ColorSpace.Named.SRGB));
                            Bitmap pixels=hardware.copy(Bitmap.Config.ARGB_8888,false);long sum=0;
                            for(int x=w/8;x<w;x+=w/8)for(int y=h/8;y<h;y+=h/8)sum+=Color.green(pixels.getPixel(x,y));
                            pixels.recycle();hardware.recycle();if(i==35)first=sum;else last=sum;
                        }
                    }
                    node.discardDisplayList();Arrays.sort(times);
                    if(first==last)throw new AssertionError("Angle changes left preparation frozen");
                    report.append(inner?"inner":"outer").append(": raw_native=PASS, live_closing=PASS, p50_ms=")
                        .append(times[36]).append(", p95_ms=").append(times[68]).append('\n');
                }finally{buffer.close();panel.close();raw[0].recycle();source.recycle();}
            }
        }finally{gpu.shutdown();}
        return report.toString();
    }
}
