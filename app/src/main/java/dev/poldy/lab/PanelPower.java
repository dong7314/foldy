package dev.poldy.lab;

import android.os.Binder;
import android.os.IBinder;
import java.lang.reflect.Method;

/** Samsung runtime API verified on SM-F971N. Separate tokens preserve each display's timeout. */
final class PanelPower {
    private static final int STATE_UNKNOWN=0, STATE_OFF=1, STATE_ON=2;
    private final Object displayService;
    private final Method override;
    private final Binder[] tokens={new Binder(),new Binder()};
    PanelPower() throws Exception {
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"display");
        displayService=Class.forName("android.hardware.display.IDisplayManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
        override=Class.forName("android.hardware.display.IDisplayManager").getMethod("setDisplayStateOverrideWithDisplayId",IBinder.class,int.class,int.class,int.class);
    }
    void hold() throws Exception {
        // Parameters verified from this phone's services.jar: token, Display.STATE_ON, ID, timeout ms.
        // Each token expires in the system server and is also linked to this process's death.
        for(int i=0;i<2;i++)override.invoke(displayService,tokens[i],STATE_ON,i,3000);
    }
    void settle() throws Exception {
        // Logical display 0 is the active panel at either physical endpoint. Stop
        // forcing the inactive panel on, while briefly covering state cancellation
        // so the active panel cannot flash before Samsung's normal policy resumes.
        override.invoke(displayService,tokens[1],STATE_OFF,1,3000);
        override.invoke(displayService,tokens[0],STATE_ON,0,3000);
    }
    void keepEndpoint() throws Exception {
        // At a settled endpoint Samsung maps the visible physical panel to display 0
        // and the inactive panel to display 1. Renew both sides until the next fold;
        // each bounded lease still disappears automatically on process death.
        override.invoke(displayService,tokens[0],STATE_ON,0,3000);
        override.invoke(displayService,tokens[1],STATE_OFF,1,3000);
    }
    void release() {
        for(int i=0;i<2;i++)try{override.invoke(displayService,tokens[i],STATE_UNKNOWN,i,0);}catch(Exception ignored){}
    }
    void releaseGracefully() {
        // Hand the inactive output back immediately, but keep the currently mapped
        // default display awake while Samsung finishes restoring its normal policy.
        try{override.invoke(displayService,tokens[1],STATE_UNKNOWN,1,0);}catch(Exception ignored){}
        try{override.invoke(displayService,tokens[0],STATE_ON,0,1500);}catch(Exception ignored){}
    }
}
