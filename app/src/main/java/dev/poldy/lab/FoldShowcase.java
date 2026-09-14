package dev.poldy.lab;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.PathInterpolator;

/** A local, interactive model. Only the sample pixels enter the production fold shader. */
final class FoldShowcase extends View {
    interface Listener { void changed(float progress); }
    private final SetupStyle style;
    private final Paint paint=new Paint(3);
    private final Path clip=new Path();
    private final Matrix matrix=new Matrix();
    private final RectF face=new RectF(0,0,320,510);
    private final float[] source={0,0,320,0,320,510,0,510},destination=new float[8];
    private final Bitmap inside=sample(true),outside=sample(false);
    private FoldRenderer innerRenderer,outerRenderer;
    private final RenderNode innerNode=new RenderNode("setup inner"),outerNode=new RenderNode("setup cover");
    private ValueAnimator animator;
    private Listener listener;
    private float progress=.79f;
    private boolean reduced;

    FoldShowcase(Context context){
        super(context);style=new SetupStyle(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    void listener(Listener value){listener=value;}
    void reduced(boolean value){reduced=value;if(value)stop();invalidate();}
    float progress(){return progress;}
    void progress(float value){stop();setProgress(value);}
    private void setProgress(float value){progress=Math.max(0,Math.min(1,value));if(listener!=null)listener.changed(progress);invalidate();}
    void play(boolean celebration){
        stop();
        if(reduced||!ValueAnimator.areAnimatorsEnabled()){setProgress(1);return;}
        float[] points=celebration?new float[]{progress,.10f,1}:new float[]{progress,0,1};
        animator=ValueAnimator.ofFloat(points);animator.setDuration(celebration?2000:3000);
        animator.setInterpolator(new PathInterpolator(.32f,0,.2f,1));
        animator.addUpdateListener(value->setProgress((float)value.getAnimatedValue()));animator.start();
    }
    void stop(){if(animator!=null){animator.cancel();animator=null;}}
    @Override protected void onDetachedFromWindow(){
        stop();if(innerRenderer!=null){innerRenderer.close();outerRenderer.close();innerRenderer=null;outerRenderer=null;}
        innerNode.discardDisplayList();outerNode.discardDisplayList();super.onDetachedFromWindow();
    }
    @Override protected void onDraw(Canvas canvas){
        if(getWidth()==0||getHeight()==0)return;
        if(innerRenderer==null){innerRenderer=new FoldRenderer();outerRenderer=new FoldRenderer();}
        float w=getWidth(),h=getHeight(),unit=Math.min(w/790f,h/680f);
        float phoneHeight=510*unit,halfWidth=320*unit;
        float cosine=(float)Math.cos(Math.PI*progress),sine=(float)Math.sin(Math.PI*progress);
        float hinge=w/2-(1+Math.min(0,cosine))*halfWidth/2;
        float top=(h-phoneHeight)/2-7*unit,bottom=top+phoneHeight,free=hinge+cosine*halfWidth;

        // Diffused light is behind the phone, never an overlay that obscures controls.
        paint.setShader(new RadialGradient(w*.5f,h*.49f,w*.51f,
            style.dark?new int[]{0x383b77e8,0x164353aa,0}:new int[]{0x507aabff,0x247ab8ff,0},null,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
        paint.setShader(new RadialGradient(w*.51f,0,w*.31f,new int[]{0x35112546,0},null,Shader.TileMode.CLAMP));
        int shadow=canvas.save();canvas.translate(0,h*.91f);canvas.scale(1,.14f);
        canvas.drawCircle(w*.51f,0,w*.31f,paint);canvas.restoreToCount(shadow);paint.setShader(null);

        if(canvas.isHardwareAccelerated()){
            float envelope=reduced?0:1;
            innerNode.setPosition(0,0,640,510);RecordingCanvas in=innerNode.beginRecording(640,510);
            innerRenderer.draw(in,640,510,null,inside,1,FoldOptics.at(true,progress*180),envelope,0);innerNode.endRecording();
            outerNode.setPosition(0,0,320,510);RecordingCanvas out=outerNode.beginRecording(320,510);
            outerRenderer.draw(out,320,510,null,outside,1,FoldOptics.at(false,progress*180),envelope,0);outerNode.endRecording();
        }
        int phone=canvas.save();canvas.rotate(reduced?0:-4,w/2,h/2);
        drawFace(canvas,hinge,top,hinge+halfWidth,top,hinge+halfWidth,bottom,hinge,bottom,true,true);
        if(Math.abs(cosine)>.008f){
            if(cosine>0)drawFace(canvas,hinge,top,free,top-22*sine*unit,free,bottom+22*sine*unit,hinge,bottom,false,false);
            else drawFace(canvas,free,top-22*sine*unit,hinge,top,hinge,bottom,free,bottom+22*sine*unit,true,false);
        }
        paint.setColor(0xffb9c8dc);paint.setStrokeWidth(Math.max(1,2*unit));
        canvas.drawLine(hinge,top+12*unit,hinge,bottom-12*unit,paint);canvas.restoreToCount(phone);
    }
    private void drawFace(Canvas c,float x0,float y0,float x1,float y1,float x2,float y2,float x3,float y3,boolean inner,boolean right){
        destination[0]=x0;destination[1]=y0;destination[2]=x1;destination[3]=y1;
        destination[4]=x2;destination[5]=y2;destination[6]=x3;destination[7]=y3;
        matrix.setPolyToPoly(source,0,destination,0,4);
        int state=c.save();c.concat(matrix);
        paint.setColor(0xff93a5bd);c.drawRoundRect(-5,-5,325,515,31,31,paint);
        paint.setColor(0xff0c1320);c.drawRoundRect(-2,-2,322,512,28,28,paint);
        clip.reset();clip.addRoundRect(face,25,25,Path.Direction.CW);
        int screen=c.save();c.clipPath(clip);
        if(right)c.translate(-320,0);
        if(c.isHardwareAccelerated())c.drawRenderNode(inner?innerNode:outerNode);
        else c.drawBitmap(inner?inside:outside,0,0,paint);
        c.restoreToCount(screen);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);paint.setColor(0x889eb3cb);c.drawRoundRect(face,25,25,paint);paint.setStyle(Paint.Style.FILL);
        c.restoreToCount(state);
    }
    private static Bitmap sample(boolean inner){
        int width=inner?640:320;Bitmap image=Bitmap.createBitmap(width,510,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(image);Paint p=new Paint(3);
        p.setShader(new LinearGradient(0,0,width,510,new int[]{0xff142650,0xff4070bc,0xff8e9be0},null,Shader.TileMode.CLAMP));c.drawPaint(p);
        p.setShader(new RadialGradient(width*.82f,310,350,new int[]{0xffbed8ff,0x907d9df3,0},null,Shader.TileMode.CLAMP));c.drawPaint(p);
        p.setShader(new LinearGradient(0,180,width,480,new int[]{0x003d74da,0x99a4e8ff,0x004069aa},null,Shader.TileMode.CLAMP));
        Path ribbon=new Path();ribbon.moveTo(-80,360);ribbon.cubicTo(width*.3f,90,width*.35f,490,width+90,200);ribbon.lineTo(width+90,330);ribbon.cubicTo(width*.4f,600,width*.3f,210,-80,450);ribbon.close();c.drawPath(ribbon,p);p.setShader(null);
        p.setColor(0xfff6faff);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(12);c.drawText("9:41",22,29,p);
        p.setStrokeWidth(3);for(int i=0;i<3;i++)c.drawLine(width-52+i*5,25,width-52+i*5,20-i*3,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f);c.drawRoundRect(width-31,15,width-15,24,3,3,p);p.setStyle(Paint.Style.FILL);
        p.setTextSize(12);p.setColor(0xffe4edff);c.drawText("MONDAY, SEPTEMBER 14",25,90,p);
        p.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));p.setTextSize(52);c.drawText("A little",23,149,p);c.drawText("more fluid.",23,207,p);
        if(inner){p.setColor(0x24ffffff);c.drawRoundRect(352,80,613,228,22,22,p);p.setTextSize(34);p.setColor(0xfff3f7ff);c.drawText("24°",375,137,p);p.setTextSize(16);c.drawText("A clear kind of day",375,186,p);}
        p.setColor(0x33ffffff);c.drawRoundRect(22,257,width-22,347,22,22,p);
        p.setColor(0xffeaf3ff);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(17);c.drawText("Make room for your day.",40,292,p);
        p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(13);p.setColor(0xffd4e4ff);c.drawText("A smooth start, every time.",40,320,p);
        int count=inner?7:4;float gap=(width-76f)/(count-1);
        int[] colors={0xffd6ebff,0xffafc5ff,0xffd8d1fa,0xffd3f5ee};
        for(int i=0;i<count;i++){
            float x=38+i*gap;p.setColor(colors[i%4]);c.drawRoundRect(x-19,384,x+19,422,12,12,p);
            p.setColor(0xff5b78ac);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);c.drawCircle(x,403,7,p);p.setStyle(Paint.Style.FILL);
        }
        p.setColor(0x33ffffff);c.drawRoundRect(22,449,width-22,489,18,18,p);
        p.setColor(0x99ffffff);c.drawRoundRect(width/2f-39,498,width/2f+39,501,2,2,p);return image;
    }
}
