package dev.poldy.lab;

import android.os.Binder;
import android.os.IBinder;
import java.lang.reflect.Method;

/** Samsung runtime API verified on SM-F971N. Separate tokens preserve each display's timeout. */
final class PanelPower {
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
        for(int i=0;i<2;i++)override.invoke(displayService,tokens[i],2,i,3000);
    }
    void release() {
        for(int i=0;i<2;i++)try{override.invoke(displayService,tokens[i],0,i,0);}catch(Exception ignored){}
    }
}
