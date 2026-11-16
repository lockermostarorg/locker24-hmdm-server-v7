# Locker24 MDM Plan 3 Implementation Plan: release flow, panel fixes, branding, dark mode

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Uploading an app version is the release (followers move automatically, on schedule, immediately or held), the panel's papercuts are fixed, and the panel is a branded Locker24 product with light and dark themes.

**Architecture:** Core Headwind v7 changes (Liquibase columns, a set-based "move followers" DAO method replacing the dead auto-update hook, REST release modes, a new `forceAppUpdate` push) plus one agent change (6.40-l24.6 handles `forceAppUpdate` as a user-initiated update). The Angular core panel gets the release UI, link and tooltip fixes, Locker24 branding and token-based light/dark theming that plugin remotes inherit.

**Tech Stack:** Java 17, Guice, Jersey, MyBatis, Liquibase, PostgreSQL 16; Angular (core project + `hmdm-ui-kit`, native federation), Angular Material 3, Karma/ChromeHeadless; Android agent (Java, Gradle, flavor `locker24`).

**Spec:** `docs/specs/2026-11-16-locker24-mdm-plan-3-release-flow-design.md` (approved 2026-11-16). Base design: `docs/specs/2026-11-11-locker24-mdm-design.md`.

## Global Constraints

- Repos: server `locker24-hmdm-server-v7` (public fork), agent `locker24-hmdm-android` (public fork). Never commit secrets; gitleaks runs in CI.
- Maven only via Docker: `docker run --rm -v "$PWD":/src -v "$HOME/.m2":/root/.m2 -w /src maven:3.9-eclipse-temurin-17 mvn -B <goals>` (add `-Denforcer.skip=true` for `-pl` builds). Never install Maven locally.
- Liquibase: never edit an applied changeset; new changesets go at the end of `server/src/main/resources/liquibase/db.changelog.xml` with `author="locker24"`, `context="common"`, id `dd.mm.yy-HH:MM`-style plus suffix `-l24-N`, each with a `<rollback>`.
- Every upstream file changed is added to `docs/locker24-core-changes.md` (file, change, why).
- "Latest" means latest **released** version everywhere; `applications.latestVersion` is recalculated over released versions only.
- Release modes: exactly `schedule`, `immediate`, `hold`; absent on the version-save request = `schedule`.
- Push types: existing `configUpdated`; new `forceAppUpdate` (server constant and agent constant spelled exactly so). `immediate` sends `configUpdated` first, then `forceAppUpdate`, per moved configuration.
- Pinned rows (`followLatest = false`) never change on upload, release, edit or delete. Editing a version never moves configurations. Deleting a version in use stays refused.
- Agent version `6.40-l24.6`, versionCode `1540006` (flavor `locker24`).
- Brand: primary `#009FD7`, accent `#FF6D00`, dark grey `#5C5C5C`; product name "Locker24 MDM"; About keeps "Based on Headwind MDM (Apache License 2.0)" with a link to https://h-mdm.com. Logos from `../locker24-web/public/logo.svg` and `../locker24-admin/public/locker-icon-white.svg`, favicon from `../locker24-web/app/favicon.svg` and `favicon.ico`.
- Theme choice values `light` | `dark` | `system`, `localStorage` key `l24-theme`, default `system`; `<html data-theme="light|dark">` set before Angular boots.
- Text contrast WCAG AA (4.5:1 body text) in both themes.
- UI copy in English (`en_US.json`); new keys added to all 13 locale files with the English text (they fall back otherwise but keep the files aligned, as Plan 2 did).
- Commits: plain messages, no Co-Authored-By, never mention AI/assistants/tools anywhere. No narrating comments.
- Deploys are automatic on push to `main`; implementers never push or deploy and never contact the droplet.

## Review Focus

