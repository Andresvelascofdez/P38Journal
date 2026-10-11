"""Verify both APKs and update the existing release on the disposable emulator."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import time
import zipfile

ROOT = Path(__file__).resolve().parent.parent
TOOLS = Path.home() / "AppData/Local/Android/Sdk/build-tools/36.0.0"


def run(*args):
    result = subprocess.run(args, capture_output=True, check=True)
    return result.stdout.decode("utf-8", errors="replace")


def adb(*args):
    return run("adb", "-s", "emulator-5554", *args)


assert "sdk_gphone" in adb("shell", "getprop", "ro.product.model")
catalogue = (ROOT / "data/maintenance-catalogue.json").read_bytes()
artifacts = []
for variant in ("release", "debug"):
    apk = ROOT / f"dist/p38-journal-4.0.0-{variant}.apk"
    signature = run(str(TOOLS / "apksigner.bat"), "verify", "--verbose", "--print-certs", str(apk))
    certificate = re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", signature).group(1)
    metadata = run(str(TOOLS / "aapt.exe"), "dump", "badging", str(apk))
    app_id = "dev.p38journal" + (".debug" if variant == "debug" else "")
    assert f"name='{app_id}'" in metadata and "versionCode='4'" in metadata
    assert "sdkVersion:'26'" in metadata and "targetSdkVersion:'36'" in metadata
    assert "android.permission.INTERNET" not in metadata
    with zipfile.ZipFile(apk) as archive:
        assert archive.read("assets/maintenance-catalogue.json") == catalogue
    if variant == "release":
        old = ROOT / ".tools/pre-v4/p38-journal-3.0.0-release.apk"
        previous = run(str(TOOLS / "apksigner.bat"), "verify", "--print-certs", str(old))
        assert certificate in previous
    artifacts.append({"path": apk.relative_to(ROOT).as_posix(), "applicationId": app_id,
                      "bytes": apk.stat().st_size, "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
                      "certificateSha256": certificate, "catalogueMatchesSource": True})

before = adb("shell", "dumpsys", "package", "dev.p38journal")
assert "versionCode=3" in before, "Expected existing original release v3 on the emulator"
installed = adb("install", "-r", str(ROOT / "dist/p38-journal-4.0.0-release.apk"))
assert "Success" in installed
after = adb("shell", "dumpsys", "package", "dev.p38journal")
assert "versionCode=4" in after
adb("shell", "am", "start", "-n", "dev.p38journal/dev.p38journal.MainActivity")
time.sleep(3)
adb("shell", "uiautomator", "dump", "/sdcard/p38-v4-release.xml")
xml = adb("shell", "cat", "/sdcard/p38-v4-release.xml")
assert 'package="dev.p38journal"' in xml and "195.000" in xml
dest = ROOT / "docs/screenshots"
(dest / "v4-release-home.xml").write_text(xml, encoding="utf-8")
png = subprocess.run(["adb", "-s", "emulator-5554", "exec-out", "screencap", "-p"], capture_output=True, check=True).stdout
(dest / "v4-release-home.png").write_bytes(png)
report = {"artifacts": artifacts, "releaseUpdate": {"fromVersionCode": 3, "toVersionCode": 4,
          "install": installed.strip(), "uninstalled": False, "dataCleared": False,
          "emulator": "emulator-5554", "renderedNativeHome": True}, "physicalDeviceTested": False}
(ROOT / "docs/v4-release-install-verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print("PASS: signatures, original release certificate, catalogue bytes, metadata and release 3->4 update without uninstall")
