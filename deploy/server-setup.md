# Locker24 MDM server setup (mdm.locker24.app)

How the production server was built, so it can be repeated.

- Host: droplet `locker24-mdm-v7`, 159.89.110.163, Ubuntu 24.04, 2 vCPU / 4 GB
- DNS: `mdm.locker24.app` (A record to the droplet IP)
- All commands run as root.

**Secrets rule:** never paste a secret into chat, tickets or git. Generate it on the server and store it in the team password manager. Placeholders below look like `<db-password>`. (The first DB password was once shown in a chat; it was rotated with `ALTER USER` before the install.)

## 1. Packages, firewall, database

```bash
apt update && apt -y upgrade
apt -y install openjdk-21-jdk-headless tomcat10 maven postgresql certbot aapt git
ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp && ufw allow 31000/tcp
ufw allow 8443/tcp
ufw --force enable
```

Port 8443 must be open too. The installer redirects 443 to 8443 with iptables, and after the redirect ufw sees port 8443.

Create the database user and database. Generate the password and keep it in the password manager:

```bash
openssl rand -base64 24 | tr -d '/+='
sudo -u postgres psql -c "CREATE USER hmdm WITH PASSWORD '<db-password>';"
sudo -u postgres psql -c "CREATE DATABASE hmdm WITH OWNER=hmdm;"
```

Verify:

```bash
java -version 2>&1 | head -1; systemctl is-active tomcat10 postgresql; ufw status | head -12
```

Expected: Java 21, `active` twice, ufw active with 22, 80, 443, 8443 and 31000 allowed.

## 2. Build

```bash
cd /root
git clone https://github.com/lockermostarorg/locker24-hmdm-server-v7.git
cd locker24-hmdm-server-v7
cp server/build.properties.example server/build.properties
cp plugins/audit/build.properties.example plugins/audit/build.properties
```

Set the rebranding lines in `server/build.properties` (use sed or an editor):

| Key | Value |
|---|---|
| `rebranding.name` | `Locker24 MDM` |
| `rebranding.vendor.name` | `Locker24` |
| `rebranding.vendor.link` | `https://locker24.app` |
| `rebranding.mobile.name` | `Locker24 MDM` |


```bash
mvn install
```

This takes about 17 minutes on 2 vCPU.

### Known quirk: `platform` artifact not found

The last module (`server`) fails with:

```
Could not find artifact com.hmdm.plugin:platform:jar:0.1.0 in jitpack.io
```

The plugins download that artifact from Headwind's repository `https://prod.h-mdm.com/api/v4/projects/21/packages/maven`, which only `plugins/pom.xml` declares. Fix:

```bash
rm -f ~/.m2/repository/com/hmdm/plugin/platform/0.1.0/_remote.repositories
mvn install -rf :server
```

If that still fails, add `-llr` (legacy local repository).

TODO: add that repository to `server/pom.xml` in the fork, so the build works without the workaround.

## 3. Install

```bash
./hmdm_install.sh
```

**Run it ONCE. Never rerun it.** On an existing install it asks you to type "erase", which wipes the database.

Answers given:

| Prompt | Answer |
|---|---|
| install missing packages | `Y` |
| language | `en` |
| PostgreSQL host / port / db / user | `localhost` / `5432` / `hmdm` / `hmdm` |
| PostgreSQL password | `<db-password>` |
| storage directory | `/var/lib/tomcat10/work` |
| scripts directory | `/opt/hmdm` |
| protocol | `https` |
| domain | `mdm.locker24.app` |
| port | empty |
| project path | `ROOT` |
| SMTP | `n` |
| Let's Encrypt | `Y` (renewal notices go to a company email) |
| update Tomcat config automatically | `Y` |
| regular certificate renewal | `Y` |
| iptables 443 to 8443 | `Y` |
| move APKs from h-mdm.com | `Y` |

Result: the panel is at https://mdm.locker24.app. Crontab now has:

```
0 5 * * 1 /opt/hmdm/letsencrypt-ssl.sh
@reboot /opt/hmdm/iptables-tomcat.sh
```

Notes:
- The installer prints a note about `/etc/java-11-openjdk`. Ignore it; we run Java 21.
- It creates a sample device `h0001`. It can be deleted in the panel.

## 4. Hardening (right after the install)

1. In the panel, change the `admin` password immediately. The default is `admin` / `admin`.
2. Back up the Tomcat config:

```bash
cp /var/lib/tomcat10/conf/Catalina/localhost/ROOT.xml /root/ROOT.xml.bak
cp /var/lib/tomcat10/conf/server.xml /root/server.xml.bak
```

3. Set `hash.secret` and `secure.enrollment` in `ROOT.xml`, and enable `jwt.secretkey`. Run `set +H` first: bash history expansion breaks on `<!--` with "event not found". Generate each value and keep it in the password manager:

```bash
set +H
openssl rand -hex 24   # hash.secret
openssl rand -hex 32   # jwt.secretkey
```

```bash
F=/var/lib/tomcat10/conf/Catalina/localhost/ROOT.xml
# hash.secret: replace the installer's placeholder value with <hash-secret>
# secure.enrollment: set to 1
# jwt.secretkey: remove the comment markers around the parameter, value <jwt-secret>
grep -n -E 'hash.secret|secure.enrollment|jwt.secretkey' $F
```

