#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
CHANGELOG_DIR="$HERE/../../main/resources/liquibase"
APP_MAPPER="$HERE/../../../../common/src/main/java/com/hmdm/persistence/mapper/ApplicationMapper.java"
CONF_MAPPER="$HERE/../../../../common/src/main/java/com/hmdm/persistence/mapper/ConfigurationMapper.java"
LB_IMAGE="liquibase/liquibase:4.8.0"
SUFFIX="$$"
DB="l24-release-move-$SUFFIX"
NET="l24-release-move-net-$SUFFIX"
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
q() { psqlq -c "$1"; }

docker run --rm --network "$NET" -v "$CHANGELOG_DIR":/liquibase/changelog "$LB_IMAGE" \
  --changeLogFile=db.changelog.xml --url="jdbc:postgresql://$DB:5432/postgres" \
  --username=postgres --password=x --contexts=common,shared \
  update -Dfile.resource.url=http://localhost >"$WORK/liquibase.log" 2>&1 \
  || { cat "$WORK/liquibase.log"; exit 1; }

cat > "$WORK/render.py" <<'PY'
import re, sys

path, method = sys.argv[1], sys.argv[2]
params = dict(a.split("=", 1) for a in sys.argv[3:])
src = open(path).read()
end = src.index(" " + method + "(")
start = max(src.rindex(tag, 0, end) for tag in ("@Select(", "@Update(", "@Insert(", "@Delete(") if tag in src[:end])
sql = "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', src[start:end]))


def repl(m):
    key = m.group(2)
    if key not in params:
        sys.exit("missing parameter " + key)
    return params[key]


print(re.sub(r"([#$])\{(\w+(?:\.\w+)?)(?:,[^}]*)?\}", repl, sql).strip())
PY
render() { python3 "$WORK/render.py" "$@"; }

LOCK_SQL() { render "$APP_MAPPER" lockApplication id="$1"; }
MOVE_SQL() { render "$APP_MAPPER" moveFollowersToLatest appId="$1" configId="$2"; }
RECALC_SQL() { render "$APP_MAPPER" recalculateLatestVersion id="$1"; }
RELEASE_SQL() { render "$APP_MAPPER" releaseApplicationVersion id="$1" mode="'$2'" userId=NULL releasedAt="$3"; }
moved_list() { echo "SELECT 'moved=' || COALESCE(string_agg(m::text, ',' ORDER BY m), 'none') FROM ($1) AS t(m);"; }
moved_of() { grep '^moved=' | sed 's/^moved=//'; }

FAILED=0
expect() {
  local label="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    echo "PASS $label"
  else
    echo "FAIL $label: expected '$expected' got '$actual'"
    FAILED=1
  fi
}

q "INSERT INTO customers (name, description, master, filesDir, prefix) VALUES ('OTHER', 'second customer', FALSE, 'OTHER', 'other')" >/dev/null
CUST1="$(q "SELECT id FROM customers WHERE master ORDER BY id LIMIT 1")"
[ -n "$CUST1" ] || CUST1="$(q "SELECT MIN(id) FROM customers")"
CUST2="$(q "SELECT id FROM customers WHERE name = 'OTHER'")"

app() { q "INSERT INTO applications (pkg, name, customerId) VALUES ('$1', '$1', $CUST1) RETURNING id" | head -1; }
ver() { q "INSERT INTO applicationVersions (applicationId, version, versionCode, releaseState) VALUES ($1, '$2', 1, '${3:-released}') RETURNING id" | head -1; }
conf() { q "INSERT INTO configurations (name, customerId) VALUES ('$1', ${2:-$CUST1}) RETURNING id" | head -1; }
row() { q "INSERT INTO configurationApplications (configurationId, applicationId, applicationVersionId, action, followLatest) VALUES ($1, $2, $3, ${5:-1}, $4)" >/dev/null; }
recalc() { RECALC_SQL "$1" | psqlq >/dev/null; }

