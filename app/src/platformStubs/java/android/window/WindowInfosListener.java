package android.window;
import android.util.Pair;
import android.view.InputWindowHandle;
/** Compile-only signatures verified on the supported phone; never packaged. */
public abstract class WindowInfosListener {
    public static final class DisplayInfo {}
    public WindowInfosListener() {}
    public abstract void onWindowInfosChanged(InputWindowHandle[] windows,DisplayInfo[] displays);
    public Pair<InputWindowHandle[],DisplayInfo[]> register(){throw new UnsupportedOperationException();}
    public void unregister(){throw new UnsupportedOperationException();}
}
