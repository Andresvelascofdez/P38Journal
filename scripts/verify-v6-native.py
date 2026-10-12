"""Capture actual native screens and open real notifications on the disposable emulator."""
from pathlib import Path
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parent.parent
dest=root/'docs/screenshots'
dest.mkdir(exist_ok=True)
pkg='dev.p38journal.debug'
runner=pkg+'.test/androidx.test.runner.AndroidJUnitRunner'
runtime_only="--runtime-only" in sys.argv
verify_only="--verify-only" in sys.argv
report=[r for r in json.loads((root/"docs/v6-native-verification.json").read_text(encoding="utf-8")) if "capture" in r and not r["capture"].startswith("notification")] if runtime_only else []
if verify_only: report=json.loads((root/'docs/v6-native-verification.json').read_text(encoding='utf-8'))

def adb(*args):
    return subprocess.run(['adb','-s','emulator-5554',*args],capture_output=True,check=True,timeout=90).stdout

def nodes():
    for _ in range(5):
        if b'dumped to:' in adb('shell','uiautomator','dump','/sdcard/p38-v6-native.xml'):
            return ET.fromstring(adb('shell','cat','/sdcard/p38-v6-native.xml'))
        time.sleep(.5)
    raise AssertionError('Hierarchy did not settle')

def swipe(down=False):
    adb('shell','input','swipe','500','750' if down else '1800','500','1800' if down else '750','300')
    time.sleep(.4)

def tap(text,scroll=False,contains=False):
    for _ in range(15 if scroll else 1):
        tree=nodes()
        parents={child:parent for parent in tree.iter() for child in parent}
        candidates=[]
        for n in reversed(list(tree.iter('node'))):
            values=(n.get('text') or '',n.get('content-desc') or '')
            if text in values or contains and any(text in value for value in values):
                parent=n
                while parent in parents and parent.get('clickable')!='true': parent=parents[parent]
                candidates.append((parent.get('clickable')=='true' and parent.get('enabled')!='false',n))
        for _,n in sorted(candidates,key=lambda candidate:(not candidate[0],int(re.findall(r'\d+',candidate[1].get('bounds'))[1]))):
                x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
                if y2>y1 and x2>x1:
                    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.7)
                    return
        swipe()
    raise AssertionError('Missing native control: '+text)

