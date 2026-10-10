"""Capture the disposable debug emulator; never operates on a physical phone."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
DEST = ROOT / "docs" / "screenshots"
DEST.mkdir(exist_ok=True)
PACKAGE = "dev.p38journal.debug"


def adb(*args):
    return subprocess.run(["adb", "-s", "emulator-5554", *args], check=True, capture_output=True).stdout


def start():
    adb("shell", "am", "start", "-n", PACKAGE + "/dev.p38journal.MainActivity")
    time.sleep(2)


def nodes():
    for attempt in range(5):
        output = adb("shell", "uiautomator", "dump", "/sdcard/p38-layout.xml")
        if b"dumped to:" in output:
            return ET.fromstring(adb("shell", "cat", "/sdcard/p38-layout.xml"))
        time.sleep(1)
    raise AssertionError("Android accessibility hierarchy did not settle: " + output.decode())


def tap(text):
    for node in nodes().iter("node"):
        if node.get("text") == text or node.get("content-desc") == text:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
            adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
            time.sleep(1)
            return
    raise AssertionError("Missing native UI control: " + text)


def screenshot(name):
    (DEST / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))
    (DEST / (name + ".xml")).write_bytes(adb("shell", "cat", "/sdcard/p38-layout.xml"))


try:
    adb("shell", "settings", "put", "system", "font_scale", "1.0")
    adb("shell", "cmd", "uimode", "night", "no")
    adb("shell", "wm", "density", "480")
    for width in (320, 360, 390, 430):
        adb("shell", "wm", "size", str(width * 3) + "x2400")
        start()
        tap("Inicio"); nodes(); screenshot(f"home-{width}dp-light")
        tap("Plan"); nodes(); screenshot(f"plan-{width}dp-light")
    adb("shell", "wm", "size", "960x2400")
    adb("shell", "settings", "put", "system", "font_scale", "1.3")
    adb("shell", "cmd", "uimode", "night", "yes")
    start(); tap("Inicio"); nodes(); screenshot("home-320dp-dark-font130")
    tap("M\u00e1s"); tap("Ajustes y copias"); nodes(); screenshot("settings-320dp-dark-font130")
finally:
    adb("shell", "wm", "size", "reset")
    adb("shell", "wm", "density", "reset")
    adb("shell", "settings", "put", "system", "font_scale", "1.0")
    adb("shell", "cmd", "uimode", "night", "no")

print("Captured 320/360/390/430 dp and 320 dp dark with 130% font; inspect PNGs separately.")
