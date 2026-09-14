package dev.foldy.securityprobe;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import android.view.*;

/** Synthetic pixels only; no Shizuku, Internet, storage, or screen capture permission. */
public final class ProbeActivity extends Activity {
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName n,IBinder b){Log.e("FoldyAudit","UNEXPECTED_SERVICE_BIND");unbindService(this);}
        public void onServiceDisconnected(ComponentName n){}
    };
    @Override public void onCreate(Bundle state){super.onCreate(state);boundaries();showMode(getIntent());}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);showMode(intent);}
    private void boundaries(){
        Log.i("FoldyAudit","probe_uid="+android.os.Process.myUid());
        try {
            getContentResolver().call(Uri.parse("content://dev.poldy.lab.shizuku"),"security-audit-noop",null,null);
            Log.e("FoldyAudit","PROVIDER_NOT_BLOCKED");
        }catch(SecurityException expected){Log.i("FoldyAudit","PROVIDER_DENIED");}
        catch(Exception other){Log.i("FoldyAudit","PROVIDER_INCONCLUSIVE:"+other.getClass().getSimpleName());}
        try {
            boolean bound=bindService(new Intent().setComponent(new ComponentName("dev.poldy.lab","dev.poldy.lab.FoldService")),connection,BIND_AUTO_CREATE);
            Log.i("FoldyAudit","SERVICE_BIND_RETURN="+bound);
            if(bound)unbindService(connection);
        }catch(SecurityException expected){Log.i("FoldyAudit","SERVICE_DENIED");}
        catch(Exception other){Log.i("FoldyAudit","SERVICE_INCONCLUSIVE:"+other.getClass().getSimpleName());}
    }
    private void showMode(Intent intent){
        boolean secure=intent.getBooleanExtra("secure",false);
        boolean hide=intent.getBooleanExtra("hide",false);
        if(secure)getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        try{getWindow().setHideOverlayWindows(hide);}
        catch(SecurityException noPermission){if(hide)throw noPermission;}
        setContentView(new View(this){
            final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c){
                c.drawColor(Color.rgb(24,184,120));
                p.setColor(Color.WHITE);p.setTextSize(36);
                c.drawText("Foldy security test",40,100,p);
                p.setTextSize(25);c.drawText("Synthetic pixels only",40,150,p);
            }
        });
        Log.i("FoldyAudit","mode:secure="+secure+",hide_overlays="+hide);
    }
}
