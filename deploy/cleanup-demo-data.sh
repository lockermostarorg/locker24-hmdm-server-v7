#!/usr/bin/env bash
# Removes Headwind's installer demo data (install/sql/hmdm_init.en.sql) from a live database.
# Usage (as root on the MDM host): deploy/cleanup-demo-data.sh [--db hmdm] [--yes]
set -euo pipefail

DB_NAME=hmdm
ASSUME_YES=0

while [ $# -gt 0 ]; do
    case "$1" in
        --db) [ $# -ge 2 ] || { echo "--db needs a database name" >&2; exit 2; }; DB_NAME=$2; shift 2 ;;
        --yes) ASSUME_YES=1; shift ;;
        -h|--help) echo "Usage: $0 [--db hmdm] [--yes]"; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
done

if [ -z "${CLEANUP_PSQL:-}" ] && [ "$(id -u)" -ne 0 ]; then
    echo "Run as root (it connects as the postgres user)." >&2
    exit 1
fi
read -r -a PSQL <<< "${CLEANUP_PSQL:-sudo -u postgres psql}"
cd /tmp

run_sql() { "${PSQL[@]}" -d "$DB_NAME" -v ON_ERROR_STOP=1 -qtA; }

compute_sql() {
    cat <<'SQL'
CREATE TEMP TABLE demo_apps (id INT PRIMARY KEY, pkg TEXT NOT NULL);
INSERT INTO demo_apps VALUES
    (1, 'com.android.systemui'), (2, 'com.android.bluetooth'), (3, 'com.google.android.gms'),
    (34, 'com.android.email'), (9, 'com.android.chrome'), (10, 'com.sec.android.app.browser'),
    (11, 'com.samsung.android.video'), (12, 'com.android.providers.media'), (13, 'com.android.gallery3d'),
    (14, 'com.sec.android.gallery3d'), (15, 'com.android.vending'), (16, 'com.samsung.android.app.memo'),
    (35, 'com.android.documentsui'), (5, 'com.google.android.packageinstaller'), (17, 'com.android.packageinstaller'),
    (18, 'com.samsung.android.calendar'), (19, 'com.android.calculator2'), (20, 'com.sec.android.app.popupcalculator'),
    (21, 'com.android.camera'), (22, 'com.huawei.camera'), (23, 'org.codeaurora.snapcam'),
    (24, 'com.mediatek.camera'), (25, 'com.sec.android.app.camera'), (26, 'com.sec.android.camera'),
    (27, 'com.google.android.apps.maps'), (28, 'com.touchtype.swiftkey'), (29, 'com.android.contacts'),
    (31, 'com.sec.android.app.myfiles'), (32, 'com.android.settings'), (33, 'com.sec.android.inputmethod'),
    (36, 'com.samsung.android.email.provider'), (37, 'android'), (38, 'com.android.mms'),
    (39, 'com.google.android.apps.messaging'), (40, 'com.android.dialer'), (41, 'com.sec.phone'),
    (42, 'com.android.phone'), (43, 'com.huaqin.filemanager'), (6, 'com.google.android.apps.photos'),
    (4, 'com.google.android.apps.docs'), (30, 'com.huawei.android.launcher'), (8, 'com.android.browser'),
    (46, 'com.hmdm.launcher'), (47, 'com.huawei.android.internal.app'), (48, 'com.hmdm.pager'),
    (49, 'com.hmdm.phoneproxy'), (50, 'com.hmdm.emuilauncherrestarter'), (51, 'com.miui.cleanmaster'),
    (52, 'com.miui.gallery'), (53, 'com.miui.notes'), (54, 'com.miui.global.packageinstaller'),
    (55, 'com.miui.msa.global'), (56, 'com.miui.securitycenter'), (57, 'com.xiaomi.discover'),
    (58, 'com.google.android.permissioncontroller'), (59, 'com.samsung.accessibility'), (60, 'com.android.updater'),
    (61, 'com.android.printspooler'), (62, 'com.google.android.documentsui'), (63, 'com.google.android.contacts'),
    (64, 'com.google.android.dialer'), (65, 'com.samsung.android.app.notes'), (66, 'com.hmdglobal.camera2'),
    (67, 'com.hmdglobal.app.camera'), (68, 'com.samsung.android.dialer'), (69, 'com.samsung.android.app.contacts'),
    (70, 'com.samsung.android.messaging'), (71, 'com.sec.android.app.launcher'), (73, 'com.google.android.apps.nbu.files'),
    (74, 'com.android.settings.intelligence'), (75, 'com.huawei.bluetooth'), (76, 'com.google.android.gms.setup'),
    (77, 'com.samsung.android.app.telephonyui'), (78, 'com.android.permissioncontroller'), (79, 'com.android.vpndialogs'),
    (80, 'com.google.android.googlequicksearchbox'), (81, 'com.android.incallui'), (82, 'com.android.deskclock'),
    (83, 'com.android.server.telecom'), (84, 'com.google.android.inputmethod.latin'), (85, 'com.android.camera2'),
    (86, 'com.android.launcher3'), (87, 'com.google.android.apps.meetings'), (88, 'com.google.android.apps.nexuslauncher'),
    (89, 'com.google.android.calculator'), (90, 'com.google.android.calendar'), (91, 'com.google.android.deskclock'),
    (92, 'com.google.android.gm'), (93, 'com.google.android.youtube'), (94, 'com.wssyncmldm'),
    (95, 'com.samsung.android.incallui'), (96, 'com.samsung.android.kgclient'), (97, 'com.sec.android.app.clockpackage'),
    (98, 'com.sec.android.app.sbrowser'), (99, 'com.google.android.GoogleCamera'), (100, 'com.hmdm.kiosk'),
    (101, 'com.hmdm.kioskdemo');

CREATE TEMP TABLE demo_confs (id INT PRIMARY KEY, name TEXT NOT NULL);
INSERT INTO demo_confs VALUES
    (1, 'Managed Launcher'), (2, 'MIUI (Xiaomi Redmi)'), (3, 'Background (Agent) Mode'),
    (4, 'Kiosk Mode'), (5, 'Kiosk Mode - Web Page');

CREATE TEMP TABLE del_devices AS
    SELECT d.id, d.number FROM devices d
    WHERE d.id = 1 AND d.number = 'h0001' AND d.customerId = 1
      AND COALESCE(d.lastUpdate, 0) = 0 AND COALESCE(d.enrollTime, 0) = 0;

CREATE TEMP TABLE del_confs AS
    SELECT c.id, c.name FROM configurations c
    JOIN demo_confs dc ON dc.id = c.id AND dc.name = c.name
    WHERE c.customerId = 1
      AND NOT EXISTS (
          SELECT 1 FROM devices d
          WHERE (d.configurationId = c.id OR d.oldConfigurationId = c.id)
            AND d.id NOT IN (SELECT id FROM del_devices));

CREATE TEMP TABLE del_apps AS
    SELECT a.id, a.pkg, a.name FROM applications a
    JOIN demo_apps da ON da.id = a.id AND da.pkg = a.pkg
    WHERE a.customerId = 1 AND a.pkg <> 'app.locker24.mdm'
      AND NOT EXISTS (
          SELECT 1 FROM configurationApplications ca
          WHERE ca.applicationId = a.id AND ca.configurationId NOT IN (SELECT id FROM del_confs))
      AND NOT EXISTS (
          SELECT 1 FROM configurationApplications ca
          JOIN applicationVersions v ON v.id = ca.applicationVersionId
          WHERE v.applicationId = a.id AND ca.configurationId NOT IN (SELECT id FROM del_confs))
      AND NOT EXISTS (
          SELECT 1 FROM configurations c
          JOIN applicationVersions v ON v.id IN (c.mainAppId, c.contentAppId)
          WHERE v.applicationId = a.id AND c.id NOT IN (SELECT id FROM del_confs))
      AND NOT EXISTS (
          SELECT 1 FROM applicationVersions v
          WHERE v.applicationId = a.id AND v.id NOT BETWEEN 10000 AND 10101)
      AND NOT EXISTS (
          SELECT 1 FROM configurationApplicationSettings s
          WHERE s.applicationId = a.id AND COALESCE(s.extrefId, -1) NOT IN (SELECT id FROM del_confs))
      AND NOT EXISTS (
          SELECT 1 FROM configurationApplicationParameters p
          WHERE p.applicationId = a.id AND p.configurationId NOT IN (SELECT id FROM del_confs))
      AND NOT EXISTS (
          SELECT 1 FROM deviceApplicationSettings s
          WHERE s.applicationId = a.id AND COALESCE(s.extrefId, -1) NOT IN (SELECT id FROM del_devices));

DO $$
BEGIN
    IF to_regclass('plugin_devicelog_settings_rules') IS NOT NULL THEN
        DELETE FROM del_apps WHERE id IN (
            SELECT applicationId FROM plugin_devicelog_settings_rules WHERE name <> 'Headwind MDM');
    END IF;
END
$$;
SQL
}

