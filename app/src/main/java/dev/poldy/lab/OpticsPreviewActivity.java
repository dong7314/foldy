package dev.poldy.lab;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.hardware.*;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Same GPU optics as the overlay, driven explicitly by a user-controlled test angle. */
public final class OpticsPreviewActivity extends Activity implements SensorEventListener {
    private Preview preview;
    private SensorManager sensors;
    private final FoldAttitude attitude=new FoldAttitude();
    private boolean liveTilt=true;
    private float gx,gy=1,gz;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xff111a20);layout.setPadding(28,20,28,20);
        layout.setOnApplyWindowInsetsListener((v,i)->{Insets bars=i.getInsets(WindowInsets.Type.systemBars());v.setPadding(28+bars.left,20+bars.top,28+bars.right,20+bars.bottom);return i;});
        TextView label=new TextView(this);label.setTextColor(Color.WHITE);label.setTextSize(21);layout.addView(label);
        TextView note=new TextView(this);note.setText("진행도는 아래 손잡이로 고정할 수 있습니다.\n폰을 앞뒤·좌우로 기울여 화면 형태와 음영을 비교하세요.");note.setTextColor(0xffa8c4ce);note.setTextSize(14);layout.addView(note);
        preview=new Preview();layout.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        SeekBar angle=new SeekBar(this);angle.setMax(180);angle.setContentDescription("미리보기 접힘 각도");layout.addView(angle);
        angle.setMinimumHeight(Math.round(48*getResources().getDisplayMetrics().density));
        Switch live=new Switch(this);live.setText("실제 폰 기울기에 반응");live.setTextColor(0xffd9eef6);live.setChecked(true);layout.addView(live);
        TextView tiltLabel=new TextView(this);tiltLabel.setText("기울기 미리보기 · 세움 → 수평으로 눕힘");tiltLabel.setTextColor(0xffa8c4ce);layout.addView(tiltLabel);
        SeekBar tilt=new SeekBar(this);tilt.setMax(100);tilt.setContentDescription("미리보기 수평 기울기");tilt.setMinimumHeight(Math.round(48*getResources().getDisplayMetrics().density));layout.addView(tilt);
        tilt.setEnabled(false);live.setOnCheckedChangeListener((button,checked)->{liveTilt=checked;tilt.setEnabled(!checked);preview.invalidate();});
        tilt.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean user){preview.flatness=value/100f;preview.invalidate();}
            public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}
        });
        angle.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean user){preview.angle=value;preview.invalidate();label.setText(String.format(Locale.ROOT,"각도별 효과 · %d°",value));}
            public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}
        });
        int initial=Math.max(0,Math.min(180,getIntent().getIntExtra("angle",90)));angle.setProgress(initial);
        if(initial==0)label.setText("각도별 효과 · 0°");
        setContentView(layout);
        sensors=getSystemService(SensorManager.class);attitude.begin();
    }
    @Override protected void onResume(){super.onResume();
        for(int type:new int[]{Sensor.TYPE_GRAVITY,Sensor.TYPE_GYROSCOPE}){
            Sensor s=sensors.getDefaultSensor(type);if(s!=null)sensors.registerListener(this,s,20_000);
        }
    }
    @Override protected void onPause(){sensors.unregisterListener(this);super.onPause();}
    @Override public void onSensorChanged(SensorEvent e){
        if(e.values.length<3)return;
        if(e.sensor.getType()==Sensor.TYPE_GRAVITY){
            gx+=(e.values[0]/SensorManager.GRAVITY_EARTH-gx)*.15f;
            gy+=(e.values[1]/SensorManager.GRAVITY_EARTH-gy)*.15f;
            gz+=(Math.abs(e.values[2])/SensorManager.GRAVITY_EARTH-gz)*.15f;
        }else attitude.gyro(e.timestamp,e.values[0],e.values[1],e.values[2]);
        if(liveTilt)preview.postInvalidateOnAnimation();
    }
    @Override public void onAccuracyChanged(Sensor s,int accuracy){}
    private final class Preview extends View {
        private final FoldRenderer cover=new FoldRenderer(),inside=new FoldRenderer();
        private final Bitmap coverImage=sample(624,986),innerImage=sample(1224,924);
        private final Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);
        float angle,flatness;
        Preview(){super(OpticsPreviewActivity.this);label.setColor(0xffb8e7d5);label.setTextSize(30);}
        @Override protected void onDraw(Canvas c){
            int w=getWidth(),h=getHeight();float scale=Math.min((w-20f)/1224,(h-120f)/(986+924));
            attitude.step(SystemClock.uptimeMillis());
            float x=liveTilt?gx:0,y=liveTilt?gy:(float)Math.sqrt(1-flatness*flatness),z=liveTilt?gz:flatness;
            cover.orientation(x,y,z);inside.orientation(x,y,z);
            cover.attitude(liveTilt?attitude.pitch():0,liveTilt?attitude.yaw():0,liveTilt?attitude.roll():0);
            inside.attitude(liveTilt?attitude.pitch():0,liveTilt?attitude.yaw():0,liveTilt?attitude.roll():0);
            c.drawText("외부 · 오른쪽부터 확산",0,36,label);
            int a=c.save();c.translate((w-624*scale)/2,52);c.scale(scale,scale);
            cover.draw(c,624,986,null,coverImage,1,FoldOptics.at(false,angle),1,0);c.restoreToCount(a);
            float top=52+986*scale+45;c.drawText("내부 · 움직이는 왼쪽 면",0,top,label);
            a=c.save();c.translate((w-1224*scale)/2,top+14);c.scale(scale,scale);
            inside.draw(c,1224,924,null,innerImage,1,FoldOptics.at(true,angle),1,0);c.restoreToCount(a);
        }
        void release(){cover.close();inside.close();coverImage.recycle();innerImage.recycle();}
    }
    private static Bitmap sample(int w,int h){
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0,0,w,h,new int[]{0xff147bbb,0xff173653,0xff0b1930},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
        p.setColor(0xffd9eef6);p.setTextSize(40);c.drawText("POLDY",30,64,p);p.setTextSize(22);c.drawText("OPTICAL STUDY / 09",30,104,p);
        float unit=w>800?w/6f:w/3f;
        for(int row=0;row<4;row++)for(int col=0;col<(w>800?6:3);col++){
            float x=24+col*unit,y=145+row*(h-235)/4f;
            p.setColor(new int[]{0xff56ccb8,0xfff1bb67,0xffd492bc,0xff70a8ed}[(row+col)%4]);
            c.drawRoundRect(x,y,x+unit-32,y+unit*.57f,18,18,p);
            p.setColor(0xffe2f2fa);p.setTextSize(20);c.drawText("CARD "+(row*6+col+1),x,y+unit*.57f+30,p);
            p.setStrokeWidth(3);for(int line=0;line<4;line++)c.drawLine(x+12,y+15+line*11,x+unit-52,y+15+line*11,p);
        }
        p.setColor(0xffd9eef6);c.drawRoundRect(25,h-64,w-25,h-25,20,20,p);return b;
    }
    @Override protected void onDestroy(){preview.release();super.onDestroy();}
}
