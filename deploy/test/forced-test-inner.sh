#!/usr/bin/env bash
# Runs inside the container. Stubs systemd-run, systemctl and deploy.sh.
# shellcheck disable=SC2010,SC2015,SC2016,SC2086
set -uo pipefail

mkdir -p /opt/locker24/src/locker24-hmdm-server-v7/deploy /run/stub-units /var/log
install -m 755 /work/deploy-forced.sh /opt/locker24/deploy-forced.sh
FORCED=/opt/locker24/deploy-forced.sh
: > /var/log/locker24-deploy.log

cat > /usr/bin/systemd-run <<'STUB'
#!/bin/bash
unit=
while [ $# -gt 0 ]; do
    case $1 in
        --unit=*) unit=${1#--unit=} ;;
        --*) ;;
        *) break ;;
    esac
    shift
done
touch "/run/stub-units/$unit.service"
echo "$unit $*" >> /run/stub-launches
setsid nohup bash -c 'u=$1; shift; "$@"; rm -f "/run/stub-units/$u.service"' x "$unit" "$@" >/dev/null 2>&1 &
STUB
cat > /usr/bin/systemctl <<'STUB'
#!/bin/bash
case $1 in
    is-active) [ -f "/run/stub-units/$3" ] ;;
    list-units) ls /run/stub-units ;;
esac
STUB
cat > /opt/locker24/src/locker24-hmdm-server-v7/deploy/deploy.sh <<'STUB'
#!/bin/bash
LOG=/var/log/locker24-deploy.log
echo "$(date +%FT%T) Locker24 MDM deploy: ref=$1 dry-run=0" >> $LOG
for i in 1 2 3; do echo "step $i for $1" >> $LOG; sleep "$(cat /etc/stub-sleep)"; done
echo "deployed ref=$1" >> $LOG
exit "$(cat /etc/stub-rc)"
STUB
chmod +x /usr/bin/systemd-run /usr/bin/systemctl /opt/locker24/src/locker24-hmdm-server-v7/deploy/deploy.sh

A=$(printf 'a%.0s' {1..40}); B=$(printf 'b%.0s' {1..40}); C=$(printf 'c%.0s' {1..40})
fail=0
ok()  { echo "PASS $1"; }
bad() { echo "FAIL $1"; fail=1; }
check() { # name expected-rc actual-rc
    if [ "$2" = "$3" ]; then ok "$1"; else bad "$1 (expected rc $2, got $3)"; fi
}
run() { SSH_ORIGINAL_COMMAND=$1 "$FORCED"; }

echo 0 > /etc/stub-sleep; echo 0 > /etc/stub-rc
out=$(run "deploy $A" 2>&1); rc=$?
check "valid deploy exits 0" 0 $rc
grep -q "step 3 for $A" <<<"$out" && ok "deploy output followed" || bad "deploy output missing: $out"
grep -q "deploy of $A finished with exit code 0" <<<"$out" && ok "result line" || bad "result line"
[ "$(wc -l < /run/stub-launches)" = 1 ] && ok "exactly one launch" || bad "launch count"

echo 7 > /etc/stub-rc
run "deploy $B" >/dev/null 2>&1; check "deploy.sh exit code 7 propagated" 7 $?
echo 0 > /etc/stub-rc

before=$(wc -l < /run/stub-launches)
rm -f /tmp/pwned
nl=$'deploy '"$A"$'\n'"touch /tmp/pwned"
for cmd in \
    "deploy abc; rm -rf /" "deploy \$(id)" "deploy $A; touch /tmp/pwned" "deploy $A touch /tmp/pwned" \
    "deploy $A $B" "deploy" "" "status" "deploy ${A:0:39}" "deploy ${A}a" "deploy ${A^^}" "deploy ${A:0:39}g" \
    "restart $A" "deploy  $A" " deploy $A" "deploy $A " "deploy ../../$A" "$nl" "deploy $A\`id\`" "bash -c id"; do
    run "$cmd" >/dev/null 2>&1; rc=$?
    check "rejected [${cmd//$'\n'/\\n}]" 1 $rc
done
run "" >/dev/null 2>&1 || true
unset SSH_ORIGINAL_COMMAND; "$FORCED" >/dev/null 2>&1; check "no SSH_ORIGINAL_COMMAND (interactive login) rejected" 1 $?
[ ! -e /tmp/pwned ] && ok "no injected command ran" || bad "injection executed"
[ "$(wc -l < /run/stub-launches)" = "$before" ] && ok "rejections launched nothing" || bad "rejection launched a deploy"

echo 6 > /etc/stub-sleep
run "deploy $A" > /tmp/long.out 2>&1 &
sleep 3
run "deploy $B" >/tmp/second.out 2>&1; check "second deploy refused while one runs" 1 $?
grep -q "already running" /tmp/second.out && ok "refusal message" || bad "refusal message"
run "deploy $A" >/dev/null 2>&1; check "same sha refused while running" 1 $?
wait
check_long=$(grep -c "deploy of $A finished" /tmp/long.out); [ "$check_long" = 1 ] && ok "first deploy unaffected by refusals" || bad "first deploy output"

echo 4 > /etc/stub-sleep; echo 5 > /etc/stub-rc
SSH_ORIGINAL_COMMAND="deploy $C" "$FORCED" > /tmp/drop.out 2>&1 &
pid=$!
sleep 3
kill -9 "$pid"; pkill -9 -f "tail -c" || true
wait "$pid" 2>/dev/null
ls /run/stub-units | grep -q "${C:0:12}" && ok "deploy survives the SSH client dying" || bad "unit gone after drop"
run "status $C" > /tmp/status.out 2>&1; check "status reports the deploy's exit code" 5 $?
grep -q "step 1 for $C" /tmp/status.out && grep -q "step 3 for $C" /tmp/status.out && ok "status shows the whole log segment" || bad "status log: $(cat /tmp/status.out)"
run "status $C" > /tmp/status2.out 2>&1; check "status of a finished deploy" 5 $?
grep -q "$B" /tmp/status2.out && bad "status leaked another deploy's lines" || ok "status shows only this deploy"
run "status ${B}" >/dev/null 2>&1; check "status of an older finished deploy still works" 7 $?
run "status $(printf 'd%.0s' {1..40})" >/dev/null 2>&1; check "status of an unknown sha" 3 $?

rm -f /run/locker24-deploy/${C:0:12}.rc; rm -f /run/stub-units/*
run "status $C" >/dev/null 2>&1; check "unit gone, no result" 1 $?

[ "$fail" = 0 ] && echo "ALL PASSED" || { echo "FAILURES"; exit 1; }
