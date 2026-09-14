"""Create Foldy's first local release identity. Never replaces an existing signing key."""
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile


def main():
    parent = Path.home() / ".config/foldy"
    target = parent / "signing"
    if target.exists():
        raise SystemExit("Signing directory already exists; reuse it instead of generating another identity.")
    java_home = os.environ.get("JAVA_HOME")
    keytool = str(Path(java_home) / "bin/keytool") if java_home else shutil.which("keytool")
    if not keytool:
        raise SystemExit("Set JAVA_HOME to a JDK before creating the signing key.")
    parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    password = secrets.token_urlsafe(40)
    # Passwords are neither command-line arguments nor printed output.
    env = dict(os.environ, FOLDY_NEW_KEY_PASSWORD=password)
    with tempfile.TemporaryDirectory(prefix=".signing-", dir=parent) as temporary:
        staging = Path(temporary)
        subprocess.run([
            keytool, "-genkeypair", "-keystore", str(staging / "release.p12"),
            "-storetype", "PKCS12", "-alias", "foldy-release", "-keyalg", "RSA",
            "-keysize", "3072", "-sigalg", "SHA256withRSA", "-validity", "10000",
            "-dname", "CN=Foldy", "-storepass:env", "FOLDY_NEW_KEY_PASSWORD",
            "-keypass:env", "FOLDY_NEW_KEY_PASSWORD", "-noprompt",
        ], env=env, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        props = staging / "release.properties"
        store_path = str(target / "release.p12").replace("\\", "\\\\")
        props.write_text(
            f"storeFile={store_path}\nstoreType=PKCS12\nkeyAlias=foldy-release\n"
            f"storePassword={password}\nkeyPassword={password}\n", encoding="utf-8")
        for item in staging.iterdir():
            item.chmod(0o600)
        # mkdir must fail if another invocation created the target in the meantime.
        target.mkdir(mode=0o700)
        for item in staging.iterdir():
            item.rename(target / item.name)
    print(f"Release signing files created outside Git: {target}")
    print("Back up this private directory securely; future APK updates require the same key.")


if __name__ == "__main__":
    main()
