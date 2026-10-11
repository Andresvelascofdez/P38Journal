"""Exercise the installed native debug screen on the disposable emulator."""
from pathlib import Path
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
DEST = ROOT / 'docs/screenshots'
PKG = 'dev.p38journal.debug'
REPORT = []

def adb(*args):
    return subprocess.run(['adb', '-s', 'emulator-5554', *args], check=True, capture_output=True, timeout=45).stdout

def nodes():
    for _ in range(5):
        data = adb('shell', 'uiautomator', 'dump', '/sdcard/p38-v5-screen.xml')
        if b'dumped to:' in data:
            return ET.fromstring(adb('shell', 'cat', '/sdcard/p38-v5-screen.xml'))
        time.sleep(1)
    raise AssertionError('Native hierarchy did not settle')

def swipe(down=False, slow=False):
    start, end = (1100,1700) if down and slow else (1700,1100) if slow else (700,1850) if down else (1850,700)
    adb('shell', 'input', 'swipe', '500', str(start), '500', str(end), '650' if slow else '250')
    time.sleep(.7 if slow else .35)

def tap(text, scroll=False):
    for _ in range(16 if scroll else 1):
        for node in reversed(list(nodes().iter('node'))):
            if text in (node.get('text'), node.get('content-desc')):
                x1,y1,x2,y2 = map(int, re.findall(r'\d+', node.get('bounds')))
                if y2 > y1:
                    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
                    time.sleep(.6)
                    return
        swipe(slow=True)
    raise AssertionError('Missing native control: ' + text)

def capture(name):
    nodes()
    (DEST / ('v5-' + name + '.xml')).write_bytes(adb('shell', 'cat', '/sdcard/p38-v5-screen.xml'))
    (DEST / ('v5-' + name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))

def plan():
    tap('Mantenimiento')
    for _ in range(4): swipe(True)

def expand_includes():
    for _ in range(16):
        text = {n.get('text') for n in nodes().iter('node')}
        if 'Ocultar qué incluye' in text: return
        if 'Qué incluye' in text:
            tap('Qué incluye')
            return
        swipe(slow=True)
    raise AssertionError('Includes toggle is not accessible')

assert b'sdk_gphone' in adb('shell', 'getprop', 'ro.product.model')
assert b'versionCode=5' in adb('shell', 'dumpsys', 'package', PKG)
rules = json.loads((ROOT / 'data/maintenance-catalogue.json').read_text(encoding='utf-8'))['rules']
try:
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    adb('shell', 'cmd', 'uimode', 'night', 'no')
    adb('shell', 'wm', 'density', '480')
    adb('shell', 'am', 'start', '-n', PKG + '/dev.p38journal.MainActivity')
    time.sleep(2)
    for width in (320,360,390,430):
        adb('shell', 'wm', 'size', str(width*3)+'x2400')
        plan(); capture(f'maintenance-{width}dp-top')
        swipe(); swipe(); capture(f'maintenance-{width}dp-lower')
    adb('shell', 'wm', 'size', '1080x2400')
    for km, count in ((195000,29),(215000,33),(235000,31)):
        expected = {r['title'] for r in rules if r.get('serviceGroup') == 'workshop'
                    and r.get('scheduleVisibility') in ('maintenance','service')
                    and r.get('recurrence') in ('component','phase','phase_b')
                    and r.get('km') and km-175000-(r.get('firstKm') or r['km']) >= 0
                    and (km-175000-(r.get('firstKm') or r['km'])) % r['km'] == 0}
        assert len(expected) == count
        plan(); tap('Revisión a los ' + f'{km:,}'.replace(',','.') + ' km', True)
        for _ in range(10): swipe(True)
        text = {n.get('text') for n in nodes().iter('node')}
        assert f'{count} operaciones incluidas' in text
        capture(f'revision-{km}-counter')
        expand_includes()
        capture(f'revision-{km}-includes-first')
        found = set()
        for _ in range(40):
            found |= {n.get('text') for n in nodes().iter('node')} & expected
            if found == expected: break
            swipe(slow=True)
        assert found == expected, sorted(expected-found)
        capture(f'revision-{km}-includes-last')
        REPORT.append({'dueKm':km,'counter':count,'nativeOperationTitlesFound':len(found),'titles':sorted(found),'result':'PASS'})
    plan(); tap('Revisión a los 195.000 km', True)
    for _ in range(5): swipe(True)
    expand_includes()
    phase = next(r['title'] for r in rules if r['id'] == 'axle-level')
    tap(phase, True); capture('phase-b-operation')
    tap('Editar mantenimiento', True); capture('phase-b-editor')
    tap('Volver'); tap('Volver')
    for _ in range(6): swipe(True)
    assert any(n.get('text') == '29 operaciones incluidas' for n in nodes().iter('node'))
    capture('return-editor-to-same-revision')
    REPORT.append({'check':'Open phase B editor and return operation then same projected revision195000','result':'PASS','dataWritten':False})
    adb('shell', 'wm', 'size', '960x2400')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
    adb('shell', 'cmd', 'uimode', 'night', 'yes')
    plan(); capture('maintenance-320dp-dark-font130')
finally:
    adb('shell', 'wm', 'size', 'reset')
    adb('shell', 'wm', 'density', 'reset')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    adb('shell', 'cmd', 'uimode', 'night', 'no')
    (ROOT / 'docs/v5-maintenance-screen-verification.json').write_text(json.dumps(REPORT,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('PASS: actual native Mantenimiento29/33/31 and all29/33/31 operation titles, four widths and dark130%')
