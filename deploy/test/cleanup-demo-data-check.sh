#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$HERE/../.."
SCRIPT="${CLEANUP_SCRIPT:-$ROOT/deploy/cleanup-demo-data.sh}"
CHANGELOG_DIR="${CHANGELOG_DIR:-$ROOT/server/src/main/resources/liquibase}"
INIT_SQL="$ROOT/install/sql/hmdm_init.en.sql"
LB_IMAGE="liquibase/liquibase:4.8.0"
SUFFIX="$$"
DB="l24-cleanup-$SUFFIX"
NET="l24-cleanup-net-$SUFFIX"
WORK="$(mktemp -d)"

cleanup() {
  docker rm -f "$DB" >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT

docker network create "$NET" >/dev/null
docker run -d --name "$DB" --network "$NET" -e POSTGRES_PASSWORD=x postgres:16 >/dev/null
for _ in $(seq 1 60); do
  docker exec "$DB" pg_isready -U postgres >/dev/null 2>&1 && break
  sleep 1
done
sleep 2

psqlq() { docker exec -i "$DB" psql -U postgres -v ON_ERROR_STOP=1 -qtA "$@"; }
qd() { local d="$1"; shift; psqlq -d "$d" -c "$1"; }

psqlq -d postgres -c "CREATE DATABASE base"
docker run --rm --network "$NET" -v "$CHANGELOG_DIR":/liquibase/changelog "$LB_IMAGE" \
  --changeLogFile=db.changelog.xml --url="jdbc:postgresql://$DB:5432/base" \
  --username=postgres --password=x --contexts=common,shared \
  update -Dfile.resource.url=http://localhost >"$WORK/liquibase.log" 2>&1 \
  || { cat "$WORK/liquibase.log"; exit 1; }

psqlq -d base <<'SQL'
CREATE TABLE plugins (id serial PRIMARY KEY, identifier TEXT, name TEXT, description TEXT);
INSERT INTO plugins (identifier, name, description) VALUES ('images', 'x', 'x');
CREATE TABLE plugin_devicelog_settings (
  id serial PRIMARY KEY, customerId INT NOT NULL REFERENCES customers(id) ON DELETE CASCADE, logsPreservePeriod INT NOT NULL DEFAULT 30);
INSERT INTO plugin_devicelog_settings (customerId) SELECT id FROM customers;
CREATE TABLE plugin_devicelog_settings_rules (
  id serial PRIMARY KEY,
  settingId INT NOT NULL REFERENCES plugin_devicelog_settings(id) ON DELETE CASCADE,
  name VARCHAR(120) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
  applicationId INT NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
  severity TEXT NOT NULL, filter TEXT,
  groupId INT REFERENCES groups(id) ON DELETE CASCADE,
  configurationId INT REFERENCES configurations(id) ON DELETE CASCADE);
CREATE TABLE plugin_devicelog_log (
  id serial PRIMARY KEY, createTime BIGINT, customerId INT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
  deviceId INT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  applicationId INT NOT NULL REFERENCES applications(id) ON DELETE CASCADE, message TEXT);
CREATE TABLE plugin_deviceinfo_settings (
  id serial PRIMARY KEY, customerId INT NOT NULL UNIQUE REFERENCES customers(id) ON DELETE CASCADE,
  dataPreservePeriod INT NOT NULL DEFAULT 30, sendData BOOLEAN NOT NULL DEFAULT FALSE, intervalMins INT NOT NULL DEFAULT 15);
SQL

psqlq -d postgres -c "CREATE DATABASE demo TEMPLATE base"
psqlq -d demo < "$INIT_SQL" >/dev/null

psqlq -d demo <<'SQL'
INSERT INTO applications (pkg, name, showIcon, customerId, system) VALUES
  ('app.locker24.mdm', 'Locker24 MDM', FALSE, 1, FALSE),
  ('com.hpmo.tablet_client', 'HPMO', TRUE, 1, FALSE);
INSERT INTO applicationVersions (applicationId, version, versionCode, url)
  SELECT id, '1.0', 1, 'http://example.com/a.apk' FROM applications WHERE pkg IN ('app.locker24.mdm', 'com.hpmo.tablet_client');
UPDATE applications SET latestVersion = (SELECT v.id FROM applicationVersions v WHERE v.applicationId = applications.id)
  WHERE pkg IN ('app.locker24.mdm', 'com.hpmo.tablet_client');
SQL

DEMO_APPS_BEFORE="$(qd demo "SELECT COUNT(*) FROM applications WHERE id BETWEEN 1 AND 101")"
DEMO_CONFS_BEFORE="$(qd demo "SELECT COUNT(*) FROM configurations WHERE id BETWEEN 1 AND 5")"
FAILED=0
expect() {
  if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1: expected '$2' got '$3'"; FAILED=1; fi
}
expect "seed: 97 demo apps loaded from the installer data" "97" "$DEMO_APPS_BEFORE"
expect "seed: 5 demo configurations loaded" "5" "$DEMO_CONFS_BEFORE"

run_script() {
  local d="$1"; shift
  CLEANUP_PSQL="docker exec -i $DB psql -U postgres" bash "$SCRIPT" --db "$d" "$@"
}

mkreal() {
  psqlq -d "$1" <<'SQL'
INSERT INTO configurations (name, customerId, mainAppId, kioskMode)
  SELECT 'Kiosk - HPMO', 1, v.id, TRUE FROM applicationVersions v JOIN applications a ON a.id = v.applicationId WHERE a.pkg = 'app.locker24.mdm';
INSERT INTO configurationApplications (configurationId, applicationId, applicationVersionId, action)
  SELECT c.id, a.id, v.id, x.action
  FROM configurations c, (VALUES ('app.locker24.mdm', 1), ('com.hpmo.tablet_client', 1)) AS x(pkg, action)
  JOIN applications a ON a.pkg = x.pkg JOIN applicationVersions v ON v.applicationId = a.id
  WHERE c.name = 'Kiosk - HPMO';
INSERT INTO configurationApplications (configurationId, applicationId, applicationVersionId, action)
  SELECT c.id, a.id, a.latestVersion, 2 FROM configurations c, applications a
  WHERE c.name = 'Kiosk - HPMO' AND a.id = 32;
INSERT INTO devices (number, description, lastUpdate, configurationId, customerId, enrollTime)
  SELECT 'M-01', 'pilot', 1700000000000, id, 1, 1700000000000 FROM configurations WHERE name = 'Kiosk - HPMO';
SQL
}

snapshot() {
  qd "$1" "SELECT (SELECT COUNT(*) FROM applications) || '/' || (SELECT COUNT(*) FROM applicationVersions) || '/' || (SELECT COUNT(*) FROM configurations) || '/' || (SELECT COUNT(*) FROM configurationApplications) || '/' || (SELECT COUNT(*) FROM devices)"
}

psqlq -d postgres -c "CREATE DATABASE s1 TEMPLATE demo"
mkreal s1
S1_BEFORE="$(snapshot s1)"

if run_script s1 </dev/null >"$WORK/noyes.out" 2>&1; then
  expect "no tty and no --yes refuses" "refused" "ran"
else
  expect "no tty and no --yes refuses" "refused" "refused"
fi
expect "refusal deletes nothing" "$S1_BEFORE" "$(snapshot s1)"

if run_script s1 --yes >"$WORK/s1.out" 2>&1; then
  echo "PASS s1 run exits 0"
else
  cat "$WORK/s1.out"; echo "FAIL s1 run exits 0"; FAILED=1
fi
cat "$WORK/s1.out"

expect "s1: demo configurations gone" "0" "$(qd s1 "SELECT COUNT(*) FROM configurations WHERE name IN ('Managed Launcher','MIUI (Xiaomi Redmi)','Background (Agent) Mode','Kiosk Mode','Kiosk Mode - Web Page')")"
expect "s1: demo device h0001 gone" "0" "$(qd s1 "SELECT COUNT(*) FROM devices WHERE number = 'h0001'")"
expect "s1: real device M-01 kept" "1" "$(qd s1 "SELECT COUNT(*) FROM devices WHERE number = 'M-01'")"
expect "s1: real configuration kept with its rows" "3" "$(qd s1 "SELECT COUNT(*) FROM configurationApplications ca JOIN configurations c ON c.id = ca.configurationId WHERE c.name = 'Kiosk - HPMO'")"
expect "s1: real main app still set" "t" "$(qd s1 "SELECT mainAppId IS NOT NULL FROM configurations WHERE name = 'Kiosk - HPMO'")"
expect "s1: only the demo app used by the real configuration remains" "com.android.settings" "$(qd s1 "SELECT string_agg(pkg, ',') FROM applications WHERE id BETWEEN 1 AND 101")"
expect "s1: its version remains" "1" "$(qd s1 "SELECT COUNT(*) FROM applicationVersions WHERE applicationId = 32")"
expect "s1: app.locker24.mdm kept" "1" "$(qd s1 "SELECT COUNT(*) FROM applications WHERE pkg = 'app.locker24.mdm'")"
expect "s1: real app kept" "1" "$(qd s1 "SELECT COUNT(*) FROM applications WHERE pkg = 'com.hpmo.tablet_client'")"
expect "s1: demo log rule went with its app" "0" "$(qd s1 "SELECT COUNT(*) FROM plugin_devicelog_settings_rules")"
S1_AFTER="$(snapshot s1)"
run_script s1 --yes >"$WORK/s1b.out" 2>&1 || { cat "$WORK/s1b.out"; FAILED=1; }
expect "s1: second run says nothing to delete" "1" "$(grep -c 'Nothing to delete' "$WORK/s1b.out" || true)"
expect "s1: second run changes nothing" "$S1_AFTER" "$(snapshot s1)"

psqlq -d postgres -c "CREATE DATABASE s2 TEMPLATE demo"
psqlq -d s2 <<'SQL'
UPDATE devices SET lastUpdate = 1700000000000 WHERE number = 'h0001';
INSERT INTO devices (number, lastUpdate, configurationId, oldConfigurationId, customerId, enrollTime)
  VALUES ('T-99', 1700000000000, 4, 2, 1, 1700000000000);
SQL
run_script s2 --yes >"$WORK/s2.out" 2>&1 || { cat "$WORK/s2.out"; FAILED=1; }
expect "s2: enrolled demo device h0001 kept" "1" "$(qd s2 "SELECT COUNT(*) FROM devices WHERE number = 'h0001'")"
expect "s2: configurations used by devices kept (current, previous, h0001)" "1,2,4" "$(qd s2 "SELECT string_agg(id::text, ',' ORDER BY id) FROM configurations WHERE id BETWEEN 1 AND 5")"
expect "s2: main app of a kept configuration kept" "1" "$(qd s2 "SELECT COUNT(*) FROM applications WHERE pkg = 'com.hmdm.launcher'")"
expect "s2: content app of a kept configuration kept" "1" "$(qd s2 "SELECT COUNT(*) FROM applications WHERE pkg = 'com.hmdm.kioskdemo'")"
expect "s2: rows of kept configurations kept" "t" "$(qd s2 "SELECT COUNT(*) > 0 FROM configurationApplications WHERE configurationId IN (1, 2)")"
expect "s2: kept configurations still have main app" "f" "$(qd s2 "SELECT bool_or(mainAppId IS NULL) FROM configurations WHERE id IN (1, 2, 4)")"
expect "s2: device rows untouched" "2" "$(qd s2 "SELECT COUNT(*) FROM devices")"

psqlq -d postgres -c "CREATE DATABASE s3 TEMPLATE demo"
mkreal s3
psqlq -d s3 <<'SQL'
UPDATE configurations SET mainAppId = 10045, contentAppId = 10101 WHERE name = 'Kiosk - HPMO';
DELETE FROM configurationApplications WHERE configurationId = (SELECT id FROM configurations WHERE name = 'Kiosk - HPMO');
INSERT INTO applicationVersions (applicationId, version, versionCode, url) VALUES (86, '9.9', 99, 'http://example.com/l.apk');
INSERT INTO plugin_devicelog_settings_rules (settingId, name, applicationId, severity) VALUES (1, 'Custom rule', 93, 'ERROR');
INSERT INTO configurationApplicationSettings (applicationId, name, type, value, readonly, extrefId, lastUpdate) SELECT 92, 'k', 'STRING', 'v', FALSE, id, 1 FROM configurations WHERE name = 'Kiosk - HPMO';
SQL
run_script s3 --yes >"$WORK/s3.out" 2>&1 || { cat "$WORK/s3.out"; FAILED=1; }
expect "s3: main and content app of a real configuration kept" "com.hmdm.kioskdemo,com.hmdm.launcher" "$(qd s3 "SELECT string_agg(pkg, ',' ORDER BY pkg) FROM applications WHERE pkg IN ('com.hmdm.launcher', 'com.hmdm.kioskdemo')")"
expect "s3: demo app with a user-uploaded version kept" "2" "$(qd s3 "SELECT COUNT(*) FROM applicationVersions WHERE applicationId = 86")"
expect "s3: demo app with a custom log rule kept" "1" "$(qd s3 "SELECT COUNT(*) FROM plugin_devicelog_settings_rules WHERE applicationId = 93")"
expect "s3: demo app with a setting of a kept configuration kept" "1" "$(qd s3 "SELECT COUNT(*) FROM applications WHERE id = 92")"
expect "s3: unrelated demo apps gone" "0" "$(qd s3 "SELECT COUNT(*) FROM applications WHERE id IN (1, 2, 3, 40, 100)")"

cat > "$WORK/pty.py" <<'PY'
import os, pty, sys, time

answer, script, db, container = sys.argv[1:5]
hook = sys.argv[5] if len(sys.argv) > 5 else None
env = dict(os.environ, CLEANUP_PSQL="docker exec -i %s psql -U postgres" % container)
pid, fd = pty.fork()
if pid == 0:
    os.execvpe("bash", ["bash", script, "--db", db], env)
buf = b""
sent = False
while True:
    try:
        chunk = os.read(fd, 4096)
    except OSError:
        break
    if not chunk:
        break
    buf += chunk
    if not sent and b"[y/N]" in buf:
        if hook:
            os.system("docker exec -i %s psql -U postgres -d %s -qtA -c \"%s\" >/dev/null" % (container, db, hook))
        os.write(fd, answer.encode() + b"\n")
        sent = True
_, status = os.waitpid(pid, 0)
sys.stdout.write(buf.decode(errors="replace"))
sys.exit(os.WEXITSTATUS(status))
PY

psqlq -d postgres -c "CREATE DATABASE s6 TEMPLATE demo"
psqlq -d s6 -c "UPDATE settings SET newDeviceConfigurationId = 3"
run_script s6 --yes >"$WORK/s6.out" 2>&1 || { cat "$WORK/s6.out"; FAILED=1; }
expect "s6: default configuration for new devices kept" "3" "$(qd s6 "SELECT string_agg(id::text, ',' ORDER BY id) FROM configurations WHERE id BETWEEN 1 AND 5")"
expect "s6: the plan says why it is kept" "1" "$(grep -c 'keep configuration Background (Agent) Mode (default for new devices)' "$WORK/s6.out" || true)"
expect "s6: its main app is kept" "1" "$(qd s6 "SELECT COUNT(*) FROM applications WHERE pkg = 'com.hmdm.launcher'")"

psqlq -d postgres -c "CREATE DATABASE s7 TEMPLATE demo"
python3 -I "$WORK/pty.py" y "$SCRIPT" s7 "$DB" "INSERT INTO devices (number, lastUpdate, configurationId, customerId, enrollTime) VALUES ('R-1', 1, 3, 1, 1)" >"$WORK/s7.out" 2>&1 && R=0 || R=$?
[ "$R" = 4 ] || cat "$WORK/s7.out"
expect "s7: data changed after the plan aborts apply with exit 4" "4" "$R"
expect "s7: nothing was deleted" "5/97/1" "$(qd s7 "SELECT (SELECT COUNT(*) FROM configurations WHERE id BETWEEN 1 AND 5) || '/' || (SELECT COUNT(*) FROM applications WHERE id BETWEEN 1 AND 101) || '/' || (SELECT COUNT(*) FROM devices WHERE number = 'h0001')")"

psqlq -d postgres -c "CREATE DATABASE s8 TEMPLATE demo"
mkreal s8
psqlq -d s8 <<'SQL'
INSERT INTO plugin_devicelog_log (createTime, customerId, deviceId, applicationId, message)
  SELECT 1, 1, id, 93, 'x' FROM devices WHERE number = 'M-01';
INSERT INTO plugin_devicelog_log (createTime, customerId, deviceId, applicationId, message)
  SELECT 1, 1, id, 92, 'y' FROM devices WHERE number = 'M-01';
SQL
run_script s8 --yes >"$WORK/s8.out" 2>&1 || { cat "$WORK/s8.out"; FAILED=1; }
expect "s8: the plan counts cascaded device log records" "1" "$(grep -c 'also removes 2 device log records' "$WORK/s8.out" || true)"
expect "s8: the log records went with their apps" "0" "$(qd s8 "SELECT COUNT(*) FROM plugin_devicelog_log")"
expect "s8: the plan counts cascaded configuration rows" "1" "$(grep -c 'also removes .* configuration rows' "$WORK/s8.out" || true)"

psqlq -d postgres -c "CREATE DATABASE s9 TEMPLATE demo"
psqlq -d s9 <<'SQL'
INSERT INTO configurationApplications (configurationId, applicationId, applicationVersionId, action)
  SELECT 2, a.id, a.latestVersion, 1 FROM applications a WHERE a.pkg = 'app.locker24.mdm';
INSERT INTO plugin_devicelog_settings_rules (settingId, name, applicationId, severity) VALUES (1, 'Headwind MDM', 91, 'ERROR');
SQL
run_script s9 --yes >"$WORK/s9.out" 2>&1 || { cat "$WORK/s9.out"; FAILED=1; }
expect "s9: demo configuration edited in place is kept" "2" "$(qd s9 "SELECT string_agg(id::text, ',' ORDER BY id) FROM configurations WHERE id BETWEEN 1 AND 5")"
expect "s9: the plan says edited, kept" "1" "$(grep -c 'keep configuration MIUI (Xiaomi Redmi) (edited, kept)' "$WORK/s9.out" || true)"
expect "s9: a rule named like the seeded one on another app still protects it" "1" "$(qd s9 "SELECT COUNT(*) FROM applications WHERE id = 91")"

psqlq -d postgres -c "CREATE DATABASE s4 TEMPLATE base"
psqlq -d s4 <<'SQL'
INSERT INTO configurations (name, customerId) VALUES ('Kiosk Mode', 1);
INSERT INTO devices (id, number, lastUpdate, configurationId, customerId) SELECT 77, 'h0001', 5, id, 1 FROM configurations WHERE name = 'Kiosk Mode';
SQL
if run_script s4 --yes >"$WORK/s4.out" 2>&1; then
  expect "s4: database without demo data says so" "1" "$(grep -c 'No Headwind demo data found (matched by id and name)' "$WORK/s4.out" || true)"
  expect "s4: it counts look-alikes that did not match" "1" "$(grep -c 'did not match: 2' "$WORK/s4.out" || true)"
else
  cat "$WORK/s4.out"; echo "FAIL s4: exit 0 on a clean database"; FAILED=1
fi

psqlq -d postgres -c "CREATE DATABASE s5 TEMPLATE demo"
S5_BEFORE="$(snapshot s5)"
python3 -I "$WORK/pty.py" n "$SCRIPT" s5 "$DB" >"$WORK/s5n.out" 2>&1 && R=0 || R=$?
expect "s5: answering n aborts without deleting" "$S5_BEFORE" "$(snapshot s5)"
expect "s5: the plan is printed before asking" "1" "$(grep -c 'Kiosk Mode - Web Page' "$WORK/s5n.out" || true)"
expect "s5: answering n exits 3" "3" "$R"
python3 -I "$WORK/pty.py" y "$SCRIPT" s5 "$DB" >"$WORK/s5y.out" 2>&1 || { cat "$WORK/s5y.out"; FAILED=1; }
expect "s5: answering y deletes the demo data" "0" "$(qd s5 "SELECT COUNT(*) FROM applications WHERE id BETWEEN 1 AND 101")"

if [ "$FAILED" -ne 0 ]; then
  exit 1
fi
echo "cleanup-demo-data-check: ok"
