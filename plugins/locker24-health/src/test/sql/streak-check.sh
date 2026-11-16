#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
MAPPER="$HERE/../../main/java/com/hmdm/plugins/locker24health/persistence/mapper/HealthMapper.java"
NAME="l24-streak-check-$$"

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

docker run -d --name "$NAME" -e POSTGRES_PASSWORD=x postgres:16 >/dev/null
for _ in $(seq 1 60); do
  docker exec "$NAME" pg_isready -U postgres >/dev/null 2>&1 && break
  sleep 1
done
sleep 2

psqlq() { docker exec -i "$NAME" psql -U postgres -v ON_ERROR_STOP=1 -qtA "$@"; }

psqlq <<'SQL'
CREATE TABLE devices (id INT PRIMARY KEY);
INSERT INTO devices VALUES (1);
CREATE TABLE locker24_device_health_latest (
    device_id INT PRIMARY KEY REFERENCES devices (id) ON DELETE CASCADE,
    customer_id INT NOT NULL,
    received_at BIGINT NOT NULL,
    measured_at BIGINT NOT NULL,
    status VARCHAR(8) NOT NULL DEFAULT 'green',
    status_reasons VARCHAR(256),
    non_green_since BIGINT,
    version_mismatch_since BIGINT,
    evaluated_at BIGINT,
    battery_pct SMALLINT,
    charging BOOLEAN,
    temperature_c REAL,
    free_storage_mb INT,
    rssi_dbm SMALLINT,
    kiosk_required BOOLEAN,
    kiosk_running BOOLEAN,
    kiosk_admin_unlocked BOOLEAN,
    kiosk_app_package VARCHAR(128),
    kiosk_app_version VARCHAR(64),
    agent_version VARCHAR(64),
    locker_short_uid VARCHAR(64),
    manufacturer VARCHAR(64),
    model VARCHAR(64),
    report_json TEXT NOT NULL,
    kiosk_down_since BIGINT,
    kiosk_down_reports INT NOT NULL DEFAULT 0
);
SQL

UPSERT_FILE="$(mktemp)"
python3 - "$MAPPER" > "$UPSERT_FILE" <<'PY'
import re, sys
src = open(sys.argv[1]).read()
m = re.search(r'@Insert\("INSERT INTO locker24_device_health_latest \((.*?)\)\s*\n\s*int upsertLatest', src, re.S)
literal = m.group(0)
parts = re.findall(r'"((?:[^"\\]|\\.)*)"', literal.split('int upsertLatest')[0])
sql = "INSERT INTO locker24_device_health_latest (" + "".join(parts)[len("INSERT INTO locker24_device_health_latest ("):]
sql = re.sub(r'#\{(\w+)(?:,[^}]*)?\}', r':\1', sql)
print(sql + ";")
PY

FAILS=0
STATE=""

upsert() {
  local measured=$1 required=$2 running=$3 admin=$4 fresh=$5 run=$6 runstart=$7 all=$8
  docker exec -i "$NAME" psql -U postgres -v ON_ERROR_STOP=1 -qtA \
    -v deviceId=1 -v customerId=1 -v receivedAt="$measured" -v measuredAt="$measured" \
    -v batteryPct=80 -v charging=true -v temperatureC=30 -v freeStorageMb=5000 -v rssiDbm=-60 \
    -v kioskRequired="$required" -v kioskRunning="$running" -v kioskAdminUnlocked="$admin" \
    -v kioskAppPackage=NULL -v kioskAppVersion=NULL -v agentVersion=NULL -v lockerShortUid=NULL \
    -v manufacturer=NULL -v model=NULL -v reportJson="'{}'" \
    -v freshReports="$fresh" -v kioskDownRun="$run" -v kioskDownRunStart="$runstart" -v kioskDownAll="$all" \
    < "$UPSERT_FILE" >/dev/null
}

state() {
  psqlq -c "SELECT COALESCE(kiosk_down_reports::text,'null') || '/' || COALESCE(kiosk_down_since::text,'null') FROM locker24_device_health_latest WHERE device_id = 1"
}

expect() {
  local name=$1 want=$2 got
  got="$(state)"
  if [ "$got" = "$want" ]; then
    echo "PASS $name ($got)"
  else
    echo "FAIL $name: want $want got $got"
    FAILS=$((FAILS + 1))
  fi
}

reset() { psqlq -c "DELETE FROM locker24_device_health_latest" >/dev/null; }

# args: measured required running admin fresh run runStart allDown
reset
upsert 1000 true false false 1 1 1000 true
expect "one down report -> count 1, not red" "1/1000"

upsert 1060 true false false 0 0 NULL::bigint true
expect "resend with new sent_at (not fresh) -> unchanged" "1/1000"

upsert 2000 true false false 1 1 2000 true
expect "two consecutive down reports -> count 2, since kept" "2/1000"

upsert 3000 true true false 2 1 3000 false
expect "batch [up, down] after a down -> count 1, since = the down" "1/3000"

reset
upsert 1000 true false false 1 1 1000 true
upsert 13000 true false false 12 12 2000 true
expect "12 queued down reports -> count 13 (1 + 12), since = first" "13/1000"

reset
upsert 100000 true false false 12 12 2000 true
expect "12 queued down reports on empty row -> count 12, since = first" "12/2000"

upsert 200000 true true false 1 0 NULL::bigint false
expect "up report resets" "0/null"

upsert 150000 true false false 1 1 150000 true
expect "older down report after an up -> unchanged" "0/null"

upsert 300000 true false false 1 1 300000 true
upsert 400000 true false true 1 0 NULL::bigint false
expect "admin-flagged report resets" "0/null"

rm -f "$UPSERT_FILE"
if [ "$FAILS" -ne 0 ]; then
  echo "$FAILS check(s) failed"
  exit 1
fi
echo "all streak checks passed"
