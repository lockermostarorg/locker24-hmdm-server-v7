#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
MAPPER="$HERE/../../main/java/com/hmdm/plugins/locker24health/persistence/mapper/HealthMapper.java"
CHANGELOG="$HERE/../../main/resources/liquibase/locker24-health.changelog.xml"
NAME="l24-alert-check-$$"
WORK="$(mktemp -d)"

cleanup() {
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT

python3 - "$CHANGELOG" "$MAPPER" "$WORK" <<'PY'
import os, re, sys
import xml.etree.ElementTree as ET
changelog, mapper, work = sys.argv[1:4]
ns = {"l": "http://www.liquibase.org/xml/ns/dbchangelog"}
ddl = []
for cs in ET.parse(changelog).getroot().findall("l:changeSet", ns):
    if cs.get("id").rsplit("-", 1)[1] in ("03", "04", "05", "06", "07"):
        ddl.append(cs.find("l:sql", ns).text)
open(os.path.join(work, "ddl.sql"), "w").write("\n".join(ddl))
src = open(mapper).read()
for m in re.finditer(r'@(?:Insert|Update|Delete|Select)\((.*?)\)\s*\n\s*[\w<>]+\s+(\w+)\(', src, re.S):
    sql = "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1)))
    sql = re.sub(r'#\{(\w+)(?:,[^}]*)?\}', r':\1', sql)
    open(os.path.join(work, m.group(2) + ".sql"), "w").write(sql + ";\n")
PY

docker run -d --name "$NAME" -e POSTGRES_PASSWORD=x postgres:16 >/dev/null
for _ in $(seq 1 60); do
  docker exec "$NAME" pg_isready -U postgres >/dev/null 2>&1 && break
  sleep 1
done
sleep 2

psqlq() { docker exec -i "$NAME" psql -U postgres -v ON_ERROR_STOP=1 -qtA "$@"; }

psqlq <<'SQL'
CREATE TABLE customers (id INT PRIMARY KEY);
CREATE TABLE configurations (id INT PRIMARY KEY, name VARCHAR(100));
CREATE TABLE devices (id INT PRIMARY KEY, number VARCHAR(100), customerId INT, configurationId INT);
INSERT INTO customers VALUES (1);
INSERT INTO configurations VALUES (1, 'hpmo-qa');
INSERT INTO devices VALUES (1, 'M-10', 1, 1), (2, 'M-02', 1, 1), (3, 'M-01', 1, NULL);
SQL
psqlq < "$WORK/ddl.sql"

psqlq <<'SQL'
INSERT INTO locker24_device_health_latest (device_id, customer_id, received_at, measured_at, report_json, kiosk_down_since, kiosk_down_reports)
VALUES (1, 1, 1000, 1000, '{}', 900, 3), (2, 1, 1000, 1000, '{}', NULL, 0), (3, 1, 1000, 1000, '{}', NULL, 0);
SQL

# run NAME [-v k=v ...]: executes the mapper statement NAME and prints its command tag or rows
run() {
  local stmt=$1; shift
  docker exec -i "$NAME" psql -U postgres -v ON_ERROR_STOP=1 -tA "$@" < "$WORK/$stmt.sql"
}
q() { psqlq -c "$1"; }

FAILS=0
expect() {
  local name=$1 want=$2 got=$3
  if [ "$got" = "$want" ]; then
    echo "PASS $name ($got)"
  else
    echo "FAIL $name: want [$want] got [$got]"
    FAILS=$((FAILS + 1))
  fi
}

got="$(run selectEvaluationRows | awk -F'|' '{print $1}' | paste -sd, -)"
expect "evaluation rows only from latest, ordered by device number" "3,2,1" "$got"

got="$(docker exec -i "$NAME" psql -U postgres -A < "$WORK/selectEvaluationRows.sql" | head -1 | tr '|' '\n' | grep -c -E '^(kioskdownsince|kioskdownreports|devicenumber|configurationname|nongreensince|versionmismatchsince|customerid)$')"
expect "evaluation select carries kiosk-down streak, number, config and state columns" "7" "$got"

got="$(run selectEvaluationRows | awk -F'|' '$1==1 {print $15 "/" $16 "/" $NF}')"
expect "device 1 row: kiosk_down_since/reports and configuration name" "900/3/hpmo-qa" "$got"

got="$(run updateEvaluation -v deviceId=1 -v status="'red'" -v reasons="'offline'" -v nonGreenSince=100 -v versionMismatchSince=NULL -v evaluatedAt=200)"
expect "updateEvaluation touches one row" "UPDATE 1" "$got"
got="$(q "SELECT status || '/' || status_reasons || '/' || non_green_since || '/' || COALESCE(version_mismatch_since::text,'null') || '/' || evaluated_at || '/' || kiosk_down_since || '/' || kiosk_down_reports FROM locker24_device_health_latest WHERE device_id = 1")"
expect "evaluation written, kiosk-down streak untouched" "red/offline/100/null/200/900/3" "$got"

alert() { run insertAlert -v deviceId="$1" -v customerId=1 -v condition="'$2'" -v severity="'$3'" -v now="$4"; }
alert_id() { q "SELECT id FROM locker24_device_health_alerts WHERE device_id = $1 AND condition = '$2' AND cleared_at IS NULL"; }

expect "first open offline alert inserted" "INSERT 0 1" "$(alert 1 offline red 1000)"
expect "second open offline alert for the same device skipped" "INSERT 0 0" "$(alert 1 offline red 1060)"
expect "other condition on the same device inserted" "INSERT 0 1" "$(alert 1 battery_low yellow 1000)"
expect "same condition on another device inserted" "INSERT 0 1" "$(alert 2 offline red 1000)"
got="$(q "SELECT raised_at || '/' || last_present_at FROM locker24_device_health_alerts WHERE id = $(alert_id 1 offline)")"
expect "skipped insert left the first row as it was" "1000/1000" "$got"

BID="$(alert_id 1 battery_low)"
expect "escalate yellow -> red" "UPDATE 1" "$(run escalateAlert -v id="$BID" -v now=2000)"
got="$(q "SELECT severity || '/' || last_present_at FROM locker24_device_health_alerts WHERE id = $BID")"
expect "escalated row is red with last_present_at = tick" "red/2000" "$got"
expect "escalating an already red row is a no-op" "UPDATE 0" "$(run escalateAlert -v id="$BID" -v now=2100)"

run touchAlert -v id="$BID" -v now=1500 >/dev/null
expect "touch never moves last_present_at backwards" "2000" "$(q "SELECT last_present_at FROM locker24_device_health_alerts WHERE id = $BID")"
run touchAlert -v id="$BID" -v now=3000 >/dev/null
expect "touch sets last_present_at to the tick" "3000" "$(q "SELECT last_present_at FROM locker24_device_health_alerts WHERE id = $BID")"

expect "clear an open alert" "UPDATE 1" "$(run clearAlert -v id="$BID" -v now=4000)"
expect "clearing twice is a no-op" "UPDATE 0" "$(run clearAlert -v id="$BID" -v now=4100)"
expect "touching a cleared alert is a no-op" "UPDATE 0" "$(run touchAlert -v id="$BID" -v now=4200)"
expect "escalating a cleared alert is a no-op" "UPDATE 0" "$(run escalateAlert -v id="$BID" -v now=4200)"
expect "condition can open again after a clear" "INSERT 0 1" "$(alert 1 battery_low red 5000)"
expect "history of that condition keeps both rows" "2" "$(q "SELECT count(*) FROM locker24_device_health_alerts WHERE device_id = 1 AND condition = 'battery_low'")"

got="$(run selectOpenAlerts | awk -F'|' '{print $2 ":" $4 ":" $5}' | paste -sd, -)"
expect "open alerts exclude cleared rows" "1:offline:red,1:battery_low:red,2:offline:red" "$got"

got="$(run selectSettings | wc -l | tr -d ' ')"
expect "no settings rows -> defaults in code" "0" "$got"
q "INSERT INTO locker24_health_settings (customer_id) VALUES (1)" >/dev/null
expect "settings row defaults to Europe/Sarajevo, no webhook" "1||Europe/Sarajevo" "$(run selectSettings)"

DAY=86400000
NOW=$((1763290060000))
CUTOFF=$((NOW - 30 * DAY))
q "INSERT INTO locker24_device_health_history (device_id, device_ts, received_at, measured_at)
   SELECT 1, g, g, $CUTOFF - g FROM generate_series(1, 7) g
   UNION ALL SELECT 1, 100 + g, 0, $CUTOFF + g - 1 FROM generate_series(1, 3) g" >/dev/null
got=""
for _ in 1 2 3 4; do
  got="$got$(run deleteHistoryBefore -v cutoff="$CUTOFF" -v limit=3 | sed 's/DELETE //'),"
done
expect "history purge runs in bounded batches until short" "3,3,1,0," "$got"
expect "rows at or after the cutoff are kept" "3" "$(q "SELECT count(*) FROM locker24_device_health_history WHERE measured_at >= $CUTOFF")"
expect "rows before the cutoff are gone" "0" "$(q "SELECT count(*) FROM locker24_device_health_history WHERE measured_at < $CUTOFF")"

q "INSERT INTO locker24_device_health_alerts (device_id, customer_id, condition, severity, raised_at, last_present_at, cleared_at) VALUES
   (3, 1, 'offline', 'red', 1, 1, $CUTOFF - 1),
   (3, 1, 'not_charging', 'yellow', 1, 1, $CUTOFF + 1),
   (3, 1, 'storage_low', 'yellow', 1, 1, NULL)" >/dev/null
expect "alerts cleared before the cutoff are purged (the one above and the old offline)" "DELETE 2" "$(run deleteClearedAlertsBefore -v cutoff="$CUTOFF" -v limit=5000)"
expect "alert cleared after the cutoff is kept" "1" "$(q "SELECT count(*) FROM locker24_device_health_alerts WHERE device_id = 3 AND condition = 'not_charging'")"
expect "no cleared alert older than the cutoff remains" "0" "$(q "SELECT count(*) FROM locker24_device_health_alerts WHERE cleared_at < $CUTOFF")"
expect "ancient but still open alert is kept" "1" "$(q "SELECT count(*) FROM locker24_device_health_alerts WHERE device_id = 3 AND condition = 'storage_low'")"

got="$(docker exec -i "$NAME" psql -U postgres -tA -v cutoff="$CUTOFF" -v limit=5000 <<SQL | grep -c locker24_health_history_measured
SET enable_seqscan = off;
EXPLAIN $(cat "$WORK/deleteHistoryBefore.sql")
SQL
)"
expect "history purge can use the measured_at index" "1" "$got"

if [ "$FAILS" -ne 0 ]; then
  echo "$FAILS check(s) failed"
  exit 1
fi
echo "all alert checks passed"
