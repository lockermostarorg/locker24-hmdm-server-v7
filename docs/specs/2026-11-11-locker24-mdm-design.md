# Locker24 MDM: design

Date: 2026-11-11
Status: approved in conversation, section by section; waiting for review of this written version

## 1. Goal

Run our own mobile device management for the locker kiosk tablets (Samsung Galaxy Tab A9, SM-X110) instead of Headwind's paid editions, Headwind Cloud or Bento. We host it, we own the code, and we can still take Headwind's security fixes.

### Success criteria

1. A factory-reset tablet becomes a working kiosk for its tenant with one QR scan and no other setup in the MDM.
2. A new tablet app release reaches every tablet of a tenant silently. Each tablet stays in kiosk mode the whole time, with no Samsung lock screen and nobody on site.
3. A new agent release reaches the tablets the same way.
4. From the panel we can see, for every tablet, whether it is healthy, and we get an alert within 10 minutes when it is not.
5. Headwind security releases can be merged and deployed within days, normally without manual conflict resolution.

### Out of scope

- Remote view, remote control and remote screenshots. These are a separate project after this one. The design leaves room for them as one more agent-side app and one more server plugin.
- Supporting tablets still enrolled in the old Headwind server (`hwmdm.locker24.app`). They are migrated by re-enrolment, see section 9.
- Multi-tenant panel accounts. Only our team uses the panel.
- Showing tablet health inside locker24 admin. This is a possible later integration.

## 2. Components and repositories

| Component | Repository | Origin | Our changes live in |
|---|---|---|---|
| Panel + API ("server") | `lockermostarorg/locker24-hmdm-server-v7` | fork of `h-mdm/hmdm-server-v7` (Java 17 target, Tomcat 10+, Angular) | new plugins under `plugins/`, branding config |
| Agent (launcher, device owner) | `lockermostarorg/locker24-hmdm-android` | fork of `h-mdm/hmdm-android` | `pro/` package, new `locker24` product flavor, new classes under `com.hmdm.launcher.locker24` |
| Kiosk app | `locker24-tablet` (existing) | ours | already done on the `hpmostar-mdm` / `hpmostar-qa-mdm` branches (MDM handover) |

Both forks keep Headwind as the remote `upstream`, fetch-only (push URL disabled). Our development happens on `main` (server) and `master` (agent).

### Licensing and naming

- Headwind's code is Apache-2.0. We keep `LICENSE` and `NOTICE` and the existing copyright headers. Files we add carry no Headwind header.
- The product is called **Locker24 MDM**. Panel title, logo, app label and QR enrolment screens do not say "Headwind MDM". The Apache licence does not grant trademark rights.

## 3. Agent

### 3.1 Build variant

- A new product flavor `locker24` is added next to the existing `opensource` flavor. `defaultConfig` and the `opensource` flavor stay unchanged, so upstream merges of `build.gradle` stay trivial.
- Flavor values:
  - `applicationId` `app.locker24.mdm`
  - `BASE_URL` and `SECONDARY_BASE_URL` `https://mdm.locker24.app`
  - `MQTT_PORT` 31000
  - app label "Locker24 MDM"
- Release signing:
  - Our own keystore, read from `keystore.properties` (gitignored), with the same pattern as `locker24-tablet`.
  - CI gets the keystore from GitHub Secrets.
  - A release build without the keystore fails. It does not fall back to debug signing.

### 3.2 Kiosk (filling in `pro/ProUtils.java`)

The open-source launcher already calls the kiosk hooks from `MainActivity`, `BootReceiver`, `ScreenOffReceiver`, `GetServerConfigTask`, `ConfigUpdater`, `DeviceInfoProvider` and `PluginApiService`. Only the bodies in `ProUtils` are stubs. We implement them there and keep the method signatures, so upstream call sites keep working.