plan_sql() {
    echo "BEGIN;"
    compute_sql
    cat <<'SQL'
SELECT 'delete device ' || number FROM del_devices
UNION ALL SELECT 'delete configuration ' || name FROM del_confs
UNION ALL (SELECT 'delete application ' || pkg || ' (' || name || ')' FROM del_apps ORDER BY pkg);
SELECT 'keep device ' || number || ' (enrolled)' FROM devices
    WHERE id = 1 AND number = 'h0001' AND customerId = 1 AND id NOT IN (SELECT id FROM del_devices);
SELECT 'keep configuration ' || c.name || ' (used by a device)' FROM configurations c
    JOIN demo_confs dc ON dc.id = c.id AND dc.name = c.name
    WHERE c.customerId = 1 AND c.id NOT IN (SELECT id FROM del_confs);
SELECT 'keep application ' || a.pkg || ': ' || COALESCE(
    (SELECT 'used by ' || string_agg(DISTINCT c.name, ', ') FROM configurations c
     WHERE c.id NOT IN (SELECT id FROM del_confs)
       AND (EXISTS (SELECT 1 FROM configurationApplications ca WHERE ca.configurationId = c.id AND ca.applicationId = a.id)
            OR EXISTS (SELECT 1 FROM applicationVersions v WHERE v.applicationId = a.id AND v.id IN (c.mainAppId, c.contentAppId)))),
    'own version, setting or log rule')
FROM applications a JOIN demo_apps da ON da.id = a.id AND da.pkg = a.pkg
WHERE a.customerId = 1 AND a.id NOT IN (SELECT id FROM del_apps) ORDER BY a.pkg;
ROLLBACK;
SQL
}