1. A configuration saved by a client that omits `followLatest` (an older open panel tab, an API script) keeps every row's stored pin; it must not silently start following. Test in Task 3.
2. A held version is never reachable except by an explicit pin: not via the per-row Upgrade link, a new configuration, a copied configuration or customer, adding an app row, or the "outdated" marker. Tests in Tasks 2 and 3.
3. "On schedule" upload for a configuration that has no update window installs at once (agent behaviour, no `forceAppUpdate` sent); with a window, no `forceAppUpdate` is ever sent. Test in Task 4.
4. Two uploads of the same app in quick succession (or upload while a release runs) end with every follower on the highest released version, one install row per app per configuration, and pushes only to configurations that changed. Test in Task 3 (SQL) and Task 4.
5. Switching the theme while a plugin page (Fleet health charts, Device Log tables) is open repaints it fully; a plugin remote loaded after the switch starts in the right theme. Test in Task 10.

---

### Task 1: Agent 6.40-l24.6 handles `forceAppUpdate`

**Repo:** `locker24-hmdm-android`, branch `feat/locker24-release-flow` from `master`.

**Files:**
- Modify: `app/src/main/java/com/hmdm/launcher/json/PushMessage.java` (constant), `app/src/main/java/com/hmdm/launcher/worker/PushNotificationProcessor.java` (handler), `app/src/main/java/com/hmdm/launcher/locker24/ConfigFlowGate.java` (only if flags are not already OR-ed), `app/build.gradle` (locker24 flavor version), `docs/locker24-core-changes.md`
- Test: `app/src/test/java/com/hmdm/launcher/locker24/ForceAppUpdateTest.java`, extend `ConfigFlowGateTest.java`

**Interfaces:**
- Produces: push type string `"forceAppUpdate"`; agent version `6.40-l24.6` / `1540006`.

- [ ] **Step 1: Write failing tests.** `ConfigFlowGateTest.configUpdatedThenForceRunsOneFlowWithUserInteraction`: acquire with `userInteraction=false`, then request with `true` while in flight → exactly one follow-up flow runs and it has `userInteraction == true`. `ForceAppUpdateTest.forcePushStartsUserInteractionUpdate`: processing a `PushMessage` of type `forceAppUpdate` invokes the update entry point with `userInteraction=true` and a non-null (no-op if needed) notifier. `ForceAppUpdateTest.burstOfForcePushesCoalesces`: three pushes within the gate window → one flow.
- [ ] **Step 2: Run** `sh ./gradlew :app:testLocker24DebugUnitTest --tests '*ConfigFlowGateTest*' --tests '*ForceAppUpdateTest*'` → FAIL (constant/handler missing).
- [ ] **Step 3: Implement.** `PushMessage.TYPE_FORCE_APP_UPDATE = "forceAppUpdate"`; in `PushNotificationProcessor` route it to the existing configuration update with `userInteraction = true` through `ConfigFlowGate` (OR the flag if a flow is in flight). Verify every `uiNotifier` call site on that path tolerates the notifier you pass. List every behaviour that `userInteraction=true` changes besides the window and Wi-Fi checks (e.g. `ConfigUpdater` around lines 195 and 726) in `docs/locker24-core-changes.md` with a one-line harmlessness judgement; suppress anything that shows UI or prompts for this push.
- [ ] **Step 4: Run** the tests → PASS; `sh ./gradlew :app:assembleLocker24Release :app:assembleOpensourceDebug` builds (opensource must still compile).
- [ ] **Step 5: Bump** the locker24 flavor to `versionCode 1540006`, `versionName "6.40-l24.6"`. Commit: `feat: install app updates at once on a forceAppUpdate push`.

### Task 2: Data model, migration, latest = latest released

**Repo:** server, branch `feat/locker24-release-flow` from `main`.

**Files:**
- Modify: `server/src/main/resources/liquibase/db.changelog.xml` (new changesets at the end), `common/src/main/java/com/hmdm/persistence/domain/Application.java`, `ApplicationVersion.java`, `Configuration.java`, `common/src/main/java/com/hmdm/persistence/mapper/ApplicationMapper.java` (+ XML if used), `ConfigurationMapper.java` / `ConfigurationMapper.xml`, `docs/locker24-core-changes.md`
- Test: `server/src/test/sql/release-flow-model-check.sh` (Docker Postgres 16, Plan 2 style: applies the real changelog, seeds rows, asserts with SQL), wired into the `sql-checks` CI job in `.github/workflows/server-ci.yml` (glob both dirs)

