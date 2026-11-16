#!/usr/bin/env bash
# Build the Locker24 MDM server from a git ref on this droplet and install the WAR.
# Usage: deploy.sh [--dry-run] [--skip-build] <ref>
#   <ref>         tag (l24-v7.04.1.1), branch or commit SHA
#   --dry-run     print the steps, change nothing
#   --skip-build  install the WAR from the last build of the same commit (retry)
# Runs as root on the droplet, inside tmux or screen. See deploy/README.md.
set -euo pipefail

# Public HTTPS needs no credentials. Change this one line if the repository is moved or
# renamed; with an SSH URL the deploy key below is used.
REPO_URL=https://github.com/lockermostarorg/locker24-hmdm-server-v7.git

SRC_DIR=/opt/locker24/src/locker24-hmdm-server-v7
BACKUP_DIR=/opt/locker24/backups
DEPLOY_KEY=/root/.ssh/locker24_deploy_key
LEGACY_CHECKOUT=/root/locker24-hmdm-server-v7
TOMCAT_SERVICE=tomcat10
TOMCAT_WEBAPPS=/var/lib/tomcat10/webapps
INSTALLED_WAR=$TOMCAT_WEBAPPS/ROOT.war
INSTALLED_DIR=$TOMCAT_WEBAPPS/ROOT
CONTEXT_XML=/var/lib/tomcat10/conf/Catalina/localhost/ROOT.xml
DEFAULT_SIGNAL_FILE=/var/lib/tomcat10/work/hmdm_install_flag
BUILT_WAR=$SRC_DIR/server/target/launcher.war
BUILD_SHA_FILE=$SRC_DIR/server/target/l24-build-sha
DB_NAME=hmdm
DB_OWNER=hmdm
URL=https://mdm.locker24.app/
MQTT_PORT=31000
HEALTH_TIMEOUT=180
MIN_FREE_GB=3
KEEP_DAYS=14
KEEP_MIN=3
LOCK_FILE=/var/lock/locker24-deploy.lock
LOG_FILE=/var/log/locker24-deploy.log

DRY_RUN=0
SKIP_BUILD=0
REF=

usage() {
    echo "usage: $0 [--dry-run] [--skip-build] <ref>" >&2
    exit 2
}

for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=1 ;;
        --skip-build) SKIP_BUILD=1 ;;
        -h|--help) usage ;;
        -*) echo "unknown option: $arg" >&2; usage ;;
        *) [ -z "$REF" ] || usage; REF=$arg ;;
    esac
done
[ -n "$REF" ] || usage

step() { echo; echo "==> $*"; }
note() { echo "    $*" || true; }
loud() { { echo; echo "!!!!!! $*" >&2; } || true; }

# Runs a mutating command, or only prints it under --dry-run.
run() {
    if [ "$DRY_RUN" -eq 1 ]; then
        echo "    [dry-run] $*"
    else
        "$@"
    fi
}

if [ "$DRY_RUN" -eq 0 ]; then
    [ "$(id -u)" -eq 0 ] || { echo "run as root" >&2; exit 1; }
    exec 9>"$LOCK_FILE"
    flock -n 9 || { echo "another deploy is running" >&2; exit 1; }
    # tee ignores the signals, so it outlives them and the rollback can still write
    exec > >(trap '' INT HUP TERM; exec tee -a "$LOG_FILE") 2>&1
fi

