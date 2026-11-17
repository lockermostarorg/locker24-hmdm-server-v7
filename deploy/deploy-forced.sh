#!/usr/bin/env bash
# SSH forced command for the GitHub deploy key (see deploy/README.md).
# Also accepts "deployed", which prints the sha of the last successful deploy (nothing if unknown).
# Accepts exactly "deploy <40-hex sha> <nonce>" or "status <40-hex sha> <nonce>" in SSH_ORIGINAL_COMMAND,
# where nonce is <run id>-<attempt> of the workflow run.
# deploy starts deploy.sh detached from the SSH session (systemd-run), then follows its log and exits with
# deploy.sh's exit code; it attaches instead if that sha is already deploying, or if this nonce was seen.
# status re-attaches after a dropped connection. Only commits on origin/main are deployable.
# Exit codes: deploy.sh's own (255 is reported as 254), 1 refused/rejected, 3 status for an attempt this host
# does not know.
set -euo pipefail
export LC_ALL=C
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export PATH

LOG_FILE=/var/log/locker24-deploy.log
SRC_DIR=/opt/locker24/src/locker24-hmdm-server-v7
DEPLOY_SH=$SRC_DIR/deploy/deploy.sh
STATE_DIR=/run/locker24-deploy
POLL=2

if [ "${SSH_ORIGINAL_COMMAND-}" = deployed ]; then
    # the commit that is live: last line of the deploy history
    sed -n 's/.* sha=\([0-9a-f]\{40\}\) .*/\1/p' /opt/locker24/last-deploy 2>/dev/null | tail -n 1
    exit 0
fi

re='^(deploy|status) ([0-9a-f]{40}) ([0-9]{1,20}-[0-9]{1,5})$'
if ! [[ ${SSH_ORIGINAL_COMMAND-} =~ $re ]]; then
    echo "rejected: only 'deploy <40-hex sha> <run>-<attempt>' and 'status <40-hex sha> <run>-<attempt>' are allowed" >&2
    exit 1
fi
action=${BASH_REMATCH[1]}
sha=${BASH_REMATCH[2]}
nonce=${BASH_REMATCH[3]}
short=${sha:0:12}
unit=locker24-deploy-$short.service
key=$short-$nonce
rc_file=$STATE_DIR/$key.rc
off_file=$STATE_DIR/$key.offset
current_file=$STATE_DIR/$short.current

install -d -m 700 "$STATE_DIR"

active_units() {
    systemctl list-units --plain --no-legend --no-pager --state=active,activating,deactivating 'locker24-deploy-*.service' | awk '{print $1}'
}

refuse() {
    echo "refused: $*" >&2
    exit 1
}

follow() {
    local tp rc
    if [ ! -f "$rc_file" ]; then
        tail -c +$(( $(<"$off_file") + 1 )) -F "$LOG_FILE" </dev/null 2>/dev/null &
        tp=$!
        trap 'kill "$tp" 2>/dev/null || true' EXIT
        while [ ! -f "$rc_file" ]; do
            if ! systemctl is-active --quiet "$unit"; then
                sleep 2
                [ -f "$rc_file" ] || { echo "deploy unit $unit ended without reporting a result" >&2; exit 1; }
            fi
            sleep "$POLL"
        done
        sleep 1
        kill "$tp" 2>/dev/null || true
        wait "$tp" 2>/dev/null || true
    else
        tail -c +$(( $(<"$off_file") + 1 )) "$LOG_FILE" | awk 'NR > 1 && /Locker24 MDM deploy: ref=/ { exit } { print }'
    fi
    rc=$(<"$rc_file")
    echo "deploy of $sha finished with exit code $rc"
    exit "$rc"
}

