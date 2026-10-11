"""Actual APK v4 to v5 update and repeated full restore, disposable emulator debug only."""
from pathlib import Path
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parent.parent
PKG = "dev.p38journal.debug"
RUNNER = PKG + ".test/androidx.test.runner.AndroidJUnitRunner"
FIXTURE = ROOT / ".tools" / "lifecycle-v5"
FIXTURE.mkdir(parents=True, exist_ok=True)
REPORT = []


def adb(*args):
    result = subprocess.run(["adb", "-s", "emulator-5554", *args], capture_output=True)
    output = result.stdout.decode("utf-8", errors="replace")
    if result.returncode or "FAILURES!!!" in output or "INSTRUMENTATION_FAILED" in output:
        raise RuntimeError(output + result.stderr.decode("utf-8", errors="replace"))
    return result.stdout


def stage(name):
    output = adb("shell", "am", "instrument", "-w", "-e", "class", "dev.p38journal.platform.UpgradeV5Test", "-e", "v5stage", name, RUNNER).decode()
    assert "OK (1 test)" in output, output
    REPORT.append({"stage": name, "result": "PASS", "instrumentation": output})


def save_backup(name):
    data = adb("exec-out", "run-as", PKG, "cat", "cache/" + name)
    path = FIXTURE / name
    path.write_bytes(data)
    REPORT.append({"backup": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()})
    return path


assert b"emulator-5554" in subprocess.run(["adb", "devices"], capture_output=True, check=True).stdout
assert b"sdk_gphone" in adb("shell", "getprop", "ro.product.model")
TEST_APK = ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
NEW_APK = ROOT / "dist/p38-journal-4.1.0-debug.apk"
OLD_APK = ROOT / ".tools/pre-v5/p38-journal-4.0.0-debug.apk"
assert b"Success" in adb("install", "-r", "-d", str(OLD_APK))
assert b"Success" in adb("install", "-r", str(TEST_APK))
stage("seed-v4")
save_backup("v5-before-update.zip")
assert b"Success" in adb("install", "-r", str(NEW_APK))
adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
stage("verify-update")
backup = save_backup("v5-after-update.zip")
assert b"Success" in adb("uninstall", PKG)
assert b"Success" in adb("install", str(NEW_APK))
assert b"Success" in adb("install", "-r", str(TEST_APK))
adb("shell", "run-as", PKG, "mkdir", "-p", "cache")
adb("push", str(backup), "/data/local/tmp/p38-v5-restore.zip")
adb("shell", "run-as", PKG, "cp", "/data/local/tmp/p38-v5-restore.zip", "cache/v5-restore.zip")
adb("shell", "rm", "/data/local/tmp/p38-v5-restore.zip")
assert adb("exec-out", "run-as", PKG, "cat", "cache/v5-restore.zip") == backup.read_bytes()
stage("restore")
(ROOT / "docs/v5-lifecycle-verification.json").write_text(json.dumps(REPORT, indent=2) + "\n", encoding="utf-8")
print("PASS: actual v4 update preserves records and v4 emitted cycle, new custom/once/disabled rules and repeated ZIP restore")
