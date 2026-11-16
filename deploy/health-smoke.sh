#!/usr/bin/env bash
# Smoke test of the Locker24 health plugin against a running server.
#
#   deploy/health-smoke.sh [--secrets-file FILE] [--device NUMBER] [--delete | --keep] <base_url>
#
# Needs a device (default SMOKE-01) that exists in the panel, in a configuration
# with no tablets. Secrets are read from a silent prompt, or from --secrets-file
# (three lines: hash.secret, panel login, panel password; the file must be
# readable by its owner only). They are never taken from arguments or the
# environment and are never printed.
set -euo pipefail
umask 077

DEVICE="SMOKE-01"
SECRETS_FILE=""
CLEANUP="ask"
BASE=""

usage() {
  echo "usage: $0 [--secrets-file FILE] [--device NUMBER] [--delete | --keep] <base_url>" >&2
  exit 2
}

while [ $# -gt 0 ]; do
  case "$1" in
    --secrets-file) [ $# -ge 2 ] || usage; SECRETS_FILE="$2"; shift 2 ;;
    --device) [ $# -ge 2 ] || usage; DEVICE="$2"; shift 2 ;;
    --delete) CLEANUP="delete"; shift ;;
    --keep) CLEANUP="keep"; shift ;;
    -h|--help) usage ;;
    -*) usage ;;
    *) [ -z "$BASE" ] || usage; BASE="$1"; shift ;;
  esac
done
[ -n "$BASE" ] || usage
BASE="${BASE%/}"

case "$BASE" in
  https://*|http://localhost|http://localhost:*|http://127.0.0.1|http://127.0.0.1:*) ;;
  *) echo "refusing a plain http:// URL (credentials would travel unencrypted): use https://" >&2; exit 2 ;;
esac

for tool in curl jq openssl; do
  command -v "$tool" >/dev/null 2>&1 || { echo "missing tool: $tool" >&2; exit 2; }
done

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

HASH_SECRET=""
PANEL_LOGIN=""
PANEL_PASSWORD=""

