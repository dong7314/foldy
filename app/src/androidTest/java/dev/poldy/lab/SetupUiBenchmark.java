package dev.poldy.lab;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import java.io.*;

/** Test-only presentation states. This runner does not request permission or start the fold service. */
final class SetupUiBenchmark {
    static void run(Instrumentation test,File directory)throws Exception{
        Activity activity=test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        SetupScreen[] ui=new SetupScreen[1];
        test.runOnMainSync(()->{
            ui[0]=new SetupScreen(activity,new SetupScreen.Actions(){public void primary(){}public void back(){}public void settings(){}public void help(){}});
            activity.setContentView(ui[0]);
        });
        for(SetupStage stage:SetupStage.values()){
            test.runOnMainSync(()->{
                ui[0].update(new SetupScreen.Model(stage,stage.ordinal()>=SetupStage.READY.ordinal(),stage==SetupStage.STARTING,"",false,false,false));
                ui[0].previewProgress(stage==SetupStage.EXPERIENCE?.72f:stage==SetupStage.CONNECT?.18f:1);
            });
            SystemClock.sleep(380);test.waitForIdleSync();
            test.runOnMainSync(()->assertVisibleAction(ui[0],stage));
            save(test,directory,stage.name().toLowerCase()+".png");
        }
        // Use the actual localized denial message, not a fabricated successful connection.
        test.runOnMainSync(()->ui[0].update(new SetupScreen.Model(SetupStage.CONNECT,false,false,"권한이 승인되지 않았어요. 연결 버튼으로 다시 시도할 수 있어요.",false,false,false)));
        SystemClock.sleep(380);save(test,directory,"permission-denied.png");
        test.runOnMainSync(()->{ui[0].update(new SetupScreen.Model(SetupStage.READY,true,false,"",false,true,false));ui[0].previewProgress(.4f);});
        SystemClock.sleep(380);save(test,directory,"reduced-motion.png");
        test.runOnMainSync(()->((MainActivity)activity).settings());SystemClock.sleep(380);save(test,directory,"settings.png");
        test.runOnMainSync(activity::finish);
    }
    private static void assertVisibleAction(View view,SetupStage stage){
        String expected=switch(stage){case EXPERIENCE->"설정 시작하기";case CONNECT->"화면 제어 연결";case READY->"애니메이션 켜기";case STARTING,PAUSED,ACTIVE->"애니메이션 끄기";};
        View action=find(view,expected);if(action==null)throw new AssertionError("Missing action "+expected);
        android.graphics.Rect visible=new android.graphics.Rect();
        if(!action.getGlobalVisibleRect(visible)||visible.height()<action.getHeight()-1)throw new AssertionError("Clipped primary action "+expected);
        if(!action.isEnabled())throw new AssertionError("Wrong action availability "+expected);
    }
    private static View find(View view,String text){
        if(view instanceof TextView label&&text.contentEquals(label.getText())&&view.isClickable())return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),text);if(result!=null)return result;}
        return null;
    }
    private static void save(Instrumentation test,File directory,String name)throws IOException{
        Bitmap shot=test.getUiAutomation().takeScreenshot();if(shot==null)throw new IOException("Screenshot unavailable");
        try(FileOutputStream out=new FileOutputStream(new File(directory,name))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}
    }
}
