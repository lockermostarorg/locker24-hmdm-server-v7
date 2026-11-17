# Locker24 MDM: releasing app versions

How a new app version reaches the tablets, and what to do when it should not reach all of them at once. This describes the behaviour as built; the design record is `specs/2026-11-16-locker24-mdm-plan-3-release-flow-design.md` (see its "Changes during build" list for where the build differs from it).

## The idea

Every install row of a configuration (and the configuration's main app and content app) either **follows the latest released version** or is **pinned** to one version. A configuration follows by default. Uploading a version is the release: every following row of every configuration moves to it, and the tablets are told. There is no second step of opening each configuration.

"Latest" always means the latest **released** version. A held version is never the latest, so nothing reaches it by accident: not the per-row Upgrade link, not a new or copied configuration, not adding an app to a configuration, and not the "outdated" marker. The only way a configuration gets a held version is to be pinned to it explicitly.

Pinned rows never change on upload, release, edit or delete.

## Uploading a version

Applications, then the application, then add a version (or Add application for a new app). The dialog asks "Apply this version":

| Choice | What happens |
|---|---|
| On each configuration's schedule (default) | The version is released. Following rows move to it and the tablets get an ordinary configuration update. Tablets install inside their configuration's update window. A configuration with no update window installs at once. |
| Immediately | Same move, then the tablets are told to install now. Details below. |
| Hold (upload only) | The version is stored and applied nowhere. Release it later from the versions list. |

Afterwards a summary shows "Released to N configurations (M assigned tablets)" or "Held; not applied". The tablet count is the number of tablets assigned to the moved configurations, not the number of tablets that are online.

Rules that can surprise you:

- The first version of an application is always released, whatever you choose (the Hold choice is hidden when the app has no released version). Nothing can follow a brand-new app, so releasing it moves nothing. To stage a brand-new app, upload it and pin the configurations you want to the version.
- Uploading a version string that already exists is refused ("This version already exists. Increase the version number and upload again."). The one exception is uploading the identical file again (same version code, size and SHA-256): that changes nothing, and the summary reports the stored state (held stays held). Bump the version name for every rebuild.
- Uploading a version lower than the latest released one stores and releases it, but moves nothing; the summary says "This version is lower than the latest released version; no configuration was moved."
- Editing a version (URL, version string) never moves any configuration and sends no push. Use Release or Notify tablets again.
- Deleting a version that a configuration uses stays refused. The last released version of an application cannot be deleted while other versions exist.

### Immediately: what the tablet does

The server first sends the usual configuration update to every moved configuration, then a `forceAppUpdate` push naming the application and version (the version string exactly as stored).

- **Agent 6.40-l24.6 or later** installs only the named app at once, ignoring the update window and the Wi-Fi-only restriction for that app. Everything else in the same configuration (other app updates, removals, files) still obeys the window and the Wi-Fi rule. Example: outside the window, an immediate release of the kiosk app installs the kiosk app now, while an agent update that was released on schedule keeps waiting for the window.
- The named app's package and the version configured on the tablet must both match the push, otherwise the app waits for the window. The push is ignored if it is more than 60 minutes old when the tablet processes it.
- A forced app also bypasses the "this version failed to install last time" block, once per push.
- **The agent installs its own update last in every flow** (forced or not): its own install restarts the process, so other apps are installed first.
- **Agents older than l24.6** ignore the unknown push. They only see the configuration update that came first, so they install in their next update window. The panel says so under the choice.
- **Offline tablets.** "Immediately" is best effort for tablets that are online when the push is sent. An offline tablet picks the new version up at its next sync and installs it in its window. If it reconnects within the 60 minutes and the broker delivers the queued push, it installs at once; do not rely on that. For stragglers, use Notify tablets again (below): it sends a fresh push.

## Hold and release

A held version appears in the versions list with state "Held". Choose Release on that row, then On each configuration's schedule or Immediately. The effect is the same as uploading with that choice: following rows move, tablets are pushed, the summary is shown. Releasing a version that is already released is refused with "This version has already been released"; the original who and when are kept.

The Release dialog notes: "Configurations pinned to this version will follow the latest version again." Releasing a held version switches every row (and main/content app) pinned to exactly that version back to following, in the same step. That is how a canary ends.

## Canary: hold, pin the pilot, release

1. Upload the new version with **Hold**.
2. Open the pilot configuration (for example the one M-01 uses), Applications tab, pick the held version for the app's row. Picking a version pins the row ("Pinned to X"); held versions are marked in the picker. Save. The pilot tablet gets the configuration update and installs per its window.
3. Check the pilot (Fleet health shows the app version).
4. Release the version, Immediately or on schedule. All following configurations move; the pilot, pinned to exactly that version, rejoins following. Pins to any other version stay.

A pinned row on a version higher than latest is not shown as outdated.

## Follow or pin, per configuration

In the configuration editor, Applications tab, each install row shows a "Follows latest" toggle:

- On: the row moves to each newly released version. Turning it on moves the row to the latest released version when you save.
- Off ("Pinned to X"): the row stays on the chosen version. Choosing a version in the row's selector turns the toggle off.

The main app (the agent) and the content app have the same toggle on the configuration's MDM settings tab (under the MDM application and content app selectors), and follow the same rule. The toggle mirrors the install row for the same package, so they cannot disagree.

Details of saving:

- A client that omits the flag (an old open tab, an API script) keeps the stored pin. An explicit flag wins: true follows at the latest released version (the version it sent is ignored), false pins to the version sent.
- A new row follows only when its version is the latest released one; a new row with another version is pinned.
- Linking an application to configurations from the application page (the Configurations dialog) follows when the chosen version is the latest released and pins otherwise.
- Copying a configuration or a customer copies the flags.

## The Upgrade link

On a row whose version is behind the latest released version the configuration editor shows the existing Upgrade link. It moves that row to the latest released version, sends the configuration update to the configuration's tablets, and leaves the row's follow state alone: a pinned row stays pinned, now on the latest version. Use the toggle to resume following. On a row that is not behind (or when only a canary is ahead) it does nothing.

## Check for updates

Panel menu, Check for updates (super admin, or the only customer) downloads application updates from the update server and stores them as versions:

- Download only: the version is stored on hold. Nothing moves; release it from the versions list.
- Update all configurations: the version is released on schedule and the configurations that use the app are moved to it, including pinned rows, which stay pinned. This is an explicit administrator action, unlike an upload. Moved configurations get one configuration update push each.

## Notify tablets again

On a released version in the versions list, Notify tablets again re-sends the update (and the immediate push if you choose Immediately) to every configuration currently on that version. It moves nothing. The panel offers it on released versions only, although the server also accepts it for a held version (it then reaches only configurations pinned to it). Use it when the summary warns "Tablets of N configurations could not be notified", or to nudge offline tablets that have since come online.

The warning counts configurations where sending threw an error (including the long-poll path). It does not detect a broker that is down: if the MQTT broker is disconnected the push is dropped silently and the summary still reports success. After an upload during a broker outage, run Notify tablets again once it is back.

## Error messages you may see

| Message | Meaning |
|---|---|
| This version already exists. Increase the version number and upload again. | The version string exists and the file is not identical. |
| This version has already been released | Release was asked for a version that is not held. |
| Unknown release mode | A script sent a mode other than schedule, immediate or hold (Release and Notify accept schedule and immediate only). |
| The last released version of an application cannot be deleted while other versions exist | Delete refused; release another version first or delete the others. |
| The version is either unique or used in configurations and cannot be deleted | Existing rule: a configuration still uses the version. |
| The application is not specified / The application version is not specified | A link request without an id. |
| Another change to this application was in progress. Please try again; if you were uploading a file, upload it again | Two changes collided (database deadlock). Nothing was applied; an upload must be repeated. |
| The requested object was not found | The version no longer exists. |
| No rights to execute / Managing of the common applications is allowed by Super-Administrator only | Permission, customer or common-app rule. |
| Another application with this name already exists / More recent application version exists | Existing Headwind checks. |

These errors come as HTTP 200 with `{"status":"ERROR","message":"<i18n key>"}`, like the rest of the API, so scripts must read `status`, not the HTTP code.

## Where to see who released what

- Versions list: the State column shows Released or Held, when, and by whom (the name is shown only for users of the same customer, or to a super admin; otherwise the user id).
- Audit plugin: release and notify are audited (actions "Application version released" and "Tablets notified of application version").
- Tomcat log (`catalina.out`): every non-hold release, Notify tablets again (mode schedule or immediate), Upgrade and Check for updates writes one INFO line, `Release app=<package> version=<v> mode=<schedule|immediate|upgrade> by=<login> configurations=<n> devices=<n>`; Upgrade and Check for updates also list `configurationIds`.

## Remember when deploying this release

- Deploy the server first, then upload agent 6.40-l24.6 (`deploy/README.md`, "Rollout order"). If l24.6 is uploaded first and the server deployed afterwards, the data migration sees the configurations on the older agent version, which is no longer the latest, and migrates them pinned.
- Rolling back the data-migration changeset alone resets every pin (every row follows again); see `deploy/README.md`.
- The QR code's APK checksum and the launcher download link's checksum are computed once per app version and cached for that version. Because a different file under an existing version string is now refused, rebuild with a new version name every time; never replace the file on disk behind an existing version.
