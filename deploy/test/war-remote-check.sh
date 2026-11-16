#!/usr/bin/env bash
# shellcheck disable=SC2123,SC2329,SC2034,SC2015
# Unit test of war_has_health_remote() from deploy.sh, for each tool (python3, jar, unzip) that is installed. Needs zip.
set -uo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
DEPLOY=$HERE/../deploy.sh
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
eval "$(sed -n '/^HEALTH_REMOTE=/p;/^HEALTH_JAR_RE=/p;/^HEALTH_CHECK_TOOLS=/p;/^war_check_[a-z0-9]*() {/,/^}/p;/^war_has_health_remote() {/,/^}/p' "$DEPLOY")"
fail=0
check() { if [ "$2" = "$3" ]; then echo "PASS $1"; else echo "FAIL $1 (expected $2, got $3)"; fail=1; fi; }

mk() { # name jarname withRemote
    local d=$T/$1; mkdir -p "$d/jar/META-INF/resources/plugins/other" "$d/war/WEB-INF/lib"
    echo '{}' > "$d/jar/META-INF/resources/plugins/other/remoteEntry.json"
    [ "$3" = 1 ] && { mkdir -p "$d/jar/META-INF/resources/plugins/locker24-health"; echo '{}' > "$d/jar/META-INF/resources/plugins/locker24-health/remoteEntry.json"; }
    (cd "$d/jar" && zip -qr "$d/war/WEB-INF/lib/$2" .)
    (cd "$d/war" && zip -qr "$d/$1.war" .)
}
mk with locker24-health-1.0.jar 1
mk without locker24-health-1.0.jar 0
mk nojar other-1.0.jar 0
mk decoy other-1.0.jar 1

for tool in python3 jar unzip; do
    command -v "$tool" >/dev/null 2>&1 || { echo "SKIP $tool (not installed)"; continue; }
    HEALTH_CHECK_TOOLS=$tool
    war_has_health_remote "$T/with/with.war"; check "$tool: remote in the plugin jar" 0 $?
    war_has_health_remote "$T/without/without.war"; check "$tool: plugin jar without the remote" 1 $?
    war_has_health_remote "$T/nojar/nojar.war"; check "$tool: no health jar at all" 1 $?
    war_has_health_remote "$T/decoy/decoy.war"; check "$tool: remote only in an unrelated jar is ignored" 1 $?
    war_has_health_remote "$T/missing.war"; check "$tool: missing file cannot be checked" 2 $?
    echo garbage > "$T/bad.war"; war_has_health_remote "$T/bad.war"; check "$tool: corrupt WAR cannot be checked" 2 $?
done
HEALTH_CHECK_TOOLS="python3 jar unzip"
war_has_health_remote "$T/with/with.war"; check "default order" 0 $?
HEALTH_CHECK_TOOLS="nonexistent-tool python3"
war_has_health_remote "$T/with/with.war"; check "missing tool is skipped, next one used" 0 $?
HEALTH_CHECK_TOOLS="python3 jar unzip"
( PATH=/nonexistent; war_has_health_remote "$T/with/with.war" ); check "no tool at all cannot be checked" 2 $?

# reset_health_menu_if_needed: stubbed sudo records the SQL; only the NULL-ing statement may run
eval "$(sed -n '/^HEALTH_MENU_SQL=/p;/^reset_health_menu_if_needed() {/,/^}/p' "$DEPLOY")"
note() { :; }; loud() { echo "LOUD $*" >> "$T/loud"; }
TOMCAT_SERVICE=tomcat10; DB_NAME=hmdmtest
sudo() { echo "$*" >> "$T/sudo"; return "${SUDO_RC:-0}"; }
INSTALLED_WAR=$T/with/with.war; : > "$T/sudo"; : > "$T/loud"
reset_health_menu_if_needed; check "WAR with remote: no SQL run" 0 "$(wc -c < "$T/sudo" | tr -d ' ')"
INSTALLED_WAR=$T/without/without.war
reset_health_menu_if_needed 2>/dev/null
check "WAR without remote: exactly the baseUrl reset runs" "-u postgres psql -d hmdmtest -v ON_ERROR_STOP=1 -c UPDATE plugins SET baseUrl=NULL, baseUrlDev=NULL WHERE identifier='locker24-health'" "$(cat "$T/sudo")"
grep -q "SET TO NULL" "$T/loud" && echo "PASS says loudly that it did" || { echo "FAIL no loud message"; fail=1; }
: > "$T/sudo"; INSTALLED_WAR=$T/missing.war
reset_health_menu_if_needed 2>"$T/err"; check "uncheckable WAR: SQL is printed, not run" 0 "$(wc -c < "$T/sudo" | tr -d ' ')"
grep -q "baseUrl=NULL" "$T/err" && echo "PASS manual SQL printed" || { echo "FAIL SQL not printed"; fail=1; }
exit $fail
