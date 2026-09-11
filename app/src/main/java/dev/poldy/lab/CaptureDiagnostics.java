package dev.poldy.lab;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.*;

/** One short, user-started check: a colored test layer must disappear from the excluded capture. */
final class CaptureDiagnostics {
    static void run(Context context,boolean inner) {
        WindowManager wm=context.getSystemService(WindowManager.class);
        PanelSurface surface=new PanelSurface(context);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT);
        p.setFitInsetsTypes(0);p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;p.alpha=.8f;
        Handler main=new Handler(Looper.getMainLooper());
        Runnable cleanup=()->{try{wm.removeViewImmediate(surface);}catch(RuntimeException ignored){}};
        surface.solid(Color.MAGENTA);
        surface.whenReady(()->main.postDelayed(()->{
            SurfaceControl layer=surface.layer();if(layer==null){cleanup.run();return;}
            ControlBridge.capture(inner,new SurfaceControl[0],(included,time,base,error)->{
                int a=center(included);if(included!=null)included.recycle();
                ControlBridge.capture(inner,new SurfaceControl[]{layer},(excluded,time2,base2,error2)->{
                    int b=center(excluded);String shape=excluded==null?"none":excluded.getWidth()+"x"+excluded.getHeight();
                    if(excluded!=null)excluded.recycle();cleanup.run();
                    String result="included="+Integer.toHexString(a)+", excluded="+Integer.toHexString(b)+", size="+shape+", ms="+time2+", error="+error+"/"+error2;
                    Log.i("PoldyCapture","exclusion_probe:"+result);FoldService.status="캡처 진단 완료 · "+shape;
                });
            });
        },160));
        try{wm.addView(surface,p);main.postDelayed(cleanup,4000);}
        catch(RuntimeException e){FoldService.status=e.toString();}
    }
    private static int center(Bitmap bitmap) {
        if(bitmap==null)return 0;
        Bitmap copy=bitmap.copy(Bitmap.Config.ARGB_8888,false);
        int value=copy.getPixel(copy.getWidth()/2,copy.getHeight()/2);copy.recycle();return value;
    }
}
