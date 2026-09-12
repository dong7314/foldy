import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/** Read-only runtime inventory of display-token and panel-power APIs on the connected phone. */
public final class SurfacePowerApiProbe {
    public static void main(String[] args) throws Exception {
        dump("android.view.SurfaceControl");
        dump("android.view.SurfaceControl$Transaction");
        dump("android.hardware.display.DisplayManagerGlobal");
        dump("android.hardware.display.IDisplayManager");
        dump("android.view.IWindowManager");
        dump("android.app.IActivityTaskManager");
        dump("com.samsung.android.core.IFoldStarManager");
    }

    private static void dump(String name) throws Exception {
        Class<?> type = Class.forName(name);
        System.out.println("CLASS " + name);
        for (Method method : type.getDeclaredMethods()) {
            String signature = method.toGenericString();
            String lower = signature.toLowerCase();
            if (lower.contains("display") || lower.contains("power") || lower.contains("token")
                    || lower.contains("fold") || lower.contains("swap")) {
                System.out.println("METHOD " + Modifier.toString(method.getModifiers()) + " "
                        + method.getName() + Arrays.toString(method.getParameterTypes())
                        + " -> " + method.getReturnType().getTypeName());
            }
        }
        for (Field field : type.getDeclaredFields()) {
            String signature = field.toGenericString().toLowerCase();
            if (signature.contains("display") || signature.contains("power") || signature.contains("token")) {
                System.out.println("FIELD " + field.toGenericString());
            }
        }
    }
}