Edit with sed or an editor. The result must read `name="hash.secret" value="<hash-secret>"`, `name="secure.enrollment" value="1"` and an uncommented `jwt.secretkey` with `<jwt-secret>`. Make sure no installer placeholder password is left in the file.

4. Bind the 8080 connector to localhost:

```bash
sed -i '0,/<Connector port="8080"/s//<Connector address="127.0.0.1" port="8080"/' /var/lib/tomcat10/conf/server.xml
systemctl restart tomcat10
```

5. Set the MQTT admin password. Without `mqtt.admin.password` in `ROOT.xml`, Headwind's push sender and broker fall back to a hard-coded default that is public in the upstream source. Port 31000 is open to the internet, and anyone with that default can publish pushes (`adminPanel`, `exitKiosk`, `intent`, `runCommand`) that the agent executes.

```bash
set +H
F=/var/lib/tomcat10/conf/Catalina/localhost/ROOT.xml
P=$(openssl rand -hex 24)
grep -c mqtt.admin.password $F   # must print 0 before the insert
sed -i "s#</Context>#    <Parameter name=\"mqtt.admin.password\" value=\"$P\"/>\n</Context>#" $F
unset P
systemctl restart tomcat10
grep -c mqtt.admin.password $F   # must print 1
```

Devices are not affected: they log in to the broker as `hmdm` with a password derived from `hash.secret`. Only the server uses the admin account. The value does not need to be stored anywhere else; to rotate it, replace the value in `ROOT.xml` and restart Tomcat.

The old server `hwmdm.locker24.app` (5.41.4, Tomcat 9) has the same weakness. Apply the same steps there with `F=/var/lib/tomcat9/conf/Catalina/localhost/ROOT.xml` and `systemctl restart tomcat9`.

6. Store the secrets (DB password, `hash.secret`, admin password, agent keystore password) in the team password manager. `hash.secret` must equal the agent's `requestSignature` in `locker24.properties`. Never commit any of them.

### Verify (read-only)

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://mdm.locker24.app/
curl -s -m 5 -o /dev/null -w "%{http_code}\n" http://159.89.110.163:8080/ || echo blocked
ssh root@159.89.110.163 'ss -ltn | grep -E ":(8080|8443|31000) "; grep -o "secure.enrollment\" value=\"[01]\"" /var/lib/tomcat10/conf/Catalina/localhost/ROOT.xml; grep -c mqtt.admin.password /var/lib/tomcat10/conf/Catalina/localhost/ROOT.xml'
```

Expected:
- the first curl prints `200`
- the second prints `blocked` or `000`
- 8080 listens on `127.0.0.1` only; 8443 and 31000 listen
- `secure.enrollment" value="1"`
- `1` for the `mqtt.admin.password` count (the public default is no longer in use)
- `jwt.secretkey` is set, and no installer placeholder password remains in `ROOT.xml`
- `server.xml` has exactly one active 8443 connector (another one may sit inside a comment)

## 5. Backups

Nightly: `deploy/backup.sh` writes a `pg_dump -Fc` of `hmdm` and tarballs of `/var/lib/tomcat10/work/files` and `/var/lib/tomcat10/work/plugins` to `/root/backups/<YYYY-MM-DD>/`, and deletes dated folders older than 14 days. It exits non-zero when any step fails.

Install it from the repo checkout:

```bash
mkdir -p /opt/locker24
install -m 700 /root/locker24-hmdm-server-v7/deploy/backup.sh /opt/locker24/backup.sh
( crontab -l 2>/dev/null; echo '30 3 * * * /opt/locker24/backup.sh >> /var/log/locker24-backup.log 2>&1' ) | crontab -
crontab -l | grep -c backup.sh   # must print 1
```

Run it once now and check the result:

```bash
/opt/locker24/backup.sh; echo "exit $?"
ls -lh /root/backups/$(date +%F)/
pg_restore -l /root/backups/$(date +%F)/hmdm.dump | head -5
```

Expected: `exit 0`; `hmdm.dump`, `files.tar.gz` and `plugins.tar.gz` exist and are not empty; `pg_restore -l` lists the archive header without errors. Check `/var/log/locker24-backup.log` the next morning.

Also turn on DigitalOcean weekly backups for the droplet (Droplet → Backups → Enable, weekly). The nightly dumps live on the same disk, so they do not protect against losing the droplet.

### Restore

Replace `<date>` with the backup folder to restore. This overwrites the current database and files.

```bash
D=/root/backups/<date>
systemctl stop tomcat10
cd /tmp
sudo -u postgres pg_restore --clean --if-exists -d hmdm "$D/hmdm.dump"
tar -C /var/lib/tomcat10/work -xzf "$D/files.tar.gz"
tar -C /var/lib/tomcat10/work -xzf "$D/plugins.tar.gz"
chown -R tomcat:tomcat /var/lib/tomcat10/work/files /var/lib/tomcat10/work/plugins
systemctl start tomcat10
```

On an empty server, create the `hmdm` user and database first (section 1), then restore with `-d hmdm` as above. The dump keeps object ownership, so the `hmdm` user must exist.

## 6. Updating the server

Updates are built and installed on the droplet by `deploy/deploy.sh <ref>` (a release tag such as `l24-v7.04.1.1`, a branch or a SHA). It takes the pre-deploy backups and rolls the WAR back if the server does not come up. Setup (deploy key), tagging, deploying and rollback are in [`README.md`](README.md).

Do not rerun `hmdm_install.sh` for an update.
