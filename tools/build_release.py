"""Build and verify locally signed distribution files; never installs, pushes or uploads."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def command(args, **kwargs):
    return subprocess.check_output([str(arg) for arg in args], cwd=ROOT, text=True, **kwargs)


def dex_classes(apk):
    classes = set()
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if not re.fullmatch(r"classes\d*\.dex", name):
                continue
            data = archive.read(name)
            if not data.startswith(b"dex\n"):
                raise RuntimeError("Unsupported DEX format; class exclusions were not verified.")
            read = lambda offset: struct.unpack_from("<I", data, offset)[0]
            strings, types, count, definitions = read(0x3C), read(0x44), read(0x60), read(0x64)
            for index in range(count):
                type_index = read(definitions + index * 32)
                string_index = read(types + type_index * 4)
                offset = read(strings + string_index * 4)
                while data[offset] & 128:
                    offset += 1
                offset += 1
                classes.add(data[offset:data.index(0, offset)].decode("utf-8"))
    return classes


def verify(apk, build_tools):
    signing = command([build_tools / "apksigner", "verify", "--verbose", "--print-certs", apk])
    if "CN=Android Debug" in signing or not re.search(r"Verified using v[23].*: true", signing):
        raise RuntimeError("Release must use a non-debug certificate and modern APK signature.")
    certificate = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]+)", signing)
    if certificate is None:
        raise RuntimeError("Could not verify the signing certificate.")
    badging = command([build_tools / "aapt2", "dump", "badging", apk])
    if "application-debuggable" in badging or "application-testOnly" in badging:
        raise RuntimeError("Debuggable or test-only APK must not be distributed.")
    manifest = command([build_tools / "aapt2", "dump", "xmltree", "--file", "AndroidManifest.xml", apk])
    for flag in ["debuggable", "testOnly", "allowBackup"]:
        if re.search(r"android:" + flag + r"[^\n]*0xffffffff", manifest):
            raise RuntimeError("Unexpected release manifest flag: " + flag)
    permissions = command([build_tools / "aapt2", "dump", "permissions", apk])
    if "android.permission.INTERNET" in permissions:
        raise RuntimeError("Unexpected Internet permission in the app APK.")
    package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if package is None or package[1] != "dev.poldy.lab":
        raise RuntimeError("Unexpected application ID.")
    classes = dex_classes(apk)
    forbidden = [name for name in classes if name.startswith("Landroid/window/")
                 or name == "Landroid/view/InputWindowHandle;"
                 or any(value in name for value in ["RendererBenchmark", "SetupUiBenchmark", "TransitionPreparationBenchmark", "securityprobe"]) ]
    if forbidden:
        raise RuntimeError("Platform stubs or test classes packaged into release: " + str(forbidden))
    for name in ["DisplayControl", "RecoveryGuard", "WindowPrivacyObserver", "ControlCaller"]:
        if "Ldev/poldy/lab/" + name + ";" not in classes:
            raise RuntimeError("Required entry point missing: " + name)
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if "assets/licenses/Pretendard-LICENSE.txt" not in names:
            raise RuntimeError("Bundled font license missing.")
        if any(name.endswith((".p12", ".jks", ".keystore", "release.properties")) for name in names):
            raise RuntimeError("Signing material found in APK.")
    return {
        "applicationId": package[1], "versionCode": int(package[2]), "versionName": package[3],
        "signerCertificateSha256": certificate[1], "debuggable": False,
        "checks": ["APK signature", "non-debug certificate", "non-debug manifest", "no test classes",
                   "no platform replacement classes", "privileged entry points preserved", "font license"],
    }, signing + "\n" + permissions + "\n" + manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", action="store_true", help="Also produce a signed Google Play app bundle.")
    args = parser.parse_args()
    sdk = Path(os.environ.get("ANDROID_SDK_ROOT", str(Path.home() / "Library/Android/sdk")))
    build_tools = sdk / "build-tools/36.0.0"
    # This AGP configuration exposes the shared JVM suite on the debug variant.
    # Release itself is checked by lint and the post-build artifact verifier below.
    tasks = [":app:testDebugUnitTest", ":app:lintRelease", ":app:assembleRelease"]
    if args.bundle:
        tasks.append(":app:bundleRelease")
    subprocess.run([str(ROOT / "gradlew"), *tasks, "--no-configuration-cache", "--console=plain"], cwd=ROOT, check=True)
    source = ROOT / "app/build/outputs/apk/release/app-release.apk"
    report, details = verify(source, build_tools)
    output = ROOT / "dist" / ("foldy-" + report["versionName"])
    output.mkdir(parents=True, exist_ok=True)
    apk = output / ("foldy-" + report["versionName"] + "-release.apk")
    shutil.copy2(source, apk)
    artifacts = [apk]
    if args.bundle:
        bundle = ROOT / "app/build/outputs/bundle/release/app-release.aab"
        java_home = os.environ.get("JAVA_HOME")
        jarsigner = str(Path(java_home) / "bin/jarsigner") if java_home else "jarsigner"
        checked = command([jarsigner, "-verify", str(bundle)], env=dict(os.environ, LC_ALL="C"))
        if "jar verified" not in checked:
            raise RuntimeError("AAB JAR signature was not verified.")
        aab = output / ("foldy-" + report["versionName"] + "-release.aab")
        shutil.copy2(bundle, aab)
        artifacts.append(aab)
    report.update({
        "builtAtUtc": datetime.now(timezone.utc).isoformat(),
        "sourceCommit": command(["git", "rev-parse", "HEAD"]).strip(),
        "sourceHasUncommittedChanges": bool(command(["git", "status", "--porcelain"]).strip()),
        "supportedAnimationDevice": "Samsung SM-F971N / Android 17", "requiresShizuku": True,
        "files": {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in artifacts},
    })
    (output / "release.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    (output / "SHA256SUMS").write_text("".join(digest + "  " + name + "\n" for name, digest in report["files"].items()))
    (output / "verification.txt").write_text(details)
    shutil.copy2(ROOT / "RELEASE.md", output / "INSTALL-ko.md")
    print("\nVerified local distribution files: " + str(output))
    print("Nothing was installed, pushed or uploaded. Private signing files are not included.")


if __name__ == "__main__":
    main()