SAFE_REF=${REF//[^A-Za-z0-9._-]/-}
STAMP=$(date +%Y%m%d-%H%M%S)
DB_DUMP=$BACKUP_DIR/pre-deploy-$SAFE_REF-$STAMP.sql.gz
WAR_BACKUP=$BACKUP_DIR/ROOT-before-$SAFE_REF-$STAMP.war
GIT_SSH="ssh -i $DEPLOY_KEY -o IdentitiesOnly=yes -o StrictHostKeyChecking=yes"

echo "$(date +%FT%T%z) Locker24 MDM deploy: ref=$REF dry-run=$DRY_RUN skip-build=$SKIP_BUILD"

is_ssh_url() {
    [[ "$REPO_URL" == git@* || "$REPO_URL" == ssh://* ]]
}

git_net() {
    if is_ssh_url; then
        GIT_SSH_COMMAND="$GIT_SSH" git "$@"
    else
        git "$@"
    fi
}

step "1/8 Source checkout at $SRC_DIR"
if [ ! -d "$SRC_DIR/.git" ]; then
    note "missing, cloning $REPO_URL"
    run mkdir -p "$(dirname "$SRC_DIR")"
    if [ "$DRY_RUN" -eq 0 ] && is_ssh_url && [ ! -f "$DEPLOY_KEY" ]; then
        echo "REPO_URL is an SSH URL but the deploy key $DEPLOY_KEY is missing; see deploy/README.md" >&2
        exit 1
    fi
    if is_ssh_url; then
        run env GIT_SSH_COMMAND="$GIT_SSH" git clone "$REPO_URL" "$SRC_DIR"
    else
        run git clone "$REPO_URL" "$SRC_DIR"
    fi
else
    note "already cloned"
fi

step "2/8 Fetch and check out $REF"
if [ ! -d "$SRC_DIR/.git" ]; then
    note "[dry-run] git fetch --tags --prune; refuse a dirty tree; git checkout --force --detach $REF"
else
    cd "$SRC_DIR"
    if [ -n "$(git status --porcelain)" ]; then
        echo "working tree in $SRC_DIR is dirty or has untracked files; refusing to deploy:" >&2
        git status --short >&2
        exit 1
    fi
    ORIGIN=$(git remote get-url origin)
    if [ "$ORIGIN" != "$REPO_URL" ]; then
        echo "origin of $SRC_DIR is $ORIGIN, expected $REPO_URL; fix it with:" >&2
        echo "    git -C $SRC_DIR remote set-url origin $REPO_URL" >&2
        exit 1
    fi
    if [ "$DRY_RUN" -eq 1 ]; then
        if ! is_ssh_url || [ -f "$DEPLOY_KEY" ]; then
            if [ -n "$(git_net ls-remote "$REPO_URL" "$REF" "refs/tags/$REF" "refs/heads/$REF")" ] || [[ "$REF" =~ ^[0-9a-f]{7,40}$ ]]; then
                note "ref $REF found on the remote"
            else
                echo "ref '$REF' not found on the remote" >&2
                exit 1
            fi
        else
            note "[dry-run] SSH URL without a deploy key, skipping git ls-remote"
        fi
        note "[dry-run] git fetch --tags --prune; git checkout --force --detach $REF"
    else
        git_net fetch --tags --prune
        if git rev-parse -q --verify "refs/tags/$REF^{commit}" >/dev/null; then
            TARGET=refs/tags/$REF
        elif git rev-parse -q --verify "refs/remotes/origin/$REF^{commit}" >/dev/null; then
            TARGET=refs/remotes/origin/$REF
        elif [[ "$REF" =~ ^[0-9a-f]{7,40}$ ]] && git rev-parse -q --verify "$REF^{commit}" >/dev/null; then
            TARGET=$REF
        else
            echo "ref '$REF' not found (not a tag, remote branch or commit SHA)" >&2
            exit 1
        fi
        git checkout --force --detach "$TARGET"
        note "checked out $(git rev-parse --short HEAD)"
    fi
fi

build_properties() {
    local f
    for f in server/build.properties plugins/audit/build.properties; do
        [ -f "$SRC_DIR/$f" ] && continue
        if [ -f "$LEGACY_CHECKOUT/$f" ]; then
            note "$f missing, copying it from $LEGACY_CHECKOUT"
            cp "$LEGACY_CHECKOUT/$f" "$SRC_DIR/$f"
            chmod 600 "$SRC_DIR/$f"
        else
            echo "$SRC_DIR/$f is missing and $LEGACY_CHECKOUT/$f does not exist; see deploy/README.md" >&2
            return 1
        fi
    done
}

step "3/8 Build (mvn -B -DskipTests clean install)"
if [ "$SKIP_BUILD" -eq 1 ]; then
    if [ "$DRY_RUN" -eq 1 ]; then
        note "[dry-run] --skip-build: require $BUILD_SHA_FILE to equal HEAD"
    else
        [ -f "$BUILD_SHA_FILE" ] && [ "$(cat "$BUILD_SHA_FILE")" = "$(git -C "$SRC_DIR" rev-parse HEAD)" ] || {
            echo "--skip-build refused: the last build was not made from $(git -C "$SRC_DIR" rev-parse --short HEAD)" >&2
            exit 1
        }
        note "--skip-build: last build matches HEAD"
    fi
elif [ "$DRY_RUN" -eq 1 ]; then
    note "[dry-run] copy server/build.properties and plugins/audit/build.properties from $LEGACY_CHECKOUT if missing"
    note "[dry-run] cd $SRC_DIR && mvn -B -DskipTests clean install  (about 17 min on 2 vCPU)"
    note "[dry-run] git rev-parse HEAD > $BUILD_SHA_FILE"
else
    build_properties
    cd "$SRC_DIR"
    BUILD_START=$(date +%s)
    rm -f "$BUILD_SHA_FILE"
    mvn -B -DskipTests clean install
    git rev-parse HEAD > "$BUILD_SHA_FILE"
    note "build took $(( $(date +%s) - BUILD_START )) s"
fi
if [ "$DRY_RUN" -eq 0 ]; then
    [ -s "$BUILT_WAR" ] || { echo "$BUILT_WAR not found" >&2; exit 1; }
fi

step "4/8 Free space check, database dump -> $DB_DUMP"
run mkdir -p "$BACKUP_DIR"
if [ "$DRY_RUN" -eq 1 ]; then
    note "[dry-run] require at least $MIN_FREE_GB GB free on $BACKUP_DIR"
    note "[dry-run] (cd /tmp && sudo -u postgres pg_dump --clean --if-exists $DB_NAME | gzip > $DB_DUMP)"
else
    FREE_GB=$(df -BG --output=avail "$BACKUP_DIR" | tail -1 | tr -dc '0-9')
    if [ "$FREE_GB" -lt "$MIN_FREE_GB" ]; then
        echo "only ${FREE_GB} GB free on $BACKUP_DIR, need $MIN_FREE_GB GB; prune $BACKUP_DIR first" >&2
        exit 1
    fi
    umask 077
    (cd /tmp && sudo -u postgres pg_dump --clean --if-exists "$DB_NAME" | gzip > "$DB_DUMP")
    gzip -t "$DB_DUMP"
    [ -s "$DB_DUMP" ] || { echo "empty database dump" >&2; exit 1; }
    note "$(du -h "$DB_DUMP" | cut -f1)"
fi

step "5/8 Save the current WAR -> $WAR_BACKUP"
if [ "$DRY_RUN" -eq 1 ]; then
    note "[dry-run] cp -p $INSTALLED_WAR $WAR_BACKUP"
    note "[dry-run] prune pre-deploy dumps and WAR backups older than $KEEP_DAYS days, keeping the newest $KEEP_MIN of each"
else
    cp -p "$INSTALLED_WAR" "$WAR_BACKUP"
    for pattern in 'pre-deploy-*.sql.gz' 'ROOT-before-*.war'; do
        # newest first; skip the newest KEEP_MIN, delete the rest only when old enough
        find "$BACKUP_DIR" -maxdepth 1 -name "$pattern" -printf '%T@ %p\n' | sort -rn | tail -n +$((KEEP_MIN + 1)) | cut -d' ' -f2- |
            while IFS= read -r old; do
                if [ -n "$(find "$old" -mtime +"$KEEP_DAYS")" ]; then
                    note "pruning $old"
                    rm -f "$old"
                fi
            done
    done
fi

signal_file() {
    local f
    f=$(sed -n 's/.*initialization.completion.signal.file" value="\([^"]*\)".*/\1/p' "$CONTEXT_XML" 2>/dev/null | head -1 || true)
    echo "${f:-$DEFAULT_SIGNAL_FILE}"
}

wait_healthy() {
    local deadline=$(( $(date +%s) + HEALTH_TIMEOUT )) code="" port_ok=0 sig_ok=0 sig
    sig=$(signal_file)
    while [ "$(date +%s)" -lt "$deadline" ]; do
        sig_ok=0
        if [ -f "$sig" ]; then
            if [ "$(cat "$sig")" = OK ]; then
                sig_ok=1
            else
                note "signal file reports a failure: $(cat "$sig")"
                return 1
            fi
        fi
        code=$(curl -s -o /dev/null -m 5 -w '%{http_code}' "$URL" || true)
        port_ok=0
        ss -ltn | grep -q ":$MQTT_PORT " && port_ok=1
        if [ "$sig_ok" -eq 1 ] && [ "$code" = 200 ] && [ "$port_ok" -eq 1 ]; then
            return 0
        fi
        sleep 5
    done
    note "last check: signal file OK=$sig_ok, HTTP ${code:-none}, port $MQTT_PORT listening=$port_ok"
    note "signal file $sig content: $(cat "$sig" 2>/dev/null || echo '<missing>')"
    return 1
}

install_war() {
    systemctl stop "$TOMCAT_SERVICE" || return 1
    rm -rf "$INSTALLED_DIR"
    cp "$1" "$INSTALLED_WAR" || return 1
    chmod 644 "$INSTALLED_WAR"
    rm -f "$(signal_file)"
    systemctl start "$TOMCAT_SERVICE" || return 1
}

rollback() {
    trap '' HUP INT TERM PIPE
    loud "DEPLOY FAILED OR INTERRUPTED: restoring the previous WAR and restarting $TOMCAT_SERVICE"
    if install_war "$WAR_BACKUP" && wait_healthy; then
        loud "ROLLED BACK to the previous WAR; $URL is serving again"
    else
        loud "ROLLBACK ALSO FAILED; check: journalctl -u $TOMCAT_SERVICE, /var/lib/tomcat10/logs/catalina.out"
    fi
    loud "The database was NOT restored (Liquibase may already have migrated it). Only if the old WAR cannot run against it:"
    cat >&2 <<RESTORE
    systemctl stop $TOMCAT_SERVICE
    (cd /tmp && sudo -u postgres pg_dump -Fc $DB_NAME > /root/$DB_NAME-before-restore.dump)   # optional
    (cd /tmp && sudo -u postgres dropdb $DB_NAME && sudo -u postgres createdb -O $DB_OWNER $DB_NAME)
    gunzip -c $DB_DUMP | (cd /tmp && sudo -u postgres psql -v ON_ERROR_STOP=1 --single-transaction -d $DB_NAME)
    cp $WAR_BACKUP $INSTALLED_WAR && rm -rf $INSTALLED_DIR
    systemctl start $TOMCAT_SERVICE
RESTORE
}

on_signal() {
    rollback
    exit 1
}

step "6/8 Stop $TOMCAT_SERVICE, replace the WAR, start it (Liquibase migrates on startup)"
if [ "$DRY_RUN" -eq 1 ]; then
    note "[dry-run] trap HUP INT TERM -> rollback"
    note "[dry-run] systemctl stop $TOMCAT_SERVICE; rm -rf $INSTALLED_DIR; cp $BUILT_WAR $INSTALLED_WAR; rm the signal file; systemctl start $TOMCAT_SERVICE"
    step "7/8 Wait up to ${HEALTH_TIMEOUT}s for the signal file to read OK, $URL to return 200 and port $MQTT_PORT to listen"
    note "[dry-run] poll every 5 s; on failure print the signal file content and roll back the WAR"
else
    trap on_signal HUP INT TERM
    OK=1
    install_war "$BUILT_WAR" || OK=0
    step "7/8 Wait up to ${HEALTH_TIMEOUT}s for the signal file to read OK, $URL to return 200 and port $MQTT_PORT to listen"
    if [ "$OK" -eq 1 ]; then
        wait_healthy || OK=0
    fi
    if [ "$OK" -eq 0 ]; then
        rollback
        exit 1
    fi
    trap - HUP INT TERM
fi

step "8/8 Done"
if [ "$DRY_RUN" -eq 1 ]; then
    note "[dry-run] would print the deployed ref $REF and its SHA, and record it in /opt/locker24/last-deploy"
else
    SHA=$(git -C "$SRC_DIR" rev-parse HEAD)
    echo "deployed ref=$REF sha=$SHA at $(date +%FT%T%z)"
    echo "$STAMP ref=$REF sha=$SHA war-backup=$WAR_BACKUP db-dump=$DB_DUMP" >> /opt/locker24/last-deploy
fi