| Method | Behaviour |
|---|---|
| `kioskModeRequired` | `true` when the current config has `kioskMode` on and a non-empty `mainApp`. |
| `isKioskAppInstalled` | `mainApp` is installed and launchable. |
| `isKioskModeRunning` | `ActivityManager.getLockTaskModeState() == LOCK_TASK_MODE_LOCKED`. |
| `getKioskAppIntent` | Launch intent for `mainApp` with `FLAG_ACTIVITY_NEW_TASK`. |
| `startCosuKioskMode` | Does the following in order: (1) `setLockTaskPackages` with launcher + `mainApp` + any app the config marks as allowed in kiosk; (2) applies the lock task features (next row); (3) `setKeyguardDisabled(true)` and `setStatusBarDisabled(true)`; (4) starts `mainApp` with `ActivityOptions.setLockTaskEnabled(true)`. Returns `false` on any failure, and the launcher then falls back to its normal desktop and logs the error. |
| `updateKioskOptions` | Maps the configuration flags (home, recents, notifications, status bar info, power button lock, screen lock) to `setLockTaskFeatures`. |
| `updateKioskAllowedApps` | Recomputes `setLockTaskPackages` after a config change without leaving lock task. |
| `unlockKiosk` | Clears lock task packages, re-enables keyguard and status bar, and stops lock task. Only reachable through the existing password dialog or the existing `exitKiosk` push command. |
| `preventStatusBarExpansion`, `preventApplicationsList` | Return `null` on Android 9+ (lock task features cover it). |
| `processConfig` | Applies the device-owner policies from 3.4. |
| `isPro` | Stays `false`. |

Other stubs (`initCrashlytics`, `processLocation`, `checkUsageStatistics`, `checkAccessibilityService`) stay as they are.

### 3.3 Staying in kiosk across updates, crashes and reboots

1. The agent installs a new kiosk app APK silently (it is device owner; this is existing Headwind behaviour).
2. Android stops the kiosk app. Its package stays in the lock task allowlist, so the device stays locked and the launcher (HOME, also allowlisted) comes to the front.
3. The launcher's existing `onResume` → `kioskModeRequired` → `startKiosk` path starts the kiosk app again.
4. Two safety nets, in a new class `com.hmdm.launcher.locker24.KioskWatchdog`:
   - A `PACKAGE_REPLACED` / `MY_PACKAGE_REPLACED` receiver that restarts the kiosk app after an update.
   - A check every 30 seconds while kiosk mode is required. If the kiosk app is not in the foreground for two checks in a row, it is started again. The interval is a config app setting `kiosk_watchdog_seconds`.
5. On boot, the existing `BootReceiver` already starts the launcher; with `kioskModeRequired` now true it enters kiosk.
6. When the agent updates itself, the new process starts through `MY_PACKAGE_REPLACED` and HOME and re-enters kiosk. This case gets its own test on a device (section 8).

The kiosk app (`locker24-tablet`) already checks `isLockTaskPermitted` and then skips its own accessibility-overlay kiosk. No further change is needed there.

### 3.4 Device-owner policies

Applied in `processConfig`; each one can be switched off with a config app setting.

- User restrictions:
  - `DISALLOW_SAFE_BOOT`
  - `DISALLOW_FACTORY_RESET`
  - `DISALLOW_ADD_USER`
  - `DISALLOW_USB_FILE_TRANSFER`
  - `DISALLOW_MOUNT_PHYSICAL_MEDIA`
- `STAY_ON_WHILE_PLUGGED_IN` set to AC + USB.
- System updates: `SystemUpdatePolicy.createWindowedInstallPolicy(02:00, 05:00)`.

### 3.5 Scheduled reboot

New class `com.hmdm.launcher.locker24.ScheduledReboot`:
- Reads the config app setting `reboot_time` (`HH:mm`, empty = off).
- Uses an exact alarm to call `DevicePolicyManager.reboot()`.
- Re-arms after boot and after config changes.

### 3.6 Health reporting

New class `com.hmdm.launcher.locker24.HealthReporter`:
- A WorkManager job every 5 minutes (config app setting `health_interval_minutes`).
- An immediate report when the charging state, network or kiosk state changes.
- Sent as JSON to the Locker24 Health plugin endpoint (section 4.1), using the same device authentication the agent already uses for its other plugin calls.

| Group | Fields |
|---|---|
| Power | battery %, charging (bool + source), battery temperature °C, battery health |
| System | free storage MB, free RAM MB, uptime s, Android version, security patch level |
| Network | Wi-Fi SSID, RSSI dBm, IP, MQTT connected (bool), last successful config sync (epoch ms) |
| Kiosk | kiosk running (bool), foreground package, kiosk app version name + code, agent version name + code, kiosk app restarts by the watchdog in the last 24 h |
| App | the latest values the kiosk app published (3.7) |

