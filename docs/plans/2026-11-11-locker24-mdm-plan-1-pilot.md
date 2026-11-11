# Locker24 MDM, Plan 1 (pilot-ready) Implementation Plan

**Goal:** One `hpmo-qa` Tab A9, enrolled by QR code into our own server at `mdm.locker24.app`, runs our agent `app.locker24.mdm` in a real lock-task kiosk. The kiosk survives silent updates of both the kiosk app and the agent, and restarts on a schedule.

**Architecture:**
- **Server:** our fork of Headwind v7 is built and installed once on the new droplet, then hardened. Plan 1 has no server code changes.
- **Agent:** our fork of the Headwind launcher gets:
  - a `locker24` build flavor;
  - real bodies for the kiosk stubs in `pro/ProUtils.java`;
  - a small `com.hmdm.launcher.locker24` package: pure policy classes with unit tests, a foreground watchdog service and a self-update receiver.
- **Merge-friendly:** Headwind's core files stay untouched apart from `app/build.gradle` (new flavor) and `ProUtils.java`.

**Tech Stack:**
- Ubuntu 24.04, Tomcat 10, OpenJDK 21, PostgreSQL 16, Headwind v7 (Maven, Angular).
- Android (Java, minSdk 26, targetSdk 34, Gradle 8.13), JUnit 4.

**Spec:** `docs/specs/2026-11-11-locker24-mdm-design.md` (this repo). Plan 1 covers spec sections 1.1, 2, 3.1–3.5, 4.2 (pilot tenant only), 5, 8 (agent part), 9 step 1. Plan 2 covers health (3.6, 3.7, 4.1). Plan 3 covers releases, branding, CI, deploy script and the upstream watch (4.3–4.5, 6, 7).

**Repos:**
- Server: `/Users/mirsadhalilcevic/Apps/locker24/locker24-hmdm-server-v7` (branch `main`).
- Agent: `/Users/mirsadhalilcevic/Apps/locker24/locker24-hmdm-android` (branch `master`).
- Droplet: `root@159.89.110.163` (`mdm.locker24.app`).

## Global Constraints

- Product name in anything a user sees: **Locker24 MDM**. Never "Headwind MDM". Keep `LICENSE`, `NOTICE` and the existing copyright headers; new files get no Headwind header.
- Agent package `app.locker24.mdm`. The Java namespace stays `com.hmdm.launcher`, so the admin receiver is `com.hmdm.launcher.AdminReceiver`.
- Agent server URLs: `BASE_URL` and `SECONDARY_BASE_URL` = `https://mdm.locker24.app`, `MQTT_PORT` 31000.
- Agent `versionName` `6.40-l24.1`, `versionCode` `1540001` (Headwind code 15400 × 100 + 1).
- A `locker24` release build must fail when `locker24.properties` (signing + secrets) is missing. It never falls back to debug signing.
- The opensource flavor must keep building unchanged (`./gradlew assembleOpensourceDebug`).
- No secrets in git: keystore, store/key passwords, `REQUEST_SIGNATURE`, `LIBRARY_API_KEY`, DB password, `hash.secret`, JWT secret.
- Server: ufw allows only 22, 80, 443 and 31000; Tomcat's HTTP connector listens on `127.0.0.1` only; `secure.enrollment=1`; `hash.secret` = the agent's `REQUEST_SIGNATURE`.
- Commits: no Co-Authored-By trailers and no tool or generator attribution anywhere.
- Deploy and server steps are **prepared** by the implementer and **run by the user**. The implementer may run read-only checks over SSH; commands that change the server are handed to the user.

## Review Focus

