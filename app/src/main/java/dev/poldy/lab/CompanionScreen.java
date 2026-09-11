package dev.poldy.lab;
import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.*;

/** Pre-created content on the other physical panel. */
final class CompanionScreen extends Presentation {
    final PanelSurface surface;
    CompanionScreen(Context context,Display display){
        super(context,display,android.R.style.Theme_Material_NoActionBar);surface=new PanelSurface(getContext());
    }
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);Window window=getWindow();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        WindowManager.LayoutParams p=window.getAttributes();p.width=-1;p.height=-1;
        p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;p.setFitInsetsTypes(0);
        window.setAttributes(p);setContentView(surface);
        surface.post(()->{WindowInsetsController c=window.getDecorView().getWindowInsetsController();if(c!=null)c.hide(WindowInsets.Type.systemBars());});
    }
}