Reports that fail are queued, up to 288 reports (24 h at the default interval), and sent when the device is back online.

### 3.7 Health from the kiosk app

- The kiosk app publishes a small key-value map through the agent's existing `PluginApiService` (AIDL; Headwind's `LibTest` repo shows how to integrate it).
- Keys:
  - `locker_short_uid`
  - `last_api_ok_at`
  - `last_api_error_at`
  - `last_api_error`
  - `app_errors_24h`
- The agent stores the latest map and includes it in every health report.
- The kiosk-app side is one small change in `locker24-tablet`, described in that repo's plan.

## 4. Server

Our server work is the v7 Locker24 plugins in this section plus branding (section 4.4). Headwind core code is changed only when a plugin hook is missing, and every such change is listed in `docs/locker24-core-changes.md` to make upstream merges easy to review.

### 4.1 Plugin `locker24-health`

- **Ingest endpoint:** the endpoint the agent calls to report health (3.6). It stores the latest report per device and appends it to the history table.
- **Tables:**
  - `locker24_device_health_latest`: one row per device.
  - `locker24_device_health_history`: rows older than 30 days are deleted by a nightly job.
- **Fleet dashboard page:**
  - One row per tablet: status light, tenant (configuration), device group, locker short UID, last seen, battery, temperature, kiosk app version, agent version, kiosk state.
  - Filter by tenant and status. Clicking a row opens the device's history charts for battery, temperature, RSSI and offline periods.
- **Status rules**, evaluated every minute:

| Condition | Status |
|---|---|
| no report for > 10 min | red, "offline" |
| kiosk required but not running | red |
| battery < 20 % or not charging | yellow (red below 10 %) |
| battery temperature > 45 °C | yellow (red above 50 °C) |
| free storage < 1 GB | yellow |
| installed kiosk app version ≠ configured version for > 2 h, outside the update window | yellow |
| otherwise | green |

- **Alerts:**
  - Raised when a device turns red, or stays yellow for 30 min.
  - Cleared when it turns green.
  - Delivery: v7's WebSocket pop-up alerts, plus an optional outgoing webhook (a URL set in plugin settings) with a JSON body, for Slack or email later.
  - At most one alert per device and condition until it clears.

### 4.2 Tenants

- One configuration per tenant: `hpmo`, `hpmo-qa`, `post`, `locker24`. Each holds that tenant's kiosk app, kiosk settings, update window and app settings.
- One device group per tenant with the same name.
- Enrolment QR codes are generated per configuration. The agent already reads the `com.hmdm.CONFIG` and `com.hmdm.GROUP` extras, and the panel can add custom QR extras, so a scanned tablet lands in the right configuration and group automatically.

### 4.3 Releases: immediate, scheduled and staged

- **Immediate or scheduled:** reuse the configuration's existing "schedule app update" window (`scheduleAppUpdate`, `appUpdateFrom`, `appUpdateTo`). Window off = install immediately. Window on = install silently inside the window. Default for production tenants: on, 02:00–05:00.
- **Staged rollout:**
  - Each tenant has a second configuration `<tenant>-canary` containing 1–2 tablets.
  - Plugin `locker24-releases` adds a Releases page: per tenant it shows the kiosk app version in canary and in production, and the health of the canary tablets since the canary version was set.
  - A **Promote** button copies the canary configuration's kiosk app version to the tenant configuration. It writes an audit log entry through the existing audit plugin.

### 4.4 Remote commands

Use Headwind's existing push commands from the device page:
- `reboot`
- `clearAppData`
- `runApp`
- `exitKiosk`
- a `broadcast` that the kiosk app handles as "restart yourself" (the watchdog then brings it back)

The kiosk app's logs come through Headwind's existing `devicelog` plugin, which the kiosk app writes to through the same `PluginApiService`.

### 4.5 Branding

- Product name, logo, favicon and primary colours are set in v7's ui-library theme files and `build.properties`, in one commit that is easy to re-apply after a merge.
- Our plugin pages use v7's ui-library components only.

## 5. Hosting

- Droplet `locker24-mdm-v7`:
  - IP 159.89.110.163 (private 10.114.0.11)
  - FRA1, 2 vCPU / 4 GB / 80 GB, Ubuntu 24.04
  - DigitalOcean backups on
