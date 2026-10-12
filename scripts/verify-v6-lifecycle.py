"""Actual debug APK5->6 preserving Room3 data, followed by complete ZIP restore twice."""
from pathlib import Path
import hashlib
import json
import subprocess
root=Path(__file__).resolve().parent.parent
pkg='dev.p38journal.debug'
runner=pkg+'.test/androidx.test.runner.AndroidJUnitRunner'
dest=root/'.tools/lifecycle-v6'
dest.mkdir(parents=True,exist_ok=True)
report=[]
def adb(*args):
    result=subprocess.run(['adb','-s','emulator-5554',*args],capture_output=True,timeout=120)
    output=result.stdout.decode('utf-8',errors='replace')
    if result.returncode or 'FAILURES!!!' in output or 'INSTRUMENTATION_FAILED' in output:
        raise RuntimeError(output+result.stderr.decode('utf-8',errors='replace'))
    return result.stdout
def stage(cls,key,value):
    out=adb('shell','am','instrument','-w','-e','class',cls,'-e',key,value,runner).decode()
    assert 'OK (1 test)' in out,out
    report.append({'class':cls,'stage':value,'result':'PASS','instrumentation':out})
def save(name):
    data=adb('exec-out','run-as',pkg,'cat','cache/'+name)
    (dest/name).write_bytes(data)
    report.append({'backup':name,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
    return dest/name
assert b'sdk_gphone' in adb('shell','getprop','ro.product.model')
existing=adb('shell','pm','list','packages',pkg)
if ('package:'+pkg+'\r').encode() in existing or ('package:'+pkg+'\n').encode() in existing:
    assert b'Success' in adb('uninstall',pkg)
assert b'Success' in adb('install',str(root/'.tools/pre-v6/p38-journal-4.1.0-debug.apk'))
assert b'Success' in adb('install','-r',str(root/'.tools/pre-v6/test-v5.apk'))
stage('dev.p38journal.platform.UpgradeV5Test','v5stage','seed-v4')
save('v5-before-update.zip')
assert b'Success' in adb('install','-r',str(root/'dist/p38-journal-5.0.0-debug.apk'))
assert b'Success' in adb('install','-r',str(root/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
adb('shell','pm','grant',pkg,'android.permission.POST_NOTIFICATIONS')
stage('dev.p38journal.platform.UpgradeV6Test','v6stage','verify-update')
backup=save('v6-after-update.zip')
assert b'Success' in adb('uninstall',pkg)
assert b'Success' in adb('install',str(root/'dist/p38-journal-5.0.0-debug.apk'))
assert b'Success' in adb('install','-r',str(root/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
adb('push',str(backup),'/data/local/tmp/p38-v6.zip')
adb('shell','run-as',pkg,'mkdir','-p','cache')
adb('shell','run-as',pkg,'cp','/data/local/tmp/p38-v6.zip','cache/v6-restore.zip')
adb('shell','rm','/data/local/tmp/p38-v6.zip')
stage('dev.p38journal.platform.UpgradeV6Test','v6stage','restore')
(root/'docs/v6-lifecycle-verification.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print('PASS: actual APK4.1->5.0 update preserving records/ledger/PDF/preferences and two complete restores of all new relations')
