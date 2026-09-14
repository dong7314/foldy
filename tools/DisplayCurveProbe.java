import android.content.res.Resources;
import android.content.res.TypedArray;
import java.lang.reflect.*;

/** Read-only inventory of the firmware's brightness-to-backlight calibration. */
public final class DisplayCurveProbe {
    public static void main(String[] args) throws Exception {
        android.os.Looper.prepareMainLooper();
        Object thread=Class.forName("android.app.ActivityThread").getMethod("systemMain").invoke(null);
        android.content.Context context=(android.content.Context)thread.getClass().getMethod("getSystemContext").invoke(thread);
        context=context.createPackageContext("android",0);
        Resources r=context.getResources();
        for(String name:new String[]{"config_screenBrightnessBacklight","config_screenBrightnessNits",
                "config_screenBrightnessSettingMinimumFloat","config_screenBrightnessSettingMaximumFloat"}) {
            String type=name.contains("Setting")?"dimen":"array";
            int id=r.getIdentifier(name,type,"android");
            System.out.println("RESOURCE "+name+" id="+id);
            if(id==0)continue;
            try {
                if(type.equals("array")) {
                    TypedArray a=r.obtainTypedArray(id);
                    for(int i=0;i<a.length();i++)System.out.println("  "+i+"="+a.getFloat(i,Float.NaN));
                    a.recycle();
                }else System.out.println("  value="+r.getFloat(id));
            }catch(Exception e){System.out.println(e);}
        }
        for(String name:new String[]{"com.android.server.display.DisplayDeviceConfig","com.android.server.display.feature.DisplayManagerFlags"}) {
            try {
                Class<?> c=Class.forName(name);System.out.println("CLASS "+name);
                for(Constructor<?> ctor:c.getDeclaredConstructors())System.out.println(ctor);
                for(Method m:c.getDeclaredMethods())if(Modifier.isStatic(m.getModifiers())||m.getName().matches(".*(Brightness|Backlight|Nits).*"))System.out.println(m);
            }catch(Throwable e){System.out.println(e);}
        }
        Class<?> config=Class.forName("com.android.server.display.DisplayDeviceConfig");
        Class<?> flags=Class.forName("com.android.server.display.feature.DisplayManagerFlags");
        Object curve=config.getConstructor(android.content.Context.class,flags).newInstance(context,flags.getConstructor().newInstance());
        for(String name:new String[]{"loadBrightnessConstraintsFromConfigXml","loadBrightnessMapFromConfigXml"})config.getMethod(name).invoke(curve);
        Field field=config.getDeclaredField("mBrightnessToBacklightSpline");field.setAccessible(true);
        Object spline=field.get(curve);
        Method interpolate=Class.forName("android.util.Spline").getMethod("interpolate",float.class);
        for(float logical:new float[]{0,.1f,.3f,.5f,.7736401f,.80799437f,1}) {
            float backlight=(float)interpolate.invoke(spline,logical);
            System.out.println("CURVE logical="+logical+" backlight="+backlight+" nits="+config.getMethod("getNitsFromBacklight",float.class).invoke(curve,backlight));
        }
        System.exit(0);
    }
}
