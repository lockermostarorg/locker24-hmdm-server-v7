# Locker24 fleet health: smoke test, rollout, checklist, deviations

The plugin itself is documented in [`plugins/locker24-health/README.md`](../plugins/locker24-health/README.md) (panel history decisions, dashboard build). The changes to upstream files are in [`locker24-core-changes.md`](locker24-core-changes.md). Deploying is in [`deploy/README.md`](../deploy/README.md).

## Releasing a server update

1. Merge to `main`. Server CI (build, tests, the `sql-checks` job and the secret scan) must pass.
2. The deploy starts by itself when CI succeeds on `main` (`.github/workflows/server-deploy.yml`). A push that only changes `docs/**` or `*.md` files is not deployed; any other change, including workflow and `deploy/` files, redeploys. The first auto-deploy is the merge that carries the dashboard: until its changeset `-08` the plugin row has a NULL `baseUrl`, so no menu entry or federation remote exists before then.
3. Rollback or redeploy of a known commit: Actions -> "Server deploy" -> Run workflow with the full SHA (`force` if it is not the tip of `main`). `deploy/deploy.sh <ref>` on the droplet is for emergencies only (CI or GitHub down); the WAR and database rollback steps are in `deploy/README.md`.
4. After every deploy, run the post-deploy checks of `deploy/README.md` (internal endpoint answers 403 from outside, also for `/rest/internal;x=1/alert`; the first real pop-up arrives; the dashboard opens; all other plugin menus are still present; the job checks below) and the plugin checks of `deploy/server-setup.md` section 6, then the smoke script below.

### Post-deploy: prove the job runs, and the menus

Core swallows a failing plugin task module: it prints one line to stderr and Tomcat starts normally, so deploy.sh reports success and the smoke test passes even if the evaluation job never runs. Every row would then stay `unknown` and no alert would ever be raised. After every deploy:

1. `grep 'Health evaluation scheduled' /var/lib/tomcat10/logs/catalina.out | tail -n 2` shows a line from the current start (full text: `Health evaluation scheduled every minute; retention in N min, then daily`), and no `Failed to instantiate and initialize plugin task module` line follows it.
2. Two minutes after the start, no device that reports is still `unknown`:
   ```bash
   sudo -u postgres psql hmdm -c "SELECT d.number, l.received_at FROM locker24_device_health_latest l JOIN devices d ON d.id=l.device_id WHERE l.evaluated_at IS NULL AND l.received_at > (extract(epoch FROM now())*1000)::bigint - 600000"
   ```
   Expect no rows (`unknown` in the panel means `evaluated_at IS NULL`). Devices that stopped reporting long ago keep an old row and are evaluated as `offline`; they are not in this query.
3. About 30 minutes after the start, the `not_charging` pop-up for M-01 arrives if M-01 is unplugged. Until it does, the deploy is not verified: it proves the job, the alert service and the loopback call to `/rest/internal/alert`.
4. All other plugin menus are still present (Device Info, Device Log, Messaging, Push, Audit, ...). If they are gone, the dashboard remote failed to load or the WAR has no remote: see "Rolling back below changeset -08".

Expect after the first deploy of this version: every device `unknown` for about a minute; M-01 yellow `not_charging` and a pop-up after about 30 minutes if unplugged; a red `offline` pop-up 10-11 minutes after the start for any device that has a `latest` row but stopped reporting (old SMOKE-01 runs, retired test tablets); and if M-01's configuration does not list kiosk app version exactly `1.2.8`, yellow `kiosk_version` 2 hours after the deploy and an alert 30 minutes later.

### Rolling back below changeset -08

Changeset `-08` sets `baseUrl` of the `locker24-health` plugin row. A WAR from before it has no dashboard remote, and any failure of the remote makes the panel drop EVERY plugin menu. `deploy.sh`'s automatic rollback checks the restored WAR and resets the row itself (and prints that it did). For a manual WAR rollback, or whenever the menus vanish, run:

```bash
sudo -u postgres psql -d hmdm -v ON_ERROR_STOP=1 -c "UPDATE plugins SET baseUrl=NULL, baseUrlDev=NULL WHERE identifier='locker24-health'"
```

