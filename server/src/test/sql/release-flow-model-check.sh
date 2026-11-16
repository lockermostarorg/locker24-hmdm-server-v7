#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
CHANGELOG_DIR="$HERE/../../main/resources/liquibase"
APP_MAPPER="$HERE/../../../../common/src/main/java/com/hmdm/persistence/mapper/ApplicationMapper.java"
LB_IMAGE="liquibase/liquibase:4.8.0"
SUFFIX="$$"
DB="l24-release-model-$SUFFIX"
NET="l24-release-model-net-$SUFFIX"
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

liquibase_update() {
  docker run --rm --network "$NET" -v "$1":/liquibase/changelog "$LB_IMAGE" \
    --changeLogFile=db.changelog.xml --url="jdbc:postgresql://$DB:5432/postgres" \
    --username=postgres --password=x --contexts=common,shared \
    update -Dfile.resource.url=http://localhost >"$WORK/liquibase.log" 2>&1 \
    || { cat "$WORK/liquibase.log"; exit 1; }
}

mkdir "$WORK/before"
python3 - "$CHANGELOG_DIR/db.changelog.xml" "$WORK/before/db.changelog.xml" <<'PY'
import sys
src = open(sys.argv[1]).read()
cut = src.index('<changeSet id="16.11.26-10:00-l24-1"')
open(sys.argv[2], 'w').write(src[:cut].rstrip() + '\n\n</databaseChangeLog>\n')
PY

liquibase_update "$WORK/before"

psqlq <<'SQL'
CREATE TEMP TABLE ids (name TEXT PRIMARY KEY, id INT);
INSERT INTO applications (pkg, name, customerId) VALUES ('com.example.a', 'A', 1);
INSERT INTO applications (pkg, name, customerId) VALUES ('com.example.b', 'B', 1);
INSERT INTO applicationVersions (applicationId, version, versionCode)
  SELECT id, v.version, v.code FROM applications, (VALUES ('1.0', 1), ('2.0', 2)) AS v(version, code)
  WHERE pkg IN ('com.example.a', 'com.example.b');
UPDATE applications SET latestVersion = (SELECT id FROM applicationVersions WHERE applicationId = applications.id AND version = '2.0')
  WHERE pkg IN ('com.example.a', 'com.example.b');
INSERT INTO configurations (name, customerId) VALUES ('c-latest', 1), ('c-pinned', 1), ('c-plain', 1);
UPDATE configurations SET
  mainAppId = (SELECT id FROM applicationVersions WHERE applicationId = (SELECT id FROM applications WHERE pkg = 'com.example.a') AND version = CASE name WHEN 'c-latest' THEN '2.0' WHEN 'c-pinned' THEN '1.0' END),
  contentAppId = (SELECT id FROM applicationVersions WHERE applicationId = (SELECT id FROM applications WHERE pkg = 'com.example.b') AND version = '2.0')
  WHERE name IN ('c-latest', 'c-pinned');
INSERT INTO configurationApplications (configurationId, applicationId, applicationVersionId, action)
  SELECT c.id, a.id, v.id, x.action
  FROM (VALUES ('c-latest', 'com.example.a', '2.0', 1), ('c-pinned', 'com.example.a', '1.0', 1), ('c-plain', 'com.example.a', '1.0', 2),
               ('c-latest', 'com.example.b', '2.0', 1), ('c-pinned', 'com.example.b', '2.0', 1)) AS x(conf, pkg, version, action)
  JOIN configurations c ON c.name = x.conf
  JOIN applications a ON a.pkg = x.pkg
  JOIN applicationVersions v ON v.applicationId = a.id AND v.version = x.version;
SQL

liquibase_update "$CHANGELOG_DIR"

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

q() { psqlq -c "$1"; }

APP_A="$(q "SELECT id FROM applications WHERE pkg = 'com.example.a'")"
APP_B="$(q "SELECT id FROM applications WHERE pkg = 'com.example.b'")"
C_LATEST="$(q "SELECT id FROM configurations WHERE name = 'c-latest'")"
C_PINNED="$(q "SELECT id FROM configurations WHERE name = 'c-pinned'")"
C_PLAIN="$(q "SELECT id FROM configurations WHERE name = 'c-plain'")"
V2="$(q "SELECT id FROM applicationVersions WHERE applicationId = $APP_A AND version = '2.0'")"