APP_A="$(app com.example.a)"
APP_B="$(app com.example.b)"
A1="$(ver "$APP_A" 1.0)"; A2="$(ver "$APP_A" 2.0)"
B1="$(ver "$APP_B" 1.0)"; B2="$(ver "$APP_B" 2.0)"
recalc "$APP_A"; recalc "$APP_B"

C_FOLLOW="$(conf c-follow)";            row "$C_FOLLOW" "$APP_A" "$A2" TRUE; row "$C_FOLLOW" "$APP_B" "$B2" TRUE
C_PIN_OLD="$(conf c-pinned-old)";       row "$C_PIN_OLD" "$APP_A" "$A1" FALSE
C_PIN_LATEST="$(conf c-pinned-latest)"; row "$C_PIN_LATEST" "$APP_A" "$A2" FALSE
C_MAIN="$(conf c-main)";                row "$C_MAIN" "$APP_A" "$A2" TRUE
C_MAIN_PIN="$(conf c-main-pinned)";     row "$C_MAIN_PIN" "$APP_A" "$A2" FALSE
C_MAIN_OFF="$(conf c-main-flag-off)";   row "$C_MAIN_OFF" "$APP_A" "$A2" TRUE
C_OTHER="$(conf c-other "$CUST2")";     row "$C_OTHER" "$APP_A" "$A2" TRUE
C_REMOVE="$(conf c-remove)";            row "$C_REMOVE" "$APP_A" "$A1" TRUE 2
C_DUP="$(conf c-dup)";                  row "$C_DUP" "$APP_A" "$A1" TRUE; row "$C_DUP" "$APP_A" "$A2" TRUE
q "UPDATE configurations SET mainAppId = $A2, contentAppId = $A2 WHERE id = $C_MAIN" >/dev/null
q "UPDATE configurations SET mainAppId = $A2, contentAppId = $A2, followLatestMainApp = FALSE, followLatestContentApp = FALSE WHERE id = $C_MAIN_PIN" >/dev/null
q "UPDATE configurations SET mainAppId = $A2, followLatestMainApp = FALSE WHERE id = $C_MAIN_OFF" >/dev/null

ver_of() { q "SELECT string_agg(applicationVersionId::text || ':' || action || ':' || followLatest::text, ',' ORDER BY applicationVersionId, action) FROM configurationApplications WHERE configurationId = $1 AND applicationId = $2"; }

A3="$(ver "$APP_A" 3.0 held)"
recalc "$APP_A"
C_COVERED="$(conf c-covered)"; row "$C_COVERED" "$APP_A" "$A2" TRUE; row "$C_COVERED" "$APP_A" "$A3" FALSE
C_CLEAR="$(conf c-clear)";     row "$C_CLEAR" "$APP_A" "$A2" TRUE; row "$C_CLEAR" "$APP_A" "$A3" TRUE 0

expect "held: a held higher version is not latest" "$A2" "$(q "SELECT latestVersion FROM applications WHERE id = $APP_A")"
expect "held: moving with only a held version ahead moves nothing" "none" "$(moved_list "$(MOVE_SQL "$APP_A" NULL)" | psqlq | moved_of)"
expect "held: following row stays on the latest released" "$A2:1:true" "$(ver_of "$C_FOLLOW" "$APP_A")"

