"""Runs only against the disposable debug emulator installation, with isolated fixtures."""
from pathlib import Path
import argparse
import subprocess
import json
import hashlib

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument("--old-apk", type=Path, required=True)
parser.add_argument("--new-apk", type=Path, required=True)
parser.add_argument("--test-apk", type=Path, required=True)
args = parser.parse_args()
package = "dev.p38journal.debug"
runner = package + ".test/androidx.test.runner.AndroidJUnitRunner"
report = []

def adb(*command, data=None):
    result = subprocess.run(["adb", "-s", "emulator-5554", *command], input=data, capture_output=True)
    output = result.stdout.decode("utf-8", errors="replace")
    if result.returncode or "FAILURES!!!" in output or "INSTRUMENTATION_FAILED" in output:
        raise RuntimeError(output + result.stderr.decode("utf-8", errors="replace"))
    return result.stdout

def stage(name):
    output = adb("shell", "am", "instrument", "-w", "-e", "class", "dev.p38journal.platform.DeliveryLifecycleTest", "-e", "stage", name, runner).decode("utf-8")
    assert "OK (1 test)" in output, output
    report.append({"stage": name, "result": "PASS", "instrumentation": output})

adb("uninstall", package) if "package:" + package in adb("shell", "pm", "list", "packages", package).decode().splitlines() else None
assert "Success" in adb("install", str(args.old_apk)).decode()
assert "Success" in adb("install", "-r", str(args.test_apk)).decode()
stage("seed")
backup = adb("exec-out", "run-as", package, "cat", "cache/lifecycle-export.zip")
backup_path = root / ".tools" / "lifecycle-fixture-backup.zip"
backup_path.write_bytes(backup)
assert "Success" in adb("install", "-r", str(args.new_apk)).decode()
stage("verify-update")
assert "Success" in adb("uninstall", package).decode()
assert "Success" in adb("install", str(args.new_apk)).decode()
adb("shell", "am", "start", "-n", package + "/dev.p38journal.MainActivity")
adb("shell", "run-as", package, "mkdir", "-p", "cache")
adb("push", str(backup_path), "/data/local/tmp/p38-delivery-fixture.zip")
adb("shell", "run-as", package, "cp", "/data/local/tmp/p38-delivery-fixture.zip", "cache/lifecycle-import.zip")
adb("shell", "rm", "/data/local/tmp/p38-delivery-fixture.zip")
assert adb("exec-out", "run-as", package, "cat", "cache/lifecycle-import.zip") == backup
stage("restore")
report.append({"backup_sha256": hashlib.sha256(backup).hexdigest(), "bytes": len(backup), "scope": "Disposable Android 16 emulator debug only"})
(root / "docs" / "lifecycle-verification.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print("PASS: update without uninstall, then uninstall/reinstall and complete restore; debug fixtures only")