**Interfaces:**
- Produces (DB): `configurationApplications.followLatest BOOLEAN NOT NULL DEFAULT TRUE`; `configurations.followLatestMainApp`, `configurations.followLatestContentApp BOOLEAN NOT NULL DEFAULT TRUE`; `applicationVersions.releaseState VARCHAR(16) NOT NULL DEFAULT 'released' CHECK (releaseState IN ('released','held'))`, `releasedAt BIGINT NULL`, `releaseMode VARCHAR(16) NULL`, `releasedBy INT NULL REFERENCES users(id) ON DELETE SET NULL`.
- Produces (Java): `ApplicationVersion.getReleaseState(): String`, `getReleasedAt(): Long`, `getReleaseMode(): String`, `getReleasedBy(): Integer`; `Application.isFollowLatest(): Boolean` (nullable, used for configuration rows); `Configuration.isFollowLatestMainApp(): boolean`, `isFollowLatestContentApp(): boolean` with setters. JSON names = field names.
- Produces (behaviour): `recalculateLatestVersion` and its SQL consider only `releaseState = 'released'`.

- [ ] **Step 1: Write the failing SQL check** with cases: (a) migration on a seeded DB sets `followLatest` TRUE for an install row on the latest version and FALSE for a row on an older version; main/content flags likewise; (b) inserting a held version higher than the released one leaves `applications.latestVersion` on the released one; (c) marking it released and recalculating moves `latestVersion` to it; (d) `mdm_config_app_upgrade` on an outdated row with a newer **held** version lands on the latest released, never the held; (e) a new configuration row inserted via the mapper's `COALESCE(usedVersionId, latestVersion)` path gets the latest released.
- [ ] **Step 2: Run** `bash server/src/test/sql/release-flow-model-check.sh` → FAIL.
- [ ] **Step 3: Implement** the changesets (columns, data migration, a CREATE OR REPLACE of any stored function that computes latest so it filters released) and the domain/mapper fields; every select of these tables returns the new columns; inserts/updates write them.
- [ ] **Step 4: Run** the SQL check → PASS; `mvn -B -pl common,server -am test -Denforcer.skip=true` in Docker → PASS.
- [ ] **Step 5: Commit**: `feat: release state on app versions and follow-latest flags on configurations`.

### Task 3: Moving followers, pins on save, copies, link dialogs

**Files:**
- Modify: `common/src/main/java/com/hmdm/persistence/ApplicationDAO.java` (replace the dead `doAutoUpdateToApplicationVersion` path; remove the call from the edit path), `ApplicationMapper.java`/XML, `ConfigurationDAO.java` (`updateConfiguration`, copy), `ConfigurationMapper.xml` (`insertConfigurationApplications` writes `followLatest`), `CustomerDAO.java` (copy keeps flags), the link endpoints' DAO calls used by `ApplicationResource` `POST /private/applications/configurations` and `POST /private/applications/version/configurations`
- Test: `server/src/test/sql/release-flow-move-check.sh`; `common/src/test/java/com/hmdm/persistence/ConfigurationFollowLatestTest.java` (unit tests of the pure decision helpers below)

**Interfaces:**
- Consumes: Task 2 columns and getters.
- Produces: `ApplicationDAO.moveFollowersToLatest(int applicationId): List<Integer>` — moves every following install row, and main/content app where the configuration flag is set, of every configuration referencing the app (any customer) to `applications.latestVersion`; returns distinct ids of configurations that actually changed; idempotent; one install row per app per configuration afterwards. `ApplicationDAO.releaseVersion(int versionId, String mode, int userId): List<Integer>` — sets `released`/`releasedAt`/`releaseMode`/`releasedBy`, recalculates latest, calls `moveFollowersToLatest`. Static helper `FollowLatestRules.followForLinkedVersion(int chosenVersionId, Integer latestReleasedId): boolean`.

