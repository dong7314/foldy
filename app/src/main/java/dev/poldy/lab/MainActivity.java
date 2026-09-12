package dev.poldy.lab;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView readings;
    private final Shizuku.OnRequestPermissionResultListener permissionListener = (code,result) -> {
        if (result == PackageManager.PERMISSION_GRANTED) ControlBridge.connect(this);
    };
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            readings.setText((ControlBridge.ready() ? "화면 제어 연결됨" : ControlBridge.status)
                + "\n" + FoldService.status);
            handler.postDelayed(this,250);
        }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(17,26,32));
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(26),dp(28),dp(26),dp(32)); scroll.addView(body);
        scroll.setOnApplyWindowInsetsListener((v,insets) -> {
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        label(body,"POLDY / FOLD STUDY 03",12,0xFFB8E7D5);
        TextView title=label(body,"접는 순간,\n화면이 이어지도록.",32,Color.WHITE);
        title.setTypeface(null,Typeface.BOLD);
        label(body,"두 화면을 미리 준비합니다.\n현재 앱 화면을 원본 해상도로 받아 접는 동안 이어 보여 줍니다.",15,0xFFB9C4CC);
        readings=label(body,"",16,0xFFDEEFE7);
        readings.setPadding(dp(18),dp(18),dp(18),dp(18));
        GradientDrawable card=new GradientDrawable();card.setColor(0xFF1F3038);card.setCornerRadius(dp(20));readings.setBackground(card);
        button(body,"1 · 화면 제어 연결",()->ControlBridge.connect(this));
        button(body,"2 · 다른 앱 위에 표시 허용",()->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:"+getPackageName()))));
        button(body,"3 · 애니메이션 시작",this::startAnimation);
        button(body,"애니메이션 중지",()->{stopService(new Intent(this,FoldService.class));ControlBridge.reset();});
        label(body,"완전히 접은 상태에서 시작해 주세요.\n기기의 접힘 각도에 맞춰 화면 효과를 움직이고, 각도 기록 사이를 자연스럽게 연결합니다.",14,0xFFB9C4CC);
        label(body,"화면 공유는 전체 화면을 선택해 주세요. 이미지는 저장하거나 전송하지 않으며 기기 안에서만 처리합니다. 화면 잠금 또는 공유 종료 시 동작을 중지합니다.",13,0xFF91A6B1);
        setContentView(scroll);
    }
    private void requestNotification() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
    }
    private void startAnimation() {
        if (FoldService.running) { Toast.makeText(this,"이미 실행 중입니다.",Toast.LENGTH_SHORT).show();return; }
        if (!ControlBridge.ready()) { ControlBridge.connect(this);return; }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this,"다른 앱 위에 표시를 먼저 허용해 주세요.",Toast.LENGTH_LONG).show();return;
        }
        requestNotification();
        MediaProjectionManager manager=getSystemService(MediaProjectionManager.class);
        Intent capture=Build.VERSION.SDK_INT>=34
            ? manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            : manager.createScreenCaptureIntent();
        startActivityForResult(capture,30);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request==30 && result==RESULT_OK && data!=null)
            startForegroundService(new Intent(this,FoldService.class).putExtra("result",result).putExtra("consent",data));
    }
    private TextView label(LinearLayout parent,String text,int size,int color) {
        TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(4),1);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(20);parent.addView(v,p);return v;
    }
    private void button(LinearLayout parent,String text,Runnable run) {
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(15);b.setMinHeight(dp(52));
        b.setOnClickListener(v->run.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);parent.addView(b,p);
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){Shizuku.removeRequestPermissionResultListener(permissionListener);super.onDestroy();}
}
