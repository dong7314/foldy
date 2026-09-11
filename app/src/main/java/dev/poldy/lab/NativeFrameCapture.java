package dev.poldy.lab;

import android.graphics.ParcelableColorSpace;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.SurfaceControl;
import java.lang.reflect.*;

/** Runs only in the authorized Shizuku UserService. No pixels are saved or transmitted off-device. */
final class NativeFrameCapture {
    private final Class<?> capture, builderClass, argsClass;
    private final IBinder innerToken, outerToken;
    private final Object deviceStates;
    private final Method readState;
    NativeFrameCapture() throws Exception {
        Class<?> sc=SurfaceControl.class;
        IBinder stateBinder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"device_state");
        Class<?> stateApi=Class.forName("android.hardware.devicestate.IDeviceStateManager");
        deviceStates=Class.forName("android.hardware.devicestate.IDeviceStateManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,stateBinder);
        readState=stateApi.getMethod("getDeviceStateInfo");
        innerToken=(IBinder)sc.getMethod("getPhysicalDisplayToken",long.class).invoke(null,4630947004648141459L);
        outerToken=(IBinder)sc.getMethod("getPhysicalDisplayToken",long.class).invoke(null,4630947123231501204L);
        capture=Class.forName("android.window.ScreenCaptureInternal");
        builderClass=Class.forName("android.window.ScreenCaptureInternal$DisplayCaptureArgs$Builder");
        argsClass=Class.forName("android.window.ScreenCaptureInternal$DisplayCaptureArgs");
    }
    CapturedFrame capture(boolean inner, SurfaceControl[] excluded) {
        CapturedFrame reply=new CapturedFrame();long start=SystemClock.elapsedRealtime();
        try {
            Object info=readState.invoke(deviceStates);
            Object base=info.getClass().getField("baseState").get(info);
            reply.baseState=(int)base.getClass().getMethod("getIdentifier").invoke(base);
            Object builder=builderClass.getConstructor(IBinder.class).newInstance(inner?innerToken:outerToken);
            builderClass.getMethod("setFrameScale",float.class).invoke(builder,1f);
            builderClass.getMethod("setSecureContentPolicy",int.class).invoke(builder,0);
            builderClass.getMethod("setProtectedContentPolicy",int.class).invoke(builder,0);
            if(excluded!=null && excluded.length>0)
                builderClass.getMethod("setExcludeLayers",SurfaceControl[].class).invoke(builder,(Object)excluded);
            Object shot=capture.getMethod("captureDisplay",argsClass)
                .invoke(null,builderClass.getMethod("build").invoke(builder));
            if(shot==null)throw new IllegalStateException("No capture buffer");
            HardwareBuffer buffer=(HardwareBuffer)shot.getClass().getMethod("getHardwareBuffer").invoke(shot);
            ColorSpace color=(ColorSpace)shot.getClass().getMethod("getColorSpace").invoke(shot);
            reply.buffer=buffer;reply.color=new ParcelableColorSpace(color);
            reply.captureMillis=SystemClock.elapsedRealtime()-start;
        } catch(Exception e) {
            Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();
            reply.close();reply.error=cause.getClass().getSimpleName()+": "+cause.getMessage();
        }
        return reply;
    }
    CapturedFrame captureLogical(SurfaceControl root,int width,int height) {
        CapturedFrame reply=new CapturedFrame();long start=SystemClock.elapsedRealtime();
        try {
            Object info=readState.invoke(deviceStates);Object base=info.getClass().getField("baseState").get(info);
            reply.baseState=(int)base.getClass().getMethod("getIdentifier").invoke(base);
            Class<?> type=Class.forName("android.window.ScreenCaptureInternal$LayerCaptureArgs$Builder");
            Object b=type.getConstructor(SurfaceControl.class).newInstance(root);
            type.getMethod("setSourceCrop",android.graphics.Rect.class).invoke(b,new android.graphics.Rect(0,0,width,height));
            type.getMethod("setFrameScale",float.class).invoke(b,1f);
            type.getMethod("setSecureContentPolicy",int.class).invoke(b,0);
            type.getMethod("setProtectedContentPolicy",int.class).invoke(b,0);
            Object shot=capture.getMethod("captureLayers",Class.forName("android.window.ScreenCaptureInternal$LayerCaptureArgs"))
                .invoke(null,type.getMethod("build").invoke(b));
            if(shot==null)throw new IllegalStateException("No logical buffer");
            reply.buffer=(HardwareBuffer)shot.getClass().getMethod("getHardwareBuffer").invoke(shot);
            reply.color=new ParcelableColorSpace((ColorSpace)shot.getClass().getMethod("getColorSpace").invoke(shot));
            reply.captureMillis=SystemClock.elapsedRealtime()-start;
        }catch(Exception e){reply.close();reply.error=e.toString();}return reply;
    }
}
