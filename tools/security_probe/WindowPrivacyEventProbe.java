import android.window.WindowInfosListener;
import android.view.InputWindowHandle;
public final class WindowPrivacyEventProbe extends WindowInfosListener {
    @Override public void onWindowInfosChanged(InputWindowHandle[] windows,DisplayInfo[] displays){
        for(InputWindowHandle w:windows)if("dev.foldy.securityprobe".equals(w.packageName))
            System.out.println("PROBE_WINDOW flags="+Integer.toHexString(w.layoutParamsFlags)+",input="+Integer.toHexString(w.inputConfig)+",alpha="+w.alpha+",display="+w.displayId);
    }
    public static void main(String[] args)throws Exception {
        for(java.lang.reflect.Field f:Class.forName("android.os.InputConfig").getFields())if(f.getType()==int.class)System.out.println("INPUT "+f.getName()+"="+Integer.toHexString(f.getInt(null)));
        WindowPrivacyEventProbe p=new WindowPrivacyEventProbe();
        try {var current=p.register();p.onWindowInfosChanged(current.first,current.second);Thread.sleep(16000);}finally{p.unregister();}
    }
}