1. **The agent updates itself while in kiosk.** The tablet must come back in kiosk within 60 s and never show the Samsung lock screen. This is device checklist step 5 (Task 9). The self-update receiver from Task 6 makes this case work.
2. **An admin exits kiosk** with the password or the `exitKiosk` push. The watchdog must not drag the tablet back into kiosk until kiosk is started again. Owned by Task 4's unit test `adminUnlockedSuppressesRelaunch` and device step 10.
3. **Kiosk app not installed yet** (first enrolment, or a failed download). Nothing may crash, and kiosk starts once the app arrives. Owned by Task 3's test `lockTaskPackagesKeepsMainAppEvenIfMissing` and Task 5's `startCosuKioskMode` returning `false` when the app cannot be launched.
4. **Reboot loops.** A scheduled reboot must happen at most once per calendar day, never in the first hour after boot, and never while the clock is unset (year < 2024). Owned by Task 4's `RebootScheduleTest`.
5. **Not device owner** (wrong enrolment, emulator). Every kiosk method returns `false`/no-op and never throws `SecurityException`. Owned by Task 5's device-owner guard and its check on the emulator.

---

### Task 1: Prepare and secure the droplet

**Files:**
- Create (server repo): `deploy/server-setup.md`. It holds the exact command list from this task, so the setup can be repeated.

**Interfaces:**
- Produces: a host with Tomcat 10, OpenJDK 21, PostgreSQL 16, Maven, certbot and Node (only for the first build), with ufw active.

- [ ] **Step 1: Write `deploy/server-setup.md`** with these commands, which the user runs as root:

```bash
apt update && apt -y upgrade
apt -y install openjdk-21-jdk-headless tomcat10 maven postgresql certbot aapt git
ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp && ufw allow 31000/tcp
ufw --force enable
sudo -u postgres psql -c "CREATE USER hmdm WITH PASSWORD '<generated>';"
sudo -u postgres psql -c "CREATE DATABASE hmdm WITH OWNER=hmdm;"
```

Generate `<generated>` with `openssl rand -base64 24`. The user keeps it in their password manager and never pastes it into the repo.

- [ ] **Step 2: The user runs it, then the implementer verifies read-only**

Run: `ssh root@159.89.110.163 'java -version 2>&1|head -1; systemctl is-active tomcat10 postgresql; ufw status | head -8'`
Expected:
- the Java line shows 21
- `active` twice
- ufw is `active` with exactly 22, 80, 443 and 31000 allowed

- [ ] **Step 3: Commit** `deploy/server-setup.md`: `git commit -m "docs: server setup steps for mdm.locker24.app"`

### Task 2: Build, install and harden our v7 fork

**Files:**
- Create: `server/build.properties` on the droplet only (from `server/build.properties.example`; it is gitignored, so check that it is).
- Create: `plugins/audit/build.properties` on the droplet only.
- Modify (server repo): `deploy/server-setup.md`. Append the install answers and the hardening steps below.

**Interfaces:**
- Consumes: Task 1 host.
- Produces: `https://mdm.locker24.app` serving the panel, MQTT on :31000, and the values `hash.secret` and `LIBRARY_API_KEY` that Task 3 needs (handed over to the user, never committed).

- [ ] **Step 1: Document the build and install.** The user clones `git@github.com:lockermostarorg/locker24-hmdm-server-v7.git` into `/root/locker24-hmdm-server-v7`, copies the two `build.properties.example` files, runs `mvn install` and then `./hmdm_install.sh`, answering:

| Prompt | Answer |
|---|---|
| language | `en` |
| PostgreSQL host / port / db / user | `localhost` / `5432` / `hmdm` / `hmdm` |
| PostgreSQL password | the Task 1 password |
| storage directory / scripts directory | defaults |
| protocol | `https` |
| domain | `mdm.locker24.app` |
| port | empty |
| Let's Encrypt | yes |

- [ ] **Step 2: Document the hardening.** The user edits Tomcat's `Catalina/localhost/ROOT.xml` and sets:
  - `hash.secret` = `openssl rand -hex 24`
  - `secure.enrollment` = `1`
  - uncomment `jwt.secretkey` = `openssl rand -hex 32`

  In Tomcat's `server.xml`, the user adds `address="127.0.0.1"` to the `port="8080"` connector, removes any duplicate `8443` connector, then runs `systemctl restart tomcat10`. In the panel the user changes the `admin` password from the default. The `hash.secret` value goes into the password manager; Task 3 needs it.

