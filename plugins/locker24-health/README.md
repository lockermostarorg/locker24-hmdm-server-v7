# locker24-health

## Panel history: decisions

- Device clock of 1970 (or any unusable clock): the ingest clamps the report time to exactly 48 h before receipt (`ClockCorrection.MAX_AGE_MS`). Such points would appear as a sample about 48 h back, so the history endpoint excludes every point with `received_at - measured_at >= 48 h`. Their receipt still counts for offline periods, because those use `received_at`. A legitimately 48 h-old queued report (possible only with an interval above 10 min) is dropped with them.
- History points are bucketed in SQL: at most 1000 buckets per response (`bucketMs = ceil(range / 1000)`), worst value per bucket, time of the last sample in the bucket. Offline gaps are computed in SQL with `lag(received_at)`. No row cap can cut one end of the data; a safety limit sets `truncated: true` if it is ever reached.
- Webhook URL: https only, never returned by the API; literal loopback, private, link-local, unique-local, CGNAT and unspecified addresses are rejected on save, and the resolved addresses are checked again at send time. The send-time check cannot pin the address the HTTP client then connects to, so a rebinding window remains between the check and the connection.

## Dashboard (Angular micro-frontend)

Source: `src/main/web/projects/locker24-health`. The panel loads it from `/plugins/locker24-health` (changeset `-08` sets `baseUrl` and `baseUrlDev` `http://localhost:4224`).

Pages (under `/home/plugins/locker24-health`): `main` (fleet), `main/device/<id>?hours=24|168|720` (history), `settings`. Fleet filters and sort live in the URL (`configuration`, `status`, `model`, `q`, `sort`, `dir`), so every view can be linked or opened in a new tab.

Local build (macOS):

1. `cd server/src/main/web && pnpm install && pnpm ng build hmdm-ui-kit --configuration production` (once; `node_modules` and `dist` stay uncommitted).
2. `cd plugins/locker24-health/src/main/web && pnpm install && pnpm run build` (tests: `pnpm ng test --watch=false --browsers=ChromeHeadless`).
3. From the repo root, Maven in Docker with `-Dskip.web.build=true`, so the container only copies `dist` into the jar and never runs pnpm against the macOS `node_modules`.

A full Maven build on Linux (CI, droplet) builds `ui-library` first and then runs the plugin's own frontend build, like the other plugins.

Translations: only `en_US.json`. A panel set to another language shows the keys.
