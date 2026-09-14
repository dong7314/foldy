package dev.poldy.lab;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.*;

/** Native preview plus grouped settings. Rendering a step never grants a permission. */
final class SetupScreen extends FrameLayout {
    interface Actions { void primary();void back();void settings();void help(); }
    record Model(SetupStage stage,boolean connected,boolean busy,String message,boolean autoPlay,boolean reduced,boolean haptics){}
    private final SetupStyle s;
    private final Actions actions;
    private final LinearLayout root,header,body,columns,preview,settings,footer,rows;
    private final TextView title,description,primary,angle,section,note,stepCount;
    private final FrameLayout back;
    private final ProgressBar spinner;
    private final ScrollView scroll;
    private final FoldShowcase showcase;
    private final SeekBar slider;
    private Model model;
    private boolean updatingSlider,foreground=true;

    SetupScreen(Context context,Actions actions){
        super(context);this.actions=actions;s=new SetupStyle(context);setBackgroundColor(s.background);
        root=vertical();addView(root,new FrameLayout.LayoutParams(-1,-1));
        header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(s.dp(16),0,s.dp(12),0);
        back=iconButton(SetupIcon.Kind.BACK,"미리보기로 돌아가기",actions::back,false);header.addView(back,new LinearLayout.LayoutParams(s.dp(40),s.dp(48)));
        SetupIcon brand=new SetupIcon(context,SetupIcon.Kind.BRAND,s.blue);header.addView(brand,new LinearLayout.LayoutParams(s.dp(23),s.dp(23)));
        TextView wordmark=s.text("Foldy",20,s.text,true);wordmark.setPadding(s.dp(8),0,0,0);header.addView(wordmark,new LinearLayout.LayoutParams(0,-2,1));
        stepCount=s.text("",13,s.secondary,false);stepCount.setPadding(s.dp(12),0,s.dp(8),0);header.addView(stepCount);
        header.addView(iconButton(SetupIcon.Kind.SETTINGS,"미리보기 설정",actions::settings,false),new LinearLayout.LayoutParams(s.dp(48),s.dp(48)));
        root.addView(header,new LinearLayout.LayoutParams(-1,s.dp(52)));
        scroll=new ScrollView(context);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout center=new FrameLayout(context);scroll.addView(center,new ScrollView.LayoutParams(-1,-1));
        body=vertical();body.setPadding(s.dp(20),s.dp(16),s.dp(20),s.dp(24));center.addView(body,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));
        title=s.text("",24,s.text,true);title.setAccessibilityHeading(true);add(body,title,0,6);
        description=s.text("",15,s.secondary,false);add(body,description,0,22);
        columns=new LinearLayout(context);columns.setOrientation(LinearLayout.VERTICAL);columns.setGravity(Gravity.TOP);body.addView(columns);