- [ ] **Step 3: Verify read-only**

Run:
```bash
curl -s -o /dev/null -w "%{http_code}\n" https://mdm.locker24.app/
curl -s -m 5 -o /dev/null -w "%{http_code}\n" http://159.89.110.163:8080/ || echo blocked
ssh root@159.89.110.163 'ss -ltn | grep -E ":(8080|8443|31000) "; grep -o "secure.enrollment\" value=\"[01]\"" /var/lib/tomcat10/conf/Catalina/localhost/ROOT.xml'
```
Expected:
- the first curl prints `200`;
- the second prints `blocked` or `000`;
- 8080 listens on `127.0.0.1` only, and 8443 and 31000 both listen;
- the grep prints `secure.enrollment" value="1"`.

- [ ] **Step 4: Commit** the updated `deploy/server-setup.md`: `git commit -m "docs: install and hardening steps for mdm.locker24.app"`

### Task 3: `locker24` flavor, signing and secrets in the agent

**Files:**
- Modify: `app/build.gradle`. Add a `locker24` entry to `productFlavors`, a `signingConfigs.locker24` block and the properties loading. `defaultConfig` and `opensource {}` stay unchanged.
- Modify: `.gitignore`. Add `/locker24.properties` and `*.jks`.
- Create: `locker24.properties.example`, with the keys and no values.
- Create: `app/src/locker24/res/values/strings.xml`, overriding `app_name` (and `vendor`, if it exists in `main`) with `Locker24 MDM` / `Locker24`.

