#!/usr/bin/env bash
# Build the Locker24 MDM server from a git ref on this droplet and install the WAR.
# Usage: deploy.sh [--dry-run] [--skip-build] <ref>
#   <ref>         tag (l24-v7.04.1.1), branch or commit SHA
#   --dry-run     print the steps, change nothing
#   --skip-build  install the WAR from the last build (retry after a failed deploy)
# Runs as root on the droplet. See deploy/README.md.
set -euo pipefail

REPO_URL=git@github.com:lockermostarorg/locker24-hmdm-server-v7.git
SRC_DIR=/opt/locker24/src/locker24-hmdm-server-v7
BACKUP_DIR=/opt/locker24/backups
DEPLOY_KEY=/root/.ssh/locker24_deploy_key
LEGACY_CHECKOUT=/root/locker24-hmdm-server-v7
TOMCAT_SERVICE=tomcat10
TOMCAT_WEBAPPS=/var/lib/tomcat10/webapps
INSTALLED_WAR=$TOMCAT_WEBAPPS/ROOT.war
INSTALLED_DIR=$TOMCAT_WEBAPPS/ROOT
BUILT_WAR=$SRC_DIR/server/target/launcher.war
DB_NAME=hmdm
URL=https://mdm.locker24.app/
MQTT_PORT=31000
HEALTH_TIMEOUT=180
LOCK_FILE=/var/lock/locker24-deploy.lock

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
note() { echo "    $*"; }
loud() { echo; echo "!!!!!! $*" >&2; }

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
fi