RELEASED="$( { echo "BEGIN;"; LOCK_SQL "$APP_A"; echo ";"; RELEASE_SQL "$A3" schedule 1000; echo ";"; RECALC_SQL "$APP_A"; echo ";"; moved_list "$(MOVE_SQL "$APP_A" NULL)"; echo "COMMIT;"; } | psqlq | moved_of)"
EXPECTED="$(printf '%s\n' "$C_FOLLOW" "$C_MAIN" "$C_MAIN_OFF" "$C_OTHER" "$C_DUP" "$C_CLEAR" | sort -n | paste -sd, -)"
expect "release: returns exactly the configurations that changed" "$EXPECTED" "$RELEASED"
expect "release: state, mode and time recorded" "released|schedule|1000" "$(q "SELECT releaseState || '|' || releaseMode || '|' || releasedAt FROM applicationVersions WHERE id = $A3")"
expect "release: latest is the released version" "$A3" "$(q "SELECT latestVersion FROM applications WHERE id = $APP_A")"
expect "release: following row moves" "$A3:1:true" "$(ver_of "$C_FOLLOW" "$APP_A")"
expect "release: other app's row untouched" "$B2:1:true" "$(ver_of "$C_FOLLOW" "$APP_B")"
expect "release: row pinned to an older version stays" "$A1:1:false" "$(ver_of "$C_PIN_OLD" "$APP_A")"
expect "release: row pinned to the previous latest stays" "$A2:1:false" "$(ver_of "$C_PIN_LATEST" "$APP_A")"
expect "release: main and content apps with the flag set move" "$A3|$A3" "$(q "SELECT mainAppId || '|' || contentAppId FROM configurations WHERE id = $C_MAIN")"
expect "release: pinned main and content apps stay" "$A2|$A2" "$(q "SELECT mainAppId || '|' || contentAppId FROM configurations WHERE id = $C_MAIN_PIN")"
expect "release: pinned row of a pinned main app stays" "$A2:1:false" "$(ver_of "$C_MAIN_PIN" "$APP_A")"
expect "release: main app with the flag off stays while its row follows" "$A2|$A3:1:true" "$(q "SELECT mainAppId FROM configurations WHERE id = $C_MAIN_OFF")|$(ver_of "$C_MAIN_OFF" "$APP_A")"
expect "release: configuration of another customer moves (common app)" "$A3:1:true" "$(ver_of "$C_OTHER" "$APP_A")"
expect "release: non-install row is never moved" "$A1:2:true" "$(ver_of "$C_REMOVE" "$APP_A")"
expect "release: two following rows collapse into one install row" "$A3:1:true" "$(ver_of "$C_DUP" "$APP_A")"
expect "release: follower dropped where a pinned row already installs the latest" "$A3:1:false" "$(ver_of "$C_COVERED" "$APP_A")"
expect "release: contradicting non-install row on the latest is cleared" "$A3:1:true" "$(ver_of "$C_CLEAR" "$APP_A")"
expect "release: second call moves nothing" "none" "$(moved_list "$(MOVE_SQL "$APP_A" NULL)" | psqlq | moved_of)"
expect "release: releasing an already released version changes no row" "0" "$(RELEASE_SQL "$A3" immediate 2000 | sed 's/$/ RETURNING id/' | psqlq | wc -l | tr -d ' ')"

single_install_rows() { q "SELECT COUNT(*) FROM (SELECT configurationId FROM configurationApplications WHERE applicationId = $APP_A AND action = 1 GROUP BY configurationId, applicationId HAVING COUNT(*) > 1) d"; }

upload_session() {
  local version="$1" pause="$2"
  {
    echo "BEGIN;"
    LOCK_SQL "$APP_A"; echo ";"
    echo "INSERT INTO applicationVersions (applicationId, version, versionCode, releaseState, releasedAt, releaseMode) VALUES ($APP_A, '$version', 1, 'released', 1, 'schedule');"
    RECALC_SQL "$APP_A"; echo ";"
    moved_list "$(MOVE_SQL "$APP_A" NULL)"
    echo "SELECT pg_sleep($pause);"
    echo "COMMIT;"
  } | psqlq | moved_of
}

race() {
  local first="$1" second="$2"
  upload_session "$first" 3 >"$WORK/first.out" &
  local pid=$!
  sleep 1
  upload_session "$second" 0 >"$WORK/second.out"
  wait "$pid"
}

