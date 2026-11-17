#!/usr/bin/env bash
set -euo pipefail
git config --global user.email t@t; git config --global user.name t; git config --global init.defaultBranch main
mkdir -p /tmp/seed/deploy /srv /var/lock /opt/locker24/backups /var/lib/tomcat10/webapps /var/lib/tomcat10/work /root/locker24-hmdm-server-v7/server /root/locker24-hmdm-server-v7/plugins/audit
sed 's#^REPO_URL=.*#REPO_URL=file:///srv/origin.git#' /work/deploy.sh > /tmp/seed/deploy/deploy.sh
chmod +x /tmp/seed/deploy/deploy.sh
printf 'server/build.properties\nplugins/audit/build.properties\nserver/target/\ndist/\n' > /tmp/seed/.gitignore
echo x > /root/locker24-hmdm-server-v7/server/build.properties
echo x > /root/locker24-hmdm-server-v7/plugins/audit/build.properties
echo oldwar > /var/lib/tomcat10/webapps/ROOT.war
mkdir -p /tmp/seed/server /tmp/seed/plugins/audit && touch /tmp/seed/server/.gitkeep /tmp/seed/plugins/audit/.gitkeep
cd /tmp/seed && git init -q && echo m1 > marker && git add -A && git commit -qm m1
echo m2 > marker && git commit -qam m2
git checkout -qb feat && echo EVIL > marker && git commit -qam evil && git checkout -q main
git init -q --bare /srv/origin.git && git push -q /srv/origin.git main feat
mkdir -p /opt/locker24/src && git clone -q file:///srv/origin.git /opt/locker24/src/locker24-hmdm-server-v7
install -o root -g root -m 755 /work/deploy-forced.sh /opt/locker24/deploy-forced.sh

cat > /usr/local/bin/mvn <<'STUB'
#!/bin/bash
sleep "$(cat /etc/stub-build-sleep 2>/dev/null || echo 0)"
find server/src/main/web plugins -maxdepth 5 -name dist -type d > /tmp/mvn-saw-dist 2>/dev/null
mkdir -p server/target && echo "war from $(git rev-parse HEAD)" > server/target/launcher.war
STUB
cat > /usr/local/bin/systemctl <<'STUB'
#!/bin/bash
if [ "${2-}" = tomcat10 ]; then
    [ "$1" = start ] && echo OK > /var/lib/tomcat10/work/hmdm_install_flag
    exit 0
fi
exec /usr/bin/systemctl "$@"
STUB
cat > /usr/local/bin/curl <<'STUB'
#!/bin/bash
case "$*" in *mdm.locker24.app*) echo -n 200 ;; *) exec /usr/bin/curl "$@" ;; esac
STUB
printf '#!/bin/bash\necho "LISTEN 0 100 *:31000 *:*"\n' > /usr/local/bin/ss
printf '#!/bin/bash\nshift 2; exec "$@"\n' > /usr/local/bin/sudo
printf '#!/bin/bash\necho "-- dump"\n' > /usr/local/bin/pg_dump
chmod +x /usr/local/bin/*

ssh-keygen -q -t ed25519 -N '' -C github-deploy -f /root/client_key
mkdir -p /root/.ssh && chmod 700 /root/.ssh
echo "command=\"/opt/locker24/deploy-forced.sh\",restrict $(cut -d' ' -f1,2 /root/client_key.pub) github-deploy" > /root/.ssh/authorized_keys
chmod 600 /root/.ssh/authorized_keys
echo "localhost $(cut -d' ' -f1,2 /etc/ssh/ssh_host_ed25519_key.pub)" > /root/known
