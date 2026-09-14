package dev.poldy.lab;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.*;
import android.view.*;
import android.widget.*;
import rikka.shizuku.Shizuku;

/** Setup coordinates real permissions; the in-app demonstration never changes a physical panel. */
public final class MainActivity extends Activity implements SetupScreen.Actions {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SetupScreen screen;
    private SharedPreferences preferences;
    private boolean experienced,connecting,starting,wasRunning,notificationPending,stopping;
    private long operationAt;
    private String message="";
    private Dialog sheet;
    private final Shizuku.OnRequestPermissionResultListener permissionListener=(code,result)->runOnUiThread(()->{
        if(code!=20)return;
        if(result==PackageManager.PERMISSION_GRANTED){connecting=true;operationAt=SystemClock.uptimeMillis();ControlBridge.connect(this);}
        else {connecting=false;message="권한이 승인되지 않았어요. 연결 버튼으로 다시 시도할 수 있어요.";}
        refreshNow();
    });
    private final Runnable refresh=new Runnable(){@Override public void run(){refreshNow();handler.postDelayed(this,400);}};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);preferences=getSharedPreferences("setup",MODE_PRIVATE);
        experienced=saved!=null?saved.getBoolean("experienced"):preferences.getBoolean("experienced",false);
        if(saved!=null){starting=saved.getBoolean("starting");notificationPending=saved.getBoolean("notificationPending");operationAt=saved.getLong("operationAt");}
        Shizuku.addRequestPermissionResultListener(permissionListener);createScreen();
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->{
            if(FoldService.running||!experienced)finish();else back();
        });
    }
    private void createScreen(){
        if(screen!=null)screen.foreground(false);
        screen=new SetupScreen(this,this);setContentView(screen);
        SetupStyle s=new SetupStyle(this);
        WindowInsetsController bars=getWindow().getInsetsController();
        if(bars!=null)bars.setSystemBarsAppearance(s.dark?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        refreshNow();
    }
    private SetupStage stage(){return SetupStage.resolve(experienced,ControlBridge.ready(),FoldService.running,FoldService.prepared,FoldService.suspended);}
    private void refreshNow(){
        if(screen==null)return;
        long elapsed=SystemClock.uptimeMillis()-operationAt;
        if(connecting){
            if(ControlBridge.ready()){connecting=false;message="";}
            else if(!ControlBridge.status.contains(" 중")&&!ControlBridge.status.contains("대기")){connecting=false;message=connectionMessage();}
            else if(elapsed>25000){connecting=false;message="연결을 확인하지 못했어요. Shizuku가 실행 중인지 확인해 주세요.";}
        }
        if(starting&&(FoldService.prepared||FoldService.suspended)){starting=false;message="";}
        else if(starting&&!notificationPending&&elapsed>4500&&!FoldService.running){starting=false;message="화면 준비를 마치지 못했어요. 연결 상태를 확인하고 다시 켜 주세요.";}
        if(wasRunning&&!FoldService.running&&!starting&&!stopping)message="애니메이션이 멈췄어요. 다시 켜거나 연결 상태를 확인해 주세요.";
        if(stopping&&!FoldService.running){stopping=false;message="";}
        wasRunning=FoldService.running;
        screen.update(new SetupScreen.Model(stage(),ControlBridge.ready(),connecting||starting||stopping,FoldService.suspended?FoldService.status:message,
            preferences.getBoolean("autoplay",true),preferences.getBoolean("reduced",false),preferences.getBoolean("haptics",true)));
    }
    private String connectionMessage(){
        if(ControlBridge.status.contains("먼저 시작"))return "Shizuku를 먼저 실행해 주세요. 아래에서 연결 방법을 볼 수 있어요.";
        if(ControlBridge.status.contains("지원")||ControlBridge.remote!=null)return "이 기기의 화면 제어를 준비하지 못했어요. 연결 정보를 확인해 주세요.";
        return "화면 제어에 연결하지 못했어요. Shizuku 상태를 확인한 뒤 다시 시도해 주세요.";
    }
    @Override public void primary(){
        message="";
        switch(stage()){
            case EXPERIENCE -> {experienced=true;preferences.edit().putBoolean("experienced",true).apply();}
            case CONNECT -> {connecting=true;operationAt=SystemClock.uptimeMillis();ControlBridge.connect(this);}
            case READY -> startAnimation();
            case ACTIVE, PAUSED, STARTING -> {stopping=true;starting=false;if(!stopService(new Intent(this,FoldService.class)))ControlBridge.reset();}
        }
        refreshNow();
    }
    @Override public void back(){experienced=false;message="";refreshNow();}
    private void startAnimation(){
        if(!ControlBridge.ready()||FoldService.running)return;
        starting=true;operationAt=SystemClock.uptimeMillis();
        if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!preferences.getBoolean("notificationAsked",false)){
            notificationPending=true;preferences.edit().putBoolean("notificationAsked",true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
        }else launchService();
    }
    private void launchService(){
        notificationPending=false;operationAt=SystemClock.uptimeMillis();
        if(!ControlBridge.ready()){starting=false;message="화면 연결이 끊겼어요. 다시 연결해 주세요.";refreshNow();return;}
        try {startForegroundService(new Intent(this,FoldService.class));}
        catch(RuntimeException e){starting=false;message="애니메이션을 시작하지 못했어요. 앱을 다시 열고 시도해 주세요.";}
        refreshNow();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        // Notification denial does not grant screen access or prevent an already-authorized FGS.
        if(code==10&&notificationPending)launchService();
    }
    @Override public void settings(){
        LinearLayout body=sheet("미리보기 설정","이 설정은 앱 안의 미리보기에 적용돼요.");
        toggle(body,"미리보기 자동재생","설정 단계를 이동하면 한 번 재생해요","autoplay",true);
        toggle(body,"햅틱 피드백","버튼을 누를 때 짧게 진동해요","haptics",true);
        toggle(body,"동작 간소화","자동 움직임 없이 직접 각도를 조절해요","reduced",false);
        SetupStyle s=new SetupStyle(this);TextView caption=s.text("휴대전화에 적용되는 접힘 효과와는 별개의 설정이에요.",13,s.secondary,false);addSheet(body,caption,20);
        TextView info=s.text("연결 상태·개인정보 안내  ›",14,s.blue,false);info.setMinHeight(s.dp(48));info.setGravity(Gravity.CENTER_VERTICAL);info.setOnClickListener(v->help());addSheet(body,info,8);
        TextView version=s.text("Foldy  ·  "+versionName(),12,s.muted,false);addSheet(body,version,20);
        showSheet();
    }
    @Override public void help(){
        LinearLayout body=sheet("화면 연결 안내","다른 앱 위에서도 효과가 이어지려면 화면 제어 연결이 필요해요.");
        SetupStyle s=new SetupStyle(this);
        TextView steps=s.text("1   Shizuku 앱을 실행해 주세요.\n\n2   Shizuku의 안내에 따라 서비스를 시작해 주세요.\n\n3   Foldy로 돌아와 ‘화면 제어 연결’을 누르고 권한을 승인해 주세요.",15,s.text,false);steps.setLineSpacing(s.dp(5),1);addSheet(body,steps,20);
        TextView privacy=s.text("화면 이미지는 효과를 그리는 동안 기기 메모리에서만 처리해요. 저장하거나 전송하지 않아요. 화면을 잠그면 처리를 쉬고, 잠금 해제 뒤 다시 준비해요.",13,s.secondary,false);addSheet(body,privacy,24);
        TextView details=s.text("현재 연결: "+(ControlBridge.ready()?"연결됨":ControlBridge.status)+"\n동작 상태: "+FoldService.status,12,s.muted,false);details.setTextIsSelectable(true);addSheet(body,details,20);
        TextView open=s.text("Shizuku 열기",16,android.graphics.Color.WHITE,true);open.setGravity(Gravity.CENTER);open.setMinHeight(s.dp(54));s.ripple(open,0xff2676ef,16);addSheet(body,open,24);
        open.setOnClickListener(v->{
            Intent intent=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
            if(intent==null){Toast.makeText(this,"Shizuku 앱을 찾지 못했어요. 먼저 설치해 주세요.",Toast.LENGTH_LONG).show();return;}
            sheet.dismiss();startActivity(intent);
        });showSheet();
    }
    private LinearLayout sheet(String title,String subtitle){
        if(sheet!=null)sheet.dismiss();SetupStyle s=new SetupStyle(this);sheet=new Dialog(this);
        screen.foreground(false);sheet.setOnDismissListener(dialog->screen.foreground(true));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(s.dp(20),s.dp(12),s.dp(20),s.dp(24));body.setBackground(s.shape(s.surface,30));
        View handle=new View(this);handle.setBackground(s.shape(s.line,3));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(s.dp(34),s.dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.bottomMargin=s.dp(17);body.addView(handle,hp);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);TextView text=s.text(title,20,s.text,true);text.setAccessibilityHeading(true);heading.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=s.text("닫기",13,s.secondary,true);close.setGravity(Gravity.CENTER);close.setMinHeight(s.dp(48));close.setMinWidth(s.dp(48));close.setOnClickListener(v->sheet.dismiss());s.ripple(close,s.surface,12);heading.addView(close);body.addView(heading);
        TextView sub=s.text(subtitle,14,s.secondary,false);addSheet(body,sub,8);scroll.addView(body);sheet.setContentView(scroll);return body;
    }
    private void showSheet(){
        sheet.show();Window window=sheet.getWindow();if(window==null)return;
        window.setBackgroundDrawableResource(android.R.color.transparent);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams lp=window.getAttributes();lp.width=Math.min(getResources().getDisplayMetrics().widthPixels,new SetupStyle(this).dp(560));lp.height=-2;lp.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;lp.dimAmount=.25f;window.setAttributes(lp);
        // Let large text and small cover windows scroll instead of clipping the bottom actions.
        int maxHeight=Math.round(getResources().getDisplayMetrics().heightPixels*.86f);
        window.getDecorView().post(()->{if(window.getDecorView().getHeight()>maxHeight)window.setLayout(lp.width,maxHeight);});
    }
    private void toggle(LinearLayout body,String title,String description,String key,boolean fallback){
        SetupStyle s=new SetupStyle(this);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,s.dp(14),0,s.dp(14));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        labels.addView(s.text(title,16,s.text,true));TextView note=s.text(description,12,s.secondary,false);addSheet(labels,note,6);
        Switch control=new Switch(this);control.setContentDescription(title);control.setMinHeight(s.dp(48));control.setChecked(preferences.getBoolean(key,fallback));row.addView(control);
        control.setOnCheckedChangeListener((v,value)->{preferences.edit().putBoolean(key,value).apply();refreshNow();});row.setOnClickListener(v->control.toggle());body.addView(row);
        View line=new View(this);line.setBackgroundColor(s.line);body.addView(line,new LinearLayout.LayoutParams(-1,s.dp(1)));
    }
    private void addSheet(LinearLayout body,View view,int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=new SetupStyle(this).dp(top);body.addView(view,lp);}
    private String versionName(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(PackageManager.NameNotFoundException e){return "";}}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);if(sheet!=null)sheet.dismiss();createScreen();}
    @Override protected void onSaveInstanceState(Bundle out){out.putBoolean("experienced",experienced);out.putBoolean("starting",starting);out.putBoolean("notificationPending",notificationPending);out.putLong("operationAt",operationAt);super.onSaveInstanceState(out);}
    @Override protected void onResume(){super.onResume();screen.foreground(true);handler.removeCallbacks(refresh);handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);screen.foreground(false);super.onPause();}
    @Override protected void onDestroy(){if(sheet!=null)sheet.dismiss();Shizuku.removeRequestPermissionResultListener(permissionListener);super.onDestroy();}
}
