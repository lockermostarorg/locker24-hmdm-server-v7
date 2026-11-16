#!/usr/bin/env bash
# Runs inside the container (with /run mounted noexec). Stubs systemd-run, systemctl, git and deploy.sh.
# shellcheck disable=SC2010,SC2015,SC2016,SC2086
set -uo pipefail

mkdir -p /opt/locker24/src/locker24-hmdm-server-v7/deploy /run/stub-units /var/log
install -m 755 /work/deploy-forced.sh /opt/locker24/deploy-forced.sh
FORCED=/opt/locker24/deploy-forced.sh
: > /var/log/locker24-deploy.log

cat > /usr/bin/systemd-run <<'STUB'
#!/bin/bash
echo "$*" >> /run/stub-allargs
unit=
while [ $# -gt 0 ]; do
    case $1 in
        --unit=*) unit=${1#--unit=} ;;
        --*) ;;
        *) break ;;
    esac
    shift
done
[ ! -f /etc/stub-systemd-run-fail ] || exit 1
touch "/run/stub-units/$unit.service"
echo "$unit" >> /run/stub-launches
setsid nohup bash -c 'u=$1; shift; "$@" 2>>/var/log/locker24-deploy.log; rm -f "/run/stub-units/$u.service"' x "$unit" "$@" >/dev/null 2>&1 &
STUB
cat > /usr/bin/systemctl <<'STUB'
#!/bin/bash
case $1 in
    is-active) [ -f "/run/stub-units/$3" ] ;;
    list-units) ls /run/stub-units | sed 's/$/ loaded active running x/' ;;
esac
STUB
cat > /usr/bin/git <<'STUB'
#!/bin/bash
# commits on main are listed in /etc/stub-main; /etc/stub-HEAD is what rev-parse HEAD reports
while [ "$1" = -C ]; do shift 2; done
case $1 in
    fetch) [ ! -f /etc/stub-fetch-fail ] ;;
    merge-base) grep -qx "$3" /etc/stub-main ;;
    ls-remote) [ ! -f /etc/stub-ambiguous ] || echo "deadbeef refs/tags/x" ;;
    rev-parse) cat /etc/stub-HEAD ;;
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
chmod +x /usr/bin/git /usr/bin/systemd-run /usr/bin/systemctl /opt/locker24/src/locker24-hmdm-server-v7/deploy/deploy.sh
mkdir -p /usr/lib/jvm/fake/bin; printf '#!/bin/sh\n' > /usr/lib/jvm/fake/bin/java; chmod +x /usr/lib/jvm/fake/bin/java
ln -s /usr/lib/jvm/fake/bin/java /usr/bin/java