Reload the panel; restart Tomcat if the menus do not come back. It touches only the `locker24-health` row. A manual WAR rollback also appends a line to `/opt/locker24/last-deploy`: `<timestamp> ref=manual-rollback sha=<sha of the restored WAR if known, else unknown>`. With an unknown sha the docs-only skip never matches, so the next push is deployed (safe). The full procedure is in the Rollback section of `deploy/README.md`.

Agent rollout order: the ingest endpoint must answer before the agent `6.40-l24.5` goes to the fleet. It already does on `mdm.locker24.app`. Then the kiosk app (PostBox QA `1.2.8+23`, PostBox `1.3.2+25`), then set the app setting `health_interval_minutes` (default 5, the agent clamps it to 1..8).

## Smoke test: `deploy/health-smoke.sh`

```bash
deploy/health-smoke.sh https://mdm.locker24.app
deploy/health-smoke.sh --secrets-file /root/.l24-smoke --delete https://mdm.locker24.app
```

`--device` must match `^[A-Za-z0-9._-]+$` and start with `SMOKE`; any other number is refused unless `--i-know-this-is-a-real-device` is given, and the script never deletes a non-SMOKE device.

Prerequisite: a panel device `SMOKE-01` in its own empty configuration (no tablets). Needs `curl`, `jq` and `openssl`. Run it on the droplet or on any machine that has the server's `hash.secret`.

Secrets: `hash.secret`, the panel login and the panel password come from a silent prompt on the terminal, or from `--secrets-file` (three lines in that order; the file must belong to you and have mode 600, otherwise the script refuses it). Nothing is read from arguments or the environment, nothing is printed, and the signature, the panel password hash and the token are handed to `curl` through a private temp directory that is removed on exit, never through its argument list. A plain `http://` URL is refused (except loopback).

It posts signed reports for `SMOKE-01` (battery 55 at `T2`, the same again, battery 11 at `T1 < T2`, and one with a wrong signature) and prints PASS or FAIL for:

1. ingest accepts a signed report (`accepted: 1`);
2. the resend is not stored twice (`accepted: 0`);
3. an older report is accepted into the history (`accepted: 1`);
4. a wrong signature is rejected with `error.signature`;
5. the fleet row (`data.devices[]`) still shows `batteryPct == 55`, so the older report did not replace the latest;
6. the history (`?hours=1`, so the 100 s gap is never merged into one bucket) gained exactly 2 points, the last two being 11 and 55.

The reports carry `charging: true` and no kiosk requirement, so the only condition SMOKE-01 can raise is `offline`. About 10 minutes after the run it turns red and the panel shows a real pop-up, because no tablet sends reports for it. The script therefore ends by offering to delete SMOKE-01 (`--delete` does it without asking, `--keep` keeps it without asking; also on Ctrl-C or a failed fetch after the reports were posted; deleting removes its health rows with it, and an open alert goes with them). Create the device again before the next run. If you keep it, delete it in the panel (Devices) when done.

The exit code is 0 only if every check passed.

## SQL checks

`plugins/locker24-health/src/test/sql/streak-check.sh`, `panel-check.sh` and `alert-check.sh` run the plugin's real SQL against a throwaway `postgres:16` container (they need Docker and `python3`, about 10 seconds each). Server CI runs all three in the `sql-checks` job, so a failing one blocks the deploy. Locally: `bash plugins/locker24-health/src/test/sql/streak-check.sh` (and the others).

## Device checklist H1-H6 (pilot Tab A9)

This is the canonical H1-H6 numbering; the agent repo's checklist mirrors exactly these. Read them on Functions -> Fleet health. The same steps belong in section 6 of the agent repo's `docs/locker24-pilot-config.md` (section 3 of that file gets the row `health_interval_minutes` | `5` | clamped 1..8).

