package android.view;
import android.graphics.Rect;
/** Compile-only signatures verified on the supported phone; never packaged. */
public class InputWindowHandle {
    public int inputConfig,layoutParamsFlags,layoutParamsType,ownerUid,displayId;
    public float alpha;
    public String packageName;
    public String name;
    public final Rect frame=new Rect();
}