        preview=vertical();preview.setBackground(s.shape(s.surface,20));preview.setPadding(s.dp(16),s.dp(8),s.dp(16),s.dp(12));
        LinearLayout previewHeader=new LinearLayout(context);previewHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView previewLabel=s.text("미리보기",17,s.text,true);previewHeader.addView(previewLabel,new LinearLayout.LayoutParams(0,-2,1));
        angle=s.text("",13,s.secondary,false);previewHeader.addView(angle);
        previewHeader.addView(iconButton(SetupIcon.Kind.PLAY,"접힘 애니메이션 다시 재생",()->{feedback();showcasePlay();},true),new LinearLayout.LayoutParams(s.dp(48),s.dp(48)));preview.addView(previewHeader);
        showcase=new FoldShowcase(context);preview.addView(showcase,new LinearLayout.LayoutParams(-1,s.dp(208)));
        LinearLayout controls=new LinearLayout(context);controls.setGravity(Gravity.CENTER_VERTICAL);controls.addView(s.text("접힘",13,s.secondary,false));
        slider=new SeekBar(context);slider.setMax(180);slider.setProgressTintList(ColorStateList.valueOf(s.blue));slider.setProgressBackgroundTintList(ColorStateList.valueOf(s.line));slider.setThumbTintList(ColorStateList.valueOf(s.blue));slider.setContentDescription("미리보기 접힘 각도");
        controls.addView(slider,new LinearLayout.LayoutParams(0,s.dp(48),1));controls.addView(s.text("펼침",13,s.secondary,false));preview.addView(controls);
        showcase.listener(value->{updatingSlider=true;slider.setProgress(Math.round(value*180));updatingSlider=false;angle.setText(Math.round(value*180)+"°");});showcase.progress(.82f);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar view,int progress,boolean fromUser){if(fromUser&&!updatingSlider)showcase.progress(progress/180f);}
            public void onStartTrackingTouch(SeekBar view){showcase.stop();}
            public void onStopTrackingTouch(SeekBar view){feedback();}
        });
        settings=vertical();section=s.text("설정",17,s.text,true);section.setAccessibilityHeading(true);add(settings,section,0,12);
        rows=vertical();rows.setPadding(0,s.dp(4),0,s.dp(4));rows.setBackground(s.shape(s.surface,20));settings.addView(rows);
        note=s.text("",13,s.secondary,false);note.setPadding(s.dp(16),0,s.dp(16),0);note.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);add(settings,note,10,0);
        columns.addView(preview);columns.addView(settings);

        footer=vertical();footer.setPadding(s.dp(20),s.dp(12),s.dp(20),s.dp(12));footer.setBackgroundColor(s.background);
        FrameLayout button=new FrameLayout(context);primary=s.text("",17,Color.WHITE,true);primary.setGravity(Gravity.CENTER);primary.setMinHeight(s.dp(54));primary.setPadding(s.dp(38),s.dp(12),s.dp(38),s.dp(12));primary.setFocusable(true);
        primary.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(Button.class.getName());}});
        s.ripple(primary,0xff3182f6,14);primary.setOnClickListener(v->{feedback();actions.primary();});button.addView(primary,new FrameLayout.LayoutParams(-1,-2));
        spinner=new ProgressBar(context);spinner.setIndeterminateTintList(ColorStateList.valueOf(Color.WHITE));FrameLayout.LayoutParams spin=new FrameLayout.LayoutParams(s.dp(20),s.dp(20),Gravity.CENTER_VERTICAL|Gravity.END);spin.setMarginEnd(s.dp(18));button.addView(spinner,spin);footer.addView(button);
        root.addView(footer,new LinearLayout.LayoutParams(-1,-2));
        setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());root.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
    }
    void update(Model value){
        if(value.equals(model))return;Model before=model;model=value;SetupStage stage=value.stage();boolean changed=before!=null&&before.stage()!=stage;
        showcase.reduced(value.reduced());if(before!=null&&before.autoPlay()&&!value.autoPlay())showcase.stop();
        back.setVisibility(stage==SetupStage.CONNECT||stage==SetupStage.READY?VISIBLE:GONE);
        stepCount.setText(stage==SetupStage.ACTIVE?"사용 중":(stage.step()+1)+" / 3");stepCount.setTextColor(stage==SetupStage.ACTIVE?s.green:s.secondary);
        String heading,bodyText,button;
        switch(stage){
            case EXPERIENCE -> {heading="접힘 애니메이션";bodyText="효과를 미리 보고 휴대전화에 적용해 보세요.";button="설정 시작하기";}
            case CONNECT -> {heading="화면 제어 연결";bodyText="다른 앱에서도 효과를 사용하려면\nShizuku 연결이 필요해요.";button=value.busy()?"연결 확인 중":"화면 제어 연결";}
            case READY -> {heading="화면이 연결됐어요";bodyText="애니메이션을 켜면 바로 사용할 수 있어요.";button="애니메이션 켜기";}
            case STARTING -> {heading="애니메이션 준비 중";bodyText="두 화면의 첫 장면을 준비하고 있어요.";button="애니메이션 끄기";}
            case PAUSED -> {heading="애니메이션 일시 정지";bodyText="보호 중인 화면에서는 효과를 쉬어가요.";button="애니메이션 끄기";}
            default -> {heading="접힘 애니메이션";bodyText="접거나 펼칠 때 효과가 적용돼요.";button="애니메이션 끄기";}
        }
        title.setText(heading);description.setText(bodyText);primary.setText(button);
        primary.setEnabled(stage==SetupStage.STARTING||stage==SetupStage.PAUSED||!value.busy());primary.setAlpha(primary.isEnabled()?1:.72f);spinner.setVisibility(value.busy()||stage==SetupStage.STARTING?VISIBLE:GONE);
        rebuildRows(value);
        if(changed){
            feedback();scroll.smoothScrollTo(0,0);
            if(!value.reduced()&&ValueAnimator.areAnimatorsEnabled()){
                title.animate().cancel();title.setAlpha(.3f);title.setTranslationY(s.dp(4));title.animate().alpha(1).translationY(0).setDuration(220).setInterpolator(new PathInterpolator(.2f,0,.2f,1)).start();
            }
            if(value.autoPlay()&&foreground)showcase.play(true);title.announceForAccessibility(heading);
        }else if(before==null&&value.autoPlay()&&foreground)post(()->{if(isAttachedToWindow()&&foreground)showcase.play(false);});
    }
    private void rebuildRows(Model value){
        rows.removeAllViews();boolean connecting=value.stage()==SetupStage.CONNECT;
        section.setText(connecting?"연결 방법":"설정");
        if(connecting){
            instruction("1","Shizuku 실행","Shizuku에서 서비스를 시작해 주세요.");divider();
            instruction("2","Foldy 연결 허용","아래 버튼을 누르고 권한을 승인해 주세요.");divider();
            row("연결 도움말","",actions::help,false);
        }else{
            row("화면 제어",value.connected()?"연결됨":"연결 필요",actions::help,value.connected());divider();
            String state=value.stage()==SetupStage.ACTIVE?"사용 중":value.stage()==SetupStage.STARTING?"준비 중":value.stage()==SetupStage.PAUSED?"일시 정지":"꺼짐";
            row("애니메이션",state,()->{if(!model.busy()&&model.stage()!=SetupStage.STARTING)actions.primary();},value.stage()==SetupStage.ACTIVE);divider();
            row("미리보기 설정",value.reduced()?"동작 간소화":value.autoPlay()?"자동재생":"직접 재생",actions::settings,false);
        }
        note.setText(value.message().isEmpty()?(connecting?"화면 이미지는 저장하거나 전송하지 않아요.":"잠금·보호 화면에서는 효과를 일시 정지해요."):value.message());
        note.setTextColor(value.message().isEmpty()?s.secondary:(s.dark?0xffffb5a3:0xffa84929));
    }
    private void instruction(String number,String title,String subtitle){
        LinearLayout row=new LinearLayout(getContext());row.setPadding(s.dp(16),s.dp(13),s.dp(16),s.dp(13));row.setGravity(Gravity.CENTER_VERTICAL);
        TextView index=s.text(number,13,s.blue,true);index.setGravity(Gravity.CENTER);index.setBackground(s.shape(s.softBlue,10));row.addView(index,new LinearLayout.LayoutParams(s.dp(28),s.dp(28)));
        LinearLayout labels=vertical();labels.setPadding(s.dp(12),0,0,0);labels.addView(s.text(title,16,s.text,true));add(labels,s.text(subtitle,13,s.secondary,false),4,0);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));rows.addView(row);
    }
    private void row(String label,String value,Runnable action,boolean highlighted){
        LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(s.dp(16),s.dp(12),s.dp(12),s.dp(12));row.setMinimumHeight(s.dp(56));
        TextView name=s.text(label,17,s.text,false);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView status=s.text(value,14,highlighted?s.blue:s.secondary,false);status.setPadding(s.dp(8),0,s.dp(8),0);row.addView(status);
        row.addView(new SetupIcon(getContext(),SetupIcon.Kind.CHEVRON,s.muted),new LinearLayout.LayoutParams(s.dp(16),s.dp(16)));
        s.ripple(row,Color.TRANSPARENT,14);row.setFocusable(true);row.setContentDescription(label+(value.isEmpty()?"":", "+value));row.setOnClickListener(v->{feedback();action.run();});rows.addView(row);
    }
    private void divider(){View line=new View(getContext());line.setBackgroundColor(s.line);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,s.dp(.5f));lp.setMarginStart(s.dp(16));lp.setMarginEnd(s.dp(16));rows.addView(line,lp);}
    private void showcasePlay(){showcase.play(false);}
    void foreground(boolean value){foreground=value;if(!value){showcase.stop();title.animate().cancel();title.setAlpha(1);title.setTranslationY(0);}}
    void previewProgress(float progress){showcase.progress(progress);}
    private void feedback(){if(model!=null&&model.haptics())performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);post(()->layoutSize(w,h));}
    private void layoutSize(int w,int h){
        boolean wide=w>=s.dp(760);int width=Math.min(w,s.dp(wide?1040:560));FrameLayout.LayoutParams bp=(FrameLayout.LayoutParams)body.getLayoutParams();bp.width=width;body.setLayoutParams(bp);
        columns.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(wide?0:-1,-2,wide?1.1f:0);preview.setLayoutParams(pp);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(wide?0:-1,-2,wide?1:0);sp.topMargin=wide?0:s.dp(20);sp.setMarginStart(wide?s.dp(24):0);settings.setLayoutParams(sp);
        showcase.getLayoutParams().height=s.dp(wide?320:h<s.dp(830)?150:180);showcase.requestLayout();
        int margin=Math.max(0,(w-s.dp(560))/2);footer.setPadding(margin+s.dp(20),s.dp(12),margin+s.dp(20),s.dp(12));
        int headMargin=Math.max(0,(w-s.dp(1040))/2);header.setPadding(headMargin+s.dp(20),0,headMargin+s.dp(12),0);
    }
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(getContext());v.setOrientation(LinearLayout.VERTICAL);return v;}
    private void add(LinearLayout parent,View view,int top,int bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=s.dp(top);lp.bottomMargin=s.dp(bottom);parent.addView(view,lp);}
    private FrameLayout iconButton(SetupIcon.Kind kind,String label,Runnable action,boolean blue){
        FrameLayout frame=new FrameLayout(getContext());frame.setContentDescription(label);frame.setFocusable(true);s.ripple(frame,Color.TRANSPARENT,16);
        frame.addView(new SetupIcon(getContext(),kind,blue?s.blue:s.text),new FrameLayout.LayoutParams(s.dp(22),s.dp(22),Gravity.CENTER));frame.setOnClickListener(v->action.run());return frame;
    }
}
