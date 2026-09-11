import java.lang.reflect.*;

/** Read-only framework metadata; does not register sensors or change service state. */
public final class AngleApiProbe {
    public static void main(String[] args) {
        for(String name:new String[]{
            "com.samsung.android.gesture.IMotionRecognitionService",
            "com.samsung.android.gesture.SemMotionRecognitionManager",
            "com.samsung.android.hardware.context.ISemContextService",
            "com.samsung.android.hardware.context.SemContextManager",
            "com.samsung.android.hardware.context.SemContext",
            "com.samsung.android.hardware.secinputdev.ISemInputDeviceManager",
            "com.samsung.android.hardware.secinputdev.SemInputDeviceManager",
            "com.samsung.android.contextengine.ISemContextEngineManager",
            "android.hardware.input.IInputManager",
            "android.hardware.SensorManager",
            "android.hardware.SystemSensorManager"}) {
            try {
                Class<?> c=Class.forName(name);System.out.println("CLASS "+name);
                for(Method m:c.getDeclaredMethods()) {
                    if(name.startsWith("android.")&&!m.getName().toLowerCase().matches(".*(fold|hinge|angle|sensor).*"))continue;
                    System.out.println(m);
                }
                for(Field f:c.getDeclaredFields())if(Modifier.isStatic(f.getModifiers())&&Modifier.isPublic(f.getModifiers())) {
                    if(f.getName().toLowerCase().matches(".*(fold|hinge|angle|sensor|device).*"))System.out.println(f+" = "+f.get(null));
                }
            }catch(Throwable e){System.out.println(name+": "+e);}
        }
    }
}