- DNS: `mdm.locker24.app` A → 159.89.110.163 (done).
- Software:
  - OpenJDK 21, Tomcat 10 (Ubuntu package), PostgreSQL 16, certbot
  - Headwind's MQTT broker on :31000
- First install: Headwind's `hmdm_install.sh` from our fork. Release builds are produced by CI; the server never runs Maven after the first install.
- Firewall (ufw): 22, 80, 443, 31000 only. Tomcat's plain HTTP port is bound to localhost.
- Secrets set at install and never left at installer defaults:
  - DB password, `hash.secret`, JWT secret
  - `secure.enrollment=1`
  - an admin password that is not the default
- Backups:
  - Nightly `pg_dump` and a tarball of the files directory, kept for 14 days on the droplet.
  - DigitalOcean weekly backups.
- The old server `hwmdm.locker24.app` stays on 5.41.4 until its last tablet has migrated, then gets a final backup and is shut down.

## 6. Build and deploy

- **Server CI** (GitHub Actions):
  - On every push to `main`: build the server and plugins, run Headwind's tests and ours.
  - On a tag `l24-v<x.y.z>`: attach the WAR and the plugin WARs to a GitHub release.
- **Server deploy:** a script `deploy/deploy.sh <tag>`, run by us:
  1. Download the release.
  2. `pg_dump`.
  3. Replace the WARs.
  4. Restart Tomcat.
  5. Wait for HTTP 200 and the MQTT port.
  6. Print the panel version.
- **Agent CI:**
  - On every push: build and run unit tests.
  - On a tag: a signed release APK of the `locker24` flavor attached to a GitHub release, then uploaded to the panel as a new app version by hand.
- **Upstream watch:**
  - A weekly workflow in each repo compares `upstream` tags with what has been merged.
  - For a new tag it opens a PR `Merge Headwind <tag>` that merges the tag into a branch and runs CI.
  - Security releases are merged and deployed within a week.

## 7. Agent and server versioning

- Agent `versionName` = `<headwind version>-l24.<n>` (for example `6.40-l24.1`).
- `versionCode` = Headwind's code × 100 + n, so it always increases across both upstream merges and our own releases.
- Server releases are tagged `l24-v<headwind version>.<n>`.

## 8. Testing

- **Agent, unit tests (JVM):**
  - the kiosk decision logic (`kioskModeRequired`, mapping configuration flags to lock task features)
  - the watchdog's two-misses rule
  - parsing of the reboot time
  - the health report builder and its offline queue
- **Agent, device checklist** on a Tab A9 before each release:
  1. QR enrolment from factory reset.
  2. Kiosk on first start, with no accessibility prompt.
  3. Escape attempts: home, back, recents, status bar, power menu.
  4. Silent kiosk app update: returns to kiosk.
  5. Silent agent update: returns to kiosk.
  6. Reboot.
  7. Power cut.
  8. Wi-Fi off for 15 minutes, then on: queued health reports arrive.
  9. Scheduled reboot fires.
  10. `exitKiosk` with the password works.
- **Server:**
  - Plugin unit tests for the status rules and promote.
  - Headwind's tests still pass after every upstream merge.
  - A smoke script that enrols an emulator, posts a health report and checks the dashboard API.
- **Rollout order for every agent or server release:** `hpmo-qa` → canaries → production tenants.

## 9. Migrating tablets

1. Pilot: one `hpmo-qa` tablet for one week. Success = green dashboard for the week, plus one immediate and one scheduled kiosk app update without leaving kiosk.
2. Per tablet, on site (about 15 minutes):
   - factory reset
   - scan the tenant QR
   - set the locker in the kiosk app
   - a test PIN unlock
3. Order: `hpmo-qa` → `post` → `locker24` → `hpmo`.
4. Each migrated tablet is deleted from the old server so the two device lists stay accurate.

## 10. Rollback

- Server:
  1. Stop Tomcat.
  2. Restore the `pg_dump` taken by the deploy script.
  3. Put back the previous WARs.
  4. Start Tomcat.
- Agent and kiosk app: publish the previous build again with a higher `versionCode`, because Android refuses downgrades.

## 11. Later projects

- Remote view, control and screenshots: an agent-side helper app plus a server plugin. On Samsung with Android 15, unattended control needs the device-owner Knox remote-control path; investigate before designing.
- Tablet health next to each locker in locker24 admin, via a webhook or a small read API from the health plugin.
