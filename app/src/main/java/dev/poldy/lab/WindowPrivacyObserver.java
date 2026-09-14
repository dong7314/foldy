package dev.poldy.lab;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.InputWindowHandle;
import android.window.WindowInfosListener;
import java.util.HashMap;
import java.util.function.BiConsumer;

/** Shell-only metadata listener. Framework stubs are compileOnly and never ship in the APK. */
final class WindowPrivacyObserver extends WindowInfosListener implements AutoCloseable {
    private final PackageManager packages;
    private final int ownerUid;
    private final BiConsumer<Boolean,Long> changed;
    private final HashMap<String,Boolean> overlayProtection=new HashMap<>();
    private volatile boolean blocked=true;
    private volatile long epoch;
    private boolean registered,closed;
    WindowPrivacyObserver(Context context,int ownerUid,BiConsumer<Boolean,Long> changed) {
        packages=context.getPackageManager();this.ownerUid=ownerUid;this.changed=changed;
        try {
            var initial=register();registered=true;
            // register() and compositor callbacks can race; serialize their evaluation.
            synchronized(this){if(epoch==0)evaluate(initial.first);}
        }catch(Throwable e){android.util.Log.e("PoldyPrivacy","window_observer_unavailable",e);}
    }
    boolean blocked(){return blocked;}
    long epoch(){return epoch;}
    @Override public synchronized void onWindowInfosChanged(InputWindowHandle[] windows,DisplayInfo[] displays){
        if(closed)return;
        try{evaluate(windows);}
        catch(RuntimeException e){
            if(!blocked||epoch==0){blocked=true;epoch++;changed.accept(true,epoch);}
            android.util.Log.e("PoldyPrivacy","window_policy_unavailable",e);
        }
    }
    private void evaluate(InputWindowHandle[] windows) {
        boolean protect=windows==null,applicationVisible=false;
        if(windows!=null)for(InputWindowHandle window:windows){
            // Our secure child buffer has a synthetic, type-0 input handle. It is not an app window.
            if(WindowPrivacyPolicy.ownRenderSurface(window.ownerUid,ownerUid,window.layoutParamsType))continue;
            // Samsung retains this secure power-off snapshot on the inactive display.
            // Real secure application windows are still checked independently below.
            if(WindowPrivacyPolicy.systemPowerFade(window.ownerUid,window.layoutParamsType,
                window.layoutParamsFlags,window.inputConfig,window.name))continue;
            if(!WindowPrivacyPolicy.visible(window.displayId,window.inputConfig,window.alpha,window.frame.isEmpty()))continue;
            if(WindowPrivacyPolicy.protectedContent(window.layoutParamsFlags,window.inputConfig)){
                if(!blocked)android.util.Log.i("PoldyPrivacy","protected_window:uid="+window.ownerUid+",type="+window.layoutParamsType
                    +",flags="+Integer.toHexString(window.layoutParamsFlags)+",input="+Integer.toHexString(window.inputConfig));
                protect=true;break;
            }
            if(WindowPrivacyPolicy.application(window.layoutParamsType)){
                applicationVisible=true;
                String name=window.packageName;
                // HIDE_OVERLAY_WINDOWS itself is not exposed in InputWindowHandle.
                // Conservatively exclude apps granted that permission for their entire visible lifetime.
                boolean protectedApp=name==null||Boolean.TRUE.equals(overlayProtection.get(name));
                if(!protectedApp&&packages.checkPermission("android.permission.HIDE_OVERLAY_WINDOWS",name)==PackageManager.PERMISSION_GRANTED){
                    protectedApp=true;overlayProtection.put(name,true);
                }
                if(protectedApp){
                    if(!blocked)android.util.Log.i("PoldyPrivacy","overlay_protected_app:uid="+window.ownerUid+",type="+window.layoutParamsType+",unknown="+(name==null));
                    protect=true;break;
                }
            }
        }
        // During an empty traversal retain the previous state instead of resuming a protected scene.
        if(!protect&&!applicationVisible)return;
        if(epoch==0||protect!=blocked){blocked=protect;epoch++;changed.accept(protect,epoch);}
    }
    @Override public synchronized void close(){
        closed=true;blocked=true;if(registered){unregister();registered=false;}overlayProtection.clear();
    }
}