expect "a: row on latest follows" "t" "$(q "SELECT followLatest FROM configurationApplications WHERE configurationId = $C_LATEST AND applicationId = $APP_A")"
expect "a: row on older version is pinned" "f" "$(q "SELECT followLatest FROM configurationApplications WHERE configurationId = $C_PINNED AND applicationId = $APP_A")"
expect "a: non-install row keeps default" "t" "$(q "SELECT followLatest FROM configurationApplications WHERE configurationId = $C_PLAIN AND applicationId = $APP_A")"
expect "a: row of other app on latest follows" "t,t" "$(q "SELECT string_agg(followLatest::text, ',' ORDER BY configurationId) FROM configurationApplications WHERE applicationId = $APP_B" | sed 's/true/t/g')"
expect "a: main app on latest follows" "t" "$(q "SELECT followLatestMainApp FROM configurations WHERE id = $C_LATEST")"
expect "a: main app on older version is pinned" "f" "$(q "SELECT followLatestMainApp FROM configurations WHERE id = $C_PINNED")"
expect "a: content app on latest follows" "t,t" "$(q "SELECT string_agg(followLatestContentApp::text, ',' ORDER BY id) FROM configurations WHERE id IN ($C_LATEST, $C_PINNED)" | sed 's/true/t/g')"
expect "a: configuration without apps follows" "t,t" "$(q "SELECT followLatestMainApp::text || ',' || followLatestContentApp::text FROM configurations WHERE id = $C_PLAIN" | sed 's/true/t/g')"
expect "a: existing versions are released" "4" "$(q "SELECT COUNT(*) FROM applicationVersions WHERE releaseState = 'released'")"
expect "a: invalid release state rejected" "rejected" "$(psqlq -c "INSERT INTO applicationVersions (applicationId, version, releaseState) VALUES ($APP_A, 'x', 'bogus')" >/dev/null 2>&1 && echo accepted || echo rejected)"

RECALC_FILE="$WORK/recalc.sql"
python3 - "$APP_MAPPER" > "$RECALC_FILE" <<'PY'
import re, sys
src = open(sys.argv[1]).read()
end = src.index('void recalculateLatestVersion')
block = src[src.rindex('@Update(', 0, end):end]
sql = "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', block))
print(sql.replace("#{id}", ":appId") + ";")
PY
recalc() { psqlq -v appId="$1" <"$RECALC_FILE" >/dev/null; }

recalc "$APP_A"
expect "recalc over released only keeps latest" "$V2" "$(q "SELECT latestVersion FROM applications WHERE id = $APP_A")"

q "INSERT INTO applicationVersions (applicationId, version, versionCode, releaseState) VALUES ($APP_A, '3.0', 3, 'held')" >/dev/null
HELD3="$(q "SELECT id FROM applicationVersions WHERE applicationId = $APP_A AND version = '3.0'")"
recalc "$APP_A"
expect "b: held higher version does not become latest" "$V2" "$(q "SELECT latestVersion FROM applications WHERE id = $APP_A")"

q "UPDATE applicationVersions SET releaseState = 'released', releasedAt = 1, releaseMode = 'schedule' WHERE id = $HELD3" >/dev/null
recalc "$APP_A"
expect "c: released version becomes latest" "$HELD3" "$(q "SELECT latestVersion FROM applications WHERE id = $APP_A")"

q "INSERT INTO applicationVersions (applicationId, version, versionCode, releaseState) VALUES ($APP_A, '4.0', 4, 'held')" >/dev/null
HELD4="$(q "SELECT id FROM applicationVersions WHERE applicationId = $APP_A AND version = '4.0'")"
recalc "$APP_A"
q "SELECT mdm_config_app_upgrade($C_PINNED, $APP_A)" >/dev/null
expect "d: upgrade lands on latest released, not held" "$HELD3" "$(q "SELECT applicationVersionId FROM configurationApplications WHERE configurationId = $C_PINNED AND applicationId = $APP_A AND action = 1")"
expect "d: held version used by no row" "0" "$(q "SELECT COUNT(*) FROM configurationApplications WHERE applicationVersionId = $HELD4")"

q "INSERT INTO configurations (name, customerId) VALUES ('c-new', 1)" >/dev/null
NEWCONF="$(q "SELECT id FROM configurations WHERE name = 'c-new'")"
q "INSERT INTO configurationApplications (configurationId, applicationId, applicationVersionId, action)
   SELECT $NEWCONF, applications.id, COALESCE(NULL::int, applications.latestVersion), 1 FROM applications WHERE applications.id = $APP_A" >/dev/null
expect "e: new row defaults to latest released" "$HELD3" "$(q "SELECT applicationVersionId FROM configurationApplications WHERE configurationId = $NEWCONF AND applicationId = $APP_A")"
expect "e: new row follows by default" "t" "$(q "SELECT followLatest FROM configurationApplications WHERE configurationId = $NEWCONF AND applicationId = $APP_A")"

if [ "$FAILED" -ne 0 ]; then
  exit 1
fi
echo "release-flow-model-check: ok"
