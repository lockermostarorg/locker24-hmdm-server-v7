#!/usr/bin/env bash
# shellcheck disable=SC2015,SC2016,SC2086
set -uo pipefail
ORIGIN=/srv/origin.git
SRC=/opt/locker24/src/locker24-hmdm-server-v7
M2=$(git -C $ORIGIN rev-parse main); M1=$(git -C $ORIGIN rev-parse main~1); F=$(git -C $ORIGIN rev-parse feat)
fail=0
ok()  { echo "PASS $1"; }
bad() { echo "FAIL $1"; fail=1; }
check() { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1 (expected $2, got $3)"; fi; }
r() { ssh -T -i /root/client_key -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=/root/known \
    -o GlobalKnownHostsFile=/dev/null -o ForwardAgent=no -o ClearAllForwardings=yes -o ServerAliveInterval=30 root@localhost "$@"; }
headers() { grep -c "Locker24 MDM deploy: ref=$1" /var/log/locker24-deploy.log; }
head_of() { git -C $SRC rev-parse HEAD; }

findmnt -n -o OPTIONS /run | grep -q noexec && ok "/run is mounted noexec (as on the droplet)" || bad "/run not noexec"

r "deploy $M2 100-1" >/tmp/first.out 2>&1; rc=$?
check "deploy of the main tip exits 0 through real sshd + systemd-run (noexec /run)" 0 $rc
check "checkout is at the requested commit" "$M2" "$(head_of)"
grep -q "war from $M2" /var/lib/tomcat10/webapps/ROOT.war && ok "WAR built from the requested commit was installed" || bad "wrong WAR"
grep -q 'JAVA_HOME' <<<"$(systemctl show -p Environment "locker24-deploy-${M2:0:12}.service" 2>/dev/null)" || true

r "deploy $F 101-1" >/tmp/feat.out 2>&1; check "feature-branch commit refused" 1 $?
grep -q "not on origin/main" /tmp/feat.out && ok "refusal says why" || bad "refusal message: $(cat /tmp/feat.out)"
check "refused deploy changed nothing" "$M2" "$(head_of)"

# tag named like a main sha but pointing at the feature commit
git -C $ORIGIN tag "$M1" "$F"
r "deploy $M1 102-1" >/tmp/tag.out 2>&1; check "forced command refuses a tag named like the sha" 1 $?
check "nothing checked out after the refusal" "$M2" "$(head_of)"
echo 0 > /etc/stub-build-sleep 2>/dev/null || true
git -C $SRC fetch -q --tags
bash $SRC/deploy/deploy.sh "$M1" >/tmp/direct.out 2>&1; rc=$?
check "deploy.sh itself, tag named like the sha: exits 0" 0 $rc
check "deploy.sh deployed the commit asked for, not the tag's target" "$M1" "$(head_of)"
git -C $ORIGIN tag -d "$M1" >/dev/null; git -C $SRC tag -d "$M1" >/dev/null
git -C $ORIGIN branch "$M1" "$F"
git -C $SRC fetch -q --prune
bash $SRC/deploy/deploy.sh "$M1" >/tmp/direct2.out 2>&1
check "deploy.sh, branch named like the sha: commit asked for" "$M1" "$(head_of)"
git -C $ORIGIN branch -D "$M1" >/dev/null

# stale web builds from a failed earlier build must be gone before mvn runs
for d in server/src/main/web/dist plugins/audit/src/main/web/dist plugins/locker24-health/src/main/web/dist; do
    mkdir -p "$SRC/$d"; echo stale > "$SRC/$d/stale.js"
done
rm -f /tmp/mvn-saw-dist
bash $SRC/deploy/deploy.sh "$M2" >/tmp/dist.out 2>&1; check "deploy.sh with stale dist folders present: exits 0" 0 $?
[ -f /tmp/mvn-saw-dist ] && ok "mvn stub ran" || bad "mvn stub did not run"
check "no web dist folder existed when mvn started" 0 "$(wc -l < /tmp/mvn-saw-dist)"
check "no web dist folder left in the checkout" 0 "$(find $SRC/server/src/main/web $SRC/plugins -maxdepth 5 -name dist -type d | wc -l)"
# --skip-build must not touch anything built
mkdir -p "$SRC/server/src/main/web/dist"
bash $SRC/deploy/deploy.sh --skip-build "$M2" >/dev/null 2>&1
[ -d "$SRC/server/src/main/web/dist" ] && ok "--skip-build leaves dist alone" || bad "--skip-build removed dist"
rm -rf "$SRC/server/src/main/web/dist"

# (a) false success: status for an attempt this host never saw must not report an earlier result
r "status $M2 999-1" >/dev/null 2>&1; check "status of an unseen attempt is unknown (3), not an old success" 3 $?

# (b) duplicate deploy: the client drops while the forced command is still validating
printf '#!/bin/bash\n[ "$3" = fetch ] && [ ! -f /tmp/fast ] && sleep 15\nexec /usr/bin/git "$@"\n' > /usr/local/bin/git; chmod +x /usr/local/bin/git
n0=$(headers "$M2")
ssh -T -i /root/client_key -o BatchMode=yes -o UserKnownHostsFile=/root/known root@localhost "deploy $M2 200-1" >/dev/null 2>&1 &
p=$!; sleep 3; kill -9 $p
touch /tmp/fast
r "deploy $M2 200-1" >/tmp/d200.out 2>&1; check "deploy resent with the same nonce after a drop: ok" 0 $?
sleep 2
check "exactly one deploy ran for the attempt" "$((n0 + 1))" "$(headers "$M2")"
rm -f /tmp/fast; rm -f /usr/local/bin/git

# drop mid-deploy, attach from a second attempt, other sha refused, status
echo 6 > /etc/stub-build-sleep
n0=$(headers "$M2")
ssh -T -i /root/client_key -o BatchMode=yes -o UserKnownHostsFile=/root/known root@localhost "deploy $M2 300-1" >/dev/null 2>&1 &
p=$!; sleep 4; kill -9 $p
systemctl is-active --quiet "locker24-deploy-${M2:0:12}.service" && ok "deploy keeps running after the SSH client dies" || bad "unit not active"
systemctl show -p RuntimeMaxUSec "locker24-deploy-${M2:0:12}.service" | grep -q '45min' && ok "RuntimeMaxSec=45min on the unit" || bad "RuntimeMaxSec: $(systemctl show -p RuntimeMaxUSec "locker24-deploy-${M2:0:12}.service")"
r "deploy $M1 300-1" >/tmp/other.out 2>&1; check "a different sha is refused while one deploys" 1 $?
r "deploy $M2 300-2" >/tmp/attach.out 2>&1; check "same sha, new attempt attaches and reports success" 0 $?
grep -q attaching /tmp/attach.out && ok "attached" || bad "attach message"
r "status $M2 300-1" >/tmp/st.out 2>&1; check "status of the dropped attempt returns the result" 0 $?
r "status $M2 300-2" >/dev/null 2>&1; check "status of the attached attempt returns the result" 0 $?
check "one deploy for three attempts" "$((n0 + 1))" "$(headers "$M2")"
echo 0 > /etc/stub-build-sleep

# systemctl stop: SIGTERM reaches deploy.sh (rollback path) and an rc is still recorded
echo 15 > /etc/stub-build-sleep
r "deploy $M2 400-1" >/tmp/stop.out 2>&1 &
sleep 5; systemctl stop "locker24-deploy-${M2:0:12}.service"; wait
r "status $M2 400-1" >/dev/null 2>&1; rc=$?
[ "$rc" != 0 ] && ok "a stopped deploy does not report success (rc $rc)" || bad "stopped deploy reported success"
echo 0 > /etc/stub-build-sleep

# the live commit is reported from the deploy history
last=$(r deployed 2>/dev/null); check "deployed reports the last deployed sha" "$(head_of)" "$last"

# status racing a slow launch (real systemd-run delayed by 5 s)
printf '#!/bin/bash\nsleep 5\nexec /usr/bin/systemd-run "$@"\n' > /usr/local/bin/systemd-run; chmod +x /usr/local/bin/systemd-run
ssh -T -i /root/client_key -o BatchMode=yes -o UserKnownHostsFile=/root/known root@localhost "deploy $M2 500-1" >/dev/null 2>&1 &
sleep 3
r "status $M2 500-1" >/tmp/race.out 2>&1; check "status during a slow launch waits and reports the real result" 0 $?
wait
rm -f /usr/local/bin/systemd-run

ssh -tt -i /root/client_key -o BatchMode=yes -o UserKnownHostsFile=/root/known root@localhost "status $M2 999-2" </dev/null >/tmp/pty.out 2>&1
grep -q "PTY allocation request failed" /tmp/pty.out && ok "pty allocation refused" || bad "pty: $(cat /tmp/pty.out)"
ssh -N -L 2222:127.0.0.1:22 -i /root/client_key -o BatchMode=yes -o UserKnownHostsFile=/root/known root@localhost >/tmp/fwd.out 2>&1 &
fp=$!; sleep 2
{ exec 3<>/dev/tcp/127.0.0.1/2222 && echo x >&3; } 2>/dev/null; sleep 1; kill $fp 2>/dev/null
grep -q "administratively prohibited" /tmp/fwd.out && ok "port forwarding refused" || bad "forwarding: $(cat /tmp/fwd.out)"
echo "ls /" | sftp -b - -i /root/client_key -o BatchMode=yes -o UserKnownHostsFile=/root/known root@localhost >/tmp/sftp.out 2>&1; rc=$?
[ "$rc" != 0 ] && ok "sftp refused" || bad "sftp worked"

[ "$fail" = 0 ] && echo "ALL PASSED" || { echo FAILURES; exit 1; }