SAFE_REF=${REF//[^A-Za-z0-9._-]/-}
STAMP=$(date +%Y%m%d-%H%M%S)
DB_DUMP=$BACKUP_DIR/pre-deploy-$SAFE_REF-$STAMP.sql.gz
WAR_BACKUP=$BACKUP_DIR/ROOT-before-$SAFE_REF-$STAMP.war

echo "Locker24 MDM deploy: ref=$REF dry-run=$DRY_RUN skip-build=$SKIP_BUILD"

git_ssh() {
    GIT_SSH_COMMAND="ssh -i $DEPLOY_KEY -o IdentitiesOnly=yes" git "$@"
}

step "1/8 Source checkout at $SRC_DIR"
if [ ! -d "$SRC_DIR/.git" ]; then
    note "missing, cloning $REPO_URL"
    run mkdir -p "$(dirname "$SRC_DIR")"
    if [ "$DRY_RUN" -eq 0 ] && [ ! -f "$DEPLOY_KEY" ]; then
        echo "deploy key $DEPLOY_KEY not found; see deploy/README.md" >&2
        exit 1
    fi
    run env GIT_SSH_COMMAND="ssh -i $DEPLOY_KEY -o IdentitiesOnly=yes" git clone "$REPO_URL" "$SRC_DIR"
else
    note "already cloned"
fi

step "2/8 Fetch and check out $REF"
if [ "$DRY_RUN" -eq 1 ] && [ ! -d "$SRC_DIR/.git" ]; then
    note "[dry-run] git fetch --tags --prune; refuse a dirty tree; git checkout --force --detach $REF"
else
    cd "$SRC_DIR"
    if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
        echo "working tree in $SRC_DIR is dirty; refusing to deploy:" >&2
        git status --short --untracked-files=no >&2
        exit 1
    fi
    if [ "$DRY_RUN" -eq 1 ]; then
        note "[dry-run] git fetch --tags --prune; git checkout --force --detach $REF"
    else
        git_ssh fetch --tags --prune
        if git rev-parse -q --verify "refs/tags/$REF^{commit}" >/dev/null; then
            TARGET=refs/tags/$REF
        elif git rev-parse -q --verify "refs/remotes/origin/$REF^{commit}" >/dev/null; then
            TARGET=refs/remotes/origin/$REF
        elif git rev-parse -q --verify "$REF^{commit}" >/dev/null; then
            TARGET=$REF
        else
            echo "ref '$REF' not found (not a tag, branch or commit)" >&2
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

step "3/8 Build (mvn -B -DskipTests install)"
if [ "$SKIP_BUILD" -eq 1 ]; then
    note "--skip-build: using the WAR from the last build"
else
    if [ "$DRY_RUN" -eq 1 ]; then
        note "[dry-run] copy server/build.properties and plugins/audit/build.properties from $LEGACY_CHECKOUT if missing"
        note "[dry-run] cd $SRC_DIR && mvn -B -DskipTests install  (about 17 min on 2 vCPU)"
        note "[dry-run] if it fails: rm -f ~/.m2/repository/com/hmdm/plugin/platform/0.1.0/_remote.repositories; mvn -B -DskipTests install -rf :server"
    else
        build_properties
        cd "$SRC_DIR"
        BUILD_START=$(date +%s)
        if ! mvn -B -DskipTests install; then
            loud "build failed; retrying once from :server with the platform-artifact workaround"
            rm -f "$HOME/.m2/repository/com/hmdm/plugin/platform/0.1.0/_remote.repositories"
            mvn -B -DskipTests install -rf :server
        fi
        note "build took $(( $(date +%s) - BUILD_START )) s"
    fi
fi
if [ "$DRY_RUN" -eq 0 ]; then
    [ -s "$BUILT_WAR" ] || { echo "$BUILT_WAR not found" >&2; exit 1; }
    if [ "$SKIP_BUILD" -eq 0 ] && [ "$BUILT_WAR" -ot "$SRC_DIR/pom.xml" ]; then
        echo "$BUILT_WAR is older than the checkout; the build did not produce it" >&2
        exit 1
    fi
fi

step "4/8 Database dump -> $DB_DUMP"
run mkdir -p "$BACKUP_DIR"
if [ "$DRY_RUN" -eq 1 ]; then
    note "[dry-run] (cd /tmp && sudo -u postgres pg_dump --clean --if-exists $DB_NAME | gzip > $DB_DUMP)"
else
    umask 077
    (cd /tmp && sudo -u postgres pg_dump --clean --if-exists "$DB_NAME" | gzip > "$DB_DUMP")
    gzip -t "$DB_DUMP"
    [ -s "$DB_DUMP" ] || { echo "empty database dump" >&2; exit 1; }
    note "$(du -h "$DB_DUMP" | cut -f1)"
fi

step "5/8 Save the current WAR -> $WAR_BACKUP"
if [ "$DRY_RUN" -eq 1 ]; then
    note "[dry-run] cp -p $INSTALLED_WAR $WAR_BACKUP"
else
    cp -p "$INSTALLED_WAR" "$WAR_BACKUP"
fi

wait_healthy() {
    local deadline=$(( $(date +%s) + HEALTH_TIMEOUT )) code port_ok
    while [ "$(date +%s)" -lt "$deadline" ]; do
        code=$(curl -s -o /dev/null -m 5 -w '%{http_code}' "$URL" || true)
        port_ok=0
        ss -ltn | grep -q ":$MQTT_PORT " && port_ok=1
        if [ "$code" = 200 ] && [ "$port_ok" -eq 1 ]; then
            return 0
        fi
        sleep 5
    done
    note "last check: HTTP ${code:-none}, port $MQTT_PORT listening=$port_ok"
    return 1
}

install_war() {
    systemctl stop "$TOMCAT_SERVICE" || return 1
    rm -rf "$INSTALLED_DIR"
    cp "$1" "$INSTALLED_WAR" || return 1
    chmod 644 "$INSTALLED_WAR"
    systemctl start "$TOMCAT_SERVICE" || return 1
}

step "6/8 Stop $TOMCAT_SERVICE, replace the WAR, start it (Liquibase migrates on startup)"
if [ "$DRY_RUN" -eq 1 ]; then
    note "[dry-run] systemctl stop $TOMCAT_SERVICE; rm -rf $INSTALLED_DIR; cp $BUILT_WAR $INSTALLED_WAR; systemctl start $TOMCAT_SERVICE"
else
    OK=1
    install_war "$BUILT_WAR" || OK=0
    step "7/8 Wait up to ${HEALTH_TIMEOUT}s for $URL (HTTP 200) and port $MQTT_PORT"
    if [ "$OK" -eq 1 ]; then
        wait_healthy || OK=0
    fi
    if [ "$OK" -eq 0 ]; then
        loud "DEPLOY FAILED: restoring the previous WAR and restarting $TOMCAT_SERVICE"
        if install_war "$WAR_BACKUP" && wait_healthy; then
            loud "ROLLED BACK to the previous WAR; $URL is serving again"
        else
            loud "ROLLBACK ALSO FAILED; check: journalctl -u $TOMCAT_SERVICE, /var/lib/tomcat10/logs/catalina.out"
        fi
        loud "The database was NOT restored (Liquibase may already have migrated it). Only if the old WAR cannot run against it:"
        echo "    systemctl stop $TOMCAT_SERVICE && gunzip -c $DB_DUMP | (cd /tmp && sudo -u postgres psql -d $DB_NAME) && systemctl start $TOMCAT_SERVICE" >&2
        exit 1
    fi
fi
if [ "$DRY_RUN" -eq 1 ]; then
    step "7/8 Wait up to ${HEALTH_TIMEOUT}s for $URL (HTTP 200) and port $MQTT_PORT"
    note "[dry-run] poll every 5 s"
fi

step "8/8 Done"
if [ "$DRY_RUN" -eq 1 ]; then
    note "[dry-run] would print the deployed ref $REF and its SHA, and record it in /opt/locker24/last-deploy"
else
    SHA=$(git -C "$SRC_DIR" rev-parse HEAD)
    echo "deployed ref=$REF sha=$SHA at $(date -Is)"
    echo "$STAMP ref=$REF sha=$SHA war-backup=$WAR_BACKUP db-dump=$DB_DUMP" >> /opt/locker24/last-deploy
fi
