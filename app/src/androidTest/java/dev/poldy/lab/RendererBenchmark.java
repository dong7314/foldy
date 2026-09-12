package dev.poldy.lab;

import android.app.*;
import android.graphics.*;
import android.hardware.*;
import android.os.*;
import java.io.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.json.*;

/** Explicit adb-only GPU comparison with generated pixels. It does not read or change the displays. */
public final class RendererBenchmark extends Instrumentation {
    private final ExecutorService callbacks=Executors.newSingleThreadExecutor();
    private File directory;
    private boolean entryOnly,edgeOnly;
    @Override public void onCreate(Bundle args){
        super.onCreate(args);entryOnly=args!=null&&"true".equals(args.getString("entryOnly"));
        edgeOnly=args!=null&&"true".equals(args.getString("edgeOnly"));start();
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            directory=new File(getTargetContext().getFilesDir(),"renderer-benchmark");
            if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Output directory unavailable");
            if(edgeOnly){
                JSONArray cases=new JSONArray();
                for(boolean inner:new boolean[]{false,true}){
                    try(Target target=new Target(inner,true,true)){
                        for(int angle:new int[]{0,2,90,110,178,180}){
                            target.draw(angle,.6f);
                            String file=(inner?"inner":"outer")+"-edge-"+angle+".png";target.save(file);
                            FoldOptics.Pose pose=FoldOptics.at(inner,angle);
                            cases.put(new JSONObject().put("file",file).put("inner",inner).put("angle",angle)
                                .put("width",target.w).put("height",target.h)
                                .put("wipeAmount",pose.wipeAmount())
                                .put("blurLeft",FoldOptics.blurArea(pose,0))
                                .put("blurCenter",FoldOptics.blurArea(pose,.5f))
                                .put("blurRight",FoldOptics.blurArea(pose,1)));
                        }
                    }
                    try(Target target=new Target(inner,true)){
                        target.draw(inner?110:65,.6f);target.save((inner?"inner":"outer")+"-edge-colour.png");
                    }
                }
                try(FileWriter file=new FileWriter(new File(directory,"edge-cases.json"))){file.write(cases.toString(2));}
                result.putString("stream","Official Wipe boundary GPU samples completed");finish(Activity.RESULT_OK,result);return;
            }
            if(entryOnly){
                for(boolean inner:new boolean[]{false,true})try(Target target=new Target(inner,true)){
                    for(int departure:new int[]{0,1,2,3,6,12}){
                        target.draw(inner?180-departure:departure,0);
                        target.save((inner?"inner":"outer")+"-entry-"+departure+".png");
                    }
                }
                result.putString("stream","Frost entry GPU samples completed");finish(Activity.RESULT_OK,result);return;
            }
            JSONArray runs=new JSONArray();
            for(boolean inner:new boolean[]{false,true}){
                // Alternate the order on pass two to reduce warmup and thermal ordering bias.
                for(int pass=0;pass<2;pass++)for(int mode=0;mode<2;mode++){
                    boolean reduced=(pass==0?mode:1-mode)==1;
                    try(Target target=new Target(inner,reduced)){
                        JSONArray times=new JSONArray();
                        for(int i=-24;i<144;i++){
                            float angle=90+86*(float)Math.sin(Math.max(0,i)*Math.PI/72);
                            long start=System.nanoTime();target.draw(angle,.6f);
                            if(i>=0)times.put((System.nanoTime()-start)/1_000_000.0);
                        }
                        runs.put(new JSONObject().put("inner",inner).put("reduced",reduced).put("pass",pass).put("milliseconds",times));
                        if(pass==0)for(int angle:new int[]{0,45,90,135,180}){
                            target.draw(angle,.6f);
                            target.save((inner?"inner":"outer")+"-"+(reduced?"reduced":"native")+"-"+angle+".png");
                        }
                        if(pass==0&&reduced)for(int tilt=-1;tilt<=1;tilt++){
                            target.renderer.attitude(tilt*.5f,tilt*.35f,tilt*.4f);
                            target.draw(inner?110:65,.6f);
                            target.save((inner?"inner":"outer")+"-attitude-"+tilt+".png");
                        }
                    }
                }
            }
            try(FileWriter file=new FileWriter(new File(directory,"timing.json"))){file.write(runs.toString(2));}
            result.putString("stream","GPU comparison completed: "+directory);
            finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
        finally {callbacks.shutdown();}
    }
    private final class Target implements AutoCloseable {
        final boolean inner;final int w,h;
        final HardwareBuffer buffer;
        final HardwareBufferRenderer output;
        final RenderNode root=new RenderNode("Poldy renderer benchmark");
        final FoldRenderer renderer;
        final Bitmap source;
        Target(boolean inner,boolean reduced){
            this(inner,reduced,false);
        }
        Target(boolean inner,boolean reduced,boolean solid){
            this.inner=inner;w=inner?2448:1248;h=inner?1848:1972;
            Bitmap generated=sample(w,h);if(solid)generated.eraseColor(Color.WHITE);
            source=generated.copy(Bitmap.Config.HARDWARE,false);generated.recycle();
            if(source==null)throw new IllegalStateException("Hardware source unavailable");
            renderer=new FoldRenderer(reduced);
            buffer=HardwareBuffer.create(w,h,HardwareBuffer.RGBA_8888,1,HardwareBuffer.USAGE_GPU_COLOR_OUTPUT|HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
            output=new HardwareBufferRenderer(buffer);root.setPosition(0,0,w,h);output.setContentRoot(root);
        }
        void draw(float angle,float flatness)throws Exception {
            renderer.orientation(.2f,.8f,flatness);
            RecordingCanvas c=root.beginRecording(w,h);c.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
            renderer.draw(c,w,h,null,source,1,FoldOptics.at(inner,angle),1,0);root.endRecording();
            CountDownLatch ready=new CountDownLatch(1);Throwable[] failure={null};
            output.obtainRenderRequest().setColorSpace(ColorSpace.get(ColorSpace.Named.SRGB)).draw(callbacks,result->{
                try(SyncFence fence=result.getFence()){
                    if(result.getStatus()!=HardwareBufferRenderer.RenderResult.SUCCESS||!fence.await(Duration.ofSeconds(2)))
                        failure[0]=new IOException("GPU result/fence failed");
                }catch(Throwable e){failure[0]=e;}finally{ready.countDown();}
            });
            if(!ready.await(3,TimeUnit.SECONDS))throw new IOException("GPU callback timed out");
            if(failure[0]!=null)throw new IOException(failure[0]);
        }
        void save(String name)throws Exception {
            Bitmap hardware=Bitmap.wrapHardwareBuffer(buffer,ColorSpace.get(ColorSpace.Named.SRGB));
            if(hardware==null)throw new IOException("Cannot read benchmark output");
            Bitmap readable=hardware.copy(Bitmap.Config.ARGB_8888,false);
            try(FileOutputStream out=new FileOutputStream(new File(directory,name))){
                if(!readable.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG failed");
            }finally{readable.recycle();hardware.recycle();}
        }
        @Override public void close(){output.close();buffer.close();renderer.close();root.discardDisplayList();source.recycle();}
    }
    private static Bitmap sample(int w,int h){
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0,0,w,h,new int[]{0xff187eaa,0xff252a44,0xff061622},null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);p.setShader(null);float unit=w/6f;
        for(int row=0;row<9;row++)for(int col=0;col<6;col++){
            float x=col*unit+20,y=row*h/9f+20;
            p.setColor(new int[]{0xffb1e3a9,0xfff5b868,0xfff0c2dc}[(row+col)%3]);
            c.drawRoundRect(x,y,x+unit-32,y+h/15f,20,20,p);
            p.setColor(Color.WHITE);p.setTextSize(25);c.drawText("PIXELS "+row+":"+col,x,y+h/15f+32,p);
            p.setStrokeWidth(2);for(int line=0;line<7;line++)c.drawLine(x+8,y+12+line*8,x+unit-42,y+12+line*8,p);
        }
        return b;
    }
}