def capture(name,required=()):
    hierarchy=nodes()
    texts=[(n.get('text') or '')+' '+(n.get('content-desc') or '') for n in hierarchy.iter('node')]
    for value in required: assert any(value in text for text in texts),(name,value,texts)
    (dest/('v6-'+name+'.xml')).write_bytes(adb('shell','cat','/sdcard/p38-v6-native.xml'))
    (dest/('v6-'+name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
    report.append({'capture':name,'requiredNativeText':list(required),'result':'PASS'})

def section(name):
    tap('Más opciones');tap(name,True)

def show(text):
    for _ in range(15):
        if any(text in (n.get('text') or '') for n in nodes().iter('node')): return
        swipe()
    raise AssertionError('Missing native text: '+text)

def wait_for_text(text):
    until=time.monotonic()+20
    while time.monotonic()<until:
        if any(n.get('text')==text for n in nodes().iter('node')): return
        time.sleep(.3)
    raise AssertionError('Native save did not return to expected screen: '+text)

def open_notification_drawer():
    for attempt in range(3):
        try:
            adb('shell','cmd','statusbar','expand-notifications')
        except subprocess.CalledProcessError as error:
            print('Transient statusbar command:', error.stderr.decode('utf-8',errors='replace'))
        time.sleep(.8)
        if any((n.get('resource-id') or '').endswith('expandableNotificationRow') for n in nodes().iter('node')): return
    raise AssertionError('Notification drawer did not expose the posted notifications')

def expand_notification_group():
    for attempt in range(3):
        tree=nodes()
        parents={child:parent for parent in tree.iter() for child in parent}
        target=None
        for n in tree.iter('node'):
            if n.get('resource-id')=='android:id/expand_button_number' and (n.get('text') or '').isdigit() and int(n.get('text'))>1:
                parent=n
                while parent in parents and parent.get('resource-id')!='android:id/expand_button': parent=parents[parent]
                if parent.get('content-desc')=='Expand': target=parent
        if target is None: return
        x1,y1,x2,y2=map(int,re.findall(r'\d+',target.get('bounds')))
        try:
            adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(1)
            return
        except subprocess.CalledProcessError as error:
            if attempt==2:
                raise AssertionError(error.stderr.decode('utf-8',errors='replace')) from error
            time.sleep(.5)

def stage(value):
    out=adb('shell','am','instrument','-w','-e','class','dev.p38journal.platform.UpgradeV6Test','-e','v6stage',value,runner).decode()
    (root/'docs/evidence/v6'/('native-'+value+'.txt')).write_text(out,encoding='utf-8')
    assert 'OK (1 test)' in out,out
    report.append({'stage':value,'result':'PASS'})

assert b'sdk_gphone' in adb('shell','getprop','ro.product.model')
assert b'versionCode=6' in adb('shell','dumpsys','package',pkg)
if verify_only:
    stage('runtime-verify')
    (root/'docs/v6-runtime-notification-state.json').write_bytes(adb('exec-out','run-as',pkg,'cat','cache/v6-runtime-notification-state.json'))
    report.append({'check':'Real Android notification taps open exact deadline/front; UI renewal/review cancel them; wheel use remains20200 and next205200','result':'PASS'})
    (root/'docs/v6-native-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('PASS: completed native route and persisted data; obsolete notification IDs absent; OS group summary distinguished')
    sys.exit(0)
try:
    adb('shell','settings','put','system','font_scale','1.0')
    adb('shell','cmd','uimode','night','no')
    adb('shell','wm','density','480');adb('shell','wm','size','1080x2400')
    if not runtime_only:
        adb('shell','am','start','-n',pkg+'/dev.p38journal.MainActivity');time.sleep(2)
        tap('Inicio')
        for _ in range(3): swipe(True)
        capture('home-updated',('v5.0.0-debug','Actualizar kilómetros'))
        show('14,00');capture('home-consumption',('14,00',))
        tap('Ver repostajes',True);capture('fuel-summary',('14,00','500 km'))
        section('Trabajos y mejoras')
        if any(n.get('text')=='Previstos' for n in nodes().iter('node')):
            tap('Previstos');tap('Realizados')
        else:
            assert any(n.get('text')=='Realizados' for n in nodes().iter('node'))
        show('FIXTURE mejora libre');capture('works-done',('FIXTURE mejora libre','Fecha no conocida'))
        section('Documentación y vencimientos');capture('deadlines-renewed',('FIXTURE documento libre',))
        tap('FIXTURE documento libre');swipe();capture('deadline-history',('Historial de renovaciones',))
        section('Ruedas');tap('Ruedas delanteras');capture('tyres-change',('190.000 km','200.000 km'))
        for width in (320,360,390,430):
            adb('shell','wm','size',str(width*3)+'x2400')
            tap('Inicio')
            for _ in range(3): swipe(True)
            capture(f'home-{width}dp',('v5.0.0-debug',))
            show('Añadir gasto');show('Registrar repostaje')
            capture(f'home-{width}dp-actions',('Añadir gasto','Registrar repostaje'))
        adb('shell','wm','size','960x2400');adb('shell','settings','put','system','font_scale','1.3');adb('shell','cmd','uimode','night','yes')
        tap('Inicio');swipe(True);capture('home-320dp-dark-font130',('v5.0.0-debug',))
        section('Trabajos y mejoras');tap('Añadir trabajo o mejora',True);capture('work-form-320dp-dark-font130',('Previsto',))
        adb('shell','wm','size','1080x2400');adb('shell','settings','put','system','font_scale','1.0');adb('shell','cmd','uimode','night','no')
    adb('shell','pm','grant',pkg,'android.permission.POST_NOTIFICATIONS')
    stage('runtime-seed')
    adb('shell','am','start','-n',pkg+'/dev.p38journal.MainActivity');time.sleep(1)
    open_notification_drawer()
    expand_notification_group()
    capture('notifications-manual-limit',('FIXTURE documento aviso','revisar ruedas delanteras'))
    tap('revisar ruedas delanteras',contains=True)
    capture('notification-opens-front',('Ruedas delanteras','195.200 km','20.200 km'))
    tap('Revisadas',True);tap('Kilómetros reales',True)
    first=next(n for n in nodes().iter('node') if n.get('class')=='android.widget.EditText')
    assert first.get('text')=='', 'Expected new review; historical records must not be overwritten'
    adb('shell','input','text','195200');adb('shell','input','keyevent','4')
    tap('Guardar ruedas',True);tap('Guardar');time.sleep(2)
    capture('tyres-reviewed-keeps-usage',('205.200 km','20.200 km'))
    open_notification_drawer()
    expand_notification_group()
    tap('FIXTURE documento aviso',contains=True)
    capture('notification-opens-deadline',('FIXTURE documento aviso',))
    tap('Registrar renovación',True)
    capture('deadline-renew-confirm',('Confirmar renovación','Nueva fecha real'))
    tap('Confirmar renovación');wait_for_text('Añadir documentación')
    stage('runtime-verify')
    (root/'docs/v6-runtime-notification-state.json').write_bytes(adb('exec-out','run-as',pkg,'cat','cache/v6-runtime-notification-state.json'))
    report.append({'check':'Real Android notification taps open exact deadline/front; UI renewal/review cancel them; wheel use remains20200 and next205200','result':'PASS'})
finally:
    adb('shell','wm','size','reset');adb('shell','wm','density','reset')
    adb('shell','settings','put','system','font_scale','1.0');adb('shell','cmd','uimode','night','no')
    (root/'docs/v6-native-verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('PASS: native new features, four widths/dark font130%, real local notification taps and cancellation')
