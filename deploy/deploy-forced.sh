#!/usr/bin/env bash
# SSH forced command for the GitHub deploy key (see deploy/README.md).
# Accepts exactly "deploy <40-hex sha>" or "status <40-hex sha>" in SSH_ORIGINAL_COMMAND.
# deploy starts deploy.sh detached from the SSH session (systemd-run), then follows its log
# and exits with deploy.sh's exit code. status re-attaches after a dropped connection.
# Exit codes: deploy.sh's own, 1 refused/rejected, 3 status for a deploy this host does not know.
set -euo pipefail
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export PATH

LOG_FILE=/var/log/locker24-deploy.log
DEPLOY_SH=/opt/locker24/src/locker24-hmdm-server-v7/deploy/deploy.sh
STATE_DIR=/run/locker24-deploy
POLL=2

re='^(deploy|status) ([0-9a-f]{40})$'
if ! [[ ${SSH_ORIGINAL_COMMAND-} =~ $re ]]; then
    echo "rejected: only 'deploy <40-hex sha>' and 'status <40-hex sha>' are allowed" >&2
    exit 1
fi
action=${BASH_REMATCH[1]}
sha=${BASH_REMATCH[2]}
short=${sha:0:12}
unit=locker24-deploy-$short.service
rc_file=$STATE_DIR/$short.rc
off_file=$STATE_DIR/$short.offset

any_running() {
    [ -n "$(systemctl list-units --plain --no-legend --no-pager --state=active,activating,deactivating 'locker24-deploy-*.service')" ]
}

follow() {
    local tp
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
    local rc
    rc=$(<"$rc_file")
    echo "deploy of $sha finished with exit code $rc"
    exit "$rc"
}

launch() {
    if any_running; then
        echo "refused: a deploy is already running (systemctl list-units 'locker24-deploy-*')" >&2
        exit 1
    fi
    [ -x "$DEPLOY_SH" ] || { echo "$DEPLOY_SH is missing or not executable" >&2; exit 1; }
    install -d -m 700 "$STATE_DIR"
    rm -f "$rc_file"
    local size=0
    [ -f "$LOG_FILE" ] && size=$(stat -c %s "$LOG_FILE")
    echo "$size" > "$off_file"
    # deploy.sh checks out other commits, possibly rewriting itself; run a private copy
    install -m 700 "$DEPLOY_SH" "$STATE_DIR/deploy-$short.sh"
    # shellcheck disable=SC2016
    systemd-run --quiet --unit="${unit%.service}" --collect \
        --property=TimeoutStartSec=0 --property=TimeoutStopSec=900 \
        --setenv=HOME=/root --setenv=PATH="$PATH" \
        /bin/bash -c 'trap : HUP INT TERM; "$1" "$2"; rc=$?; echo "$rc" > "$3.tmp"; mv "$3.tmp" "$3"; exit "$rc"' \
        locker24-deploy "$STATE_DIR/deploy-$short.sh" "$sha" "$rc_file"
    echo "started deploy of $sha as $unit"
}

case $action in
    deploy)
        launch
        follow
        ;;
    status)
        if [ ! -f "$off_file" ]; then
            echo "no deploy of $sha is known on this host" >&2
            exit 3
        fi
        follow
        ;;
esac
