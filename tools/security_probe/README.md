# Synthetic security probe

This is a separate diagnostic APK, not a Foldy application component. The APK does not request Shizuku, network, screenshot, or storage access. `HIDE_OVERLAY_WINDOWS` is used only to protect its own synthetic window during a compatibility test.

`ProbeActivity` tries a no-op provider call and binding to FoldService from a different UID, then shows a fixed green surface. Intent booleans `secure` and `hide` toggle FLAG_SECURE and hiding application overlays. It never displays user content.

`SecurityCaptureProbe` runs under authorized ADB shell with the installed Foldy APK and the helper dex on CLASSPATH. It calls the product's `NativeFrameCapture` and refuses to sample unless the synthetic test Activity is foreground and the phone is unlocked. It repeats that check after capture, copies to a temporary in-memory bitmap, prints color counts, and recycles both bitmaps. It writes no screenshot.

Modes:

- No arguments: print the framework secure/protected-content policy constants.
- `sample`: inspect 900 pixels of the synthetic green test region.
- `overlay`: briefly draw a synthetic red root SurfaceControl over the test region using the same mechanism as NativeScene, sample it, then remove and release the layer in `finally`.

Build the Activity with Android SDK 36, Java 17, d8 and aapt2 using the adjacent manifest. Sign it as a standalone diagnostic APK. Compile the helper with SDK 36 plus Foldy's compiled Java classes, then dex only the helper class. At runtime include the actual installed Foldy APK before the helper dex jar in CLASSPATH. Verify that the APK under review and the helper's compile-time source match before interpreting results.

Install only if `dev.foldy.securityprobe` is absent. Keep the phone unlocked on the synthetic Activity and run a normal positive control before testing secure or overlay behavior. `SKIP` is inconclusive, not a pass. Run `sample` for `(secure=false, hide=false)`, `(true,false)` and `(false,true)`, then `overlay` for `(false,true)` and `(true,true)`. When done, return to Foldy, uninstall the diagnostic package and remove the helper jar from `/data/local/tmp`. These tools do not change developer options or Foldy settings.

See `research/security-0.20.3.md` for the observed device, artifact hash, results and limits. Measurements belong under the gitignored `measurements/` directory. Do not use real banking or messaging screens as test content.
