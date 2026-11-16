#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
MAPPER="$HERE/../../main/java/com/hmdm/plugins/locker24health/persistence/mapper/HealthMapper.java"
CHANGELOG="$HERE/../../main/resources/liquibase/locker24-health.changelog.xml"
NAME="l24-panel-check-$$"
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
consts = {}
for c in re.finditer(r'(?:String|int)\s+([A-Z][A-Z_]+)\s*=\s*(.*?);', src, re.S):
    consts[c.group(1)] = "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', c.group(2))) or c.group(2).strip()
for m in re.finditer(r'@(?:Insert|Update|Delete|Select)\((.*?)\)\s*\n(?:\s*@Options\([^)]*\)\s*\n)?\s*[\w<>]+\s+(\w+)\(', src, re.S):
    parts = []
    for tok in re.finditer(r'"((?:[^"\\]|\\.)*)"|\b([A-Z][A-Z_]+)\b', m.group(1)):
        parts.append(tok.group(1) if tok.group(1) is not None else consts[tok.group(2)])
    sql = "".join(parts)
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
CREATE TABLE devices (id INT PRIMARY KEY, number VARCHAR(100), description VARCHAR(100), customerId INT, configurationId INT);
CREATE TABLE users (id INT PRIMARY KEY, customerId INT, allDevicesAvailable BOOLEAN);
CREATE TABLE groups (id INT PRIMARY KEY, name VARCHAR(100), customerId INT);
CREATE TABLE deviceGroups (id SERIAL PRIMARY KEY, deviceId INT, groupId INT);
CREATE TABLE userDeviceGroupsAccess (id SERIAL PRIMARY KEY, userId INT, groupId INT);
INSERT INTO customers VALUES (1), (2);
INSERT INTO configurations VALUES (1, 'hpmo-qa'), (2, 'other');
INSERT INTO devices VALUES (1, 'M-10', 'ten', 1, 1), (2, 'M-02', NULL, 1, 1), (3, 'M-01', NULL, 1, NULL), (4, 'X-01', NULL, 2, 2), (5, 'M-05', NULL, 1, 2);
INSERT INTO users VALUES (10, 1, TRUE), (11, 1, FALSE), (12, 1, FALSE), (20, 2, TRUE);
INSERT INTO groups VALUES (1, 'Split', 1), (2, 'Zadar', 1), (3, 'Other', 2);
INSERT INTO deviceGroups (deviceId, groupId) VALUES (1, 1), (1, 2), (2, 1), (3, 2), (4, 3);
INSERT INTO userDeviceGroupsAccess (userId, groupId) VALUES (11, 1);
SQL
psqlq < "$WORK/ddl.sql"

NOW=1763290000000
psqlq <<SQL
INSERT INTO locker24_device_health_latest (device_id, customer_id, received_at, measured_at, report_json, status, status_reasons, evaluated_at, battery_pct, charging, model, kiosk_running)
VALUES (1, 1, $NOW, $NOW, '{}', 'red', 'offline,battery_low', $NOW, 12, FALSE, 'SM-X110', FALSE),
       (2, 1, $NOW, $NOW, '{}', 'green', NULL, $NOW, 90, TRUE, 'SM-X110', TRUE),
       (3, 1, $NOW, $NOW, '{}', 'green', NULL, NULL, 50, TRUE, 'Pixel', TRUE),
       (4, 2, $NOW, $NOW, '{}', 'green', NULL, $NOW, 50, TRUE, 'Pixel', TRUE);
SQL

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

fleet() { run selectFleet -v customerId="$1" -v userId="$2" -v configurationId="$3" -v status="$4" -v model="$5" | awk -F'|' '{print $1}' | paste -sd, -; }

expect "all-devices user sees own customer only, ordered by number, never reported included" "3,2,5,1" "$(fleet 1 10 NULL NULL NULL)"
expect "group-limited user sees only devices of granted groups" "2,1" "$(fleet 1 11 NULL NULL NULL)"
expect "user without any grant sees nothing" "" "$(fleet 1 12 NULL NULL NULL)"
expect "other customer's user sees only their own devices" "4" "$(fleet 2 20 NULL NULL NULL)"
expect "customer id mismatch with the user's customer sees nothing" "" "$(fleet 2 10 NULL NULL NULL)"
expect "device in two granted groups is listed once" "2,1" "$(fleet 1 11 NULL NULL NULL)"
expect "configuration filter" "2,1" "$(fleet 1 10 1 NULL NULL)"
expect "status red" "1" "$(fleet 1 10 NULL "'red'" NULL)"
expect "status unknown = never reported or not yet evaluated" "3,5" "$(fleet 1 10 NULL "'unknown'" NULL)"
expect "status green" "2" "$(fleet 1 10 NULL "'green'" NULL)"
expect "model filter" "2,1" "$(fleet 1 10 NULL NULL "'SM-X110'")"
expect "model + status" "2" "$(fleet 1 10 NULL "'green'" "'SM-X110'")"