- [ ] **Step 1: Write failing tests.** SQL check: following row moves, pinned row does not; main/content flags respected; configuration in another customer that references a common app moves; second call returns empty list; two versions released back to back end on the higher one with a single install row. Java: `savingWithNullFollowLatestKeepsStoredPin`, `savingNewRowDefaultsToFollow`, `copyConfigurationKeepsFlags`, `linkDialogFollowsOnlyWhenLatestChosen`.
- [ ] **Step 2: Run** both → FAIL.
- [ ] **Step 3: Implement.** In `updateConfiguration`, read stored flags by `applicationId` before the delete, and for each incoming row use its `followLatest` when non-null, else the stored value, else TRUE. Upload (insert version) with mode `schedule`/`immediate` and the release endpoint call `releaseVersion`; mode `hold` inserts with `releaseState='held'` and moves nothing. Remove the auto-update call from the version-edit path.
- [ ] **Step 4: Run** → PASS; full `mvn -B test` in Docker.
- [ ] **Step 5: Commit**: `feat: move following configurations to the latest released version`.

### Task 4: REST release modes, release endpoint, pushes, summary

**Files:**
- Modify: `server/src/main/java/com/hmdm/rest/resource/ApplicationResource.java` (version save reads `releaseMode`; new `POST /private/applications/versions/{id}/release`), `ConfigurationResource.java` (Upgrade endpoint pushes), `notification/src/main/java/com/hmdm/notification/PushService.java` (new `notifyDevicesOnForceUpdate(int configurationId)`), `notification/.../persistence/domain/PushMessage.java` (`TYPE_FORCE_APP_UPDATE = "forceAppUpdate"`), the version-save request JSON class
- Test: `server/src/test/java/com/hmdm/rest/resource/ApplicationReleaseResourceTest.java`, `ConfigurationUpgradePushTest.java`

**Interfaces:**
- Consumes: `releaseVersion`, `moveFollowersToLatest` (Task 3).
- Produces: request field `releaseMode: "schedule"|"immediate"|"hold"` on the existing version save; `POST /rest/private/applications/versions/{id}/release` body `{"mode":"schedule"|"immediate"}`; both respond with the usual envelope whose `data` is `{"configurations": <int>, "devices": <int>, "mode": "<mode>"}`. Permission: same as editing applications. A release of an already released version returns 400 `error.version.already.released`. One INFO log line per release: `Release app=<pkg> version=<v> mode=<m> by=<login> configurations=<n> devices=<n>`.

- [ ] **Step 1: Write failing tests** (mock DAO + PushService): schedule → `notifyDevicesOnUpdate` once per moved config, no force; immediate → update then force per moved config, in that order; hold → no push, summary zeros; pinned-only app → no push; Upgrade endpoint → `notifyDevicesOnUpdate(configId)` after the upgrade; absent `releaseMode` behaves as schedule; unknown mode → 400.
- [ ] **Step 2: Run** `mvn -B -pl notification,common,server -am test -Denforcer.skip=true` → FAIL.
- [ ] **Step 3: Implement**; pushes happen after the DAO transaction returns.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit**: `feat: release modes, a release endpoint and pushes for moved configurations`.

### Task 5: Panel release UI, and Angular tests in CI

