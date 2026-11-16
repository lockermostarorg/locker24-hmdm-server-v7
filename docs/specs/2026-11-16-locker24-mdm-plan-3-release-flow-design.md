# Locker24 MDM, Plan 3: release flow, panel fixes, branding and dark mode

Date: 2026-11-16. Builds on `2026-11-11-locker24-mdm-design.md` (the overall MDM design) and Plan 2 (health). Server repo `locker24-hmdm-server-v7`, agent repo `locker24-hmdm-android`.

## Why

Today a new app version reaches the tablets in two manual steps: upload the APK, then open every configuration that uses the app and pick the new version (or click "Upgrade" per row). It is easy to forget the second step, so an uploaded fix silently never ships. When a configuration has a scheduled update window, there is no way to say "this one is urgent, install it now". The panel also has small papercuts that slow daily work: fields without explanations, a "Package ID" column that shows the app name, URLs and menu entries that cannot be opened in a new tab, a dead "Auto Update" checkbox, and Headwind's demo apps and configurations cluttering the lists.

## Goals

1. A configuration follows the latest uploaded version of each of its apps by default. Uploading a version is the release; no second step.
2. Each upload chooses how it is applied: **on schedule** (respect each configuration's update window), **immediately** (install now, ignoring the window), or **hold** (upload only; release later).
3. A held version can be released later with one click, with the same schedule/immediately choice. Together with pinning, this gives a canary: upload on hold, pin the pilot configuration to it, release when it works.
4. Pinning stays possible: a configuration can be pinned to a specific version and then does not follow.
5. Tablets learn about every change that moves them to a new version right away (push), including the existing per-row "Upgrade" link, which today saves silently.
6. Panel fixes: help tooltips on the application and version forms; real links (menu, navigation, app URLs) that open in a new tab with Cmd/middle-click; the "Package ID" column shows the package; the dead "Auto Update" checkbox is removed; Headwind's demo data is removed.
7. Locker24 branding: the panel looks and reads like a Locker24 product (name, logo, favicon, colours), with Headwind credited as the base.
8. Dark mode: light, dark, or follow the system, chosen per browser; every core page and every plugin page (including the Fleet health dashboard) is readable in both.

## Non-goals

- A Bosnian translation: a later plan (Plan 4).
- A per-user theme stored on the server (the choice is per browser).
- Staged percentage rollouts or automatic rollback.
- Uploading APKs from CI.
- Disabling the power button, the app-drawn keyboard.

## Anti-goals (would make this a failure even if it works)

- A release that moves configurations nobody meant to move: a pinned configuration must never change on upload or release.
- A release that installs outside the update window when "on schedule" was chosen.
- Losing the per-configuration version choice that exists today, or changing what older agents (6.40-l24.5 and earlier) do with an ordinary configuration update.
- A cleanup that deletes anything a real configuration or device uses.

## Constraints

- Headwind v7 data model (from the code): `configurationApplications(configurationId, applicationId, applicationVersionId, action, ...)` pins each install row to a version; `applications.latestVersion` is recalculated on version insert/update/delete; `configurations.mainAppId`/`contentAppId` point at versions. The stored procedure `mdm_config_app_upgrade(configId, appId)` moves one configuration's rows (and main/content app) to the latest version. The private DAO hook `ApplicationDAO.doAutoUpdateToApplicationVersion` already runs after a version becomes latest, but is gated on `configurations.autoUpdate`, which is hard-coded false — it is dead code.
- Pushes: `PushService.notifyDevicesOnUpdate(configId)` sends `configUpdated` to the configuration's devices over MQTT and the polling queue. It must run after the database transaction commits, from the REST layer.
- The agent installs app updates only inside the configuration's window (`appUpdateFrom`/`appUpdateTo`, sent only when `scheduleAppUpdate` is on) unless the update was user-initiated (`ConfigUpdater.updateConfig(..., userInteraction=true)`), which also bypasses the Wi-Fi-only restriction. No server-triggered path sets it today.
- Liquibase changesets already applied must never be edited; new changesets only. Core changes go in `docs/locker24-core-changes.md`.
- API backward compatibility: the panel and the server ship together, but agents in the field are older. Anything new for the agent must be additive: an unknown push type is ignored by older agents.
- Deploys are automatic on push to `main` (Plan 2 Task 18). The MDM server is not yet serving the production fleet.

## Approaches considered

- **A. Follow latest per configuration app row, choice at upload (chosen by the user).** A per-row "follow latest" flag, default on; the upload dialog chooses schedule / immediately / hold; a Release action for held versions. Most work (server, panel, agent), but does exactly what was asked and keeps pinning and a canary path.
- B. Follow latest per application. One switch per app moves every configuration. Simpler; since each tenant has its own package (PostBox vs PostBox QA) it is nearly the same, but there is no canary inside one tenant.
- C. Keep pinning; add a one-click "Release to all configurations". Cheapest, but keeps the two steps the user wants gone.

## Design

### Data

New changeset(s) in core Liquibase:

- `configurationApplications.followLatest BOOLEAN NOT NULL DEFAULT TRUE`. Existing install rows (`action = 1`): TRUE when the row is on the app's latest version, FALSE (pinned) when it is on an older one, so a deliberate older pin survives the migration. Rows with other actions keep the default; it has no meaning for them. (On mdm.locker24.app today there is one customer, and both real configurations are on the latest agent and kiosk versions, so both will follow.)
- `configurations.followLatestMainApp BOOLEAN NOT NULL DEFAULT TRUE` and `configurations.followLatestContentApp BOOLEAN NOT NULL DEFAULT TRUE`, so the agent itself (main app) and the kiosk content app follow the same rule. *My call:* both default on, because the agent's self-update and the kiosk app are exactly the apps we release most. Migration sets each to whether the current main/content app version is the latest, like the rows.
- `applicationVersions.releaseState VARCHAR(16) NOT NULL DEFAULT 'released'` with values `released` and `held`, plus `releasedAt BIGINT NULL`, `releaseMode VARCHAR(16) NULL` (`schedule` | `immediate`) and `releasedBy INT NULL` (user id). Existing versions are `released`.

"Latest" means the highest version (Headwind's existing version comparison) **among released versions**, everywhere. *Decision:* `applications.latestVersion` itself changes meaning to "latest released": `recalculateLatestVersion` skips held versions. That way every existing reader of `latestVersion` stays correct without separate queries: the per-row Upgrade (`mdm_config_app_upgrade`), new and copied configuration rows (`COALESCE(usedVersionId, latestVersion)`), and the "outdated" marker never offer a held version. Holding a version therefore really holds it; the only way a configuration reaches a held version is an explicit pin to it (the canary). Releasing a held version recalculates `latestVersion`.

### When does a configuration move?

A configuration app row moves to version V of app X when all of:
1. the row is an install row (`action = 1`) for app X and `followLatest` is true;
2. V is released and is the latest released version of X;
3. the row is not already on V.

The same rule applies to `mainAppId` / `contentAppId` with their configuration-level flags.

Moves happen at three moments:
- **Upload** of a new version with mode schedule or immediately: V is released at once and every following row of every configuration in the same customer moves.
- **Release** of a held version (new action on the version): same move.
- No other moment. *Decisions:* deleting a version that any configuration row uses stays refused, as today (following rows are on the latest released version, so it cannot be deleted while followed; release a newer version or pin first). Editing a version (its URL, or even its version string) never moves configurations: the edit path's call to the auto-update hook is removed, and the panel points to Release instead.

Pinning and following, wherever a version is chosen:
- **Configuration editor:** each install row has a "Follow latest version" toggle. Choosing a specific version in the row's version selector turns the toggle off (pinned). Turning it on moves the row to the latest released version on save.
- **Application page, "configurations" dialog** (the existing `POST /private/applications/configurations` and `POST /private/applications/version/configurations` links): a row linked there follows when the chosen version is the latest released version, and is pinned otherwise.
- **Saving a configuration** deletes and re-inserts its application rows (Headwind's existing behaviour). The row DTO carries `followLatest`; when a client omits it (null), the stored value for that application is kept (read before the delete). New rows default to following. Copying a configuration or a customer copies the flag.

Scope: a move applies to every configuration row that references the application, whatever the configuration's customer. Common (shared) applications are shared on purpose, so all their followers move and are pushed. mdm.locker24.app has a single customer today.

### Implementation of the move

Replace the body of the dead `doAutoUpdateToApplicationVersion` path with a set-based update keyed on `followLatest` (and the configuration flags for main/content app), returning the ids of the configurations it changed. The release mode travels as a new optional field `releaseMode` (`schedule` | `immediate` | `hold`; absent = `schedule`) on the existing version-save request, and as the body of a new `POST /private/applications/versions/{id}/release` endpoint. The REST layer pushes to each changed configuration after commit: `configUpdated` always, plus `forceAppUpdate` for `immediate` (a new `PushService` method next to `notifyDevicesOnUpdate`). Configurations that were already on the new version because they were pinned to it are not pushed. `mdm_config_app_upgrade` keeps its logic and now always lands on a released version because `latestVersion` means latest released.

### Applying: schedule vs immediately

- **On schedule:** after the move, send the ordinary `configUpdated` push. Tablets download and install inside their configuration's window (or at once if the configuration has no window). Older agents behave exactly as today.
- **Immediately:** after the move, send a new push type **`forceAppUpdate`** to the same devices. Agent 6.40-l24.6 handles it by running a configuration update with `userInteraction = true`, so it installs at once regardless of the window and the Wi-Fi-only restriction. Agents older than l24.6 ignore the unknown type, so the server also sends `configUpdated` first; old agents then install in their window. The panel says so ("Immediately: tablets on agent 6.40-l24.6 or later install now; older agents install in their next update window").
- A tablet that is offline when the push is sent picks the change up at its next sync and installs per its window; "immediately" is best-effort for online tablets. *My call:* not worth a persistent per-device "force" flag in this plan.

### Agent (6.40-l24.6)

- `PushMessage.TYPE_FORCE_APP_UPDATE = "forceAppUpdate"`; `PushNotificationProcessor` handles it by running the existing configuration update with `userInteraction = true`, through the existing single-flight `ConfigFlowGate`. The builder must verify, with tests: (a) when `configUpdated` and `forceAppUpdate` arrive back to back, one flow runs and it has `userInteraction = true` (the gate's flags must be OR-ed; change the gate if they are not); (b) the update path is safe with no UI notifier (the push arrives with no activity in front), or a no-op notifier is passed; (c) every other place `userInteraction` changes behaviour (e.g. ignoring the last-download back-off, `ConfigUpdater` around lines 195 and 726) is listed in the core-changes doc and judged harmless for a kiosk; anything that would show UI or prompt is suppressed for this push.
- No other agent change. Listed in `docs/locker24-core-changes.md`.

### Panel

- **Add version dialog:** the "Auto Update" checkbox is replaced by a required choice "Apply this version": *On each configuration's schedule* (default) / *Immediately* / *Hold (upload only)*, each with a one-line help text. After upload, a summary: "Released to N configurations (M tablets)" or "Held; not applied".
- **Versions list:** a state column (Released / Held, with who and when), and a **Release** action on held versions that asks the same schedule/immediately choice and shows the same summary.
- **Configuration → Applications tab:** each install row shows "Follows latest" or "Pinned to X"; picking a version pins; a toggle re-enables following. The main app and content app selectors get the same toggle.
- **Upgrade link** on outdated rows: unchanged behaviour plus a push to the configuration's devices.
- **Tooltips:** every field of the application form, the add-version and edit-version forms gets a help tooltip using the ui-kit `help` input and the existing `form.application.*.title` keys, adding keys where missing (file, URL, the release choice). English only.
- **Real links:** the side menu, table cells that navigate (device, configuration, application names), and the app URL cells render as `<a href>` (router links for internal pages, `target="_blank" rel="noopener"` for APK URLs), so Cmd-click and middle-click open new tabs. Plain click keeps today's behaviour.
- **Package ID column:** shows `pkg` (drop the wrong `AppNameCell` renderer).

### Branding

Brand (from the Locker24 apps and admin): primary blue `#009FD7`, call-to-action orange `#FF6D00`, dark grey `#5C5C5C`; the wordmark `logo.svg` from `locker24-web/public` (identical in `locker24-admin`), the white locker icon `locker-icon-white.svg` and the favicon from `locker24-web/app`. Font stays Roboto (*my call*: the panel's Material components are tuned for it).

- **Name:** "Locker24 MDM" in the browser tab title (`index.html`), the About dialog, the login page, the i18n keys `app.name`, `app.title` and `footer.copyright` (all 13 locale files, since English is the fallback and other locales are still selectable), and the server's `rebranding.name`/`rebranding.vendor` (so password-reset and other emails say Locker24 MDM). The About dialog keeps a line "Based on Headwind MDM (Apache License 2.0)" with a link, to honour the licence and the upstream.
- **Logo:** the side menu and the login page show the Locker24 logo instead of Headwind's; square spots (the 100×100 `rebranding.logo`/`images/logo.png` slot) use the locker icon on the brand blue. Each logo has a light and a dark variant (the wordmark's dark-grey text must be white in dark mode).
- **Favicon** and the boot spinner colour switch to Locker24.
- **Colours:** the Angular Material theme (`custom-theme.scss`) is generated from the brand blue as primary and the orange as accent; the side menu keeps a dark background in both themes (brand dark grey family).
- Branding values live in the fork's code and `build.properties`/context, not only in the database, so a fresh install is branded. No Headwind trademark or logo remains in the UI apart from the "Based on" credit.

### Dark mode

- **Choice:** a theme switch (Light / Dark / System) in the top bar's user menu, stored in the browser (`localStorage`); default System (`prefers-color-scheme`). A small inline script in `index.html` applies the stored choice before Angular boots, so there is no white flash.
- **Mechanism:** Angular Material 3 theming with `color-scheme: light dark` and a `data-theme` attribute on `<html>` for the explicit choices. All hard-coded colours in the core panel's SCSS (`styles.scss`, `custom-theme.scss`, component styles, the ui-kit library) are replaced by CSS custom properties (`--l24-*` or the Material `--mat-sys-*` tokens) defined for both themes. The builder inventories every hex/rgb literal first (grep) and the plan lists them.
- **Plugins:** the plugin pages are separate remotes but share the page's CSS custom properties, so they inherit the tokens. Every plugin UI in use (audit, deviceinfo, devicelog, messaging, push, xtra, locker24-health) is checked in both themes; hard-coded colours in them are replaced the same way. The Fleet health charts read their colours from the tokens at render time and redraw when the theme changes; status colours (green/yellow/red/grey) keep their meaning and contrast in both themes.
- **Contrast:** text and status colours meet WCAG AA (4.5:1 for body text) in both themes.

### Audit

Each upload with a mode, each release and each automatic move writes an audit record via the existing audit plugin's mechanism (who, version, mode, configurations moved). If the audit plugin cannot record server-side events without an HTTP request, a log line at INFO is enough; the builder decides.

### Headwind demo data cleanup

A one-off, idempotent SQL script under `deploy/` (not a Liquibase changeset, because the demo data was never seeded by Liquibase but by Headwind's installer, and other installs of this fork may have none):
- deletes the demo device `h0001` if present and not enrolled;
- deletes the five demo configurations by name if no device uses them;
- deletes applications from Headwind's demo list (by package, from `install/sql/hmdm_init.en.sql`) that are not referenced by any configuration row, `mainAppId` or `contentAppId`;
- never deletes `app.locker24.mdm`, `com.hmdm.launcher` if referenced, or anything a real configuration uses;
- prints what it would delete and asks for confirmation unless `--yes`. The user runs it on the droplet.

## Testing

- SQL check scripts (Plan 2 style, Docker Postgres) for the move rule: follow vs pinned rows, held versions skipped, delete moves back, main/content app flags, customer isolation.
- Server unit tests for the REST paths: upload with each mode pushes the right types to the right configurations; release; pinned configuration never pushed; Upgrade link now pushes.
- Agent unit test: `forceAppUpdate` triggers one user-interaction flow; repeated pushes coalesce.
- Angular tests for the release choice, the versions state/Release action, the follow/pin toggle and link rendering.
- Device check (pilot M-01): upload with schedule inside/outside a window, immediately outside a window (installs at once on l24.6), hold + pin + release.
- Branding and dark mode: screenshots of the main pages (login, devices, configurations and its tabs, applications and versions, Fleet health, settings, each plugin page) in light and dark, reviewed side by side; an automated check that no hex/rgb colour literal remains outside the theme files; contrast check of the status colours.

## Rollout

1. Agent 6.40-l24.6 first (it only adds a push type; harmless without the server).
2. Server deploy (auto on push to main): changesets add columns with defaults, so existing configurations start following.
3. Run the demo cleanup script on the droplet.
4. Document the release flow in `docs/locker24-release-flow.md` (how to release, hold, canary, pin).
5. Set `rebranding.*` on the droplet if the build properties alone do not carry them (the deploy notes say which).

## Left to the builder

- Exact SQL of the set-based move and whether it lives in a stored function or the mapper.
- How the summary counts tablets (devices per moved configuration).
- Audit record shape.
- Which navigation cells become links beyond the ones named.
- The exact token names and the dark palette shades, within the brand and contrast rules above.
