"""Verify both APKs and update the existing release on the disposable emulator."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import time
import zipfile
import xml.etree.ElementTree as ET

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
    apk = ROOT / f"dist/p38-journal-4.1.0-{variant}.apk"
    signature = run(str(TOOLS / "apksigner.bat"), "verify", "--verbose", "--print-certs", str(apk))
    certificate = re.search(r"certificate SHA-256 digest: ([0-9a-f]+)", signature).group(1)
    metadata = run(str(TOOLS / "aapt.exe"), "dump", "badging", str(apk))
    app_id = "dev.p38journal" + (".debug" if variant == "debug" else "")
    assert f"name='{app_id}'" in metadata and "versionCode='5'" in metadata
    assert "sdkVersion:'26'" in metadata and "targetSdkVersion:'36'" in metadata
    assert "android.permission.INTERNET" not in metadata
    with zipfile.ZipFile(apk) as archive:
        assert archive.read("assets/maintenance-catalogue.json") == catalogue
    if variant == "release":
        old = ROOT / ".tools/pre-v5/p38-journal-4.0.0-release.apk"
        previous = run(str(TOOLS / "apksigner.bat"), "verify", "--print-certs", str(old))
        assert certificate in previous
    artifacts.append({"path": apk.relative_to(ROOT).as_posix(), "applicationId": app_id,
                      "bytes": apk.stat().st_size, "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
                      "certificateSha256": certificate, "catalogueMatchesSource": True})

before = adb("shell", "dumpsys", "package", "dev.p38journal")
assert "versionCode=4" in before, "Expected existing original release v4 on the emulator"
installed = adb("install", "-r", str(ROOT / "dist/p38-journal-4.1.0-release.apk"))
assert "Success" in installed
after = adb("shell", "dumpsys", "package", "dev.p38journal")
assert "versionCode=5" in after
adb("shell", "am", "start", "-n", "dev.p38journal/dev.p38journal.MainActivity")
time.sleep(3)
adb("shell", "uiautomator", "dump", "/sdcard/p38-v5-release.xml")
xml = adb("shell", "cat", "/sdcard/p38-v5-release.xml")
assert 'package="dev.p38journal"' in xml and "195.000" in xml and "v4.1.0" in xml and "5" in xml
dest = ROOT / "docs/screenshots"
(dest / "v5-release-home.xml").write_text(xml, encoding="utf-8")
png = subprocess.run(["adb", "-s", "emulator-5554", "exec-out", "screencap", "-p"], capture_output=True, check=True).stdout
(dest / "v5-release-home.png").write_bytes(png)
hierarchy = ET.fromstring(xml)
node = next(n for n in hierarchy.iter("node") if n.get("content-desc") == "Mantenimiento")
x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
time.sleep(1)
adb("shell", "uiautomator", "dump", "/sdcard/p38-v5-release.xml")
xml = adb("shell", "cat", "/sdcard/p38-v5-release.xml")
assert "29 operaciones incluidas" in xml and "33 operaciones incluidas" in xml and "v4.1.0" in xml
(dest / "v5-release-maintenance.xml").write_text(xml, encoding="utf-8")
png = subprocess.run(["adb", "-s", "emulator-5554", "exec-out", "screencap", "-p"], capture_output=True, check=True).stdout
(dest / "v5-release-maintenance.png").write_bytes(png)
report = {"artifacts": artifacts, "releaseUpdate": {"fromVersionCode": 4, "toVersionCode": 5,
          "install": installed.strip(), "uninstalled": False, "dataCleared": False,
          "emulator": "emulator-5554", "renderedNativeHome": True, "renderedNativeMaintenance29And33": True}, "physicalDeviceTested": False}
(ROOT / "docs/v5-release-install-verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
print("PASS: signatures, original release certificate, catalogue bytes, metadata and release 4->5 update without uninstall")