if [ -n "$SECRETS_FILE" ]; then
  [ -f "$SECRETS_FILE" ] || { echo "secrets file not found" >&2; exit 2; }
  perms="$(stat -c '%a %u' "$SECRETS_FILE" 2>/dev/null || stat -f '%Lp %u' "$SECRETS_FILE")"
  mode="${perms%% *}"
  owner="${perms##* }"
  if [ "$owner" != "$(id -u)" ] || [ $(( 8#$mode & 8#077 )) -ne 0 ]; then
    echo "secrets file must be owned by you and not readable by group or others (chmod 600)" >&2
    exit 2
  fi
  { IFS= read -r HASH_SECRET; IFS= read -r PANEL_LOGIN; IFS= read -r PANEL_PASSWORD; } < "$SECRETS_FILE" || true
else
  [ -r /dev/tty ] || { echo "no terminal for the prompts: use --secrets-file" >&2; exit 2; }
  printf 'hash.secret (hidden): ' >/dev/tty
  IFS= read -rs HASH_SECRET </dev/tty; echo >/dev/tty
  printf 'Panel login: ' >/dev/tty
  IFS= read -r PANEL_LOGIN </dev/tty
  printf 'Panel password (hidden): ' >/dev/tty
  IFS= read -rs PANEL_PASSWORD </dev/tty; echo >/dev/tty
fi
if [ -z "$HASH_SECRET" ] || [ -z "$PANEL_LOGIN" ] || [ -z "$PANEL_PASSWORD" ]; then
  echo "hash.secret, panel login and panel password are all required" >&2
  exit 2
fi

hex_of() { openssl dgst "-$1" | awk '{print $NF}'; }
sign() { printf '%s' "$HASH_SECRET$DEVICE" | hex_of sha1 | tr 'a-f' 'A-F'; }
panel_pass_hash() { printf '%s' "$PANEL_PASSWORD" | hex_of md5 | tr 'a-f' 'A-F'; }

PASSES=0
FAILS=0
check() {
  local ok="$1" label="$2" detail="${3:-}"
  if [ "$ok" = "1" ]; then
    PASSES=$((PASSES + 1)); echo "PASS  $label"
  else
    FAILS=$((FAILS + 1)); echo "FAIL  $label${detail:+ ($detail)}"
  fi
}

# call <out-file> <curl-config-lines...>; prints the HTTP code, body in out-file.
call() {
  local out="$1"; shift
  local cfg="$WORK/curl.cfg"
  : > "$cfg"
  local line
  for line in "$@"; do printf '%s\n' "$line" >> "$cfg"; done
  curl -sS --max-time 30 -K "$cfg" -o "$out" -w '%{http_code}' || echo 000
  rm -f "$cfg"
}

now_ms() { echo $(( $(date +%s) * 1000 + RANDOM % 1000 )); }

report_json() {
  jq -cn --argjson ts "$1" --argjson batt "$2" '{
    v: 1, ts: $ts, trigger: "smoke",
    power: {battery_pct: $batt, charging: true, charge_source: "ac", temperature_c: 30.0},
    system: {manufacturer: "smoke", model: "SMOKE-TEST", free_storage_mb: 20000},
    network: {rssi_dbm: -60},
    kiosk: {required: false, running: false}
  }'
}

post_batch() {
  local report="$1" signature="$2" out="$3"
  jq -cn --argjson sent "$(now_ms)" --argjson r "$report" '{sent_at: $sent, reports: [$r]}' > "$WORK/body.json"
  call "$out" \
    "url = \"$BASE/rest/public/plugin-locker24-health/report/$DEVICE\"" \
    'request = "POST"' \
    'header = "Content-Type: application/json"' \
    "header = \"X-Request-Signature: $signature\"" \
    "data-binary = \"@$WORK/body.json\""
}

echo "Locker24 health smoke test: $BASE, device $DEVICE"

jq -cn --arg login "$PANEL_LOGIN" --arg password "$(panel_pass_hash)" '{login: $login, password: $password}' > "$WORK/login.json"
code="$(call "$WORK/login.out" \
  "url = \"$BASE/rest/public/jwt/login\"" \
  'request = "POST"' \
  'header = "Content-Type: application/json"' \
  "data-binary = \"@$WORK/login.json\"")"
rm -f "$WORK/login.json"
if [ "$code" != "200" ]; then
  echo "panel login failed (HTTP $code)" >&2
  exit 1
fi
TOKEN="$(jq -r '.id_token // empty' "$WORK/login.out")"
rm -f "$WORK/login.out"
[ -n "$TOKEN" ] || { echo "panel login returned no token" >&2; exit 1; }

private_get() {
  call "$2" \
    "url = \"$BASE/rest/private/plugin-locker24-health/$1\"" \
    "header = \"Authorization: Bearer $TOKEN\""
}

fleet_row() {
  local code
  code="$(private_get devices "$WORK/fleet.out")"
  [ "$code" = "200" ] || { echo "fleet request failed (HTTP $code)" >&2; return 1; }
  jq -c --arg n "$DEVICE" '[.data.devices[]? | select(.number == $n)] | first // empty' "$WORK/fleet.out"
}

ROW="$(fleet_row)" || exit 1
if [ -z "$ROW" ]; then
  echo "device $DEVICE not found in the fleet list: create it in the panel first (own empty configuration)" >&2
  exit 1
fi
DEVICE_ID="$(jq -r '.deviceId' <<<"$ROW")"

history_points() {
  local code
  code="$(private_get "devices/$DEVICE_ID/history?hours=1" "$WORK/history.out")"
  [ "$code" = "200" ] || { echo "history request failed (HTTP $code)" >&2; return 1; }
  jq -c '.data.points // []' "$WORK/history.out"
}

BASELINE="$(history_points | jq 'length')"

NOW="$(now_ms)"
T2=$((NOW - 30000))
T1=$((NOW - 130000))
R_NEW="$(report_json "$T2" 55)"
R_OLD="$(report_json "$T1" 11)"
SIG="$(sign)"

code="$(post_batch "$R_NEW" "$SIG" "$WORK/p1.out")"
check "$([ "$code" = 200 ] && [ "$(jq -r '.status' "$WORK/p1.out")" = OK ] && [ "$(jq -r '.data.accepted' "$WORK/p1.out")" = 1 ] && echo 1 || echo 0)" \
  "ingest accepts a signed report" "HTTP $code, $(jq -r '.message // "no message"' "$WORK/p1.out" 2>/dev/null)"

code="$(post_batch "$R_NEW" "$SIG" "$WORK/p2.out")"
check "$([ "$code" = 200 ] && [ "$(jq -r '.status' "$WORK/p2.out")" = OK ] && [ "$(jq -r '.data.accepted' "$WORK/p2.out")" = 0 ] && echo 1 || echo 0)" \
  "resending the same report is not stored twice" "HTTP $code"

code="$(post_batch "$R_OLD" "$SIG" "$WORK/p3.out")"
check "$([ "$code" = 200 ] && [ "$(jq -r '.status' "$WORK/p3.out")" = OK ] && [ "$(jq -r '.data.accepted' "$WORK/p3.out")" = 1 ] && echo 1 || echo 0)" \
  "an older report is accepted into the history" "HTTP $code"

BAD_SIG="$(printf '%s' "wrong-$SIG" | hex_of sha1 | tr 'a-f' 'A-F')"
code="$(post_batch "$R_NEW" "$BAD_SIG" "$WORK/p4.out")"
check "$([ "$(jq -r '.message // empty' "$WORK/p4.out")" = error.signature ] && echo 1 || echo 0)" \
  "a wrong signature is rejected with error.signature" "HTTP $code"

ROW="$(fleet_row)" || exit 1
check "$([ "$(jq -r '.batteryPct' <<<"$ROW")" = 55 ] && echo 1 || echo 0)" \
  "an older report after a newer one does not replace the latest" "batteryPct $(jq -r '.batteryPct' <<<"$ROW")"

POINTS="$(history_points)" || exit 1
ADDED=$(( $(jq 'length' <<<"$POINTS") - BASELINE ))
LAST2="$(jq -c 'sort_by(.t) | .[-2:] | map(.batteryPct)' <<<"$POINTS")"
check "$([ "$ADDED" = 2 ] && [ "$LAST2" = "[11,55]" ] && echo 1 || echo 0)" \
  "history gained exactly 2 points (resend ignored)" "added $ADDED, last two $LAST2"

echo
echo "$PASSES passed, $FAILS failed"

echo
echo "$DEVICE has no tablet behind it, so about 10 minutes after the last report it turns"
echo "red offline and the panel raises a real pop-up. Remove it now unless you want to keep it."
do_delete=0
case "$CLEANUP" in
  delete) do_delete=1 ;;
  ask)
    if [ -r /dev/tty ]; then
      printf 'Delete %s from the panel now (its health rows go with it)? [y/N] ' "$DEVICE" >/dev/tty
      read -r answer </dev/tty || answer=""
      case "$answer" in y|Y|yes|YES) do_delete=1 ;; esac
    fi
    ;;
esac
if [ "$do_delete" = 1 ]; then
  code="$(call "$WORK/del.out" \
    "url = \"$BASE/rest/private/devices/$DEVICE_ID\"" \
    'request = "DELETE"' \
    "header = \"Authorization: Bearer $TOKEN\"")"
  if [ "$code" = "200" ] && [ "$(jq -r '.status // empty' "$WORK/del.out")" = OK ]; then
    echo "Deleted $DEVICE. Create it again before the next run."
  else
    echo "Could not delete $DEVICE (HTTP $code): delete it in the panel (Devices) before the pop-up appears." >&2
  fi
else
  echo "Kept $DEVICE: delete it in the panel (Devices) when done."
fi

[ "$FAILS" -eq 0 ]