- **H1. Report arrives.** Within 5 minutes of the agent update the tablet shows on Fleet health with model SM-X110, battery, temperature and both versions (agent `6.40-l24.5`, kiosk app). After a few hours the 24 h history charts render battery, temperature, RSSI and offline periods.
- **H2. Offline turns red and recovers.** Wi-Fi off for 15 minutes. The tablet turns red `offline` 10-11 minutes after its last report and a pop-up alert appears (the webhook receives `raised` if one is set). Wi-Fi on: the queued reports arrive, the history shows the gap as an offline period with points inside it, and the status turns green after a real report.
- **H3. Kiosk-app keys.** The kiosk-app values are not shown in the panel, so verify them in the database. After PostBox publishes its values, `locker_short_uid` equals the locker set in the kiosk app, and after a PIN unlock attempt `last_api_ok_at` moves:
  ```bash
  sudo -u postgres psql hmdm -x -c "SELECT l.locker_short_uid, l.kiosk_app_version, l.kiosk_running, l.kiosk_admin_unlocked, l.report_json::json->'app' AS app FROM locker24_device_health_latest l JOIN devices d ON d.id=l.device_id WHERE d.number='M-01'"
  ```
  `app` holds `last_api_ok_at`, `last_api_error_at`, `last_api_error` and `app_errors_24h` (epoch milliseconds for the timestamps). Run it before and after the PIN attempt, with a report cycle in between (up to the health interval).
- **H4. Alert raised and cleared.** The offline alert of H2 clears about 5 minutes after the first real report. Unplug the charger: yellow `not_charging` within about 2 minutes and an alert after 30 minutes non-green; replug: green, and the alert clears about 5 minutes later.
- **H5. Admin exit of the kiosk.** Push `exitKiosk`: yellow "Kiosk off (admin)" within about 3 minutes (30 s tick, 60 s debounce, 1 min evaluation) and NO alert. Start the kiosk again: green. An accidental loss of lock task (not admin) still turns red `kiosk_not_running` (after 2 consecutive fresh reports) and alerts.
- **H6. Update gap does not go red.** Publish a kiosk app update (or reinstall the same version) while watching: the 5-10 second gap while the app is replaced must NOT turn the tablet red or raise an alert, and a scheduled update window must not leave it yellow or alerting before the window has had its chance.

Record the results in the agent repo's `docs/locker24-supported-devices.md`.

## Deviations from the spec (3.6, 3.7, 4.1)

- **No WorkManager job every 5 minutes.** WorkManager's periodic minimum is 15 minutes. The agent reports on its watchdog tick, plus a 15-minute WorkManager safety net. The safety net is a degraded mode: if the watchdog service is not running the tablet reports only every 15 minutes, so it shows red `offline` between reports.
- **Interval capped at 8 minutes.** `health_interval_minutes` is clamped to 1..8 (not up to 60), so it stays below the 10-minute offline rule. Keep `kiosk_watchdog_seconds` at 60 or less with health on.
- **Signature scheme.** The agent's other plugin calls are unsigned; health reports are signed. The header `X-Request-Signature` is the uppercase hex SHA-1 of `hash.secret + deviceNumber`, the same as the config sync. It is per device, not per body, so a captured signature can be replayed. The MDM has no nginx (iptables redirects 443 to Tomcat's 8443, which serves the ingest directly), so the ingest has NO rate limit: nothing mitigates the replay. The risk is the accepted Headwind signature model, plus disk growth (each POST carries up to 50 reports, kept 30 days) and a forgeable green `latest` row for a dead tablet; see the known gaps below.
- **Not an immediate report on a change.** The agent waits 30 s / 60 s after a charging, network or kiosk change before reporting.
- **Offline alert after 10-11 minutes.** Offline is "no report for more than 10 minutes", evaluated once a minute, measured from `max(last report received, server start)` using the server's receive time only.
- **Alerts go to the panel only.** Pop-ups through the existing alert service. A webhook exists but its URL is empty by default; it must be https, loopback, private, link-local, unique-local, CGNAT and unspecified addresses are rejected on save and the resolved addresses are checked again when sending (a DNS-rebinding window between that check and the connection remains). The URL is never returned by the API.
- **`kiosk_admin_off` never alerts.** Admin exit of the kiosk shows as yellow and is excluded from alerting.
- **`kiosk_not_running` needs 2 consecutive fresh reports.** A single report showing it never turns red, so the 5-10 second gap of a kiosk app update does not. Resent and queued duplicates count once. Real kiosk loss is delayed by about one interval.
- **Version mismatch rule.** Installed kiosk version differs from the configured one: with the scheduled app update window on, it counts only after one full window has ended since the mismatch began and now is outside the window; with it off (or when `from == to`), after 2 hours. A DST spring-forward gap skips to the next day.
- **Yellow to red escalates.** An open yellow alert whose condition turns red is raised to red once, and never lowered.
- **Clearing.** The spec clears an alert when the tablet turns green; here each condition clears after being absent for 5 minutes, and offline clears only after a real report arrives (not after a server restart).
- **Yellow alert rule.** "Yellow for 30 minutes" is implemented as non-green for 30 minutes with the condition yellow.
- **Pop-up cap.** At most 5 pop-ups per customer per rolling 10 minutes (red first), then one summary pop-up; clear events are capped at 5 per evaluation cycle plus a "more recovered" summary (not by the rolling budget). The webhook always gets every event.
- **Summaries are invisible to restricted users.** Summary pop-ups have no device, so users without access to all devices do not see them.
- **Retention at 03:30.** History and cleared alerts older than 30 days are deleted nightly at 03:30 in the plugin time zone (default `Europe/Sarajevo`). The 24 h period shifts by one hour after a DST change until the server restarts.
- **1970-clock history points.** A device clock that is unusable is clamped to 48 hours before receipt; the history endpoint excludes those points (it would otherwise show a sample 48 hours back), while their receipt still counts for offline periods. A genuine 48 hour old queued report is dropped with them.
- **`SpaFilter` `;` rejection is load-bearing.** Guice-servlet matches filters against the raw URI including `;` path parameters, Tomcat and Jersey strip them. Only `SpaFilter` (400 on `;` or `%3B`) stops `/rest/private;x/...`. Never remove it or reorder it after `guice-filter`. This is an upstream gap, together with the missing `InternalRestModule` that left `/rest/internal/alert` open; both fixes are in `locker24-core-changes.md`. The internal endpoint also checks the caller inside the resource.
- **Deploys.** Every push to `main` that passes CI is deployed automatically (docs-only pushes excepted), instead of a manual deploy on the droplet.
- **Plugin README location.** The plugin's own notes are in `plugins/locker24-health/README.md`; there is no separate file under `docs/`.

