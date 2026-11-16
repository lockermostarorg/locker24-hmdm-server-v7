# locker24-health

## Panel history: decisions

- Device clock of 1970 (or any unusable clock): the ingest clamps the report time to exactly 48 h before receipt (`ClockCorrection.MAX_AGE_MS`). Such points would appear as a sample about 48 h back, so the history endpoint excludes every point with `received_at - measured_at >= 48 h`. Their receipt still counts for offline periods, because those use `received_at`. A legitimately 48 h-old queued report (possible only with an interval above 10 min) is dropped with them.
- History points are bucketed in SQL: at most 1000 buckets per response (`bucketMs = ceil(range / 1000)`), worst value per bucket, time of the last sample in the bucket. Offline gaps are computed in SQL with `lag(received_at)`. No row cap can cut one end of the data; a safety limit sets `truncated: true` if it is ever reached.
- Webhook URL: https only, never returned by the API; literal loopback, private, link-local, unique-local, CGNAT and unspecified addresses are rejected on save, and the resolved addresses are checked again at send time. The send-time check cannot pin the address the HTTP client then connects to, so a rebinding window remains between the check and the connection.
