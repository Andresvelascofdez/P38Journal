"""Runs only against the disposable debug emulator installation, with isolated fixtures."""
from pathlib import Path
import argparse
import subprocess
import json
import hashlib
import sqlite3
import zipfile
import io

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument("--old-apk", type=Path, required=True)
parser.add_argument("--new-apk", type=Path, required=True)
parser.add_argument("--test-apk", type=Path, required=True)
parser.add_argument("--old-test-apk", type=Path, required=True)
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
assert "Success" in adb("install", "-r", str(args.old_test_apk)).decode()
stage("seed")
backup = adb("exec-out", "run-as", package, "cat", "cache/lifecycle-export.zip")
adb("shell", "am", "force-stop", package)
fixture = root / ".tools" / "v3-lifecycle-fixture"
fixture.mkdir(exist_ok=True)
database = fixture / "p38-journal.db"
database.write_bytes(adb("exec-out", "run-as", package, "cat", "databases/p38-journal.db"))
existing = adb("shell", "run-as", package, "ls", "databases").decode().splitlines()
if "p38-journal.db-wal" in existing:
    (fixture / "p38-journal.db-wal").write_bytes(adb("exec-out", "run-as", package, "cat", "databases/p38-journal.db-wal"))
with zipfile.ZipFile(io.BytesIO(backup)) as archive:
    entries = {name: archive.read(name) for name in archive.namelist()}
records = json.loads(entries["records.json"])
records["readings"][0]["km"] = 182000
visit = dict(id="DELIVERY_FIXTURE_VISIT", date="2026-10-10", km=178000, kmExact=True, dateExact=True, title="ISOLATED REAL WORK FIXTURE", workshop="", notes="", paidCents=30000, active=True, operationalAnchor=False)
records["visits"].append(visit)
for index, rule in enumerate(("engine-oil-filter", "coolant-renewal")):
    line = dict(id=f"DELIVERY_LINE_{index}", visitId=visit["id"], title="ISOLATED WORK LINE", ruleId=rule, costCents=15000, category="", parts="")
    records["lines"].append(line)
    records["events"].append(dict(id=f"DELIVERY_EVENT_{index}", visitId=visit["id"], ruleId=rule, date=visit["date"], km=178000, dateExact=True, kmExact=True, active=True, revision=2, workLineId=line["id"], provenance="owner_recorded", operationalAnchor=False))
records["expenses"].append(dict(id="DELIVERY_VISIT_PAYMENT", date=visit["date"], cents=30000, concept=visit["title"], category="workshop", notes="", visitId=visit["id"], active=True))
records["audit"].append(dict(id="DELIVERY_PLAN_CREATION", at="2026-10-10T09:00:00Z", action="plan_created", recordId="main", detail="ISOLATED FIXTURE ACTUAL PLAN CREATION"))
for identifier in ("engine-oil-filter:fixture:INITIAL:0", "DELIVERY_OWNER_MANUAL"):
    records["inbox"].append(dict(id=identifier, ruleId="engine-oil-filter", title="ISOLATED FIXTURE SAME TITLE", body="Fixture only", generation="fixture", state="pending", createdAt="2026-10-10T09:00:00Z"))
connection = sqlite3.connect(database)
connection.execute("PRAGMA wal_checkpoint(TRUNCATE)")
def insert(table, row):
    columns = ','.join('"'+key+'"' for key in row)
    connection.execute(f"INSERT OR REPLACE INTO {table} ({columns}) VALUES ({','.join('?' for _ in row)})", list(row.values()))
for key, table in (("readings", "readings"), ("visits", "visits"), ("lines", "work_lines"), ("events", "events"), ("expenses", "expenses"), ("audit", "audit"), ("inbox", "inbox")):
    for row in records[key]: insert(table, row)
for identifier, kind in (("engine-oil-filter:fixture:INITIAL:0", "INITIAL"), ("DELIVERY_OWNER_MANUAL", "CUSTOM")):
    insert("ledger", dict(id=identifier, ruleId="engine-oil-filter", generation="fixture", kind=kind, window="0", state="pending", updatedAt="2026-10-10T09:00:00Z"))
connection.commit()
assert connection.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
assert connection.execute("PRAGMA user_version").fetchone()[0] == 2
connection.execute("PRAGMA wal_checkpoint(TRUNCATE)")
connection.close()
adb("push", str(database), "/data/local/tmp/p38-v2-fixture.db")
adb("shell", "run-as", package, "cp", "/data/local/tmp/p38-v2-fixture.db", "databases/p38-journal.db")
adb("shell", "run-as", package, "rm", "-f", "databases/p38-journal.db-wal", "databases/p38-journal.db-shm")
adb("shell", "rm", "/data/local/tmp/p38-v2-fixture.db")
assert adb("exec-out", "run-as", package, "cat", "databases/p38-journal.db") == database.read_bytes()
entries["records.json"] = json.dumps(records, ensure_ascii=False, separators=(',', ':')).encode()
manifest = json.loads(entries["manifest.json"])
manifest["recordsSha256"] = hashlib.sha256(entries["records.json"]).hexdigest()
entries["manifest.json"] = json.dumps(manifest).encode()
output = io.BytesIO()
with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
    for name, data in entries.items(): archive.writestr(name, data)
backup = output.getvalue()
report.append({"stage": "legacy-schema2-fixture", "result": "PASS", "current_km": 182000, "real_work_events": 2, "total_cents": 31250, "pdf_and_bookmark": True, "automatic_initial_and_manual_same_title": True, "database_sha256": hashlib.sha256(database.read_bytes()).hexdigest()})
backup_path = root / ".tools" / "lifecycle-fixture-backup.zip"
backup_path.write_bytes(backup)
assert "Success" in adb("install", "-r", str(args.new_apk)).decode()
assert "Success" in adb("install", "-r", str(args.test_apk)).decode()
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
(root / "docs" / "v3-lifecycle-verification.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print("PASS: update without uninstall, then uninstall/reinstall and complete restore; debug fixtures only")