## Known gaps / later

Before the fleet-wide migration (Plan 3):

- **Per-device ingest rate cap.** The public ingest has no rate limit at all (no nginx on the MDM; Tomcat serves 443 -> 8443 through iptables). Cap accepted history rows per device per hour in `IngestService`, or rate-limit at the iptables level.
- One rejected report wipes the agent's 24 h queue; DROP drains one report per POST in probe mode; polling keeps the panel session alive.
- Rollback leftovers on the agent (queue file, prefs, periodic job) are only cancelled by a health-free agent build.
- Every push to `main` restarts Tomcat whenever it lands (no quiet-hours gate): fine while the MDM is not in production use; MQTT drops and config pushes pause once the fleet is on it.
- Pin `actions/checkout` to a SHA in the deploy job (that step has the SSH key in its environment).

Later, harmless now:

- Version rule is strict string inequality ("installed newer than configured" also goes yellow); check M-01's configured kiosk version at deploy time.
- Summary pop-ups do not consume the rolling pop-up budget.
- Upstream `AlertService` leaks a Jersey client per alert and has no timeouts (3 of 10 shared `submitTask` threads can block).
- Disabling the plugin for a customer freezes its last status and open alerts.
- Health scheduling shares core's 2-thread scheduler; a slow retention night holds one thread.
- A device moved to another customer keeps the old customer on its rows until the next report.
- The committed tablet `pubspec.yaml` says `1.2.0+15` while the shipped APK is `1.2.8+23` (set with `--build-name/--build-number`); write the exact build command into the tablet repo's release notes.
- `wifi_ssid` is null whenever location is off.
- Show the agent's `app` group (and wifi, ip, mqtt, uptime, watchdog restarts, last config sync) on the device page; today they are only in `report_json`.
- A Guice smoke test that builds the plugin modules with stub core bindings.
- Streak-check DDL is hand-copied from the changelog; a changelog edit is not caught by CI.
- Smaller items: device numbers sort as strings in pop-up order, `received_at` range is unindexed, no plain `device_id` index on alerts, the device page loads the whole fleet, no lifecycle flush on the agent, the template `widget_test` of the tablet still fails.
