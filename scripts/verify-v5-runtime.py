"""Native notifications and lifecycle checks on emulator-5554 debug only."""
from pathlib import Path
from datetime import datetime
from zoneinfo import ZoneInfo
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs"
PKG = "dev.p38journal.debug"
resume = "--resume-lifecycle" in sys.argv
report = json.loads((OUT / "v5-runtime-verification.json").read_text(encoding="utf-8")) [:2] if resume else []


def adb(*args):
    return subprocess.run(["adb", "-s", "emulator-5554", *args], capture_output=True, check=True).stdout


def nodes():
    for _ in range(5):
        result = adb("shell", "uiautomator", "dump", "/sdcard/p38-runtime.xml")
        if b"dumped to:" in result:
            return ET.fromstring(adb("shell", "cat", "/sdcard/p38-runtime.xml"))
        time.sleep(1)
    raise AssertionError("Accessibility hierarchy unavailable")


def tap(text, scroll=False, pause=1):
    for _ in range(8 if scroll else 1):
        for node in nodes().iter("node"):
            if node.get("text") == text or node.get("content-desc") == text:
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
                if y2 > y1:
                    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
                    time.sleep(pause)
                    return
        adb("shell", "input", "swipe", "550", "1850", "550", "750", "350")
    raise AssertionError("Control missing: " + text)


def edit_number(value):
    for node in nodes().iter("node"):
        if node.get("class") == "android.widget.EditText" and (not node.get("text") or node.get("text", "").isdigit()):
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
            adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
            adb("shell", "input", "keycombination", "113", "29")
            adb("shell", "input", "text", value)
            adb("shell", "input", "keyevent", "4")
            time.sleep(1)
            return
    raise AssertionError("Reading input missing")


def capture(name):
    (OUT / "screenshots" / ("v5-" + name + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))
    (OUT / "screenshots" / ("v5-" + name + ".xml")).write_bytes(adb("shell", "cat", "/sdcard/p38-runtime.xml"))


def start():
    adb("shell", "am", "start", "-n", PKG + "/dev.p38journal.MainActivity")
    time.sleep(2)


def job_dump(name):
    data = adb("shell", "dumpsys", "jobscheduler")
    (OUT / ("v5-" + name + ".txt")).write_bytes(data)
    return re.findall(r"^\s*JOB [^\n]*?/(\d+):[^\n]*" + re.escape(PKG) + r"/androidx.work", data.decode(), re.MULTILINE)


try:
    adb("shell", "cmd", "connectivity", "airplane-mode", "enable")
    if not resume:
        adb("shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
        start(); tap("M\u00e1s opciones"); tap("Ajustes"); tap("Activar avisos")
        tap("Probar aviso")
        adb("shell", "cmd", "statusbar", "expand-notifications"); time.sleep(1)
        assert any(n.get("text") == "P38 Journal \u00b7 prueba" for n in nodes().iter("node"))
        capture("notification-test-offline")
        tap("P38 Journal \u00b7 prueba")
        assert any(n.get("text") == "Bandeja de avisos" for n in nodes().iter("node"))
        capture("notification-test-destination")
        report.append({"check": "Visible test notification offline and actual PendingIntent destination", "result": "PASS"})
        tap("Inicio")
        tap("Actualizar kil\u00f3metros")
        edit_number("194999")
        tap("Guardar lectura", pause=3)
        tap("Inicio")
        assert any(n.get("text") == "194.999 km" for n in nodes().iter("node"))
        tap("Actualizar kil\u00f3metros")
        edit_number("195000")
        adb("logcat", "-c")
        tap("Guardar lectura", pause=3)
        adb("shell", "input", "keyevent", "3")
        (OUT / "v5-background-activity.txt").write_bytes(adb("shell", "dumpsys", "activity", "activities"))
        (OUT / "v5-background-work.log").write_bytes(adb("logcat", "-d", "-s", "WM-WorkerWrapper", "WM-SystemJobService"))
        adb("shell", "cmd", "statusbar", "expand-notifications"); time.sleep(1)
        assert any(n.get("text") == "P38 \u00b7 mantenimiento pendiente" for n in nodes().iter("node"))
        capture("notification-km-crossing-offline")
        tap("P38 \u00b7 mantenimiento pendiente")
        assert any(n.get("text") == "Detalle de mantenimiento" for n in nodes().iter("node"))
        capture("notification-maintenance-destination")
        report.append({"check": "Manual reading 194999 to195000 produces real DUE notification offline and group PendingIntent", "result": "PASS", "current_km": 195000})
    adb("shell", "input", "keyevent", "3")
    adb("shell", "am", "kill", PKG)
    assert job_dump("jobs-after-process-kill")
    start(); assert any(n.get("package") == PKG for n in nodes().iter("node")); capture("after-process-kill")
    report.append({"check": "Normal process kill retains scheduled jobs and reopening", "result": "PASS"})
    adb("reboot")
    adb("wait-for-device")
    for _ in range(60):
        if adb("shell", "getprop", "sys.boot_completed").strip() == b"1": break
        time.sleep(1)
    else: raise AssertionError("Emulator reboot timeout")
    deadline = time.monotonic() + 90
    while not job_dump("jobs-after-reboot"):
        if time.monotonic() >= deadline: raise AssertionError("WorkManager did not reschedule after boot within 90 seconds")
        time.sleep(2)
    start(); assert any(n.get("package") == PKG for n in nodes().iter("node")); capture("after-reboot")
    report.append({"check": "Actual reboot retains WorkManager jobs; app reopens", "result": "PASS"})
    adb("shell", "am", "force-stop", PKG)
    job_dump("jobs-after-force-stop")
    start(); assert job_dump("jobs-after-force-stop-reopen")
    report.append({"check": "Force-stop then explicit reopen reschedules jobs", "result": "PASS", "limit": "No delivery guarantee while force-stopped"})
finally:
    adb("shell", "cmd", "connectivity", "airplane-mode", "disable")
    (OUT / "v5-runtime-verification.json").write_text(json.dumps(report, indent=2), encoding="utf-8")

print("PASS: visible native offline notifications, destinations, background, process kill, reboot, force-stop recovery")