**Interfaces:**
- Produces: `./gradlew assembleLocker24Release` → `app/build/outputs/apk/locker24/release/app-locker24-release.apk`, package `app.locker24.mdm`, `versionName` `6.40-l24.1`, `versionCode` `1540001`.
- `locker24.properties` keys:
  - `storeFile`, `storePassword`, `keyAlias`, `keyPassword`
  - `requestSignature` (= the server's `hash.secret`)
  - `libraryApiKey` (`openssl rand -hex 16`)

- [ ] **Step 1: Create the agent keystore (the user does this once and keeps two backups).** This key can never be replaced: losing it means no agent update can ever be installed again.

```bash
keytool -genkeypair -v -keystore ~/keys/locker24-mdm-agent.jks -alias locker24-mdm -keyalg RSA -keysize 4096 -validity 36500
```

- [ ] **Step 2: Add the flavor.**
  - `locker24 { applicationId "app.locker24.mdm"; versionCode 1540001; versionName "6.40-l24.1" }`
  - `buildConfigField`s inside the flavor:
    - `BASE_URL`, `SECONDARY_BASE_URL` = `"https://mdm.locker24.app"`
    - `REQUEST_SIGNATURE` from `requestSignature`
    - `LIBRARY_API_KEY` from `libraryApiKey`
  - `signingConfig signingConfigs.locker24`
  - A `gradle.taskGraph.whenReady` check that throws `GradleException("locker24.properties missing: release builds must be signed with the Locker24 MDM key")` when any `*Locker24Release*` task is scheduled and the file is absent.

- [ ] **Step 3: Verify**

Run:
```bash
./gradlew assembleOpensourceDebug
mv locker24.properties /tmp/ ; ./gradlew assembleLocker24Release ; mv /tmp/locker24.properties .
./gradlew assembleLocker24Release
BT=$(ls -d ~/Library/Android/sdk/build-tools/* | sort -V | tail -1)
$BT/aapt2 dump badging app/build/outputs/apk/locker24/release/app-locker24-release.apk | head -1
$BT/apksigner verify --print-certs app/build/outputs/apk/locker24/release/app-locker24-release.apk | grep SHA-256
$BT/aapt2 dump badging app/build/outputs/apk/locker24/release/app-locker24-release.apk | grep application-label:
```
Expected:
- The opensource build succeeds.
- The release build without the file fails with the message from Step 2.
- With the file, the release build succeeds.
- The badging line shows `package: name='app.locker24.mdm' versionCode='1540001' versionName='6.40-l24.1'`.
- The certificate SHA-256 is the new key's, not Headwind's or debug's.
- The label is `Locker24 MDM`.

- [ ] **Step 4: Commit:** `git commit -m "build: locker24 flavor with its own package, server and signing"`

### Task 4: Pure kiosk, watchdog and reboot policies (unit-tested)

**Files:**
- Create: `app/src/main/java/com/hmdm/launcher/locker24/KioskPolicy.java`
- Create: `app/src/main/java/com/hmdm/launcher/locker24/WatchdogPolicy.java`
- Create: `app/src/main/java/com/hmdm/launcher/locker24/RebootSchedule.java`
- Test: `app/src/test/java/com/hmdm/launcher/locker24/KioskPolicyTest.java`, `WatchdogPolicyTest.java`, `RebootScheduleTest.java`

None of these classes may touch `Context` or any Android API other than `DevicePolicyManager` int constants, so they run on the JVM.

**Interfaces:**
- Produces:
  - `KioskPolicy.kioskRequired(Boolean kioskMode, String mainApp): boolean`
  - `KioskPolicy.lockTaskFeatures(Boolean home, Boolean recents, Boolean notifications, Boolean systemInfo, Boolean keyguard, Boolean lockButtons): int`
  - `KioskPolicy.lockTaskPackages(String launcherPackage, String mainApp, String extraCsv): String[]`
  - `WatchdogPolicy(int launcherInFrontLimit)`, with `Action evaluate(boolean required, boolean adminUnlocked, boolean lockTaskLocked, boolean launcherInFront)` and `enum Action { NONE, RELAUNCH }`
  - `RebootSchedule.parse(String hhmm): int[] /* {h,m} or null */`
  - `RebootSchedule.isDue(java.time.LocalDateTime now, int[] at, java.time.LocalDate lastRebootDate, long uptimeMs): boolean`

- [ ] **Step 1: Write the failing tests**

```java
// KioskPolicyTest
@Test public void requiredOnlyWithModeAndApp() {
    assertTrue(KioskPolicy.kioskRequired(true, "com.hpmoqa.tablet_client"));
    assertFalse(KioskPolicy.kioskRequired(true, " "));
    assertFalse(KioskPolicy.kioskRequired(null, "com.hpmoqa.tablet_client"));
    assertFalse(KioskPolicy.kioskRequired(false, "com.hpmoqa.tablet_client"));
}
@Test public void allOffBlocksEverythingButGlobalActionsUnlessLocked() {
    assertEquals(DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
        KioskPolicy.lockTaskFeatures(false, false, false, false, false, false));
    assertEquals(DevicePolicyManager.LOCK_TASK_FEATURE_NONE,
        KioskPolicy.lockTaskFeatures(false, false, false, false, false, true));
}
@Test public void recentsAndNotificationsNeedHome() {
    int f = KioskPolicy.lockTaskFeatures(false, true, true, false, false, true);
    assertEquals(DevicePolicyManager.LOCK_TASK_FEATURE_NONE, f);
    int g = KioskPolicy.lockTaskFeatures(true, true, true, true, true, true);
    assertEquals(DevicePolicyManager.LOCK_TASK_FEATURE_HOME | DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW
        | DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS | DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
        | DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD, g);
}
@Test public void lockTaskPackagesKeepsMainAppEvenIfMissing() {
    assertArrayEquals(new String[]{"app.locker24.mdm", "com.hpmoqa.tablet_client", "com.hmdm.control"},
        KioskPolicy.lockTaskPackages("app.locker24.mdm", "com.hpmoqa.tablet_client", " com.hmdm.control, ,app.locker24.mdm"));
}

// WatchdogPolicyTest (limit 4)
@Test public void relaunchesWhenLockTaskLostOnSecondCheck() {
    WatchdogPolicy p = new WatchdogPolicy(4);
    assertEquals(NONE, p.evaluate(true, false, false, false));
    assertEquals(RELAUNCH, p.evaluate(true, false, false, false));
}
@Test public void relaunchesWhenLauncherInFrontOnFourthCheck() { /* NONE x3 then RELAUNCH for (true,false,true,true) */ }
@Test public void healthyCheckResetsCounters() { /* miss, then (true,false,true,false) → NONE, then a miss → NONE */ }
@Test public void adminUnlockedSuppressesRelaunch() { /* (true,true,false,false) x10 → always NONE */ }
@Test public void notRequiredNeverRelaunches() { /* (false,false,false,true) x10 → always NONE */ }

// RebootScheduleTest
@Test public void parse() {
    assertArrayEquals(new int[]{4, 0}, RebootSchedule.parse("04:00"));
    assertNull(RebootSchedule.parse("")); assertNull(RebootSchedule.parse("25:00")); assertNull(RebootSchedule.parse("4"));
}
@Test public void dueInsideFiveMinutesAfterTarget() {
    int[] at = {4, 0}; long hour = 3_600_000L;
    assertTrue(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 4, 2), at, LocalDate.of(2026, 11, 11), 5 * hour));
    assertFalse(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 4, 6), at, LocalDate.of(2026, 11, 11), 5 * hour));
    assertFalse(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 3, 59), at, LocalDate.of(2026, 11, 11), 5 * hour));
}
@Test public void neverTwiceADay() {
    assertFalse(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 4, 1), new int[]{4, 0}, LocalDate.of(2026, 11, 12), 5 * 3_600_000L));
}
@Test public void neverInFirstHourAfterBoot() {
    assertFalse(RebootSchedule.isDue(LocalDateTime.of(2026, 11, 12, 4, 1), new int[]{4, 0}, null, 59 * 60_000L));
}
@Test public void neverWithUnsetClock() {
    assertFalse(RebootSchedule.isDue(LocalDateTime.of(1970, 1, 1, 4, 1), new int[]{4, 0}, null, 5 * 3_600_000L));
}
```

- [ ] **Step 2: Run them and see them fail**

Run: `./gradlew testOpensourceDebugUnitTest --tests 'com.hmdm.launcher.locker24.*'`
Expected: compilation failure (classes missing).

- [ ] **Step 3: Implement the three classes.** The rules the tests pin:
  - `lockTaskFeatures`:
    - `GLOBAL_ACTIONS` is on unless `lockButtons` is true.
    - `OVERVIEW` and `NOTIFICATIONS` are only added when `home` is true (Android requirement).
    - `null` counts as `false`.
  - `lockTaskPackages`: launcher first, then `mainApp`, then the trimmed, non-empty, de-duplicated extras, in order.
  - `WatchdogPolicy`:
    - "lock task lost" (`required && !lockTaskLocked`) acts on the 2nd consecutive miss.
    - "launcher in front" (`required && lockTaskLocked && launcherInFront`) acts on the `launcherInFrontLimit`-th consecutive miss. The limit of 4 at 30 s gives an admin 2 minutes to type the unlock password.
    - Any healthy check resets both counters.
    - After `RELAUNCH` the counters reset.
  - `isDue`: `now.getYear() >= 2024`, and `uptimeMs >= 3_600_000`, and `!now.toLocalDate().equals(lastRebootDate)`, and `now` is within `[at, at + 5 min)`.

- [ ] **Step 4: Run them and see them pass**

Run: `./gradlew testOpensourceDebugUnitTest --tests 'com.hmdm.launcher.locker24.*'`
Expected: all tests pass.

- [ ] **Step 5: Commit:** `git commit -m "feat: kiosk, watchdog and reboot policies"`

### Task 5: Real kiosk in `ProUtils`

**Files:**
- Modify: `app/src/main/java/com/hmdm/launcher/pro/ProUtils.java`. Fill in only the methods listed below and keep every signature.
- Create: `app/src/main/java/com/hmdm/launcher/locker24/KioskState.java`, holding the `admin_unlocked` flag in the `locker24` SharedPreferences.

**Interfaces:**
- Consumes: `KioskPolicy` (Task 4); `SettingsHelper.getInstance(context).getConfig()` (`getKioskMode()`, `getMainApp()`, `getKioskHome()`, `getKioskRecents()`, `getKioskNotifications()`, `getKioskSystemInfo()`, `getKioskKeyguard()`, `getKioskLockButtons()`); `SettingsHelper.getAppPreference(packageId, attr)`; `LegacyUtils.getAdminComponentName(context)`; `Utils.isDeviceOwner(context)`.
- Produces:
  - `KioskState.isAdminUnlocked(Context): boolean`
  - `KioskState.setAdminUnlocked(Context, boolean)`
  - the `ProUtils` behaviour below, which Task 6's watchdog relies on

- [ ] **Step 1: Implement**

| Method | Body |
|---|---|
| `kioskModeRequired(ctx)` | `KioskPolicy.kioskRequired(config.getKioskMode(), config.getMainApp())`; `false` if there is no config. |
| `isKioskAppInstalled(ctx)` | `getLaunchIntentForPackage(mainApp) != null`. |
| `isKioskModeRunning(ctx)` | `ActivityManager.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_LOCKED`. |
| `getKioskAppIntent(app, activity)` | The launch intent with `FLAG_ACTIVITY_NEW_TASK`, or `null`. |
| `startCosuKioskMode(app, activity, enableSettings)` | If not device owner, or there is no launch intent → `false`. Otherwise:<br>1. `setLockTaskPackages(admin, KioskPolicy.lockTaskPackages(pkg, app, getAppPreference(pkg, "kiosk_extra_packages")))`<br>2. `updateKioskOptions(activity)`<br>3. `setKeyguardDisabled(admin, !Boolean.TRUE.equals(getKioskKeyguard()))` and `setStatusBarDisabled(admin, true)`<br>4. `KioskState.setAdminUnlocked(ctx, false)`<br>5. `activity.startActivity(intent, ActivityOptions.makeBasic().setLockTaskEnabled(true).toBundle())`<br>6. return `true`.<br>Any exception → log and `false`. |
| `updateKioskOptions(activity)` | If device owner: `setLockTaskFeatures(admin, KioskPolicy.lockTaskFeatures(...config flags...))`. |
| `updateKioskAllowedApps(app, activity, enableSettings)` | If device owner: `setLockTaskPackages` as above. |
| `unlockKiosk(activity)` | If device owner:<br>1. `KioskState.setAdminUnlocked(ctx, true)`<br>2. `setLockTaskPackages(admin, new String[0])`<br>3. `setKeyguardDisabled(admin, false)` and `setStatusBarDisabled(admin, false)`<br>4. `activity.stopLockTask()` (catch `IllegalStateException`) |
| `processConfig(ctx, config)` | Starts the watchdog and applies the policies; the body comes in Task 6 and Task 7. Leave a call to `Locker24Hooks.onConfig(ctx, config)`, which Task 6 creates; until then, create `Locker24Hooks` with an empty `onConfig`. |

  `preventStatusBarExpansion` and `preventApplicationsList` return `null` (unchanged). `isPro` stays `false`.

- [ ] **Step 2: Build both flavors and run the unit tests**

Run: `./gradlew assembleOpensourceDebug assembleLocker24Release testOpensourceDebugUnitTest`
Expected: all succeed.

- [ ] **Step 3: Check that nothing throws without device owner.** Install `app-opensource-debug.apk` on an emulator (not device owner) and set a config with kiosk on. Then run `adb logcat | grep -i -E "SecurityException|FATAL"`.
Expected: no `SecurityException` and no crash; the launcher shows its desktop.

- [ ] **Step 4: Commit:** `git commit -m "feat: lock-task kiosk in ProUtils"`

### Task 6: Watchdog service and self-update receiver

**Files:**
- Create: `app/src/main/java/com/hmdm/launcher/locker24/Locker24Hooks.java` (replaces the empty stub from Task 5).
- Create: `app/src/main/java/com/hmdm/launcher/locker24/KioskWatchdogService.java`
- Create: `app/src/main/java/com/hmdm/launcher/locker24/SelfUpdateReceiver.java`
- Create: `app/src/locker24/AndroidManifest.xml`. It declares the service (`foregroundServiceType="systemExempted"`), the `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` permissions, and the receiver for `android.intent.action.MY_PACKAGE_REPLACED`. Because these live in the flavor manifest, the main manifest stays untouched.

**Interfaces:**
- Consumes: `WatchdogPolicy` and `RebootSchedule` (Task 4); `ProUtils.kioskModeRequired`, `ProUtils.isKioskModeRunning`, `KioskState.isAdminUnlocked` (Task 5).
- Produces:
  - `Locker24Hooks.onConfig(Context, ServerConfig)`: starts the watchdog in the foreground if it isn't running and re-reads its settings.
  - `KioskWatchdogService.ACTION_RECHECK`
  - an every-tick hook `KioskWatchdogService.onTick()` that Task 7 extends for the reboot.

- [ ] **Step 1: Implement**
  - **`KioskWatchdogService`:**
    - A foreground service with a silent, low-importance notification channel `locker24_watchdog`.
    - Every `kiosk_watchdog_seconds` (an app setting of the launcher package, default 30) it computes:
      - `launcherInFront` = own process importance `== IMPORTANCE_FOREGROUND`
      - `lockTaskLocked` = `ProUtils.isKioskModeRunning`
      - `required` = `ProUtils.kioskModeRequired`
      - `adminUnlocked` = `KioskState.isAdminUnlocked`
    - It feeds them to `WatchdogPolicy(4)`. On `RELAUNCH` it starts `MainActivity` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`; the existing `onResume` path then starts the kiosk.
    - It registers at runtime a receiver for `ACTION_PACKAGE_REPLACED` and `ACTION_PACKAGE_ADDED` (data scheme `package`). When the package is the configured `mainApp`, it immediately starts `MainActivity` in the same way.
  - **`SelfUpdateReceiver`:** on `MY_PACKAGE_REPLACED`, it starts `MainActivity` and the watchdog service.
  - **`Locker24Hooks.onConfig`:** `startForegroundService` for the watchdog; no-op on the opensource flavor (check `BuildConfig.FLAVOR.equals("locker24")`).

- [ ] **Step 2: Build and run the unit tests**

Run: `./gradlew assembleLocker24Release testOpensourceDebugUnitTest`
Expected: success.

- [ ] **Step 3: Commit:** `git commit -m "feat: kiosk watchdog and self-update relaunch"`

### Task 7: Scheduled reboot and stay-awake policy

**Files:**
- Modify: `KioskWatchdogService.java` (`onTick`).
- Modify: `Locker24Hooks.java` (`onConfig`).
- Create: `app/src/main/java/com/hmdm/launcher/locker24/DevicePolicies.java`

**Interfaces:**
- Consumes: `RebootSchedule` (Task 4); app settings of the launcher package `reboot_time` (`HH:mm`, empty = off) and `stay_on_plugged` (`1` default / `0`).
- Produces: `DevicePolicies.apply(Context)`.

- [ ] **Step 1: Implement**
  - **`onTick`:** if `RebootSchedule.isDue(LocalDateTime.now(), parse(reboot_time), lastRebootDate, SystemClock.elapsedRealtime())`:
    1. Store today as `last_reboot_date` in the `locker24` prefs **before** rebooting.
    2. `RemoteLogger.log(ctx, Const.LOG_INFO, "Scheduled reboot")`
    3. `dpm.reboot(admin)`
  - **`DevicePolicies.apply`:** if device owner and `stay_on_plugged` is not `0`, set `dpm.setGlobalSetting(admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "3")`. Log `PowerManager.isIgnoringBatteryOptimizations(pkg)` for the agent and for `mainApp` with `RemoteLogger` so the device checklist can read it. Called from `onConfig`.

- [ ] **Step 2: Build and run the unit tests**

Run: `./gradlew assembleLocker24Release testOpensourceDebugUnitTest`
Expected: success.

- [ ] **Step 3: Commit:** `git commit -m "feat: scheduled reboot and stay-awake while charging"`

### Task 8: Pilot configuration in the panel

**Files:**
- Create (agent repo): `docs/locker24-pilot-config.md`. It records every value below so the configuration can be rebuilt.

**Interfaces:**
- Consumes:
  - the Task 3 APK `app-locker24-release.apk`
  - the kiosk app `~/Desktop/mdm/hpmoqa-1.2.0+15-mdm-release.apk` (package `com.hpmoqa.tablet_client`)

- [ ] **Step 1: Write the configuration record.** The user then applies it in the panel at `https://mdm.locker24.app`:
  - **Applications:** upload the agent APK (package `app.locker24.mdm`, version `6.40-l24.1`) and the kiosk app.
  - **Configuration `hpmo-qa`:**
    - MDM application `app.locker24.mdm`
    - admin receiver `com.hmdm.launcher.AdminReceiver`
    - kiosk mode on, content app `com.hpmoqa.tablet_client`
    - home, recents, notifications, status bar info and screen lock all off; lock power button on
    - restrictions `no_factory_reset,no_add_user,no_safe_boot,no_usb_file_transfer,no_physical_media`
    - system update: scheduled window 02:00–05:00
    - scheduled app update off for the pilot (immediate)
    - Wi-Fi for enrolment
  - **App settings (package `app.locker24.mdm`):** `kiosk_watchdog_seconds=30`, `reboot_time=04:00`, `stay_on_plugged=1`.
  - **Device group `hpmo-qa`.** The QR code is made from configuration `hpmo-qa`, with the custom extra `com.hmdm.GROUP=hpmo-qa`.

- [ ] **Step 2: Verify read-only.** Download the QR code's JSON from the panel.
Expected:
- `android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME` is `app.locker24.mdm/com.hmdm.launcher.AdminReceiver`.
- The APK URL is on `mdm.locker24.app`.
- The checksum matches `apksigner`'s SHA-256 of the uploaded agent.

- [ ] **Step 3: Commit:** `git commit -m "docs: hpmo-qa pilot configuration"`

### Task 9: Device checklist on the pilot Tab A9

**Files:**
- Create (agent repo): `docs/locker24-supported-devices.md`. One table row per model: model, Android version and patch level, agent version, date, result of each checklist step, and the battery-optimisation log line from Task 7.

**Interfaces:**
- Consumes: everything above. The user performs the steps on the tablet; the implementer reads the panel and device logs and fills in the document.

- [ ] **Step 1: Run the checklist** from spec section 8:
  1. QR enrolment from factory reset.
  2. Kiosk on first start, with no Accessibility prompt.
  3. Home, back, recents, status bar and power menu are all blocked.
  4. Upload `hpmoqa-1.2.1+16-update-test.apk` as a new version: the tablet updates and is back in kiosk within 60 s.
  5. Build agent `6.40-l24.2` / `1540002`, upload it: the agent updates and is back in kiosk within 60 s.
  6. Reboot: back in kiosk.
  7. Power cut: back in kiosk.
  8. Wi-Fi off 15 min, then on: the device syncs again (health queue is Plan 2).
  9. Set `reboot_time` 3 min ahead: the tablet reboots once, and not again that day.
  10. Exit kiosk with the panel password: the tablet stays out of kiosk for 5 minutes (watchdog quiet); a config push re-enters kiosk.

  Expected: every step passes. Any failure goes back to the owning task as a bug, with the logcat lines.

- [ ] **Step 2: Commit:** `git commit -m "docs: Tab A9 passes the Locker24 MDM device checklist"`
