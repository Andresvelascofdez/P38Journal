"""Capture consultation, creation and expense/history screens on disposable debug."""
from pathlib import Path
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
PKG = "dev.p38journal.debug"
DEST = ROOT / "docs/screenshots"


def adb(*args):
    return subprocess.run(["adb", "-s", "emulator-5554", *args], check=True, capture_output=True, timeout=30).stdout


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/p38-v4-details.xml")
    return ET.fromstring(adb("shell", "cat", "/sdcard/p38-v4-details.xml"))


def top():
    for _ in range(12):
        adb("shell", "input", "swipe", "500", "650", "500", "1900", "250")
    time.sleep(1)


def tap(text, scroll=False):
    for _ in range(10 if scroll else 1):
        for node in reversed(list(nodes().iter("node"))):
            if node.get("text") == text or node.get("content-desc") == text:
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
                if y2 > y1:
                    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                    time.sleep(1)
                    return
        adb("shell", "input", "swipe", "500", "1800", "500", "750", "300")
    raise AssertionError("Missing native control: " + text)


def capture(name):
    nodes()
    (DEST / ("v4-" + name + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))
    (DEST / ("v4-" + name + ".xml")).write_bytes(adb("shell", "cat", "/sdcard/p38-v4-details.xml"))


assert b"sdk_gphone" in adb("shell", "getprop", "ro.product.model")
adb("shell", "wm", "density", "480")
adb("shell", "wm", "size", "1080x2400")
try:
    adb("shell", "am", "start", "-n", PKG + "/dev.p38journal.MainActivity")
    time.sleep(2)
    tap("Mantenimiento"); top()
    if any(n.get("text") == "Previsión de revisiones futuras" for n in nodes().iter("node")):
        tap("Previsión de revisiones futuras"); tap("Próximos vencimientos reales")
    tap("Revisión a los 195.000 km", scroll=True)
    top()
    capture("group-detail-360dp")
    tap("Registrar realizado", scroll=True)
    capture("visit-new-selection-360dp")
    adb("shell", "input", "swipe", "500", "1800", "500", "750", "300")
    capture("visit-new-unchecked-360dp")
    tap("Volver")
    tap("Mantenimiento"); top(); tap("Revisión a los 195.000 km", scroll=True)
    top()
    if any(n.get("text") == "Qué incluye" for n in nodes().iter("node")):
        tap("Qué incluye", scroll=True)
    catalogue = json.loads((ROOT / "data/maintenance-catalogue.json").read_text(encoding="utf-8"))
    phase = next(rule for rule in catalogue["rules"] if rule["id"] == "transfer-level")
    tap(re.sub(r"^Comprobar\s+", "Comprobación de ", phase["title"]), scroll=True)
    top()
    capture("phase-operation-detail-360dp")
    tap("Editar mantenimiento", scroll=True)
    capture("phase-operation-edit-360dp")
    tap("Mantenimiento"); top(); tap("Crear mantenimiento", scroll=True)
    capture("own-recurring-form-360dp")
    tap("Recurrente", scroll=True); tap("Tarea única")
    capture("own-unique-form-360dp")
    tap("Historial"); top(); capture("history-search-360dp")
    tap("Gastos"); top(); capture("expenses-filters-360dp")
    tap("Todos los meses", scroll=True); tap("Enero")
    tap("Todos los tipos", scroll=True); tap("Taller", scroll=True)
    capture("expenses-filtered-empty-360dp")
    adb("shell", "input", "swipe", "500", "1800", "500", "800", "300")
    capture("expenses-filtered-totals-360dp")
    tap("Gastos"); top(); tap("Limpiar filtros", scroll=True)
finally:
    adb("shell", "wm", "size", "reset")
    adb("shell", "wm", "density", "reset")

print("PASS: native per-operation consultation/edit, own recurring/unique forms, history search and empty combined expense filters captured")