**Files:**
- Modify (under `server/src/main/web/projects/core/src/app/`): `applications/components/add-version-form/*` and `version-dialog/*` (replace the Auto Update checkbox with the required "Apply this version" radio group; summary after save), `applications/configuration/version-form.config.ts`, `applications/types/version-form.type.ts`, `entity/application/types/create-version-request.type.ts` (`releaseMode`), `applications/services/application-dialog.service.ts` (drop `autoUpdateDisplayed`), `applications/components/application-versions-table/*` + `application-versions-table.config.ts` (state column, Release action → dialog with schedule/immediate + summary), `configurations/...` Applications tab row component (Follows latest / Pinned to X, toggle; choosing a version pins), main/content app selectors (toggle), `entity/configuration/types/configuration-dto.type.ts` (remove `autoUpdate`, add flags), entity services for the release endpoint; `public/i18n/*.json`
- Modify: `.github/workflows/server-ci.yml` (new job `web-tests`: pnpm 9.15.0, `pnpm exec ng test core --watch=false --browsers=ChromeHeadless` and the locker24-health plugin's tests)
- Test: `*.spec.ts` next to each changed component

**Interfaces:**
- Consumes: Task 4 request/response shapes; Task 2 JSON fields.

- [ ] **Step 1: Write failing specs:** add-version form requires a mode, default `schedule`, sends `releaseMode`; the summary renders "Released to 2 configurations (5 tablets)" and "Held; not applied"; versions table shows Held/Released with who/when and Release only on held rows; release dialog posts the chosen mode; configuration row: picking a version sets `followLatest=false`, toggling on sets true; the Auto Update checkbox no longer exists. Copy (en_US): "Apply this version", "On each configuration's schedule", "Immediately", "Hold (upload only)", help "Immediately: tablets on agent 6.40-l24.6 or later install now; older agents install in their next update window."
- [ ] **Step 2: Run** `pnpm exec ng test core --watch=false --browsers=ChromeHeadless` (in `server/src/main/web`) → FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** → PASS; `pnpm run build` → OK; actionlint on the workflow (Docker `rhysd/actionlint`).
- [ ] **Step 5: Commit**: `feat: release choice on upload, release of held versions, follow or pin per configuration`.

### Task 6: Panel fixes: tooltips, real links, Package ID

**Files:**
- Modify: `applications/configuration/applications-table.config.ts` (drop `AppNameCell` from `pkg`; delete `app-name-cell/` if unused), `applications/components/url-cell/*` (`<a [href] target="_blank" rel="noopener">`, both split URLs), `apk-form`, `add-version-form`, `edit-version-form` templates (`[help]` on every field using `form.application.*.title`; new keys `form.application.file.title`, `form.application.url.title` if missing), `shared/components/side-menu/*` and navigating table cells (device, configuration, application names) to anchors with `routerLink` (plain click unchanged; Cmd/middle-click opens a tab)
- Test: specs for each

- [ ] **Step 1: Failing specs:** Package ID cell shows `pkg`; URL cell renders an anchor with `target="_blank"` and `rel="noopener"`; every form control in the three forms has a non-empty help text; side-menu items and the named cells are `<a href>` elements with the route as `href`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS; build OK.
- [ ] **Step 5: Commit**: `fix: package column, clickable links and help tooltips in the panel`.

### Task 7: Headwind demo data cleanup script

**Files:**
- Create: `deploy/cleanup-demo-data.sh`, `deploy/test/cleanup-demo-data-check.sh` (Docker Postgres: applies the changelog plus `install/sql/hmdm_init.en.sql`, adds a "real" configuration that uses one demo app, runs the script with `--yes`, asserts)
- Modify: `deploy/README.md` (how to run), CI `sql-checks` glob

**Interfaces:** `cleanup-demo-data.sh [--db hmdm] [--yes]`, run as root on the droplet; prints the plan (counts and names) and asks `y/N` unless `--yes`; exits 0 when nothing to delete.

- [ ] **Step 1: Failing check:** after the run, demo configurations unused by devices are gone, demo device `h0001` gone if it has no enrolment (`lastUpdate` null/0), unreferenced demo apps gone (by the package list from `install/sql/hmdm_init.en.sql`), the demo app used by the real configuration and `app.locker24.mdm` remain, second run deletes nothing.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** in one transaction with `ON_ERROR_STOP`. **Step 4: Run** → PASS; shellcheck clean.
- [ ] **Step 5: Commit**: `feat: script to remove Headwind demo data`.

### Task 8: Locker24 branding

**Files:**
- Modify: `server/src/main/web/projects/core/src/index.html` (title "Locker24 MDM", favicon links, boot spinner `#009FD7`), `core/public/favicon.ico` (+ add `favicon.svg`), `core/public/images/logo.png` (100×100 locker icon on `#009FD7`), add `core/public/images/logo-wordmark.svg` and `logo-wordmark-dark.svg` (text white), `shared/components/side-menu/*`, `auth/components/auth-container/*`, `privacy/pages/privacy/*`, `shared/components/about-dialog/about-dialog.html` (name + "Based on Headwind MDM (Apache License 2.0)" link), `public/i18n/*.json` (`app.name`, `app.title`, `footer.copyright` → Locker24 MDM / © Locker24), `server/build.properties.example` and `server/conf/context.xml` (`rebranding.name=Locker24 MDM`, `rebranding.vendor=Locker24`), `custom-theme.scss` (palette from `#009FD7` primary, `#FF6D00` accent)
- Test: spec that About shows both lines; a script `server/src/main/web/tools/check-branding.sh` (grep) failing on any remaining `Headwind`/`h-mdm` in templates and en_US except the credit line; run in the `web-tests` job

- [ ] **Step 1: Failing spec + check script.** **Step 2: Run** → FAIL. **Step 3: Implement.** **Step 4: Run** → PASS; screenshots of login, devices, About into the review package (`playwright` or Chrome headless screenshot via `pnpm exec`, builder's choice).
- [ ] **Step 5: Commit**: `feat: Locker24 MDM branding in the panel`.

### Task 9: Dark mode in the core panel

**Files:**
- Create: `core/src/app/shared/services/theme.service.ts` (`ThemeService`: `mode(): 'light'|'dark'|'system'`, `setMode(m)`, `effective(): 'light'|'dark'`, emits on change incl. OS changes), theme switch in the top-bar user menu component, inline pre-boot script in `index.html` (reads `l24-theme`, sets `data-theme`), `server/src/main/web/tools/check-colors.sh` (fails on hex/rgb()/hsl() literals in `projects/core` and `projects/hmdm-ui-kit` SCSS/HTML/TS outside the theme token files)
- Modify: `custom-theme.scss` (light and dark token sets under `:root[data-theme=light]`/`[data-theme=dark]`, `color-scheme`), `styles.scss` and every component/ui-kit style with literals (replace by tokens)
- Test: `theme.service.spec.ts` (default system, persists, follows OS change only in system mode, try/catch around storage), switch component spec, `check-colors.sh` in `web-tests`

- [ ] **Step 1: Failing specs + color check** (expect the check to list today's literals; record the count in the report). **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**; storage access wrapped in try/catch (private mode); no white flash on reload in dark.
- [ ] **Step 4: Run** → PASS; contrast check of body text and status colours on both backgrounds (a small node script with the WCAG formula over the token values) → AA; screenshots of every core page in both themes.
- [ ] **Step 5: Commit**: `feat: light, dark and system themes in the panel`.

### Task 10: Dark mode in plugin pages and Fleet health charts

**Files:**
- Modify: style and template files with colour literals in `plugins/{audit,deviceinfo,devicelog,messaging,push,xtra}/src/main/web/**` (whichever exist) and `plugins/locker24-health/src/main/web/**`; the health charts read colours from CSS tokens at render and redraw on `ThemeService` change (or on `data-theme` mutation if the remote cannot inject the core service)
- Test: health plugin spec `charts redraw with new token colours on theme change`; `check-colors.sh` extended to plugin web sources

- [ ] **Step 1: Failing spec + extended check.** **Step 2: Run** → FAIL.
- [ ] **Step 3: Implement**; status colours keep meaning and AA contrast in both themes.
- [ ] **Step 4: Run** → PASS (core + health plugin tests); screenshots of each plugin page in both themes, including switching theme while Fleet health is open and loading a plugin page after switching.
- [ ] **Step 5: Commit**: `feat: dark mode for plugin pages and fleet health charts`.

### Task 11: Release-flow docs, device checks, deploy notes

**Files:**
- Create: `docs/locker24-release-flow.md` (upload modes, hold and release, canary with pin, follow/pin, what older agents do, offline tablets, the Upgrade link, where to see who released what)
- Modify: `docs/locker24-core-changes.md` (complete), `deploy/README.md` (post-deploy checks for this release; `rebranding.*` on the droplet if needed; run `cleanup-demo-data.sh`), agent repo `docs/locker24-pilot-config.md` (device checks R1–R5: schedule inside window; schedule outside window waits; immediate outside window installs at once on l24.6; hold + pin pilot + release; pinned configuration untouched by an upload)

- [ ] **Step 1: Write the docs** against the code as built (every claim checked against the implementation).
- [ ] **Step 2: Commit** in each repo: `docs: release flow, device checks and deploy notes`.

## Rollout (controller, after the final review)

1. Agent: merge to `master`, build `6.40-l24.6` release APK into `dist/`, user uploads it (on schedule) to both configurations.
2. Server: merge to `main`, user pushes → automatic deploy; post-deploy checks from `deploy/README.md`.
3. User runs `deploy/cleanup-demo-data.sh` on the droplet.
4. Device checks R1–R5 on M-01.
