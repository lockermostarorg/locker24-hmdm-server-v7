# Locker24 MDM Plan 2: Health Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every tablet reports its health every 5 minutes (queued while offline), the kiosk app adds its own five values, and the panel shows a fleet dashboard with status lights, history charts and alerts within about 10 minutes of a problem.

**Architecture:** The agent collects a snapshot on its watchdog tick (and soon after a charging, network or kiosk change), writes it to an on-disk queue of 288 reports, and a WorkManager job uploads the queue oldest-first to a new public endpoint of the server plugin `locker24-health`, signed like the config sync. The plugin stores the latest report and a 30-day history, evaluates the status rules every minute, raises alerts through v7's existing `AlertService` (alerts table + WebSocket pop-up) and an optional webhook, and serves an Angular micro-frontend dashboard. The kiosk app hands its five values to the agent through one new method appended to the existing `PluginApiService` AIDL.

**Tech Stack:**
- Agent: Java, Android minSdk 26 / targetSdk 34, Gradle 8.13, WorkManager 2.9.1, Retrofit 3 + OkHttp, Jackson 2.21, JUnit 4.12.
- Server: Java 17, Tomcat 10, Guice, Jersey, MyBatis, Liquibase, PostgreSQL 16, JUnit 4.13.1 + Mockito 1.10.19; Angular 20 native-federation micro-frontend with `hmdm-ui-kit` (incl. its `hmdm-chart`, Chart.js).
- Kiosk app: Flutter 3.35.5 via the GLOBAL install `/Users/mirsadhalilcevic/Applications/flutter/bin/flutter` (NOT fvm: fvm's 3.47.7 needs Gradle 8.14, these branches have 8.10), Kotlin, AIDL.

**Spec:** docs/specs/2026-11-11-locker24-mdm-design.md (sections 3.6, 3.7, 4.1)

**Repos and branches:**
- Server: `/Users/mirsadhalilcevic/Apps/locker24/locker24-hmdm-server-v7`, new branch `feat/locker24-health` off `main` (tree is clean).
- Agent: `/Users/mirsadhalilcevic/Apps/locker24/locker24-hmdm-android`. Another agent is committing 6.40-l24.4 on `feat/locker24-kiosk`. Wait for that commit, then `git worktree add ../locker24-hmdm-android-health -b feat/locker24-health feat/locker24-kiosk` and work only in that worktree. Copy the gitignored `locker24.properties` into the worktree; never print it.
- Kiosk app: `/Users/mirsadhalilcevic/Apps/locker24/locker24-tablet`. The main checkout holds the user's uncommitted work on `post`: never check out there. Use `git worktree add ../locker24-tablet-hpmoqa hpmostar-qa` (and later `../locker24-tablet-hpmo hpmostar`). Copy the gitignored `android/key.properties` and `android/app/upload-keystore.jks` into each worktree (never `.fvmrc`); never print them.

## Global Constraints

- Product name in anything a user sees (panel pages, menu, alert text, webhook body, kiosk app): **Locker24 MDM**. Never "Headwind". Keep `LICENSE`, `NOTICE` and existing copyright headers; new files get no Headwind header.
- Agent: Java, minSdk 26, targetSdk 34, Gradle 8.13, JUnit 4.12 only (no Robolectric, no Mockito); pure classes must not touch `Context` so they run on the JVM. New classes live in `com.hmdm.launcher.locker24` (the spec names `com.hmdm.launcher.locker24.HealthReporter`).
- Agent version `6.40-l24.5`, versionCode `1540005` (1540004 belongs to the concurrent bugfix).
- `./gradlew assembleOpensourceDebug` keeps building.
- Agent Headwind core files touched by this plan, and only these: `app/build.gradle` (version), `app/src/main/aidl/com/hmdm/IMdmApi.aidl` and `lib/src/main/aidl/com/hmdm/IMdmApi.aidl` (one appended method), `service/PluginApiService.java` (one method), `util/PushNotificationMqttWrapper.java` (one getter). Each is listed in the agent repo's `docs/locker24-core-changes.md`.
- Server: Java 17 (root pom `java.version`). The plugin lives in `plugins/locker24-health`, Java package `com.hmdm.plugins.locker24health` (`PluginJarList` only scans `com.hmdm` for `PluginConfiguration` implementations), plugin identifier `locker24-health`, permission `plugin_locker24_health_access`, REST prefixes `/rest/public/plugin-locker24-health/*` and `/rest/private/plugin-locker24-health/*`, dev port 4224.
- Server core files touched: `plugins/pom.xml` (one `<module>` line) and `server/pom.xml` (one single-line dependency, formatting kept). Both are listed in this repo's `docs/locker24-core-changes.md`. Alerts use the existing `com.hmdm.service.AlertService`, so no core Java change.
- Status thresholds, exactly as spec 4.1: no report for > 10 min = red `offline`; kiosk required but not running = red; battery < 20 % = yellow, < 10 % = red; not charging = yellow; temperature > 45 °C = yellow, > 50 °C = red; free storage < 1 GB (1024 MB) = yellow; installed kiosk version ≠ configured version for > 2 h outside the update window = yellow; otherwise green.
- Alerts: raised when a condition is red, or when the device has been non-green for 30 min and the condition is yellow. At most one open alert per device and condition. Health history kept 30 days. Agent queue: 288 reports. Default interval 5 min (`health_interval_minutes`).
- The JSON contract fixed in Task 1 is used unchanged by the server (Task 6) and by the kiosk app keys (Task 13).
- Kiosk app scope: only `hpmostar-qa` (`com.hpmoqa.tablet_client`, "PostBox Client QA") and `hpmostar` (`com.hpmo.tablet_client`, "PostBox"). Finish on `hpmostar-qa`, then port by hand to `hpmostar` (no cherry-pick). No PostHog. Always the global `flutter` (3.35.5), never `fvm`; if a build rewrites `pubspec.lock`, `analysis_options.yaml` or `android/gradle.properties`, revert them before committing.
- Never print or commit secrets: `hash.secret` / `REQUEST_SIGNATURE`, `LIBRARY_API_KEY`, keystore passwords, panel passwords, and the webhook URL (it may carry a token). Scripts read them from a silent prompt; logs never contain them.
- The implementer writes and tests the server code locally. The user runs every change on the droplet: build, WAR replacement, Tomcat restart (which applies Liquibase), panel settings and APK uploads. Restarts only in quiet hours (nightly from midnight, or Fri 17:00 to Mon 06:00).
- Commits: plain messages, no Co-Authored-By trailer, and no mention of AI, assistants or tools anywhere (messages, comments, docs).

## User decisions (2026-11-16)

- Admin exit of kiosk: no alert; shown as yellow `kiosk_admin_off`.
- Not charging = yellow even at 100 % (tablets must always be on power).
- Alerts: panel pop-ups only; webhook built but URL left empty.
- Plugin time zone default `Europe/Sarajevo`; smoke device `SMOKE-01` in its own empty configuration is OK.

## Review Focus

1. **Device clock wrong or skewed.** Right after boot the clock can read 1970, or it can be minutes off. "Last seen" and the offline rule use only the server's receive time. Each report's `ts` is moved onto the server clock using the batch's `sent_at`, and the shift is clamped to 0..48 h. Pinned by Task 6 `ClockCorrectionTest` and by Task 8 `offlineUsesServerReceiveTimeOnly`.
2. **Offline queue overflow, ordering and duplicates.** At 289 reports the oldest is dropped. Upload goes oldest-first in batches of 50 and keeps the batch on a transient failure. The server ignores a repeated `(device, ts)`, and an older report never replaces the latest one. Pinned by Task 2 `HealthQueueTest` and Task 7 `IngestServiceTest.onlyNewestOfBatchUpdatesLatest`. The SQL guards are checked by Task 16's smoke script (older-after-newer and resend).
3. **Report from an unknown, deleted or unsigned device.** It is rejected with `error.notfound.device` or `error.signature` and nothing is stored. The agent drops that batch instead of retrying it forever. A deleted device's rows go with it (`ON DELETE CASCADE`). Pinned by Task 7 `HealthPublicResourceTest` and Task 2 `UploadOutcomeTest`.
4. **Kiosk app not bound, old kiosk app, or old agent.** A missing `app` group never changes the status. A foreign app cannot publish values. A kiosk app talking to an agent without the new method gets `false` and carries on. Pinned by Task 3 `HealthValuesTest`, Task 8 `missingAppValuesNeverChangeStatus` and Task 13 `no agent never throws`.
5. **Alert storms and flapping.** These come from a server restart or deploy, a site losing Wi-Fi, a value hovering at a threshold, or the 5-second kiosk gap during an app update. The guards:
   - offline is measured from `max(lastReceived, serverStart)`;
   - at most 5 pop-ups per cycle and per customer, plus one summary;
   - one open alert per device and condition, also enforced by a partial unique index;
   - an alert clears only after its condition has been absent for 5 min;
   - the agent waits 60 s before reporting a kiosk change.

   Pinned by Task 9 `AlertPolicyTest` and Task 2 `HealthScheduleTest`.

---

### Task 1: Health report contract (agent)

**Files:**
- Create: `app/src/main/java/com/hmdm/launcher/locker24/HealthSnapshot.java` (public fields only, no Android types)
- Create: `app/src/main/java/com/hmdm/launcher/locker24/HealthReport.java`
- Create: `app/src/test/resources/health/report-v1.json` (golden file, also copied by Task 6)
- Test: `app/src/test/java/com/hmdm/launcher/locker24/HealthReportTest.java`

**Interfaces:**
- Produces:
  - `HealthReport.toJson(HealthSnapshot s): ObjectNode`
  - `HealthReport.toJsonString(HealthSnapshot s): String`
  - `HealthReport.envelope(List<String> reports, long sentAt): String`
  - helpers `chargeSource(int plugged)`, `batteryHealth(int health)`, `cleanSsid(String)`, `celsius(Integer tenths)`
- The contract. Every key is always present; unknown values are JSON `null`. `app` is `null` until the kiosk app has published.

```json
{"v":1,"ts":1763290000000,"trigger":"schedule",
 "power":{"battery_pct":87,"charging":true,"charge_source":"ac","temperature_c":31.5,"battery_health":"good"},
 "system":{"manufacturer":"samsung","model":"SM-X110","android_build":"AP3A.240905.015.A2.X110XXS5CYJ1","android_version":"15","sdk_int":35,"security_patch":"2026-10-01","free_storage_mb":40960,"free_ram_mb":1536,"uptime_s":86400},
 "network":{"wifi_ssid":"Locker24","rssi_dbm":-61,"ip":"192.168.1.23","mqtt_connected":true,"last_config_sync_at":1763289700000},
 "kiosk":{"required":true,"running":true,"admin_unlocked":false,"foreground_package":"com.hpmoqa.tablet_client","app_package":"com.hpmoqa.tablet_client","app_version_name":"1.2.8","app_version_code":23,"agent_version_name":"6.40-l24.5","agent_version_code":1540005,"watchdog_restarts_24h":0},
 "app":{"published_at":1763289950000,"locker_short_uid":"HP01","last_api_ok_at":1763289900000,"last_api_error_at":1763280000000,"last_api_error":"unlock HTTP 503","app_errors_24h":2}}
```

  - Upload body: `{"sent_at":<device epoch ms at send>,"reports":[<report>, ...]}`, with 1 to 50 reports, oldest first.
  - `trigger` ∈ `schedule|charging|network|kiosk|boot`.
  - `charging` means external power is connected.
  - `ts`, `sent_at`, `published_at` and all `*_at` fields are device epoch ms.

- [ ] **Step 1: Write the failing test** `HealthReportTest`:
  - `matchesGoldenFile`: a snapshot holding the values above serializes to a JsonNode equal to `report-v1.json` (compared as JsonNode, so key order does not matter).
  - `celsiusFromTenths`: 315 → 31.5; `null` → JSON null.
  - `chargeSourceMapping`: `BATTERY_PLUGGED_AC`→`ac`, `_USB`→`usb`, `_WIRELESS`→`wireless`, `_DOCK`→`dock`, `0`→`none`; `charging` is `plugged != 0`.
  - `batteryHealthMapping`: GOOD→`good`, OVERHEAT→`overheat`, DEAD→`dead`, OVER_VOLTAGE→`over_voltage`, UNSPECIFIED_FAILURE→`failure`, COLD→`cold`, anything else→`unknown`.
  - `ssidCleaning`: `"\"Locker24\""`→`Locker24`; `<unknown ssid>`→null; null→null.
  - `nullsStayExplicit`: with `rssiDbm == null`, `network.has("rssi_dbm")` and `isNull()`.
  - `appNullUntilPublished`: `appValues == null` gives `"app": null`.
  - `envelopeKeepsOrder`: `envelope(["{\"ts\":1}","{\"ts\":2}"], 99)` gives `{"sent_at":99,"reports":[{"ts":1},{"ts":2}]}`.
- [ ] **Step 2: Run, expect failure:** `./gradlew testOpensourceDebugUnitTest --tests 'com.hmdm.launcher.locker24.HealthReportTest'`. Expected: compilation failure (`HealthReport` missing).
- [ ] **Step 3: Implement** `HealthSnapshot` (one public field per contract value, boxed types so they can be null; `Map<String,Object> appValues` plus `Long appPublishedAt`) and `HealthReport` (a Jackson `ObjectMapper` building `ObjectNode`s with `putNull` for null values; `envelope` embeds each report with `readTree` so they are not double-encoded).
- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit:** `git add app/src/main/java/com/hmdm/launcher/locker24/HealthSnapshot.java app/src/main/java/com/hmdm/launcher/locker24/HealthReport.java app/src/test && git commit -m "feat: health report contract"`

### Task 2: Queue, upload outcome, counters and schedule (agent, pure)

**Files:**
- Create in `app/src/main/java/com/hmdm/launcher/locker24/`: `HealthQueue.java`, `QueueStore.java` (interface), `UploadOutcome.java`, `RollingCounter.java`, `HealthSchedule.java`
- Test in `app/src/test/java/com/hmdm/launcher/locker24/`: `HealthQueueTest.java`, `UploadOutcomeTest.java`, `RollingCounterTest.java`, `HealthScheduleTest.java`

**Interfaces:**
- Produces:
  - `QueueStore { List<String> load(); void save(List<String> lines); }`
  - `HealthQueue(QueueStore store, int max)` with `MAX = 288`, `BATCH = 50`, `synchronized void add(String report)`, `synchronized List<String> peek(int n)`, `synchronized void remove(int n)`, `synchronized int size()`
  - `enum UploadOutcome.Result { SENT, DROP, RETRY }`, `UploadOutcome.classify(int httpCode, String bodyStatus, String bodyMessage)` (httpCode `-1` = I/O error)
  - `RollingCounter.record(List<Long> stamps, long now)`, `RollingCounter.count(List<Long> stamps, long now)` (`WINDOW_MS = 86_400_000`, `CAP = 500`)
  - `HealthSchedule.intervalMinutes(String setting)`, `HealthSchedule.isDue(long now, long lastCollectAt, int intervalMin)`, `TRIGGER_DEBOUNCE_MS = 30_000`, `KIOSK_DEBOUNCE_MS = 60_000`

- [ ] **Step 1: Write the failing tests** (an in-memory `QueueStore` in the test):
  - `HealthQueueTest.dropsOldestBeyond288`: add r1..r289 → `size()==288`, `peek(1)==[r2]`.
  - `HealthQueueTest.peeksOldestFirstInBatches`: add 120 → `peek(50)` = r1..r50; `remove(50)` → `peek(1)==[r51]`.
  - `HealthQueueTest.survivesReload`: a new `HealthQueue` on the same store sees the same 120 lines in order.
  - `HealthQueueTest.blankLinesIgnored`: a store returning `["a","","b"]` gives `size()==2`.
  - `UploadOutcomeTest`:
    - `(200,"OK",null)` → SENT
    - `(200,"ERROR","error.notfound.device")`, `(200,"ERROR","error.signature")`, `(200,"ERROR","error.validation")` → DROP
    - `(200,"ERROR","error.internal.server")`, `(200,"ERROR","error.resource.disabled")`, `(404,null,null)`, `(500,null,null)`, `(-1,null,null)` → RETRY (a 404 means the plugin is not deployed yet).
  - `RollingCounterTest.countsLast24h`: stamps `[now-86_400_000, now-86_399_999, now]` → 2.
  - `RollingCounterTest.capsAt500`: 600 records keep the newest 500.
  - `RollingCounterTest.dropsStampsFromTheFuture`: a stamp at `now+61_000` is pruned (the clock went back).
  - `HealthScheduleTest.intervalDefaultsAndClamps`: `null`, `""` and `"abc"` → 5; `"0"`→1; `"61"`→60; `"15"`→15.
  - `HealthScheduleTest.dueAfterIntervalMinus15s`, interval 5, `last=1_000_000`:
    - `now=last+284_999` → false
    - `now=last+285_000` → true
    - `last=0` → true
    - `now<last` → true
- [ ] **Step 2: Run, expect failure:** `./gradlew testOpensourceDebugUnitTest --tests 'com.hmdm.launcher.locker24.*'`. Expected: compilation failure.
- [ ] **Step 3: Implement.**
  - `HealthQueue` keeps an in-memory list loaded once and saves after every change.
  - `isDue`: `last==0 || now<last || now-last >= interval*60_000 - 15_000`. The 15 s tolerance keeps a 30 s tick from stretching a 5 min cadence to 5.5 min.
- [ ] **Step 4: Run, expect PASS** (same command; the Plan 1 tests still pass).
- [ ] **Step 5: Commit:** `git add app/src/main/java/com/hmdm/launcher/locker24 app/src/test/java/com/hmdm/launcher/locker24 && git commit -m "feat: health queue, upload outcome, rolling counter and schedule"`

### Task 3: Kiosk-app values through PluginApiService (agent)

**Files:**
- Modify: `app/src/main/aidl/com/hmdm/IMdmApi.aidl` and `lib/src/main/aidl/com/hmdm/IMdmApi.aidl`. Append, after `forceConfigUpdateWithCallback`:
  - `// Added by Locker24 MDM. Keep it last of our additions; upstream methods go after it.`
  - `boolean publishHealth(in Bundle values);`
- Modify: `app/src/main/java/com/hmdm/launcher/service/PluginApiService.java`. One override: `return HealthValues.publish(PluginApiService.this, Binder.getCallingUid(), values);`. `getVersion()` stays 119.
- Create: `app/src/main/java/com/hmdm/launcher/locker24/HealthValues.java`
- Create: `docs/locker24-core-changes.md`. List every Headwind file we changed, with the reason and the merge rule:
  - Plan 1: `app/build.gradle`, `pro/ProUtils.java`
  - Plan 2: the two AIDL files, `PluginApiService.java`, `PushNotificationMqttWrapper.java`
  - The AIDL rule: our method keeps its position; on an upstream merge, upstream's new methods go after it, and the kiosk app's copy of the AIDL is updated to the same order.
- Test: `app/src/test/java/com/hmdm/launcher/locker24/HealthValuesTest.java`

**Interfaces:**
- Consumes: the `app` keys of Task 1.
- Produces:
  - `HealthValues.sanitize(Map<String,Object> raw): Map<String,Object>`
  - `HealthValues.isAllowedCaller(String[] callerPackages, String mainApp): boolean`
  - `HealthValues.publish(Context, int callingUid, Bundle values): boolean` (not unit-tested; Android)
  - `HealthValues.latest(Context): Map<String,Object>` or null
  - `HealthValues.publishedAt(Context): Long` or null
  - Storage: SharedPreferences `locker24_health`, keys `app_values` (JSON) and `app_published_at`. A publish replaces the whole map.
- Wire behaviour: a client calling `publishHealth` on an older agent gets `false` (an unknown transaction leaves an empty reply), so clients treat `false` or any exception as "not delivered".

- [ ] **Step 1: Write the failing test** `HealthValuesTest`:
  - `keepsOnlyTheFiveKeys`: `{locker_short_uid, last_api_ok_at, last_api_error_at, last_api_error, app_errors_24h, foo}` → `foo` is dropped.
  - `typesAreEnforced`:
    - `last_api_ok_at` given as `"123"` (String) → dropped
    - given as `123L` or `123` (Integer) → `123L`
    - `app_errors_24h` `-1` → dropped
  - `truncates`: `last_api_error` of 500 chars → 200; `locker_short_uid` of 100 → 64.
  - `onlyTheKioskAppMayPublish`:
    - `(["com.hpmoqa.tablet_client"], "com.hpmoqa.tablet_client")` → true
    - `(["com.other"], "com.hpmoqa.tablet_client")` → false
    - `(null, "x")` → false
    - `(["x"], null)` → false
- [ ] **Step 2: Run, expect failure:** `./gradlew testOpensourceDebugUnitTest --tests 'com.hmdm.launcher.locker24.HealthValuesTest'`. Expected: compilation failure.
- [ ] **Step 3: Implement.** `publish`:
  1. `getPackagesForUid(callingUid)`, then `isAllowedCaller` against the config's `mainApp`.
  2. `Bundle` → `Map` via `keySet()/get()`.
  3. `sanitize`.
  4. If the result is empty, return false; otherwise store it with `commit()` and return true.

  Any exception → false.
- [ ] **Step 4: Run, expect PASS**, then `./gradlew assembleOpensourceDebug assembleLocker24Release`. Expected: both build (the AIDL compiles in `app` and `lib`).
- [ ] **Step 5: Commit:** `git add app/src/main/aidl lib/src/main/aidl app/src/main/java/com/hmdm/launcher/service/PluginApiService.java app/src/main/java/com/hmdm/launcher/locker24/HealthValues.java app/src/test docs/locker24-core-changes.md && git commit -m "feat: kiosk app publishes health values through the plugin API"`

### Task 4: Collect, schedule and upload; agent 6.40-l24.5

**Files:**
- Create in `app/src/main/java/com/hmdm/launcher/locker24/`:
  - `HealthCollector.java`, `HealthReporter.java`, `HealthUploadWorker.java`, `HealthSafetyNetWorker.java`
  - `HealthApi.java` (Retrofit interface + a factory with its own OkHttp client, 15 s timeouts)
  - `FileQueueStore.java` (`filesDir/locker24_health_queue.jsonl`, written to a temp file then renamed)
  - `HealthCounters.java` (prefs `locker24_health`: `watchdog_restarts` CSV, `last_config_sync_at`, `last_collect_at`)
- Modify:
  - `KioskWatchdogService.java`: tick hook, restart counter, change triggers
  - `Locker24Hooks.java`: config sync time, safety-net scheduling
  - `util/PushNotificationMqttWrapper.java`: add `public boolean isConnected() { return client != null && client.isConnected(); }`
  - `app/build.gradle`: locker24 `versionCode 1540005`, `versionName "6.40-l24.5"`

**Interfaces:**
- Consumes: Tasks 1–3; `ProUtils.kioskModeRequired/isKioskModeRunning`; `KioskState.isAdminUnlocked/isLauncherResumed`; `SettingsHelper` (`getBaseUrl`, `getServerProject`, `getDeviceId`, `getConfig().getMainApp()/getPushOptions()`, `getAppPreference`); `CryptoHelper.getSHA1String`.
- Produces:
  - `HealthReporter.onTick(Context)`: collects when `isDue`.
  - `HealthReporter.requestSoon(Context, String trigger, long delayMs)`: a main-looper Handler with one token, so a new request replaces the pending one.
  - `HealthReporter.collectNow(Context, String trigger)`
  - `HealthCounters.recordWatchdogRestart(Context)`, `HealthCounters.setLastConfigSyncAt(Context, long)`
  - `HealthApi.report(@Path("project") String project, @Path("number") String number, @Header("X-Request-Signature") String sig, @Body RequestBody body)` on `POST {project}/rest/public/plugin-locker24-health/report/{number}`. The signature is `CryptoHelper.getSHA1String(BuildConfig.REQUEST_SIGNATURE + deviceId)`, the same scheme as `GetServerConfigTask`.
  - App setting of package `app.locker24.mdm`: `health_interval_minutes`.

- [ ] **Step 1: Implement the collector.** `HealthCollector.collect(ctx, trigger)` fills a `HealthSnapshot`. Each source sits in its own try/catch and gives `null` on failure:

| Fields | Source |
|---|---|
| power group | sticky `ACTION_BATTERY_CHANGED`: `EXTRA_LEVEL`/`EXTRA_SCALE`, `EXTRA_PLUGGED`, `EXTRA_TEMPERATURE` (tenths), `EXTRA_HEALTH` |
| manufacturer, model, android_build, android_version, sdk_int, security_patch | `Build.MANUFACTURER`, `MODEL`, `DISPLAY`, `VERSION.RELEASE`, `SDK_INT`, `VERSION.SECURITY_PATCH` |
| free_storage_mb / free_ram_mb / uptime_s | `StatFs(Environment.getDataDirectory()).getAvailableBytes()/1048576`; `ActivityManager.MemoryInfo.availMem/1048576`; `SystemClock.elapsedRealtime()/1000` |
| wifi_ssid, rssi_dbm | `WifiManager.getConnectionInfo()` when the active network is Wi-Fi. The SSID needs the location permission and location on; null otherwise. |
| ip | first IPv4 of `ConnectivityManager.getLinkProperties(getActiveNetwork())` |
| mqtt_connected | `PushNotificationMqttWrapper.getInstance().isConnected()` when `pushOptions` starts with `mqtt`, else null |
| last_config_sync_at | `HealthCounters` (set in `Locker24Hooks.onConfig`, which runs only after a successful config sync) |
| kiosk required / running / admin_unlocked | `ProUtils.kioskModeRequired`, `ProUtils.isKioskModeRunning`, `KioskState.isAdminUnlocked` |
| foreground_package | own package if `KioskState.isLauncherResumed()`, else `mainApp` if running, else null (the agent has no usage-stats access) |
| app_package / version | `getPackageInfo(mainApp)` → `versionName`, `getLongVersionCode()` |
| agent version | `BuildConfig.VERSION_NAME` / `VERSION_CODE` |
| watchdog_restarts_24h | `HealthCounters` via `RollingCounter.count` |
| app group | `HealthValues.latest` / `publishedAt` |

- [ ] **Step 2: Implement scheduling and upload.**
  - **`KioskWatchdogService`:**
    - `onTick` calls `HealthReporter.onTick` in its own try/finally after the reboot check.
    - `watchdogTick` calls `HealthCounters.recordWatchdogRestart` right before its `relaunch()`. Only the tick relaunch counts, not the package-replaced relaunch.
    - When `lockTaskLocked` differs from the previous tick: `requestSoon("kiosk", KIOSK_DEBOUNCE_MS)`.
    - `onCreate` registers at runtime `ACTION_POWER_CONNECTED/DISCONNECTED` → `requestSoon("charging", TRIGGER_DEBOUNCE_MS)` and `registerDefaultNetworkCallback` (onAvailable/onLost) → `requestSoon("network", TRIGGER_DEBOUNCE_MS)`. It also calls `requestSoon("boot", 60_000)`. Everything is unregistered in `onDestroy`.
  - **`collectNow`:** snapshot → `HealthQueue.add` → `last_collect_at` = now → `HealthUploadWorker.enqueue`.
  - **`HealthUploadWorker`:**
    - `OneTimeWorkRequest`, unique name `locker24_health_upload`, `ExistingWorkPolicy.KEEP`, constraint `NetworkType.CONNECTED`, linear backoff 60 s.
    - `doWork` loops `peek(BATCH)` → `envelope(batch, now)` → POST.
    - Parse `status`/`message` from the body with Jackson, then `UploadOutcome.classify`:
      - SENT → `remove`;
      - DROP → `remove` plus one `RemoteLogger` warning with the message;
      - RETRY → `Result.retry()`.
    - An empty queue → `Result.success()`.
  - **`HealthSafetyNetWorker`:** periodic 15 min (WorkManager's minimum), unique `locker24_health_safety_net`, `KEEP`, calls `HealthReporter.onTick`. It is scheduled from `Locker24Hooks.onConfig`, which also calls `HealthCounters.setLastConfigSyncAt(ctx, System.currentTimeMillis())`.
  - All of this is a no-op unless `BuildConfig.FLAVOR.equals("locker24")`, as in Plan 1.
- [ ] **Step 3: Bump the version** in `app/build.gradle` to `1540005` / `6.40-l24.5`.
- [ ] **Step 4: Build and test:** `./gradlew testOpensourceDebugUnitTest assembleOpensourceDebug assembleLocker24Release`, then `$BT/aapt2 dump badging app/build/outputs/apk/locker24/release/app-locker24-release.apk | head -1`. Expected: all pass, and `package: name='app.locker24.mdm' versionCode='1540005' versionName='6.40-l24.5'`.
- [ ] **Step 5: Commit:** `git add app/src/main/java/com/hmdm/launcher/locker24 app/src/main/java/com/hmdm/launcher/util/PushNotificationMqttWrapper.java app/build.gradle && git commit -m "feat: health reports every 5 minutes with an offline queue, agent 6.40-l24.5"`

### Task 5: Server plugin skeleton, tables and registration

**Files (server repo, branch `feat/locker24-health`):**
- Create `plugins/locker24-health/pom.xml`. It is a copy of `plugins/deviceinfo/pom.xml` with artifactId `locker24-health`, version 0.1.0 and dependencies `platform` + `notification`. It has **no** frontend blocks yet; Task 12 adds them.
- Create `plugins/locker24-health/conf/context.xml` (copy from deviceinfo).
- Create `plugins/locker24-health/src/main/resources/locker24-health.properties` with `plugin.version=${project.version}`.
- Create `plugins/locker24-health/src/main/resources/liquibase/locker24-health.changelog.xml`, with `logicalFilePath="locker24-health.changelog.xml"`.
- Create in `src/main/java/com/hmdm/plugins/locker24health/`:
  - `HealthPluginConfigurationImpl.java`
  - `guice/module/HealthLiquibaseModule.java`, `HealthPersistenceModule.java`, `HealthRestModule.java`
  - `rest/HealthLicenseResource.java`
- Modify: `plugins/pom.xml` (`<module>locker24-health</module>`); `server/pom.xml` (one line: `<dependency><groupId>com.hmdm.plugin</groupId><artifactId>locker24-health</artifactId><version>0.1.0</version><scope>runtime</scope></dependency>`).
- Create: `docs/locker24-core-changes.md` with those two entries.

**Interfaces:**
- Produces:
  - `HealthPluginConfigurationImpl.PLUGIN_ID = "locker24-health"`, root package `com.hmdm.plugins.locker24health`.
  - `HealthRestModule`: protected resource `/rest/private/plugin-locker24-health/*`; public resource `/rest/public/plugin-locker24-health/*`.
  - `GET /rest/private/plugin-locker24-health/license` → `Response.OK(LicenseResponse.genericValidResponse())`. Without it the panel's `isPluginValidGuard` redirects to the licences page.
- Changesets (author `locker24`, context `common`, ids `plugin-locker24-health-16.11.2026-01`…):
  1. `INSERT INTO plugins (identifier, name, description, namelocalizationkey, settingsPermission, functionsPermission, deviceFunctionsPermission, enabledForDevice, icon, baseUrl, baseUrlDev, hasSettingsPage) VALUES ('locker24-health', 'Fleet health', 'Tablet health dashboard and alerts', 'plugin.locker24-health.localization.key.name', 'plugin_locker24_health_access', 'plugin_locker24_health_access', 'plugin_locker24_health_access', FALSE, 'monitor_heart', '/plugins/locker24-health', 'http://localhost:4224', TRUE)`. Rollback: delete it.
  2. Permission `plugin_locker24_health_access` granted to every role (same SQL as deviceinfo's `plugin-deviceinfo-22.10.2019-13:36`).
  3. `locker24_device_health_latest`:
     - key: `device_id INT PRIMARY KEY REFERENCES devices(id) ON DELETE CASCADE`, `customer_id INT NOT NULL`
     - times: `received_at BIGINT NOT NULL`, `measured_at BIGINT NOT NULL`
     - status: `status VARCHAR(8) NOT NULL DEFAULT 'green'`, `status_reasons VARCHAR(256)`, `non_green_since BIGINT`, `version_mismatch_since BIGINT`, `evaluated_at BIGINT`
     - values: `battery_pct SMALLINT`, `charging BOOLEAN`, `temperature_c REAL`, `free_storage_mb INT`, `rssi_dbm SMALLINT`, `kiosk_required BOOLEAN`, `kiosk_running BOOLEAN`, `kiosk_app_package VARCHAR(128)`, `kiosk_app_version VARCHAR(64)`, `agent_version VARCHAR(64)`, `locker_short_uid VARCHAR(64)`, `manufacturer VARCHAR(64)`, `model VARCHAR(64)`
     - `report_json TEXT NOT NULL`
  4. `locker24_device_health_history`:
     - columns: `id BIGSERIAL PRIMARY KEY`, `device_id INT NOT NULL REFERENCES devices(id) ON DELETE CASCADE`, `device_ts BIGINT NOT NULL`, `received_at BIGINT NOT NULL`, `measured_at BIGINT NOT NULL`, `battery_pct SMALLINT`, `charging BOOLEAN`, `temperature_c REAL`, `rssi_dbm SMALLINT`, `kiosk_running BOOLEAN`
     - `UNIQUE (device_id, device_ts)`, index `(device_id, measured_at)`, index `(measured_at)`
  5. `locker24_device_health_alerts`:
     - columns: `id SERIAL PRIMARY KEY`, `device_id INT NOT NULL REFERENCES devices(id) ON DELETE CASCADE`, `customer_id INT NOT NULL`, `condition VARCHAR(32) NOT NULL`, `severity VARCHAR(8) NOT NULL`, `raised_at BIGINT NOT NULL`, `last_present_at BIGINT NOT NULL`, `cleared_at BIGINT`
     - `CREATE UNIQUE INDEX locker24_health_alerts_open ON locker24_device_health_alerts(device_id, condition) WHERE cleared_at IS NULL`
  6. `locker24_health_settings`: `customer_id INT PRIMARY KEY REFERENCES customers(id) ON DELETE CASCADE`, `webhook_url VARCHAR(1024)`, `time_zone VARCHAR(64) NOT NULL DEFAULT 'Europe/Sarajevo'`.

  The table names follow the spec. Headwind's `plugin_<id>_` naming is a convention only; nothing in the platform depends on it.

- [ ] **Step 1: Toolchain.** Maven is NOT installed on this Mac and must not be installed; every `mvn ...` command in this plan runs in Docker (Docker is running locally) from the repo root as `docker run --rm -v "$PWD":/src -v "$HOME/.m2":/root/.m2 -w /src maven:3.9-eclipse-temurin-17 mvn ...`. Define `alias mvn='docker run --rm -v "$PWD":/src -v "$HOME/.m2":/root/.m2 -w /src maven:3.9-eclipse-temurin-17 mvn'` in the shell, or write the full form; `mvn -v` through it must report Maven 3.9+ and JDK 17.
- [ ] **Step 2: Implement** the files above, mirroring deviceinfo's classes:
  - `getPluginModules` = Liquibase + Persistence + Rest modules;
  - `getTaskModules` = `HealthTaskModule`, added in Task 10 (return `Optional.empty()` until then);
  - the persistence module's mapper package is `com.hmdm.plugins.locker24health.persistence.mapper` and its domain package is `...persistence.domain`.
- [ ] **Step 3: Build:** `mvn -q -pl plugins/locker24-health -am -DskipTests package && unzip -l plugins/locker24-health/target/locker24-health-0.1.0.jar | grep -E "changelog|HealthPluginConfigurationImpl"`. Expected: the build succeeds, and both entries are listed. The plugins parent declares the `prod-public-maven` repository, so the `platform` jar resolves.
- [ ] **Step 4: Commit:** `git add plugins/locker24-health plugins/pom.xml server/pom.xml docs/locker24-core-changes.md && git commit -m "feat: locker24-health plugin skeleton and tables"`

### Task 6: Report parsing, validation and clock correction (server, pure)

**Files:**
- Create in `plugins/locker24-health/src/main/java/com/hmdm/plugins/locker24health/`: `model/ParsedBatch.java`, `model/ParsedReport.java`, `ingest/ReportParser.java`, `ingest/ValidationException.java`, `ingest/ClockCorrection.java`
- Create: `plugins/locker24-health/src/test/resources/report-v1.json` (verbatim copy of the agent's Task 1 golden file)
- Test: `.../src/test/java/com/hmdm/plugins/locker24health/ingest/ReportParserTest.java`, `ClockCorrectionTest.java`

**Interfaces:**
- Consumes: the Task 1 contract.
- Produces:
  - `ReportParser.parse(String body): ParsedBatch`; it throws `ValidationException` (message `error.validation`).
  - `ParsedBatch(long sentAt, List<ParsedReport> reports)`
  - `ParsedReport`: typed fields for every contract value, plus `String json` (the report re-serialized).
  - `ClockCorrection.measuredAt(long receivedAt, long sentAt, long ts): long`, `MAX_AGE_MS = 172_800_000`

- [ ] **Step 1: Write the failing tests:**
  - `ReportParserTest.parsesTheContract`: `report-v1.json` wrapped as `{"sent_at":1763290005000,"reports":[...]}` gives:
    - `batteryPct==87`, `temperatureC==31.5`, `kioskAppVersion=="1.2.8"`, `lockerShortUid=="HP01"`
    - `appErrors24h==2`, `rssiDbm==-61`, `freeStorageMb==40960`
  - `rejectsBadEnvelopes`: unsupported `v:2`, an empty `reports`, 51 reports, missing `ts`, a body over 262144 chars, and malformed JSON each throw `ValidationException`.
  - `outOfRangeBecomesNull`:
    - battery 101 → null, battery -1 → null
    - temperature 120.5 → null (valid range -40..100)
    - rssi 5 → null (valid range -127..0)
    - free_storage_mb -3 → null
  - `stringsTruncated`: a 300-char `model` → 64; `last_api_error` → 200.
  - `missingGroupsAreNull`: no `app` key → app fields null; no `network` → network fields null.
  - `ClockCorrectionTest`, with `R` = receive time:
    - `skewRemoved`: `sent=R-3_600_000`, `ts=sent-600_000` → `R-600_000`.
    - `negativeAgeClamps`: `ts>sent` → `R`.
    - `ageCappedAt48h`: `sent-ts = 50 h` → `R-172_800_000`.
    - `unsetDeviceClock`: `ts=1_000`, `sent=301_000` → `R-300_000`.
- [ ] **Step 2: Run, expect failure:** `mvn -q -pl plugins/locker24-health -am -Dtest='ReportParserTest,ClockCorrectionTest' -Dsurefire.failIfNoSpecifiedTests=false test`. Expected: compilation failure.
- [ ] **Step 3: Implement** with Jackson `JsonNode` (lenient: unknown keys ignored). `measuredAt = receivedAt - clamp(sentAt - ts, 0, MAX_AGE_MS)`.
- [ ] **Step 4: Run, expect PASS** (same command).
- [ ] **Step 5: Commit:** `git add plugins/locker24-health && git commit -m "feat: health report parsing, validation and clock correction"`

### Task 7: Ingest endpoint and persistence

**Files:**
- Create in `.../locker24health/`:
  - `persistence/mapper/HealthMapper.java` (MyBatis annotations)
  - `persistence/domain/LatestRow.java`, `HistoryRow.java`
  - `persistence/HealthDAO.java`
  - `ingest/IngestService.java`
  - `rest/HealthPublicResource.java`
- Modify: `guice/module/HealthRestModule.java` (bind the resource).
- Test: `.../rest/HealthPublicResourceTest.java`, `.../ingest/IngestServiceTest.java` (Mockito)

**Interfaces:**
- Consumes: `ReportParser`, `ClockCorrection` (Task 6); `UnsecureDAO.getDeviceByNumber`; `PluginStatusCache.isPluginDisabled`; `@Named("hash.secret") String`; `CryptoUtil.checkRequestSignature`; `SecurityContext.init/release`.
- Produces:
  - `POST /rest/public/plugin-locker24-health/report/{deviceNumber}`, header `X-Request-Signature` = uppercase or lowercase hex `SHA1(hash.secret + deviceNumber)`, body = the Task 1 envelope.
  - Replies, all HTTP 200 like every Headwind endpoint:
    - `Response.OK({"accepted":n})`
    - `Response.ERROR("error.signature")`, `Response.ERROR("error.validation")`
    - `Response.DEVICE_NOT_FOUND_ERROR()`, `Response.PLUGIN_DISABLED()`, `Response.INTERNAL_ERROR()`
  - `IngestService.store(Device device, ParsedBatch batch, long receivedAt): int`
  - `HealthMapper`:
    - `insertHistory(HistoryRow)` with `ON CONFLICT (device_id, device_ts) DO NOTHING`
    - `upsertLatest(LatestRow)` with `ON CONFLICT (device_id) DO UPDATE SET <data columns> WHERE locker24_device_health_latest.measured_at <= EXCLUDED.measured_at`
    - `touchReceived(int deviceId, long receivedAt)`: `SET received_at = GREATEST(received_at, #{receivedAt})`

- [ ] **Step 1: Write the failing tests:**
  - `HealthPublicResourceTest.unknownDeviceRejected`: `getDeviceByNumber("X")` returns null → status ERROR, message `error.notfound.device`; `verify(ingest, never()).store(...)`.
  - `signatureRequired`:
    - a null header → `error.signature`
    - the signature of `"s"+"M-02"` sent for `M-01` → `error.signature`
    - `CryptoUtil.getSHA1String("s"+"M-01")`, in either case → OK
  - `disabledPluginRejected`: `isPluginDisabled("locker24-health")` → `PLUGIN_DISABLED`.
  - `invalidBodyRejected`: 51 reports → `error.validation`, nothing stored.
  - `IngestServiceTest.historyInsertedOldestFirst`: a batch with ts 3, 1, 2 calls `insertHistory` in the order 1, 2, 3.
  - `IngestServiceTest.onlyNewestOfBatchUpdatesLatest`: `upsertLatest` is called once, with the ts=3 report.
  - `IngestServiceTest.receivedAlwaysTouched`: `touchReceived(deviceId, R)` is called even when the batch only holds older reports.
- [ ] **Step 2: Run, expect failure:** `mvn -q -pl plugins/locker24-health -am -Dtest='HealthPublicResourceTest,IngestServiceTest' -Dsurefire.failIfNoSpecifiedTests=false test`. Expected: compilation failure.
- [ ] **Step 3: Implement.** The resource takes the raw body as `String` and checks, in order:
  1. device exists;
  2. signature;
  3. plugin enabled (inside `SecurityContext.init(customerId)` … `finally release`);
  4. parse;
  5. `store`.

  `receivedAt = System.currentTimeMillis()`. The log line is device number plus count, never the body.
- [ ] **Step 4: Run, expect PASS** (same command, plus all earlier plugin tests: drop `-Dtest`).
- [ ] **Step 5: Commit:** `git add plugins/locker24-health && git commit -m "feat: signed health report ingest endpoint"`

### Task 8: Status rules

**Files:**
- Create in `.../locker24health/status/`: `Status.java` (GREEN, YELLOW, RED), `Severity.java` (YELLOW, RED), `Condition.java`, `Finding.java` (record: condition, severity, detail), `DeviceHealth.java`, `ConfiguredKiosk.java` (record: pkg, version, scheduleAppUpdate, from, to), `EvalContext.java`, `Evaluation.java`, `StatusEvaluator.java`
- Test: `.../status/StatusEvaluatorTest.java`

**Interfaces:**
- Produces:
  - `Condition` codes: `offline`, `kiosk_not_running`, `kiosk_admin_off` (never alerts), `battery_low`, `not_charging`, `temperature`, `storage_low`, `kiosk_version`.
  - `StatusEvaluator.evaluate(DeviceHealth h, EvalContext c): Evaluation`
    - `DeviceHealth`: `receivedAt`, `batteryPct`, `charging`, `temperatureC`, `freeStorageMb`, `kioskRequired`, `kioskRunning`, `kioskAppPackage`, `kioskAppVersion`
    - `EvalContext`: `now`, `serverStartedAt`, `ConfiguredKiosk configured` (nullable), `ZoneId zone`, `Long versionMismatchSince`
    - `Evaluation`: `Status status`, `List<Finding> findings`, `Long versionMismatchSince`
  - Constants: `OFFLINE_AFTER_MS = 600_000`, `VERSION_GRACE_MS = 7_200_000`, `LOW_STORAGE_MB = 1024`.

- [ ] **Step 1: Write the failing test** `StatusEvaluatorTest`. Unless a case says otherwise, the health is fine: battery 80, charging, 30 °C, 5000 MB, kiosk required and running, versions equal.
  - `offlineUsesServerReceiveTimeOnly`: `now-receivedAt = 600_000` → GREEN; `600_001` → RED with exactly `[offline]`.
  - `offlineMeasuredFromServerStart`: `receivedAt = start-3_600_000`; `now = start+600_000` → GREEN; `now = start+600_001` → RED.
  - `offlineSuppressesStaleFindings`: offline with battery 5 → findings `[offline]` only.
  - `kioskRule`:
    - `required=true, running=false, admin_unlocked=false` → RED `kiosk_not_running`
    - `required=true, running=false, admin_unlocked=true` → YELLOW `kiosk_admin_off` (user decision 2026-11-16: an admin's deliberate exit never raises a red alert; it is shown on the dashboard as "kiosk off (admin)" and does not alert, so `AlertPolicy` must not raise for `kiosk_admin_off`)
    - `required=false, running=false` → GREEN
    - `required=null` → GREEN
  - `batteryBoundaries`: 20 → GREEN; 19 → YELLOW; 10 → YELLOW; 9 → RED; null → GREEN.
  - `notCharging`: `charging=false` → YELLOW `not_charging`; null → GREEN.
  - `temperatureBoundaries`: 45.0 → GREEN; 45.1 → YELLOW; 50.0 → YELLOW; 50.1 → RED.
  - `storageBoundary`: 1024 → GREEN; 1023 → YELLOW `storage_low`.
  - `kioskVersionNeedsTwoHoursOutsideWindow`. Configured `1.2.8`, installed `1.2.7`, window off:
    - first evaluation → GREEN and `versionMismatchSince == now`;
    - since `now-7_200_000` → GREEN; since `now-7_200_001` → YELLOW `kiosk_version`;
    - with window on 02:00–05:00 Europe/Sarajevo: at 03:00 local → GREEN; at 05:00 local → YELLOW;
    - equal versions → GREEN and `versionMismatchSince == null`;
    - `configured == null` → GREEN and `null`.
  - `windowAcrossMidnight`: window 22:00–02:00, since > 2 h: 23:30 → GREEN, 01:59 → GREEN, 02:00 → YELLOW.
  - `worstSeverityWins`: battery 15 plus 51 °C → RED with both findings.
  - `missingAppValuesNeverChangeStatus`: a `DeviceHealth` built from a report without `app` → GREEN.
- [ ] **Step 2: Run, expect failure:** `mvn -q -pl plugins/locker24-health -am -Dtest=StatusEvaluatorTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- [ ] **Step 3: Implement.**
  - Offline when `now - max(receivedAt, serverStartedAt) > OFFLINE_AFTER_MS`; when offline, return only that finding.
  - The update window is `[from, to)` in `zone`, wrapping past midnight when `from > to`.
  - Version mismatch only when both versions are non-null and differ, and the installed package equals `configured.pkg()`.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit:** `git add plugins/locker24-health && git commit -m "feat: health status rules"`

### Task 9: Alert policy

**Files:**
- Create in `.../locker24health/alert/`: `OpenAlert.java` (record: id, condition, severity, raisedAt, lastPresentAt), `AlertEvent.java`, `AlertDecision.java`, `AlertPolicy.java`
- Test: `.../alert/AlertPolicyTest.java`

**Interfaces:**
- Consumes: `Status`, `Finding`, `Condition` (Task 8).
- Produces:
  - `AlertPolicy.decide(long now, Status status, Long nonGreenSince, List<Finding> findings, Map<Condition,OpenAlert> open): AlertDecision`
    - `AlertDecision`: `List<Finding> raise`, `List<OpenAlert> touch`, `List<OpenAlert> clear`, `Long nonGreenSince`
  - `AlertPolicy.limitPopups(List<AlertEvent> events, int max): List<AlertEvent>`: when `events.size() > max`, the first `max` (red before yellow), then one summary event with `deviceId == null`.
  - Constants: `YELLOW_ALERT_AFTER_MS = 1_800_000`, `CLEAR_AFTER_ABSENT_MS = 300_000`, `MAX_POPUPS_PER_CYCLE = 5`.

- [ ] **Step 1: Write the failing test** `AlertPolicyTest`:
  - `redRaisesImmediately`: a RED `offline` finding with no open alert → raise `[offline]`.
  - `yellowRaisesAfterThirtyMinutes`: `nonGreenSince = now-1_799_999` → no raise; `now-1_800_000` → raise.
  - `oneOpenAlertPerCondition`: an open `battery_low` plus a RED `battery_low` finding → no raise, touch.
  - `clearsAfterFiveMinutesAbsent`: open `temperature` with `lastPresentAt = now-299_999` and the condition absent → no clear; `now-300_000` → clear.
  - `offlineKeepsOtherAlertsOpen`: status RED `[offline]`, open `battery_low` last present 20 min ago → not cleared.
  - `adminKioskOffNeverAlerts`: a YELLOW `kiosk_admin_off` finding with `nonGreenSince = now-3_600_000` → no raise (and it does not count toward any other yellow condition's 30 min).
  - `nonGreenSinceTracking`: GREEN → `null`; YELLOW with `null` → `now`; YELLOW with `t` → `t`.
  - `stormCap`:
    - 12 events (11 red `offline`, 1 yellow `battery_low`), `max=5` → 6 events: 5 red `offline`, then a summary whose message is `"7 more tablets need attention: offline 6, battery_low 1"`;
    - 5 events → the same 5.
- [ ] **Step 2: Run, expect failure:** `mvn -q -pl plugins/locker24-health -am -Dtest=AlertPolicyTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- [ ] **Step 3: Implement** the rules exactly as the tests state them.
- [ ] **Step 4: Run, expect PASS.**
- [ ] **Step 5: Commit:** `git add plugins/locker24-health && git commit -m "feat: health alert policy with dedup, hysteresis and storm cap"`

### Task 10: Evaluation job, alert delivery, webhook and retention

**Files:**
- Create in `.../locker24health/`:
  - `guice/module/HealthTaskModule.java`
  - `job/EvaluationJob.java`, `job/ConfiguredKioskResolver.java`, `job/RetentionJob.java`, `job/RetentionSchedule.java`
  - `alert/AlertText.java`, `alert/WebhookPayload.java`, `alert/WebhookSender.java`, `alert/AlertDispatcher.java`
- Modify: `HealthMapper.java` (evaluation, alert and retention queries); `HealthPluginConfigurationImpl.getTaskModules` → `HealthTaskModule`.
- Test: `.../job/RetentionScheduleTest.java`, `.../alert/AlertTextTest.java`, `.../alert/WebhookPayloadTest.java`

**Interfaces:**
- Consumes:
  - Tasks 7–9
  - `BackgroundTaskRunnerService.submitRepeatableTask/submitTask`
  - `PluginDAO.updatePluginVersion`, `PluginVersionService.getVersion`
  - `UnsecureDAO.getConfigurationById`, `findApplicationVersionById`, `findApplicationById`
  - `ConfigurationMapper.getPlainConfigurationAppsOptimized(configId)`: the configured version is the row with `pkg` = content app pkg and `action == 1`
  - `com.hmdm.service.AlertService.sendAlert(Alert)`: POSTs to `internal.url + /rest/internal/alert`, which inserts into `alerts` and calls `AlertWebsocketEndpoint.publish`. The defaults `http://127.0.0.1:8080` and `127.0.0.1` match Plan 1's loopback 8080.
- Produces:
  - `EvaluationJob.runOnce()`
  - `RetentionJob.purge()`: `DELETE FROM locker24_device_health_history WHERE measured_at < now-2_592_000_000`, and alerts cleared more than 30 days ago.
  - `RetentionSchedule.initialDelayMinutes(ZonedDateTime now, LocalTime at)`
  - `AlertText.message(AlertEvent)`
  - `WebhookPayload.build(List<AlertEvent>, long sentAt): String`
  - Alert levels: red = `Alert.SEVERE` (30), yellow = `Alert.WARNING` (20), cleared = `Alert.INFO` (10). Clears are recorded in the alerts list but do not pop up for users on the default alert level 20.
  - Webhook body:
    ```json
    {"source":"locker24-mdm","sent_at":1763290060000,"events":[{"event":"raised|cleared","severity":"red|yellow","condition":"offline","device_id":5,"device_number":"M-01","configuration":"hpmo-qa","locker_short_uid":"HP01","message":"M-01 (hpmo-qa, locker HP01): offline","at":1763290060000}]}
    ```

- [ ] **Step 1: Write the failing tests:**
  - `RetentionScheduleTest`: at 03:29 → 1; at 03:30 → 1440; at 23:00 → 270 (all Europe/Sarajevo, `at` = 03:30).
  - `AlertTextTest`:
    - `"M-01 (hpmo-qa, locker HP01): battery 9 % (red)"`
    - `"M-01 (hpmo-qa): offline"` when there is no short UID
    - `"M-01 (hpmo-qa): cleared not_charging"`
    - every message is ≤ 1024 chars and has no `Headwind`
  - `WebhookPayloadTest`: the exact keys above; `sent_at` is passed through; no `Headwind` anywhere in the body.
- [ ] **Step 2: Run, expect failure:** `mvn -q -pl plugins/locker24-health -am -Dtest='RetentionScheduleTest,AlertTextTest,WebhookPayloadTest' -Dsurefire.failIfNoSpecifiedTests=false test`.
- [ ] **Step 3: Implement.**
  - **`HealthTaskModule.init()`:**
    - `updatePluginVersion`;
    - records `serverStartedAt`;
    - `submitRepeatableTask(guard(job::runOnce), 1, 1, MINUTES)`;
    - `submitRepeatableTask(guard(retention::purge), RetentionSchedule.initialDelayMinutes(now, 03:30), 1440, MINUTES)`.
    - `guard` catches every `Throwable` and logs it. `scheduleAtFixedRate` would otherwise stop the schedule for good.
  - **`runOnce`:**
    1. Load all latest rows with device number, configuration id and name, and the open alerts.
    2. Resolve `ConfiguredKiosk` once per configuration per run.
    3. Load settings per customer; the time zone defaults to `Europe/Sarajevo`.
    4. Evaluate each device and update `status`, `status_reasons`, `non_green_since`, `version_mismatch_since` and `evaluated_at`.
    5. Apply `AlertPolicy`: insert or touch or clear rows in `locker24_device_health_alerts`. An insert conflicting with the partial unique index means another run won; skip it.
    6. Hand the events to `AlertDispatcher`.
  - **`AlertDispatcher`:**
    - per customer, `limitPopups(events, 5)` → `AlertService.sendAlert`, with `customerId` and `deviceId` set;
    - when `webhook_url` is set, one `WebhookPayload` POST per customer per cycle via `submitTask`, using `java.net.http.HttpClient` with 5 s connect and request timeouts;
    - the log line holds the status code only, never the URL.
- [ ] **Step 4: Run, expect PASS** (all plugin tests: `mvn -q -pl plugins/locker24-health -am test`).
- [ ] **Step 5: Commit:** `git add plugins/locker24-health && git commit -m "feat: per-minute health evaluation, alerts, webhook and 30-day retention"`

### Task 11: Panel REST for fleet, history and settings

**Files:**
- Create in `.../locker24health/`: `rest/HealthResource.java`, `rest/json/FleetRow.java`, `rest/json/HistoryResponse.java`, `rest/json/HealthSettings.java`, `history/OfflinePeriods.java`, `settings/SettingsValidator.java`
- Modify: `HealthMapper.java` (fleet, history and settings queries); `HealthRestModule.java` (bind).
- Test: `.../history/OfflinePeriodsTest.java`, `.../settings/SettingsValidatorTest.java`

**Interfaces:**
- Consumes: `SecurityContext.get()` (`hasPermission`, `getCurrentUser()`: `customerId`, `isAllDevicesAvailable()`); `UnsecureDAO.userHasDeviceAccess`.
- Produces. Every endpoint checks `plugin_locker24_health_access` first, otherwise `Response.PERMISSION_DENIED()`.
  - `GET /rest/private/plugin-locker24-health/devices?configurationId=&status=&model=` → `List<FleetRow>`.
    - A LEFT JOIN from `devices` of the current customer, so tablets that never reported appear with status `unknown` (and never alert).
    - `FleetRow` fields: `deviceId, number, description, configurationId, configurationName, groups` (comma-joined from `deviceGroups`/`groups`), `lockerShortUid, lastSeen` (= `received_at`), `status` (`green|yellow|red|unknown`), `reasons` (array of condition codes), `batteryPct, charging, temperatureC, kioskAppVersion, agentVersion, kioskRequired, kioskRunning, model`.
  - `GET /rest/private/plugin-locker24-health/devices/{deviceId}/history?hours=24` (clamped 1..720) → `{"points":[{"t","batteryPct","temperatureC","rssiDbm","charging","kioskRunning"}],"offlinePeriods":[{"from","to"}]}`. `t` = `measured_at`; offline periods come from `received_at`. A device of another customer, or one without access, → `PERMISSION_DENIED`.
  - `GET/PUT /rest/private/plugin-locker24-health/settings` ↔ `{"webhookUrl","timeZone"}`.
  - `OfflinePeriods.compute(List<Long> receivedAtSorted, long from, long to, long threshold)`
  - `SettingsValidator.validate(HealthSettings)`: throws `IllegalArgumentException`.

- [ ] **Step 1: Write the failing tests:**
  - `OfflinePeriodsTest.gapsOverThreshold`: samples at 0, 5, 25 and 30 min; range 0–60 min; threshold 10 min → `[(5,25),(30,60)]` in minutes.
  - `exactlyThresholdIsNotOffline`: samples 0 and 10 min, range 0–10 → `[]`.
  - `leadingGap`: first sample at 15 min, range 0–20 → `[(0,15)]`.
  - `noSamples`: → `[(from,to)]`.
  - `SettingsValidatorTest`:
    - `webhookUrl` null or `""` → stored as null;
    - `https://hooks.slack.com/services/x` → ok;
    - `http://x`, `ftp://x` and 1025 chars → rejected;
    - `timeZone` `Europe/Sarajevo` → ok; `Mars/Base` → rejected.
- [ ] **Step 2: Run, expect failure:** `mvn -q -pl plugins/locker24-health -am -Dtest='OfflinePeriodsTest,SettingsValidatorTest' -Dsurefire.failIfNoSpecifiedTests=false test`.
- [ ] **Step 3: Implement** the resource and the queries. Fleet filtering happens in SQL by `customer_id`. When the user does not have all devices, filter with `userHasDeviceAccess`.
- [ ] **Step 4: Run, expect PASS** (all plugin tests).
- [ ] **Step 5: Commit:** `git add plugins/locker24-health && git commit -m "feat: panel API for fleet health, history and settings"`

### Task 12: Fleet dashboard micro-frontend

**Files:**
- Create `plugins/locker24-health/src/main/web/`, copied from `plugins/deviceinfo/src/main/web`:
  - project name `locker24-health`, dev port 4224;
  - `federation.config.js` `name: 'locker24-health'`, exposing `./routes`;
  - `tsconfig` path to `hmdm-ui-kit`;
  - `package.json` adds `chart.js` and `ng2-charts` at the versions in `server/src/main/web/package.json`, because `hmdm-chart` imports them.
- Create `projects/locker24-health/src/app/`:
  - `app.routes.ts` (`MFE_ROUTES`: `''`→`fleet`, `fleet`, `device/:deviceId`, `settings`)
  - `pages/fleet/*`, `pages/device/*`, `pages/settings/*`
  - `services/health.service.ts`, `types/*.type.ts`
- Create `projects/locker24-health/public/i18n/en_US.json`. It includes `plugin.locker24-health.localization.key.name: "Fleet health"` and `breadcrumb.plugin.locker24-health.main`. The panel runs in English; other languages fall back to keys.
- Modify `plugins/locker24-health/pom.xml`: add the four frontend blocks from deviceinfo's pom (check-web-build, copy-plugin-frontend to `META-INF/resources/plugins/locker24-health`, the WAR copy and `frontend-maven-plugin`) with paths renamed.

**Interfaces:**
- Consumes: the Task 11 endpoints, using relative URLs `rest/private/plugin-locker24-health/...` like the deviceinfo services; `hmdm-ui-kit` components only (`hmdm-table`, status cell, selector, `hmdm-chart`).
- Produces:
  - **Fleet page:** one row per tablet with status light, configuration, groups, locker short UID, last seen, battery, temperature, kiosk app version, agent version and kiosk state. Filters: configuration, status, model. It polls every 60 s, and a row click navigates to `device/:deviceId`.
  - **Device page:** 24 h / 7 d / 30 d selector; line charts for battery, temperature and RSSI (`hmdm-chart` `line`, category labels, no time adapter); a stepped online/offline line built from `offlinePeriods`, plus a list of those periods.
  - **Settings page:** webhook URL (password-style field, with a "change" button rather than showing the stored value) and time zone.
  - The menu entry under Functions and Settings comes from the `plugins` row (Task 5); no core change. Making this page the panel's landing page is Plan 3 (spec 4.5).

- [ ] **Step 1: Build the web project:** `cd plugins/locker24-health/src/main/web && pnpm install && pnpm run build && ls dist/locker24-health/browser/remoteEntry.json`. Expected: the build succeeds and the file exists.
- [ ] **Step 2: Package the jar:** `mvn -q -pl plugins/locker24-health -am package && unzip -l plugins/locker24-health/target/locker24-health-0.1.0.jar | grep "META-INF/resources/plugins/locker24-health/remoteEntry.json"`. Expected: one line. From now on, backend-only test runs add `-Dskip.web.build=true`.
- [ ] **Step 3: Branding check:** `grep -ri headwind plugins/locker24-health/src/main/web/projects` → no output.
- [ ] **Step 4: Commit:** `git add plugins/locker24-health && git commit -m "feat: fleet health dashboard, device history and settings pages"`

### Task 13: Kiosk app health state and hooks (hpmostar-qa, Dart)

**Files (worktree `../locker24-tablet-hpmoqa`, branch `hpmostar-qa`):**
- Create: `lib/services/mdm_health.dart`
- Modify:
  - `lib/http/locker.dart`, `lib/http/auth.dart`: wrap each `http` call
  - `lib/services/preferences.dart`: after `set`, call `MdmHealth.instance.setLockerShortUid(locker.shortUid)`; after `clear`, call it with `''`
  - `lib/pages/home.dart`: in `_reloadPreferences`, call `setLockerShortUid(stored.shortUid)`
  - `lib/main.dart`: error handlers
- Test: `test/mdm_health_test.dart`

**Interfaces:**
- Consumes: the Task 1 `app` keys; MethodChannel `com.hpmoqa.tablet_client/kiosk` (`KioskManager.platform`), new method `publishHealth`.
- Produces:
  - `MdmHealth.instance`
  - `Future<http.Response> MdmHealth.observe(String label, Future<http.Response> Function() call)`:
    - a response with status < 500 → `last_api_ok_at = now`;
    - status ≥ 500 → `last_api_error_at = now`, `last_api_error = "$label HTTP $status"`;
    - an exception → `"$label ${e.runtimeType}"`, then rethrow.
    - Labels: `unlock`, `locker`, `login`. No codes, e-mails or tokens in the text.
  - `MdmHealth.recordAppError()`, `MdmHealth.setLockerShortUid(String)`
  - `Map<String, Object?> MdmHealth.values()`: the five keys with types `String`, `int` (epoch ms), `int`, `String`, `int`.
  - `RollingCount` (24 h window, cap 500).
  - Publishing:
    - at most once every 5 s (trailing timer);
    - every channel error (`MissingPluginException`, `PlatformException`) is swallowed;
    - `app_errors_24h` timestamps are persisted in SharedPreferences key `mdm_app_errors`.
  - `main.dart`: `FlutterError.onError` records the error and then calls the previous handler; `PlatformDispatcher.instance.onError` records the error and returns `false` (default handling kept).

- [ ] **Step 1: Write the failing test** `test/mdm_health_test.dart`. It mocks the channel the way `kiosk_manager_test.dart` does, with an injectable clock:
  - `values has the five keys with exact types`
  - `observe 200 sets last_api_ok_at`, `observe 503 sets error "unlock HTTP 503"`
  - `observe exception records type and rethrows`
  - `last_api_error is cut to 200 chars`
  - `app errors older than 24 h are not counted`
  - `no agent never throws`: the channel throws `MissingPluginException`, then `publish` completes normally
  - `setLockerShortUid publishes the new value`
- [ ] **Step 2: Run, expect failure:** `flutter test test/mdm_health_test.dart`. Expected: compile error (`mdm_health.dart` missing).
- [ ] **Step 3: Implement** the service and the hooks listed above.
- [ ] **Step 4: Run, expect PASS:** `flutter test`. All tests pass, including the existing ones.
- [ ] **Step 5: Commit:** `git add lib test && git commit -m "feat: track API and app health for the MDM agent"`

### Task 14: Kiosk app binding to the agent (hpmostar-qa, Kotlin) and QA build

**Files (same worktree):**
- Create: `android/app/src/main/aidl/com/hmdm/IMdmApi.aidl`, `IMdmApiCallback.aidl`. These are verbatim copies from the agent after Task 3, including `publishHealth` last.
- Create: `android/app/src/main/kotlin/com/hpmoqa/tablet_client/MdmHealthClient.kt`
- Modify:
  - `KioskPlugin.kt`: add a case `"publishHealth" -> result.success(MdmHealthClient.publish(context, call.arguments as? Map<*, *>))`
  - `android/app/build.gradle.kts`: `buildFeatures { aidl = true }`
  - `AndroidManifest.xml`: `<queries><package android:name="app.locker24.mdm" /></queries>`, which Android 11+ package visibility needs before `bindService` can see the agent
  - `pubspec.yaml`: version, see Step 3

**Interfaces:**
- Consumes: `IMdmApi.publishHealth(Bundle)` (Task 3); bind intent action `com.hmdm.action.Connect` with package `app.locker24.mdm`.
- Produces: `MdmHealthClient.publish(ctx, map): Boolean`.
  - When bound, it builds the `Bundle` (`putLong` for `*_at`, `putInt` for `app_errors_24h`, `putString` for the rest) and calls `publishHealth`.
  - When unbound, it keeps the map as pending and calls `bindService(BIND_AUTO_CREATE)`; `onServiceConnected` flushes the pending map.
  - If `bindService` returns false (no agent), it stops trying for 10 min.
  - Every `RemoteException`, `SecurityException` or `RuntimeException` → `false`. Nothing throws into Flutter.

- [ ] **Step 1: Implement** the files above.
- [ ] **Step 2: Build:** `flutter test && flutter build apk --release --build-name 1.2.8 --build-number 23`. Then `apksigner verify --print-certs build/app/outputs/flutter-apk/app-release.apk | grep SHA-256` must show the upload key used for the earlier QA builds, not the debug key.
- [ ] **Step 3: Version.** Version 1.2.8+23 is passed with build flags (the panel has 1.2.7+22); do not edit pubspec.yaml. Rebuild, and keep the APK as `hpmoqa-1.2.8+23-health-release.apk` for Task 16.
- [ ] **Step 4: Commit:** `git add android pubspec.yaml && git commit -m "feat: publish health values to the Locker24 MDM agent"`

### Task 15: Port to hpmostar (PostBox)

**Files (worktree `../locker24-tablet-hpmo`, branch `hpmostar`):** the same changes as Tasks 13–14, made by hand:
- Kotlin package `com.hpmo.tablet_client`, file `android/app/src/main/kotlin/com/hpmo/tablet_client/MdmHealthClient.kt`
- channel `com.hpmo.tablet_client/kiosk`
- the same AIDL copies, manifest `<queries>` and gradle flag
- version 1.3.2+25 via build flags (1.3.1+24 is the latest built)

**Interfaces:** identical to Tasks 13–14.

- [ ] **Step 1: Port the Dart files and tests.** Read each file on `hpmostar` first; the branches differ (no cherry-pick).
- [ ] **Step 2: Run:** `flutter test`. Expected: PASS.
- [ ] **Step 3: Build:** `flutter build apk --release --build-name 1.3.2 --build-number 25`, then check the certificate (as in Task 14).
- [ ] **Step 4: Commit:** `git add lib test android pubspec.yaml && git commit -m "feat: publish health values to the Locker24 MDM agent"`

### Task 16: Smoke script, deploy note and device checklist

**Files:**
- Create (server repo): `deploy/health-smoke.sh`
- Modify (server repo): `deploy/server-setup.md`. Append "## 6. Updating the server" with the deploy below.
- Modify (agent repo, worktree): `docs/locker24-pilot-config.md`
  - Section 3 gets the row `health_interval_minutes` | `5` | clamped 1..60.
  - Section 6 gets health steps H1–H6.
- Modify (agent repo, worktree): `docs/locker24-supported-devices.md`, with the results.

**Interfaces:**
- Consumes: everything above. A panel device `SMOKE-01` in a configuration with no tablets, created by the user.
- Produces: `deploy/health-smoke.sh <base_url>`.
  - It prompts silently for `hash.secret`, the panel login and the password.
  - It POSTs four signed envelopes for `SMOKE-01`:
    1. a report with ts `T2` (battery 55)
    2. the same report again
    3. a report with ts `T1 < T2` (battery 11)
    4. one with a wrong signature
  - It logs in through `POST /rest/public/jwt/login` (`password` = uppercase hex MD5 of the password, as `PasswordUtil.passwordMatch` expects) and checks:
    - the `SMOKE-01` row in `/rest/private/plugin-locker24-health/devices` has `batteryPct == 55`, so the older report did not replace the latest;
    - the history endpoint returns exactly 2 points, so the resend was ignored;
    - the wrong signature got `error.signature`.
  - It prints PASS or FAIL per check and never echoes a secret.

- [ ] **Step 1: Write and commit the script and the deploy note.** The deploy, which the user runs in quiet hours:
  1. `pg_dump` into the backup directory of section 5.
  2. After the user has pushed `feat/locker24-health`: `cd /root/locker24-hmdm-server-v7 && git fetch && git checkout feat/locker24-health && mvn install`. Apply the known `platform` quirk if needed.
  3. `cp server/target/launcher.war /var/lib/tomcat10/webapps/ROOT.war && systemctl restart tomcat10`.
  4. Wait for HTTP 200.
  5. `sudo -u postgres psql hmdm -c '\dt locker24_*'` shows 4 tables, and `SELECT identifier FROM plugins WHERE identifier='locker24-health'` returns 1 row.
  6. Functions → Fleet health opens.

  Rollback is spec section 10.

  Commit: `git add deploy && git commit -m "docs: health smoke script and server update steps"`
- [ ] **Step 2: The user deploys and runs the smoke script:** `./deploy/health-smoke.sh https://mdm.locker24.app`. Expected: every check PASS.
- [ ] **Step 3: The user uploads and assigns the APKs:**
  - agent `6.40-l24.5`, as a new version;
  - `hpmoqa-1.2.8+23`, as a new version, to `hpmo-qa`;
  - app setting `health_interval_minutes=5`.
- [ ] **Step 4: Health checklist on the pilot Tab A9** (H1–H6). The implementer reads the panel and writes the results:
  - **H1.** Within 5 min of the agent update, the tablet shows on Fleet health with model SM-X110, battery, temperature and both versions.
  - **H2.** `locker_short_uid` matches the locker set in the kiosk app. After a PIN unlock attempt, `last_api_ok_at` moves.
  - **H3.** Wi-Fi off for 15 min:
    - the tablet turns red `offline` 10–11 min after its last report;
    - a pop-up alert appears, and the webhook receives `raised` if one is set.
    - Then Wi-Fi on:
      - the queued reports arrive, and the history shows the gap as an offline period with points inside it;
      - the status turns green, and the alert clears about 5 min later.
  - **H4.** Unplug the charger: yellow `not_charging` within about 2 min, and an alert after 30 min. Replug: green.
  - **H5.** `exitKiosk` push: yellow "kiosk off (admin)" within about 3 min (30 s tick, 60 s debounce, 1 min evaluation) and NO alert. Start kiosk again: green. An accidental loss of lock task (not admin) still turns red and alerts.
  - **H6.** The 24 h history charts render battery, temperature, RSSI and offline periods.
- [ ] **Step 5: Commit the checklist results** (agent repo): `git add docs && git commit -m "docs: health reporting checklist for the Tab A9"`

### Task 17: Server CI on GitHub and an on-server build-and-deploy script (runs right after Task 5)

User decision 2026-11-16, revised: the repos are becoming private.
- **Deploy model:** the droplet builds the server itself. It fetches the code over SSH with a read-only GitHub **deploy key**, builds with Maven on the droplet (it already has Maven and JDK 17 from the first install), and installs the WAR.
- **GitHub Actions:** only run the build and the tests on every push (CI). There are no release assets to download.
- **Deploy unit:** a git tag `l24-v<headwind version>.<n>`, for example `l24-v7.04.1.1` (spec section 7), or any ref for a test deploy.
- Agent APK releases stay in Plan 3.

**Files:**
- Create: `.github/workflows/server-ci.yml`, `deploy/deploy.sh`, `deploy/README.md`
- Modify: `deploy/server-setup.md` (one section: "Updating the server")

**Interfaces:**
- Consumes: the Maven build as in Task 5 and Plan 1's first install. Find the WAR's path and name under `server/target/`, and the installed WAR path and name on the droplet, in `deploy/server-setup.md` and `hmdm_install.sh`.
- Known build issue: `com.hmdm.plugin:platform` resolves only from the `prod-public-maven` repository that `plugins/pom.xml` declares. If `server/pom.xml` needs it too, add it there and list it in `docs/locker24-core-changes.md`.
- Produces: `deploy/deploy.sh <ref>`, where `<ref>` is a tag, branch or SHA, plus a source checkout at `/opt/locker24/src/locker24-hmdm-server-v7`.

- [ ] **Step 1: CI workflow.** `server-ci.yml` runs on push and pull_request to `main` and `feat/**`.
  - Steps: `actions/checkout`, `actions/setup-java` (temurin 17 with the maven cache), then `mvn -B package` with tests (Headwind's and ours).
  - The only secret is `GITHUB_TOKEN`.
  - Pin third-party actions to a major version from GitHub's own `actions/*` org only.
- [ ] **Step 2: `deploy/deploy.sh <ref>`.** It runs as root on the droplet, with `set -euo pipefail`, is idempotent, and prints every step.
  1. If `/opt/locker24/src/locker24-hmdm-server-v7` is missing, clone it over SSH with `GIT_SSH_COMMAND="ssh -i /root/.ssh/locker24_deploy_key -o IdentitiesOnly=yes"`. Never print the key.
  2. `git fetch --tags --prune`, then `git checkout --force <ref>`. Refuse a dirty tree.
  3. `mvn -B -DskipTests install`, using the same flags as Plan 1's first install, including the `-rf :server` workaround if it is still needed. Print the elapsed time. The build takes about 17 min on 2 vCPU.
  4. `pg_dump` the hmdm database to `/opt/locker24/backups/pre-deploy-<ref>-<timestamp>.sql.gz`, following `backup.sh`'s conventions.
  5. Copy the current WAR to `/opt/locker24/backups/`.
  6. Stop tomcat10, replace the WAR, start tomcat10. Liquibase migrates on startup.
  7. Wait up to 180 s for `https://mdm.locker24.app/` to return HTTP 200 and for port 31000 to listen. Print the deployed ref and SHA.
  8. On failure, restore the previous WAR and restart, and say so loudly. Never restore the database automatically; print the restore command instead.

  `--dry-run` prints the steps and changes nothing. `--skip-build` installs the WAR from the last build, which is useful for a retry.
- [ ] **Step 3: Verify locally.**
  - `bash -n deploy/deploy.sh` passes.
  - `shellcheck` is clean, run via Docker `koalaman/shellcheck:stable`.
  - `actionlint` is clean on the workflow, run via Docker `rhysd/actionlint:latest`.
  - `deploy.sh --dry-run main` prints every step.
- [ ] **Step 4: Docs.** `deploy/README.md` covers:
  - one-time setup:
    - generate the deploy key on the droplet: `ssh-keygen -t ed25519 -f /root/.ssh/locker24_deploy_key -N ""`
    - the user adds the `.pub` key as a **read-only deploy key** in GitHub repo Settings → Deploy keys
    - `ssh -T git@github.com` with that key
  - how to tag a release: `git tag l24-v7.04.1.1 && git push origin l24-v7.04.1.1`
  - how to deploy: `deploy.sh l24-v7.04.1.1`
  - rollback

  The "Updating the server" section in `server-setup.md` points to it.
- [ ] **Step 5: Commit.** `git add .github deploy docs && git commit -m "build: server CI and an on-server build-and-deploy script"`

The user (or the controller, if the user allows it) adds the deploy key and runs `deploy.sh` on the droplet. Until the user says otherwise, the MDM server has no quiet-hours restriction.

### Task 18: Deploy from GitHub on push to main

User decision 2026-11-16: every push to `main` deploys the MDM server automatically, after CI passes. GitHub Actions SSHes to the droplet with a dedicated key that can only start a deploy; the build still happens on the droplet with `deploy/deploy.sh` (Task 17).

**Files:**
- Create: `.github/workflows/server-deploy.yml`, `deploy/deploy-forced.sh`
- Modify: `deploy/README.md` (a section "Automatic deploys from GitHub"), `deploy/deploy.sh` only if needed (for example a `--sha` mode)

**Design (binding):**
- **Trigger:** `workflow_run` on the "Server CI" workflow, `completed`, branch `main`, and only when `conclusion == 'success'`. Deploy the exact `head_sha` that CI tested, never "whatever main is now". Also allow `workflow_dispatch` with a `sha` input, for a manual redeploy.
- **Concurrency:** `concurrency: { group: mdm-deploy, cancel-in-progress: false }`, so deploys queue and never run in parallel.
- **SSH:** the private key comes from repo secret `MDM_DEPLOY_SSH_KEY`, user `root`, host `mdm.locker24.app`. The droplet's host key is pinned in the workflow, either as a known_hosts line committed in the file (public info, collected with `ssh-keyscan -t ed25519 mdm.locker24.app`, which the controller provides) or as secret `MDM_KNOWN_HOSTS`. Use `StrictHostKeyChecking=yes`, `ServerAliveInterval=30`, and no agent or port forwarding.
- **Forced command on the droplet:** the key's `authorized_keys` line is `command="/opt/locker24/deploy-forced.sh",no-port-forwarding,no-agent-forwarding,no-X11-forwarding,no-pty ssh-ed25519 AAAA… github-deploy`. `deploy-forced.sh`:
  - reads `$SSH_ORIGINAL_COMMAND`
  - accepts ONLY `deploy <40-hex sha>`, or `status <40-hex sha>` to follow progress; anything else exits 1 with a message
  - starts the deploy detached from the SSH session, so a dropped connection never interrupts a swap:

    ```
    systemd-run --unit=locker24-deploy-<sha12> --collect --property=TimeoutStartSec=0 /opt/locker24/src/locker24-hmdm-server-v7/deploy/deploy.sh <sha>
    ```

    or an equivalent `setsid nohup`, whichever is robust on Ubuntu 24.04. It refuses if a deploy unit is already running.
  - then follows `/var/log/locker24-deploy.log` (only this deploy's lines) until the unit finishes, and exits with the deploy's exit code. If SSH drops, the workflow reconnects with `status <sha>`, retrying for up to 40 min.
  - The script is copied from the checkout to `/opt/locker24/deploy-forced.sh` once by the user (README step), owned by root with mode 755. `deploy.sh` must be able to update the checkout to `<sha>` first, and must already accept a hex SHA.
- **Workflow job:** `timeout-minutes: 50`, `permissions: contents: read`. The log prints the deployed SHA and the deploy result. It never prints the key.
- **Secrets hygiene:** public repo. Secrets never reach PRs from forks (workflow_run on main only). The forced command means a leaked key can only redeploy a commit that exists in the repo.

- [ ] **Step 1:** Write `deploy-forced.sh`.
  - `set -euo pipefail` and strict validation, using regex `^(deploy|status) [0-9a-f]{40}$`.
  - Test it locally in Docker (ubuntu:24.04) with a stubbed `systemd-run`/`deploy.sh`: valid deploy, invalid input (an injection attempt such as `deploy abc; rm -rf /`, a non-hex SHA, extra args), a deploy already running, and status follow.
  - shellcheck must be clean.
- [ ] **Step 2:** Write `server-deploy.yml` with the design above. actionlint must be clean (Docker `rhysd/actionlint`).
- [ ] **Step 3:** In the README, document the one-time setup the user runs:
  1. Generate the key pair. The controller will generate it in its scratchpad and hand over the files; never commit it.
  2. Add the private key as repo secret `MDM_DEPLOY_SSH_KEY`.
  3. Install the forced-command line into `/root/.ssh/authorized_keys` on the droplet.
  4. Copy `deploy-forced.sh` into `/opt/locker24/`.
  5. Test with `workflow_dispatch`.
- [ ] **Step 4:** Commit: `build: deploy the MDM server from GitHub after CI on main`.
