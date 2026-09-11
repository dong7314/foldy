package dev.poldy.lab;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.concurrent.*;
import rikka.shizuku.Shizuku;

final class ControlBridge {
    static volatile IDisplayControl remote;
    static volatile String status = "화면 제어 연결 대기";
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static Shizuku.UserServiceArgs args;
    interface Result { void done(String result); }
    private static final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            remote = IDisplayControl.Stub.asInterface(binder);
            worker.execute(() -> {
                try { status = remote.capabilities(); }
                catch (Exception e) { status = "화면 제어 연결 실패"; remote = null; }
            });
        }
        @Override public void onServiceDisconnected(ComponentName name) { remote = null; status = "화면 제어 연결 끊김"; }
    };
    static void connect(Context context) {
        try {
            if (!Shizuku.pingBinder()) { status = "Shizuku를 먼저 시작해 주세요."; return; }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(20); status = "Shizuku 권한 승인 대기"; return;
            }
            if (remote != null) return;
            args = new Shizuku.UserServiceArgs(new ComponentName(context, DisplayControl.class))
                .daemon(false).processNameSuffix("display_control").debuggable(false).version(39);
            status = "화면 제어 연결 중";
            Shizuku.bindUserService(args, connection);
        } catch (RuntimeException e) { status = "화면 제어 연결 실패: " + e.getClass().getSimpleName(); }
    }
    static boolean ready() { return remote != null && "READY".equals(status); }
    interface FrameResult {void done(android.graphics.Bitmap frame,long elapsed,int baseState,String error);}
    private static final ExecutorService captureWorker=Executors.newSingleThreadExecutor();
    static void capture(boolean inner,android.view.SurfaceControl[] excluded,FrameResult callback) {
        // Snapshot parcelable handles before the window can be removed on the main thread.
        android.view.SurfaceControl[] copies=new android.view.SurfaceControl[excluded.length];
        for(int i=0;i<excluded.length;i++) {
            Parcel parcel=Parcel.obtain();
            try {excluded[i].writeToParcel(parcel,0);parcel.setDataPosition(0);copies[i]=android.view.SurfaceControl.CREATOR.createFromParcel(parcel);}
            finally {parcel.recycle();}
        }
        captureWorker.execute(()->{
            android.graphics.Bitmap bitmap=null;long elapsed=0;int baseState=-1;String error=null;
            try(CapturedFrame result=remote.captureFrame(inner,copies)) {
                if(result==null)error="Empty capture reply";
                else {elapsed=result.captureMillis;baseState=result.baseState;error=result.error;bitmap=result.takeBitmap();}
            }catch(Exception e){error=e.toString();}
            finally {for(android.view.SurfaceControl copy:copies)copy.release();}
            android.graphics.Bitmap answer=bitmap;long duration=elapsed;int physical=baseState;String failure=error;
            main.post(()->callback.done(answer,duration,physical,failure));
        });
    }
    interface SceneResult {void done(android.view.SurfaceControl[] panels,String error);}
    static void createScene(boolean inner,SceneResult callback){
        worker.execute(()->{
            android.view.SurfaceControl[] result=null;String error=null;
            try {result=remote.createScene(inner);if(result==null)error="GPU 화면을 만들지 못했습니다.";}
            catch(Exception e){error=e.toString();}
            android.view.SurfaceControl[] panels=result;String failure=error;
            main.post(()->callback.done(panels,failure));
        });
    }
    static void routeScene(boolean inner){worker.execute(()->{try{if(remote!=null)remote.routeScene(inner);}catch(Exception ignored){}});}
    static void startAngles(IHingeAngleListener listener,Result callback){
        worker.execute(()->{
            String result;
            try{result=remote!=null?remote.startAngles(listener):"각도 제어 연결이 없습니다.";}
            catch(Exception e){result="각도 연결 실패: "+e.getClass().getSimpleName();}
            String answer=result;main.post(()->callback.done(answer));
        });
    }
    static void stopAngles(){worker.execute(()->{try{if(remote!=null)remote.stopAngles();}catch(Exception ignored){}});}
    static void switchTo(boolean inner, Result callback) {
        worker.execute(() -> {
            String result;
            try { result = remote != null ? remote.requestMode(inner) : "제어 연결이 없습니다."; }
            catch (Exception e) { result = "화면 요청 실패: " + e.getClass().getSimpleName(); }
            String answer = result;
            main.post(() -> callback.done(answer));
        });
    }
    static void renew() { worker.execute(() -> { try { if (remote != null) remote.renew(); } catch (Exception ignored) {} }); }
    static void reset() { worker.execute(() -> { try { if (remote != null) remote.reset(); } catch (Exception ignored) {} }); }
    static void settle(boolean fullyOpen) { worker.execute(() -> { try { if (remote != null) remote.settle(fullyOpen); } catch (Exception ignored) {} }); }
}