race 5.0 4.0
A5="$(q "SELECT id FROM applicationVersions WHERE applicationId = $APP_A AND version = '5.0'")"
expect "race (higher first): latest is the higher version" "$A5" "$(q "SELECT latestVersion FROM applications WHERE id = $APP_A")"
expect "race (higher first): followers end on the higher version" "$A5:1:true" "$(ver_of "$C_FOLLOW" "$APP_A")"
expect "race (higher first): the lower upload moves nothing" "none" "$(cat "$WORK/second.out")"
expect "race (higher first): one install row per app per configuration" "0" "$(single_install_rows)"

race 6.0 7.0
A7="$(q "SELECT id FROM applicationVersions WHERE applicationId = $APP_A AND version = '7.0'")"
expect "race (lower first): latest is the higher version" "$A7" "$(q "SELECT latestVersion FROM applications WHERE id = $APP_A")"
expect "race (lower first): followers end on the higher version" "$A7:1:true" "$(ver_of "$C_FOLLOW" "$APP_A")"
expect "race (lower first): pinned rows untouched" "$A1:1:false|$A2:1:false" "$(ver_of "$C_PIN_OLD" "$APP_A")|$(ver_of "$C_PIN_LATEST" "$APP_A")"
expect "race (lower first): the higher upload moves the followers again" "$EXPECTED" "$(cat "$WORK/second.out")"
expect "race (lower first): one install row per app per configuration" "0" "$(single_install_rows)"

C_SAVE="$(conf c-save)";   row "$C_SAVE" "$APP_B" "$B1" TRUE
C_SAVE2="$(conf c-save2)"; row "$C_SAVE2" "$APP_B" "$B1" TRUE
expect "save: moving one configuration returns only it" "$C_SAVE" "$(moved_list "$(MOVE_SQL NULL "$C_SAVE")" | psqlq | moved_of)"
expect "save: its following row moves" "$B2:1:true" "$(ver_of "$C_SAVE" "$APP_B")"
expect "save: another configuration is untouched" "$B1:1:true" "$(ver_of "$C_SAVE2" "$APP_B")"

C_SYNC="$(conf c-sync)"; row "$C_SYNC" "$APP_B" "$B2" FALSE
q "UPDATE configurations SET mainAppId = $B2, contentAppId = $B2, followLatestMainApp = TRUE, followLatestContentApp = TRUE WHERE id = $C_SYNC" >/dev/null
render "$CONF_MAPPER" syncMainAndContentFollowFlags id="$C_SYNC" | psqlq >/dev/null
expect "sync: main/content flags follow their install row" "false|false" "$(q "SELECT followLatestMainApp::text || '|' || followLatestContentApp::text FROM configurations WHERE id = $C_SYNC")"
q "UPDATE configurationApplications SET followLatest = TRUE WHERE configurationId = $C_SYNC" >/dev/null
render "$APP_MAPPER" syncMainAndContentFollowFlagsForCustomer customerId="$CUST1" | psqlq >/dev/null
expect "sync (customer): main/content flags follow their install row" "true|true" "$(q "SELECT followLatestMainApp::text || '|' || followLatestContentApp::text FROM configurations WHERE id = $C_SYNC")"
expect "sync (customer): configuration without a main app keeps its flags" "true|true" "$(q "SELECT followLatestMainApp::text || '|' || followLatestContentApp::text FROM configurations WHERE id = $C_SAVE")"

expect "rows read before save: id, version, flag, action" "$APP_B|$B2|t|1" "$(render "$CONF_MAPPER" getConfigurationFollowRows id="$C_SAVE" | psqlq -F'|')"
ver "$APP_A" 8.0 held >/dev/null
expect "counts: released and all versions" "7|8" "$(render "$APP_MAPPER" countReleasedVersions id="$APP_A" | psqlq)|$(render "$APP_MAPPER" countVersions id="$APP_A" | psqlq)"

if [ "$FAILED" -ne 0 ]; then
  exit 1
fi
echo "release-flow-move-check: ok"
