package dev.poldy.lab;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small original line symbols, independent of SF Symbols or Toss graphic assets. */
final class SetupIcon extends View {
    enum Kind { BRAND, BACK, SETTINGS, CHECK, LINK, MOTION, LOCK, PLAY, CLOSE, CHEVRON }
    private final Kind kind;private final Paint p=new Paint(3);private final Path path=new Path();
    private final int color;
    SetupIcon(Context context,Kind kind,int color){super(context);this.kind=kind;this.color=color;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas c){
        int saved=c.save();float size=Math.min(getWidth(),getHeight());c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/24,size/24);
        p.setColor(color);p.setStrokeWidth(1.75f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);
        path.reset();
        switch(kind){
            case BRAND -> {
                path.moveTo(3.5f,5.6f);path.quadTo(3.5f,4.5f,4.6f,4.893f);
                path.lineTo(10.5f,7);path.lineTo(10.5f,20);path.lineTo(4.6f,17.893f);
                path.quadTo(3.5f,17.5f,3.5f,16.4f);path.close();c.drawPath(path,p);
                path.reset();path.moveTo(13.5f,7);path.lineTo(19.4f,4.893f);
                path.quadTo(20.5f,4.5f,20.5f,5.6f);path.lineTo(20.5f,16.4f);
                path.quadTo(20.5f,17.5f,19.4f,17.893f);path.lineTo(13.5f,20);
                path.close();c.drawPath(path,p);
            }
            case BACK -> {path.moveTo(14.5f,5);path.lineTo(7.5f,12);path.lineTo(14.5f,19);c.drawPath(path,p);}
            case CHEVRON -> {path.moveTo(9,6);path.lineTo(15,12);path.lineTo(9,18);c.drawPath(path,p);}
            case CLOSE -> {c.drawLine(6,6,18,18,p);c.drawLine(18,6,6,18,p);}
            case CHECK -> {path.moveTo(5,12);path.lineTo(10,17);path.lineTo(19,7);c.drawPath(path,p);}
            case LINK -> {c.save();c.rotate(-35,12,12);c.drawRoundRect(3,8,14,16,4,4,p);c.drawRoundRect(10,8,21,16,4,4,p);c.restore();}
            case SETTINGS -> {for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(12+8*(float)Math.cos(a),12+8*(float)Math.sin(a),12+10*(float)Math.cos(a),12+10*(float)Math.sin(a),p);}c.drawCircle(12,12,7.5f,p);c.drawCircle(12,12,2.7f,p);}
            case LOCK -> {c.drawRoundRect(5,10,19,21,3,3,p);c.drawArc(8,3,16,17,180,180,false,p);c.drawLine(12,14,12,17,p);}
            case PLAY -> {path.moveTo(9,5);path.lineTo(19,12);path.lineTo(9,19);path.close();p.setStyle(Paint.Style.FILL);c.drawPath(path,p);}
            case MOTION -> {c.drawArc(4,4,20,20,205,275,false,p);path.moveTo(3,5);path.lineTo(3,11);path.lineTo(9,11);c.drawPath(path,p);}
        }
        c.restoreToCount(saved);
    }
}
