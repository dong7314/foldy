import java.lang.reflect.*;
public final class WindowPrivacyApiProbe {
    public static void main(String[] args)throws Exception {
        for(String name:new String[]{"android.view.IWindowManager","android.window.WindowInfosListener","android.window.WindowInfosListener$WindowInfo","android.window.WindowInfosListenerForTest","android.view.InputWindowHandle","android.os.InputConfig","android.window.ScreenCapture$ScreenshotHardwareBuffer"}){
            try{
                Class<?> type=Class.forName(name);System.out.println("TYPE "+name);
                for(Field f:type.getDeclaredFields())System.out.println("FIELD "+f);
                for(Method m:type.getDeclaredMethods()){
                    String n=m.getName().toLowerCase();
                    if(!name.endsWith("IWindowManager")||n.contains("secure")||n.contains("overlay")||n.contains("windowinfo")||n.contains("visiblewindow")||n.contains("listener"))System.out.println("METHOD "+m);
                }
                for(Constructor<?> c:type.getDeclaredConstructors())System.out.println("CTOR "+c);
            }catch(Exception e){System.out.println("UNAVAILABLE "+e.getClass().getSimpleName());}
        }
    }
}
