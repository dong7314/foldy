package dev.poldy.lab;

import android.graphics.*;
import android.hardware.HardwareBuffer;
import android.os.*;
import android.view.SurfaceControl;

/** Offscreen GPU/parcel smoke test. Does not request a display state or capture app pixels. */
public final class NativeHandoffProbe {
    private static Handler main;
    private static int remaining=2;
    public static void main(String[] args)throws Exception {
        Looper.prepareMainLooper();main=new Handler(Looper.getMainLooper());
        run();Looper.loop();
    }
    private static void run()throws Exception {
        main.postDelayed(()->{System.err.println("TIMEOUT");System.exit(2);},5000);
        TaskProfileReader.Profile task=new TaskProfileReader().read();
        System.out.println("TASK_PROFILE ownerKnown="+(task.owner()!=null)+", inner="+task.inner()+", ready="+task.ready());
        try(CapturedFrame frame=new CapturedFrame()) {
            frame.buffer=HardwareBuffer.create(4,4,HardwareBuffer.RGBA_8888,1,HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE);
            frame.color=new ParcelableColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
            frame.owner="synthetic-owner";frame.generation=42;frame.inner=true;frame.nativeReady=true;
            Parcel parcel=Parcel.obtain();
            try {
                frame.writeToParcel(parcel,0);parcel.setDataPosition(0);
                try(CapturedFrame copy=CapturedFrame.CREATOR.createFromParcel(parcel)) {
                    if(!copy.inner||!copy.nativeReady||copy.generation!=42||!frame.owner.equals(copy.owner)||copy.buffer==null)
                        throw new AssertionError("Parcel metadata mismatch");
                }
            }finally{parcel.recycle();}
            System.out.println("PARCEL_OK");
        }
        for(boolean inner:new boolean[]{true,false}) {
            int width=inner?2448:1248,height=inner?1848:1972;
            SurfaceControl control=new SurfaceControl.Builder().setName("Poldy offscreen preparation test").setBufferSize(width,height).build();
            NativePanel panel=new NativePanel(control,width,height);
            panel.frame(null,inner,1,0);
            Bitmap wrong=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);
            try {
                try{panel.frame(wrong,inner,1,0);throw new AssertionError("Accepted non-native frame");}
                catch(IllegalArgumentException expected){}
            }finally{wrong.recycle();}
            panel.snapshot(main,bitmap->{
                try {
                    if(bitmap==null||bitmap.getWidth()!=width||bitmap.getHeight()!=height)throw new AssertionError("Missing native snapshot");
                    Bitmap pixels=bitmap.copy(Bitmap.Config.ARGB_8888,false);
                    int pixel=pixels.getPixel(width/2,height/2);pixels.recycle();
                    if(Color.alpha(pixel)!=255||(pixel&0xffffff)==0)throw new AssertionError("Transparent or black preparation");
                    System.out.println("PREPARATION_OK inner="+inner+", size="+width+"x"+height+", pixel="+Integer.toHexString(pixel));
                    bitmap.recycle();panel.close();
                    if(--remaining==0){System.out.println("PASS");System.exit(0);}
                }catch(Throwable failure){failure.printStackTrace();panel.close();System.exit(1);}
            });
        }
    }
}
