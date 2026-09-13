import android.os.IBinder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Read-only inventory of Samsung brightness values and units. */
public final class BrightnessUnitProbe {
    public static void main(String[] args) throws Exception {
        IBinder binder=(IBinder)Class.forName("android.os.ServiceManager")
            .getMethod("getService",String.class).invoke(null,"display");
        Class<?> api=Class.forName("android.hardware.display.IDisplayManager");
        Object displays=Class.forName("android.hardware.display.IDisplayManager$Stub")
            .getMethod("asInterface",IBinder.class).invoke(null,binder);
        Method getBrightness=api.getMethod("getBrightness",int.class);
        Method getByUnit=api.getMethod("getBrightnessByUnit",int.class,int.class);
        Method getInfo=api.getMethod("getBrightnessInfo",int.class);
        for(int display=0;display<2;display++) {
            System.out.println("DISPLAY "+display+" brightness="+getBrightness.invoke(displays,display));
            Object info=getInfo.invoke(displays,display);
            System.out.println("INFO "+info);
            if(info!=null)for(Field field:info.getClass().getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers()))continue;
                try{field.setAccessible(true);System.out.println("  FIELD "+field.getName()+"="+field.get(info));}
                catch(Throwable e){System.out.println("  FIELD "+field.getName()+"=<"+e.getClass().getSimpleName()+">");}
            }
            for(int unit=-1;unit<=8;unit++) {
                try{System.out.println("  UNIT "+unit+"="+getByUnit.invoke(displays,display,unit));}
                catch(Throwable e){System.out.println("  UNIT "+unit+"=<"+root(e)+">");}
            }
        }
        for(String name:new String[]{"android.hardware.display.DisplayManager","android.provider.Settings$System"}) {
            Class<?> type=Class.forName(name);System.out.println("CONSTANTS "+name);
            for(Field field:type.getDeclaredFields())if(field.getName().toUpperCase().contains("BRIGHTNESS")) {
                try{field.setAccessible(true);System.out.println("  "+field.getName()+"="+field.get(null));}
                catch(Throwable ignored){}
            }
        }
        for(String name:new String[]{"com.android.internal.display.BrightnessUtils","com.android.internal.display.BrightnessSynchronizer"}) {
            try {
                Class<?> type=Class.forName(name);System.out.println("METHODS "+name);
                for(Method method:type.getDeclaredMethods())
                    if(method.getName().toLowerCase().contains("brightness")||method.getName().toLowerCase().contains("gamma")||method.getName().toLowerCase().contains("linear"))
                        System.out.println("  "+method);
                try {
                    Method conversion=type.getDeclaredMethod("convertGammaToLinear",float.class);
                    conversion.setAccessible(true);
                    System.out.println("  CONVERT .7248921="+conversion.invoke(null,.7248921f));
                    System.out.println("  CONVERT .745093="+conversion.invoke(null,.745093f));
                }catch(Throwable e){System.out.println("  CONVERT=<"+root(e)+">");}
            }catch(Throwable e){System.out.println("METHODS "+name+"=<"+root(e)+">");}
        }
    }
    private static Throwable root(Throwable e){while(e.getCause()!=null)e=e.getCause();return e;}
}
