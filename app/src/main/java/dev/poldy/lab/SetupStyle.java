package dev.poldy.lab;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.View;
import android.widget.TextView;

/** Native Android type and surface tokens, inspired by the referenced Toss/Apple hierarchy. */
final class SetupStyle {
    final Context context;
    private static Typeface regular,semibold;
    final boolean dark;
    final int background,surface,text,secondary,muted,line,blue,softBlue,green;
    SetupStyle(Context context){
        this.context=context;
        if(regular==null){Typeface font=context.getResources().getFont(R.font.pretendard);regular=Typeface.create(font,400,false);semibold=Typeface.create(font,600,false);}
        dark=(context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        background=dark?0xff101012:0xfff2f4f6;surface=dark?0xff1b1b1e:0xffffffff;
        text=dark?0xffe7e7ec:0xff191f28;secondary=dark?0xffa0a0aa:0xff6b7684;
        muted=dark?0xff8f9aa9:0xff788494;line=dark?0xff2a2a30:0xfff0f1f3;
        blue=dark?0xff6fa5ff:0xff3182f6;softBlue=dark?0xff202f48:0xffeaf2ff;
        green=dark?0xff72dcb0:0xff12875e;
    }
    int dp(float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    TextView text(String label,float size,int color,boolean bold){
        TextView v=new TextView(context);v.setText(label);v.setTextSize(size);v.setTextColor(color);
        v.setTypeface(bold?semibold:regular);v.setIncludeFontPadding(false);
        v.setLineHeight(Math.round(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,size*(size>=22?1.38f:1.5f),context.getResources().getDisplayMetrics())));
        v.setLetterSpacing(size>=20?-.018f:-.01f);
        v.setLineBreakWordStyle(android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);return v;
    }
    GradientDrawable shape(int color,float radius){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;
    }
    void ripple(View view,int color,float radius){
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(dark?0x22ffffff:0x160b397a),shape(color,radius),shape(0xffffffff,radius)));
    }
}
