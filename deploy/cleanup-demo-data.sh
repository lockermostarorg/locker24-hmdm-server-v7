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

run_sql() { "${PSQL[@]}" -d "$DB_NAME" -v ON_ERROR_STOP=1 -qtA "$@"; }

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

CREATE TEMP TABLE demo_confs (id INT PRIMARY KEY, name TEXT NOT NULL, content_app INT, row_count INT NOT NULL, row_hash TEXT NOT NULL);
INSERT INTO demo_confs VALUES
    (1, 'Managed Launcher', NULL, 73, '5d6d6a60306cafbf5ade84e9bb249030'),
    (2, 'MIUI (Xiaomi Redmi)', NULL, 35, 'e8ac42410d8090e9db50b483e21d0700'),
    (3, 'Background (Agent) Mode', NULL, 73, '5d6d6a60306cafbf5ade84e9bb249030'),
    (4, 'Kiosk Mode', 10101, 73, '246d941ba7647bab08b2e0ac50e94eb7'),
    (5, 'Kiosk Mode - Web Page', 10100, 73, '181dbd57b5988cfaa06e86607123cfeb');

CREATE TEMP TABLE conf_edited AS
    SELECT c.id FROM configurations c
    JOIN demo_confs dc ON dc.id = c.id AND dc.name = c.name
    WHERE c.customerId = 1
      AND (c.mainAppId IS DISTINCT FROM 10045
           OR c.contentAppId IS DISTINCT FROM dc.content_app
           OR (SELECT COUNT(*) FROM configurationApplications ca WHERE ca.configurationId = c.id) <> dc.row_count
           OR md5(COALESCE((SELECT string_agg(ca.applicationId || ':' || ca.applicationVersionId || ':' || ca.remove::text || ':' || ca.showIcon::text || ':' || ca.action,
                                              ',' ORDER BY ca.applicationId, ca.applicationVersionId)
                            FROM configurationApplications ca WHERE ca.configurationId = c.id), '')) <> dc.row_hash);

CREATE TEMP TABLE del_devices AS
    SELECT d.id, d.number FROM devices d
    WHERE d.id = 1 AND d.number = 'h0001' AND d.customerId = 1
      AND COALESCE(d.lastUpdate, 0) = 0 AND COALESCE(d.enrollTime, 0) = 0;

CREATE TEMP TABLE del_confs AS
    SELECT c.id, c.name FROM configurations c
    JOIN demo_confs dc ON dc.id = c.id AND dc.name = c.name
    WHERE c.customerId = 1
      AND c.id NOT IN (SELECT id FROM conf_edited)
      AND NOT EXISTS (SELECT 1 FROM settings st WHERE st.newDeviceConfigurationId = c.id)
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
            SELECT applicationId FROM plugin_devicelog_settings_rules
            WHERE NOT (id = 1 AND applicationId = 46 AND name = 'Headwind MDM'));
    END IF;
END
$$;

CREATE TEMP TABLE cascades (label TEXT, n BIGINT);
INSERT INTO cascades SELECT 'application versions', COUNT(*) FROM applicationVersions
    WHERE applicationId IN (SELECT id FROM del_apps);
INSERT INTO cascades SELECT 'configuration rows', COUNT(*) FROM configurationApplications
    WHERE configurationId IN (SELECT id FROM del_confs) OR applicationId IN (SELECT id FROM del_apps);
INSERT INTO cascades SELECT 'application settings', COUNT(*) FROM configurationApplicationSettings
    WHERE applicationId IN (SELECT id FROM del_apps) OR extrefId IN (SELECT id FROM del_confs);
INSERT INTO cascades SELECT 'configuration files', COUNT(*) FROM configurationFiles
    WHERE configurationId IN (SELECT id FROM del_confs);
INSERT INTO cascades SELECT 'user access rows', COUNT(*) FROM userConfigurationAccess
    WHERE configurationId IN (SELECT id FROM del_confs);
INSERT INTO cascades SELECT 'device group links', COUNT(*) FROM deviceGroups
    WHERE deviceId IN (SELECT id FROM del_devices);
DO $$
BEGIN
    IF to_regclass('plugin_devicelog_log') IS NOT NULL THEN
        INSERT INTO cascades SELECT 'device log records', COUNT(*) FROM plugin_devicelog_log
            WHERE applicationId IN (SELECT id FROM del_apps) OR deviceId IN (SELECT id FROM del_devices);
    END IF;
    IF to_regclass('plugin_devicelog_settings_rules') IS NOT NULL THEN
        INSERT INTO cascades SELECT 'device log rules', COUNT(*) FROM plugin_devicelog_settings_rules
            WHERE applicationId IN (SELECT id FROM del_apps) OR configurationId IN (SELECT id FROM del_confs);
    END IF;
