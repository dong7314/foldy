"""Build two disposable synthetic APKs; does not install or start anything."""
import os
from pathlib import Path
import subprocess
import zipfile

root = Path(__file__).resolve().parents[2]
sdk = Path(os.environ.get("ANDROID_SDK_ROOT", str(Path.home() / "Library/Android/sdk")))
java = Path(os.environ.get("JAVA_HOME", "/Applications/Android Studio.app/Contents/jbr/Contents/Home"))
build = sdk / "build-tools/36.0.0"
android = sdk / "platforms/android-36/android.jar"
out = root / "measurements/privacy-closing-0.21/probes"
env = dict(os.environ, JAVA_HOME=str(java))

def run(*args):
    subprocess.run([str(a) for a in args], check=True, env=env, stdout=subprocess.DEVNULL)

classes, dex = out / "classes", out / "dex"
classes.mkdir(parents=True, exist_ok=True)
dex.mkdir(parents=True, exist_ok=True)
run(java / "bin/javac", "-source", "17", "-target", "17", "-cp", android, "-d", classes,
    root / "tools/security_probe/ProbeActivity.java")
run(build / "d8", "--lib", android, "--output", dex, *classes.rglob("*.class"))
for variant in ("security", "plain"):
    manifest = (root / "tools/security_probe/AndroidManifest.xml").read_text()
    if variant == "plain":
        manifest = manifest.replace('package="dev.foldy.securityprobe"', 'package="dev.foldy.plainprobe"')
        manifest = manifest.replace('    <uses-permission android:name="android.permission.HIDE_OVERLAY_WINDOWS" />\n', '')
    manifest = manifest.replace('android:name=".ProbeActivity"', 'android:name="dev.foldy.securityprobe.ProbeActivity"')
    path = out / variant
    path.mkdir(exist_ok=True)
    (path / "AndroidManifest.xml").write_text(manifest)
    unsigned, apk = path / "unsigned.apk", path / "probe.apk"
    run(build / "aapt2", "link", "-I", android, "--manifest", path / "AndroidManifest.xml", "-o", unsigned)
    with zipfile.ZipFile(unsigned, "a") as archive:
        archive.write(dex / "classes.dex", "classes.dex")
    run(build / "apksigner", "sign", "--ks", Path.home() / ".android/debug.keystore",
        "--ks-pass", "pass:android", "--out", apk, unsigned)
    print(apk)