A=$(printf 'a%.0s' {1..40}); B=$(printf 'b%.0s' {1..40}); C=$(printf 'c%.0s' {1..40}); D=$(printf 'd%.0s' {1..40}); E=$(printf 'e%.0s' {1..40})
printf '%s\n' "$A" "$B" "$C" > /etc/stub-main
echo "$A" > /etc/stub-HEAD
fail=0
ok()  { echo "PASS $1"; }
bad() { echo "FAIL $1"; fail=1; }
check() { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1 (expected rc $2, got $3)"; fi; }
run() { SSH_ORIGINAL_COMMAND=$1 "$FORCED"; }
launches() { wc -l < /run/stub-launches 2>/dev/null || echo 0; }

grep -E ' /run ' /proc/mounts | grep -q noexec && ok "/run is mounted noexec" || bad "/run is not noexec (test setup)"

echo 0 > /etc/stub-sleep; echo 0 > /etc/stub-rc
out=$(run "deploy $A 1-1" 2>&1); rc=$?
check "valid deploy exits 0 (deploy.sh run through bash from noexec /run)" 0 $rc
grep -q "step 3 for $A" <<<"$out" && ok "deploy output followed" || bad "deploy output missing: $out"
grep -q "deploy of $A finished with exit code 0" <<<"$out" && ok "result line" || bad "result line"
[ "$(launches)" = 1 ] && ok "exactly one launch" || bad "launch count"
grep -q 'JAVA_HOME=/usr/lib/jvm/fake' /run/stub-allargs && ok "JAVA_HOME resolved from java and passed" || bad "JAVA_HOME not passed"
grep -q 'RuntimeMaxSec=45min' /run/stub-allargs && ok "RuntimeMaxSec set" || bad "RuntimeMaxSec"
grep -q 'StandardError=append:/var/log/locker24-deploy.log' /run/stub-allargs && ok "unit stderr goes to the deploy log" || bad "StandardError"

echo 7 > /etc/stub-rc
run "deploy $B 2-1" >/dev/null 2>&1; check "deploy.sh exit code 7 propagated" 7 $?
echo 255 > /etc/stub-rc
run "deploy $A 3-1" >/dev/null 2>&1; check "deploy.sh exit 255 is reported as 254" 254 $?
echo 0 > /etc/stub-rc

before=$(launches); rm -f /tmp/pwned
nl=$'deploy '"$A"$'\n'"touch /tmp/pwned"
for cmd in \
    "deploy abc; rm -rf /" "deploy \$(id) 1-1" "deploy $A 1-1; touch /tmp/pwned" "deploy $A 1-1 touch /tmp/pwned" \
    "deploy $A" "deploy $A 11" "deploy $A 1-" "deploy $A -1" "deploy $A 1-123456" "deploy $A 123456789012345678901-1" "deploy $A 1-1-1" "deploy $A a-1" \
    "deploy $A $B 1-1" "deploy" "" "status" "deploy ${A:0:39} 1-1" "deploy ${A}a 1-1" "deploy ${A^^} 1-1" "deploy ${A:0:39}g 1-1" \
    "restart $A 1-1" "deploy  $A 1-1" " deploy $A 1-1" "deploy $A 1-1 " "deploy ../../$A 1-1" "$nl 1-1" "deploy $A 1-1\`id\`" "bash -c id" \
    "deploy $A 1-1"$'\n'; do
    run "$cmd" >/dev/null 2>&1; rc=$?
    check "rejected [${cmd//$'\n'/\\n}]" 1 $rc
done
unset SSH_ORIGINAL_COMMAND; "$FORCED" >/dev/null 2>&1; check "no SSH_ORIGINAL_COMMAND (interactive login) rejected" 1 $?
[ ! -e /tmp/pwned ] && ok "no injected command ran" || bad "injection executed"
[ "$(launches)" = "$before" ] && ok "rejections launched nothing" || bad "rejection launched a deploy"
LC_ALL=de_DE.UTF-8 LC_COLLATE=de_DE.UTF-8 run "deploy ${A^^} 1-1" >/dev/null 2>&1; check "uppercase rejected even with a foreign locale in the environment" 1 $?

before=$(launches)
run "deploy $D 4-1" >/tmp/off.out 2>&1; check "commit not on origin/main refused (other branch or unmerged)" 1 $?
grep -q "not on origin/main" /tmp/off.out && ok "off-main message" || bad "off-main message"
run "deploy $E 4-1" >/dev/null 2>&1; check "unknown commit refused" 1 $?
touch /etc/stub-fetch-fail
run "deploy $A 4-2" >/dev/null 2>&1; check "fetch failure refuses the deploy" 1 $?
rm /etc/stub-fetch-fail
touch /etc/stub-ambiguous
run "deploy $A 4-3" >/tmp/amb.out 2>&1; check "a tag or branch named like the sha is refused" 1 $?
grep -q "named $A" /tmp/amb.out && ok "ambiguity message" || bad "ambiguity message"
rm /etc/stub-ambiguous
touch /etc/stub-systemd-run-fail
run "deploy $A 4-4" >/dev/null 2>&1; check "systemd-run failure refused" 1 $?
rm /etc/stub-systemd-run-fail
run "status $A 4-4" >/dev/null 2>&1; check "failed start leaves no state behind" 3 $?
[ "$(launches)" = "$before" ] && ok "refusals launched nothing" || bad "refusal launched"

echo 6 > /etc/stub-sleep
run "deploy $A 5-1" > /tmp/long.out 2>&1 &
sleep 3
run "deploy $B 5-1" >/tmp/second.out 2>&1; check "different sha refused while one runs" 1 $?
grep -q "already running" /tmp/second.out && ok "refusal message" || bad "refusal message"
n=$(launches)
run "deploy $A 5-2" >/tmp/attach.out 2>&1; check "same sha, new attempt attaches and reports the deploy's result" 0 $?
grep -q "attaching" /tmp/attach.out && grep -q "step 3 for $A" /tmp/attach.out && ok "attached output" || bad "attach output: $(cat /tmp/attach.out)"
[ "$(launches)" = "$n" ] && ok "attach started no second deploy" || bad "duplicate deploy started"
wait
grep -q "deploy of $A finished with exit code 0" /tmp/long.out && ok "first deploy unaffected" || bad "first deploy output"

echo 6 > /etc/stub-sleep
run "deploy $A 6-1" > /tmp/long2.out 2>&1 &
sleep 3
run "deploy $A 6-2" > /tmp/attach2.out 2>&1 &
sleep 1
pkill -9 -f 'forced.sh' || true
sleep 1
run "status $A 6-2" >/tmp/st62.out 2>&1; check "status of an attached attempt reaches the same result" 0 $?
grep -q "step 3 for $A" /tmp/st62.out && ok "attached status streams the deploy" || bad "attached status output"
wait
c=$(grep -c "deploy: ref=$A" /var/log/locker24-deploy.log)
n=$(launches)
run "deploy $A 6-1" >/tmp/dup.out 2>&1; check "re-sent deploy with a known nonce follows instead of redeploying" 0 $?
[ "$(launches)" = "$n" ] && [ "$(grep -c "deploy: ref=$A" /var/log/locker24-deploy.log)" = "$c" ] && ok "no duplicate deploy for a known nonce" || bad "duplicate deploy"

echo 0 > /etc/stub-rc; echo 3 > /etc/stub-sleep; echo "$B" > /etc/stub-HEAD
run "deploy $B 7-1" >/dev/null 2>&1; check "deploy B succeeds" 0 $?
run "status $A 7-1" >/dev/null 2>&1; check "status never returns an earlier attempt's result (unknown nonce)" 3 $?
run "status $B 7-1" >/dev/null 2>&1; check "status for its own attempt" 0 $?

echo 4 > /etc/stub-sleep; echo 5 > /etc/stub-rc
SSH_ORIGINAL_COMMAND="deploy $C 8-1" "$FORCED" > /tmp/drop.out 2>&1 &
pid=$!
sleep 3
kill -9 "$pid"; pkill -9 -f "tail -c" || true
wait "$pid" 2>/dev/null
ls /run/stub-units | grep -q "${C:0:12}" && ok "deploy survives the SSH client dying" || bad "unit gone after drop"
run "status $C 8-1" > /tmp/status.out 2>&1; check "status reports the deploy's exit code" 5 $?
grep -q "step 1 for $C" /tmp/status.out && grep -q "step 3 for $C" /tmp/status.out && ok "status shows the whole log segment" || bad "status log: $(cat /tmp/status.out)"
run "status $C 8-1" > /tmp/status2.out 2>&1; check "status of a finished deploy" 5 $?
grep -q "$B" /tmp/status2.out && bad "status leaked another deploy's lines" || ok "status shows only this deploy"
run "status $B 2-1" >/dev/null 2>&1; check "status of an older finished attempt still works" 7 $?
run "status $D 1-1" >/dev/null 2>&1; check "status of an unknown sha" 3 $?

echo 0 > /etc/stub-rc; echo 0 > /etc/stub-sleep
echo "$D" > /etc/stub-HEAD
run "deploy $A 9-1" >/tmp/wrong.out 2>&1; check "deploy.sh success with the checkout at another commit fails loudly" 1 $?
grep -q "WRONG COMMIT" /var/log/locker24-deploy.log && ok "wrong-commit message in the deploy log" || bad "wrong-commit message"
echo "$B" > /etc/stub-HEAD
run "deploy $B 9-2" >/dev/null 2>&1; check "matching HEAD passes the post-check" 0 $?

rm -f /run/locker24-deploy/${C:0:12}-8-1.rc /run/stub-units/*
run "status $C 8-1" >/dev/null 2>&1; check "unit gone, no result" 1 $?

[ "$fail" = 0 ] && echo "ALL PASSED" || { echo "FAILURES"; exit 1; }