END
$$;

CREATE TEMP TABLE plan_fp AS
    SELECT md5(COALESCE((SELECT string_agg(id::text, ',' ORDER BY id) FROM del_devices), '') || '|'
            || COALESCE((SELECT string_agg(id::text, ',' ORDER BY id) FROM del_confs), '') || '|'
            || COALESCE((SELECT string_agg(id::text, ',' ORDER BY id) FROM del_apps), '')) AS fp;
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
SELECT 'keep configuration ' || c.name || ' (' || concat_ws('; ',
    CASE WHEN EXISTS (SELECT 1 FROM devices d WHERE (d.configurationId = c.id OR d.oldConfigurationId = c.id)
                      AND d.id NOT IN (SELECT id FROM del_devices)) THEN 'used by a device' END,
    CASE WHEN EXISTS (SELECT 1 FROM settings st WHERE st.newDeviceConfigurationId = c.id) THEN 'default for new devices' END,
    CASE WHEN c.id IN (SELECT id FROM conf_edited) THEN 'edited, kept' END) || ')'
FROM configurations c
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
SELECT 'also removes ' || n || ' ' || label FROM cascades WHERE n > 0
    AND EXISTS (SELECT 1 FROM del_devices UNION ALL SELECT 1 FROM del_confs UNION ALL SELECT 1 FROM del_apps);
SELECT 'lookalikes ' || (
    (SELECT COUNT(*) FROM configurations c JOIN demo_confs dc ON dc.name = c.name
        WHERE NOT (c.id = dc.id AND c.customerId = 1))
  + (SELECT COUNT(*) FROM applications a JOIN demo_apps da ON da.pkg = a.pkg
        WHERE NOT (a.id = da.id AND a.customerId = 1))
  + (SELECT COUNT(*) FROM devices d WHERE d.number = 'h0001' AND NOT (d.id = 1 AND d.customerId = 1)));
SELECT 'fingerprint ' || fp FROM plan_fp;
ROLLBACK;
SQL
}

apply_sql() {
    cat <<'SQL'
BEGIN;
SET LOCAL lock_timeout = '30s';
LOCK TABLE devices, configurations, configurationApplications, configurationApplicationSettings,
    configurationApplicationParameters, deviceApplicationSettings, applications, applicationVersions, settings
    IN SHARE ROW EXCLUSIVE MODE;
SQL
    compute_sql
    cat <<'SQL'
SET LOCAL l24.expected = :'expected';
DO $$
BEGIN
    IF (SELECT fp FROM plan_fp) <> current_setting('l24.expected') THEN
        RAISE EXCEPTION 'the data changed since the plan was made';
    END IF;
END
$$;
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
FINGERPRINT=$(sed -n 's/^fingerprint //p' <<< "$PLAN")
LOOKALIKES=$(sed -n 's/^lookalikes //p' <<< "$PLAN")
SHOWN=$(grep -v -e '^fingerprint ' -e '^lookalikes ' -e '^also removes ' <<< "$PLAN" || true)

if ! grep -q '^delete ' <<< "$PLAN"; then
    if [ -n "$SHOWN" ]; then
        printf '%s\n' "$SHOWN"
        echo "Nothing to delete: the demo data that remains is in use (see above)."
    else
        echo "No Headwind demo data found (matched by id and name)."
    fi
    echo "Rows that looked like demo data but did not match: $LOOKALIKES"
    exit 0
fi

echo "Plan:"
printf '  %s\n' "${SHOWN//$'\n'/$'\n'  }"
CASCADES=$(grep '^also removes ' <<< "$PLAN" || true)
[ -z "$CASCADES" ] || printf '  %s\n' "${CASCADES//$'\n'/$'\n'  }"
echo "Totals: $(grep -c '^delete device ' <<< "$PLAN" || true) device(s), $(grep -c '^delete configuration ' <<< "$PLAN" || true) configuration(s), $(grep -c '^delete application ' <<< "$PLAN" || true) application(s)"
echo "Rows that looked like demo data but did not match (left alone): $LOOKALIKES"

if [ "$ASSUME_YES" -ne 1 ]; then
    if [ ! -t 0 ]; then
        echo "Not a terminal and no --yes given; nothing deleted." >&2
        exit 1
    fi
    read -r -p "Delete the items above? [y/N] " ANSWER
    case "$ANSWER" in
        y|Y|yes|YES) ;;
        *) echo "Cancelled, nothing deleted."; exit 3 ;;
    esac
fi

if ! apply_sql | run_sql -v expected="$FINGERPRINT"; then
    echo "Apply failed or the data changed since the plan; the transaction was rolled back and nothing was deleted." >&2
    exit 4
fi
echo "done"