launch() {
    local active cur_nonce cur_sha refs size=0 java java_home=
    exec 8>"$STATE_DIR/lock"
    flock -w 300 8 || refuse "could not get the deploy lock"

    if [ -f "$off_file" ]; then
        echo "attempt $nonce of $sha is already known, following it"
        exec 8>&-
        return
    fi

    active=$(active_units)
    if [ -n "$active" ]; then
        if [ "$active" = "$unit" ] && [ -f "$current_file" ] && read -r cur_nonce cur_sha < "$current_file" && [ "$cur_sha" = "$sha" ]; then
            ln -sf "$STATE_DIR/$short-$cur_nonce.offset" "$off_file"
            ln -sf "$STATE_DIR/$short-$cur_nonce.rc" "$rc_file"
            echo "deploy of $sha is already running (attempt $cur_nonce), attaching"
            exec 8>&-
            return
        fi
        refuse "another deploy is already running ($active)"
    fi

    if ! timeout 120 git -C "$SRC_DIR" fetch --quiet origin '+refs/heads/main:refs/remotes/origin/main' </dev/null; then
        refuse "could not fetch origin/main"
    fi
    if ! git -C "$SRC_DIR" merge-base --is-ancestor "$sha" refs/remotes/origin/main 2>/dev/null; then
        refuse "$sha is not on origin/main"
    fi
    # a tag or branch named like the sha would make deploy.sh resolve it to another commit
    if ! refs=$(timeout 60 git -C "$SRC_DIR" ls-remote origin "refs/tags/$sha" "refs/heads/$sha" </dev/null); then
        refuse "could not list the remote refs"
    fi
    [ -z "$refs" ] || refuse "a tag or branch named $sha exists on the remote"
    [ -x "$DEPLOY_SH" ] || refuse "$DEPLOY_SH is missing or not executable"

    if java=$(command -v java); then
        java=$(readlink -f "$java")
        java_home=$(dirname "$(dirname "$java")")
    fi
    find "$STATE_DIR" -maxdepth 1 \( -type f -o -type l \) -mtime +1 ! -name lock -delete
    [ -f "$LOG_FILE" ] && size=$(stat -c %s "$LOG_FILE")
    echo "$size" > "$off_file"
    echo "$nonce $sha" > "$current_file"
    # deploy.sh checks out other commits, possibly rewriting itself; run a private copy
    # (/run is mounted noexec on Ubuntu, so the copy is run through bash)
    install -m 600 "$DEPLOY_SH" "$STATE_DIR/deploy-$key.sh"
    # shellcheck disable=SC2016
    if ! systemd-run --quiet --unit="${unit%.service}" --collect \
        --property=TimeoutStartSec=0 --property=TimeoutStopSec=900 --property=RuntimeMaxSec=90min \
        --property=StandardError=append:"$LOG_FILE" \
        --setenv=HOME=/root --setenv=PATH="$PATH" ${java_home:+--setenv=JAVA_HOME="$java_home"} \
        /bin/bash -c 'trap : HUP INT TERM
/bin/bash "$1" "$2"; rc=$?
if [ "$rc" -eq 0 ]; then
    head=$(git -C "$4" rev-parse HEAD 2>&1) || true
    if [ "$head" != "$2" ]; then
        echo "!!!!!! WRONG COMMIT: the checkout is at $head, not the requested $2" >&2
        rc=1
    fi
fi
[ "$rc" -eq 255 ] && rc=254
echo "$rc" > "$3.tmp"; mv "$3.tmp" "$3"; exit "$rc"' \
        locker24-deploy "$STATE_DIR/deploy-$key.sh" "$sha" "$rc_file" "$SRC_DIR" 8>&-; then
        rm -f "$off_file" "$current_file"
        refuse "could not start the deploy unit"
    fi
    echo "started deploy of $sha as $unit"
    exec 8>&-
}

case $action in
    deploy)
        launch
        follow
        ;;
    status)
        # wait for a launch in progress, so state written just before systemd-run is not read too early
        exec 8>"$STATE_DIR/lock"
        flock -w 300 8 || refuse "could not get the deploy lock"
        exec 8>&-
        if [ ! -f "$off_file" ]; then
            echo "no attempt $nonce of $sha is known on this host" >&2
            exit 3
        fi
        follow
        ;;
esac
