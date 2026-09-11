package dev.poldy.lab;

import android.view.SurfaceControl;
import dev.poldy.lab.CapturedFrame;
import dev.poldy.lab.IHingeAngleListener;

interface IDisplayControl {
    String capabilities() = 0;
    String requestMode(boolean inner) = 1;
    void renew() = 2;
    void reset() = 3;
    void settle(boolean fullyOpen) = 4;
    CapturedFrame captureFrame(boolean inner, in SurfaceControl[] excluded) = 5;
    SurfaceControl[] createScene(boolean inner) = 6;
    void routeScene(boolean inner) = 7;
    String startAngles(IHingeAngleListener listener) = 8;
    void stopAngles() = 9;
    void destroy() = 16777114;
}
