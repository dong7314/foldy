package dev.poldy.lab;

import android.graphics.*;
import android.os.*;
import android.view.*;
import java.lang.reflect.*;
import java.io.*;
import java.util.concurrent.*;

/** Logical capture and recovery lease; real panel changes supply the native app configuration. */
final class StableDisplay implements AutoCloseable {
    private final Object wm;
    private final Class<?> api=Class.forName("android.view.IWindowManager");
    private SurfaceControl sourceParent,source;
    private volatile boolean closed;
    private boolean inner;
    private final java.lang.Process guard;
    private final PrintWriter heartbeat;
    StableDisplay(String apkPath)throws Exception {
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"window");
        wm=Class.forName("android.view.IWindowManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        ProcessBuilder launch=new ProcessBuilder("/system/bin/app_process","/system/bin",RecoveryGuard.class.getName(),Integer.toString(android.os.Process.myPid()));
        launch.environment().put("CLASSPATH",apkPath);launch.redirectError(java.lang.ProcessBuilder.Redirect.to(new File("/dev/null")));
        guard=launch.start();heartbeat=new PrintWriter(guard.getOutputStream(),true);
        ExecutorService reader=Executors.newSingleThreadExecutor();
        try {
            String ready=reader.submit(()->new BufferedReader(new InputStreamReader(guard.getInputStream())).readLine()).get(2,TimeUnit.SECONDS);
            if(!"READY".equals(ready))throw new IOException("Recovery guard unavailable");
            sourceParent=new SurfaceControl.Builder().setName("Poldy logical capture parent").setBufferSize(2448,2448).build();
            source=mirror();
            try(SurfaceControl.Transaction t=new SurfaceControl.Transaction()){
                SurfaceControl.Transaction.class.getMethod("setLayerStack",SurfaceControl.class,int.class).invoke(t,sourceParent,2147483000);
                t.setVisibility(sourceParent,true).setAlpha(sourceParent,1).reparent(source,sourceParent).setVisibility(source,true).setAlpha(source,1).apply();
            }
        }catch(Exception e){close();throw e;}finally{reader.shutdownNow();}
    }
    private SurfaceControl mirror()throws Exception {
        SurfaceControl result=SurfaceControl.class.getConstructor().newInstance();
        if(!(boolean)api.getMethod("mirrorDisplay",int.class,SurfaceControl.class).invoke(wm,0,result)){
            result.release();throw new IllegalStateException("Logical mirror unavailable");
        }return result;
    }
    void resize(boolean opened)throws Exception {
        if(closed)throw new IllegalStateException("Display session closed");
        long until=SystemClock.elapsedRealtime()+1800;
        while(SystemClock.elapsedRealtime()<until) {
            if(profileReady(opened))return;
            if(!unlocked())throw new IllegalStateException("Locked during profile change");
            renew();Thread.sleep(12);
        }
        throw new IllegalStateException("Native display profile did not settle");
    }
    boolean profileReady(boolean opened)throws Exception {
        if(closed)throw new IllegalStateException("Display session closed");
        Point size=new Point();api.getMethod("getBaseDisplaySize",int.class,Point.class).invoke(wm,0,size);
        boolean ready=PhysicalPanels.primaryIsInner()==opened&&size.x==(opened?2448:1248)&&size.y==(opened?1848:1972);
        if(ready)inner=opened;return ready;
    }
    boolean isInner(){return inner;}
    boolean unlocked()throws Exception{return !(boolean)api.getMethod("isKeyguardLocked").invoke(wm);}
    void renew()throws IOException {if(!guard.isAlive())throw new IOException("Recovery guard stopped");heartbeat.println("BEAT");}
    CapturedFrame capture(NativeFrameCapture capture){return capture.captureLogical(source,inner?2448:1248,inner?1848:1972);}
    @Override public void close(){
        if(closed)return;closed=true;
        if(source!=null){source.release();source=null;}if(sourceParent!=null){sourceParent.release();sourceParent=null;}
        // The guard stays armed until DisplayControl has canceled its own device-state request.
    }
    void disarm(){heartbeat.println("DISARM");heartbeat.close();}
}