apply_sql() {
    echo "BEGIN;"
    compute_sql
    cat <<'SQL'
WITH d AS (DELETE FROM devices WHERE id IN (SELECT id FROM del_devices) RETURNING 1)
    SELECT 'deleted devices: ' || COUNT(*) FROM d;
WITH d AS (DELETE FROM configurations WHERE id IN (SELECT id FROM del_confs) RETURNING 1)
    SELECT 'deleted configurations: ' || COUNT(*) FROM d;
WITH d AS (DELETE FROM applications WHERE id IN (SELECT id FROM del_apps) RETURNING 1)
    SELECT 'deleted applications: ' || COUNT(*) FROM d;
COMMIT;
SQL
}

echo "Database: $DB_NAME"
if ! echo "SELECT 1 FROM applications LIMIT 1; SELECT 1 FROM devices LIMIT 1; SELECT 1 FROM configurations LIMIT 1;" | run_sql >/dev/null; then
    echo "Cannot read the Headwind tables in database $DB_NAME" >&2
    exit 1
fi

PLAN=$(plan_sql | run_sql)
if ! grep -q '^delete ' <<< "$PLAN"; then
    grep '^keep ' <<< "$PLAN" || true
    echo "nothing to delete"
    exit 0
fi

echo "Plan:"
printf '  %s\n' "${PLAN//$'\n'/$'\n'  }"
echo "Totals: $(grep -c '^delete device ' <<< "$PLAN" || true) device(s), $(grep -c '^delete configuration ' <<< "$PLAN" || true) configuration(s), $(grep -c '^delete application ' <<< "$PLAN" || true) application(s)"

if [ "$ASSUME_YES" -ne 1 ]; then
    if [ ! -t 0 ]; then
        echo "Not a terminal and no --yes given; nothing deleted." >&2
        exit 1
    fi
    read -r -p "Delete the items above? [y/N] " ANSWER
    case "$ANSWER" in
        y|Y|yes|YES) ;;
        *) echo "Aborted, nothing deleted."; exit 0 ;;
    esac
fi

apply_sql | run_sql
echo "done"