got="$(run selectFleet -v customerId=1 -v userId=10 -v configurationId=NULL -v status=NULL -v model=NULL | awk -F'|' '$1==1 {print $6 "/" $9 "/" $10 "/" $11}')"
expect "device 1 row: groups joined, status, reasons, battery" "Split, Zadar/red/offline,battery_low/12" "$got"
got="$(run selectFleet -v customerId=1 -v userId=10 -v configurationId=NULL -v status=NULL -v model=NULL | awk -F'|' '$1==5 {print $9 "/" $8 "/" $11}')"
expect "never-reported device: unknown, no lastSeen, no battery" "unknown//" "$got"

access() { run selectDeviceAccess -v customerId="$1" -v userId="$2" -v deviceId="$3"; }
expect "device access: all-devices user" "1" "$(access 1 10 1)"
expect "device access: granted group" "2" "$(access 1 11 2)"
expect "device access: group user, device in other group only" "" "$(access 1 11 3)"
expect "device access: other customer's device" "" "$(access 1 10 4)"
expect "device access: unknown device" "" "$(access 1 10 99)"

M=60000
q "INSERT INTO locker24_device_health_history (device_id, device_ts, received_at, measured_at, battery_pct, charging, temperature_c, rssi_dbm, kiosk_running) VALUES
   (1, 1, 1000, 1000, 50, TRUE, 30, -60, TRUE),
   (1, 2, 2000, 2000, 49, TRUE, 31, -61, TRUE),
   (1, 3, 3000, 3000, 48, TRUE, 32, -62, TRUE),
   (1, 4, 4000, 4000 - 172800000, 47, TRUE, 33, -63, TRUE),
   (1, 5, 4000, 4000 - 172800000 + 1000, 46, TRUE, 33, -63, TRUE),
   (1, 6, 9000, 9000, 40, TRUE, 33, -63, TRUE),
   (2, 1, 2000, 2000, 90, TRUE, 30, -60, TRUE)" >/dev/null

hp() { run selectHistoryPoints -v deviceId="$1" -v from="$2" -v to="$3" -v maxAgeMs=172800000 | awk -F'|' '{print $1}' | paste -sd, -; }
expect "history points newest first, in range" "3000,2000,1000" "$(hp 1 1000 3000)"
expect "history points exclude clock-clamped (1970) reports, keep a 47.99h-old one" "9000,3000,2000,1000,-172795000" "$(hp 1 -999999999999 10000)"
expect "history points are per device" "2000" "$(hp 2 0 10000)"
got="$(run selectHistoryPoints -v deviceId=1 -v from=1000 -v to=1000 -v maxAgeMs=172800000)"
expect "history point columns" "1000|50|t|30|-60|t" "$got"

rt() { run selectReceivedTimes -v deviceId="$1" -v from="$2" -v to="$3" | paste -sd, -; }
expect "received times are distinct and ascending" "1000,2000,3000,4000,9000" "$(rt 1 0 10000)"
expect "received times respect the range" "2000,3000" "$(rt 1 2000 3000)"
expect "last received before" "3000" "$(run selectLastReceivedBefore -v deviceId=1 -v before=4000)"
expect "last received before the first is null" "" "$(run selectLastReceivedBefore -v deviceId=1 -v before=1000)"

expect "no settings row" "" "$(run selectCustomerSettings -v customerId=1)"
up() { run upsertCustomerSettings -v customerId=1 -v webhookUrl="$1" -v timeZone="$2" -v setWebhook="$3" -v setTimeZone="$4"; }
expect "first save inserts with the given values" "INSERT 0 1" "$(up "'https://x.example/h'" "'Europe/Zagreb'" true true)"
expect "settings view hides the url: set + zone" "t|Europe/Zagreb" "$(run selectCustomerSettings -v customerId=1)"
up NULL "'Europe/Sarajevo'" false true >/dev/null
expect "absent webhook keeps the stored url, zone changes" "https://x.example/h/Europe/Sarajevo" "$(q "SELECT webhook_url || '/' || time_zone FROM locker24_health_settings WHERE customer_id = 1")"
up NULL NULL false false >/dev/null
expect "nothing set changes nothing" "https://x.example/h/Europe/Sarajevo" "$(q "SELECT webhook_url || '/' || time_zone FROM locker24_health_settings WHERE customer_id = 1")"
up NULL NULL true false >/dev/null
expect "set webhook to null clears it" "t" "$(q "SELECT webhook_url IS NULL FROM locker24_health_settings WHERE customer_id = 1")"
expect "view after clear: not set" "f|Europe/Sarajevo" "$(run selectCustomerSettings -v customerId=1)"
up "'https://y.example/h'" NULL true false >/dev/null
expect "webhook set again, zone untouched" "https://y.example/h/Europe/Sarajevo" "$(q "SELECT webhook_url || '/' || time_zone FROM locker24_health_settings WHERE customer_id = 1")"
q "DELETE FROM locker24_health_settings" >/dev/null
up "'https://z.example/h'" NULL true false >/dev/null
expect "insert without a zone falls back to Europe/Sarajevo" "Europe/Sarajevo" "$(q "SELECT time_zone FROM locker24_health_settings WHERE customer_id = 1")"
q "INSERT INTO customers VALUES (3)" >/dev/null
expect "settings are per customer" "" "$(run selectCustomerSettings -v customerId=3)"

if [ "$FAILS" -ne 0 ]; then
  echo "$FAILS check(s) failed"
  exit 1
fi
echo "all panel checks passed"
