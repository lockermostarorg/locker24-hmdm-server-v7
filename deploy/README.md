# Deploying Locker24 MDM

CI (GitHub Actions, `.github/workflows/server-ci.yml`) builds and tests every push and pull request to `main` and `feat/**`. It publishes nothing.

Deploying is done on the droplet: `deploy/deploy.sh <ref>` fetches the code over SSH, builds it with Maven, backs up, swaps the WAR and checks that the server is back.

## One-time setup

1. On the droplet, generate a deploy key (never print or copy the private half):

```bash
ssh-keygen -t ed25519 -f /root/.ssh/locker24_deploy_key -N ""
cat /root/.ssh/locker24_deploy_key.pub
```

2. In GitHub, repo Settings -> Deploy keys -> Add deploy key. Paste the `.pub` line, leave "Allow write access" **off**.
3. Check it on the droplet:

```bash
ssh -i /root/.ssh/locker24_deploy_key -o IdentitiesOnly=yes -T git@github.com
```

Expected: `Hi lockermostarorg/locker24-hmdm-server-v7! You've successfully authenticated...`

4. Install the script (the first deploy clones the repo to `/opt/locker24/src/locker24-hmdm-server-v7`, so copy this one file from the existing checkout):

```bash
install -m 700 /root/locker24-hmdm-server-v7/deploy/deploy.sh /opt/locker24/deploy.sh
```

After later deploys, run the copy inside the source checkout instead: `/opt/locker24/src/locker24-hmdm-server-v7/deploy/deploy.sh <ref>`.

The build needs `server/build.properties` and `plugins/audit/build.properties` (git-ignored, they hold the database password and the rebranding). The script never overwrites them. If the fresh checkout has none, it copies them from `/root/locker24-hmdm-server-v7` once. Without that old checkout, create them as in `deploy/server-setup.md` section 2.

## Release a version

Tag the commit on your machine. The tag is the Headwind version plus our counter:

```bash
git tag l24-v7.04.1.1 && git push origin l24-v7.04.1.1
```

## Deploy

```bash
deploy.sh --dry-run l24-v7.04.1.1   # prints the steps, changes nothing
deploy.sh l24-v7.04.1.1
```

`<ref>` may also be a branch or a SHA for a test deploy. The build takes about 17 minutes; the server is down only for the swap (about a minute, plus the Liquibase migration). The script:

1. clones the repo if missing, 2. fetches and checks out the ref (refuses a dirty tree), 3. builds with `mvn -B -DskipTests install` (retrying from `:server` with the known platform-artifact workaround if the first build fails), 4. dumps the database to `/opt/locker24/backups/pre-deploy-<ref>-<time>.sql.gz`, 5. copies the current WAR to `/opt/locker24/backups/ROOT-before-<ref>-<time>.war`, 6. stops Tomcat, installs the WAR, starts Tomcat, 7. waits up to 180 s for `https://mdm.locker24.app/` to return 200 and port 31000 to listen, 8. prints the ref and SHA and appends them to `/opt/locker24/last-deploy`.

`--skip-build` installs the WAR from the last build, for a retry after a failed deploy.

If the new server does not come up, the script restores the previous WAR and says so. It never restores the database by itself: Liquibase may already have migrated it, and the old WAR normally still runs against a migrated schema. It prints the restore command for the case where it does not.

## Rollback

Previous version (WAR only):

```bash
systemctl stop tomcat10
rm -rf /var/lib/tomcat10/webapps/ROOT
cp /opt/locker24/backups/ROOT-before-<ref>-<time>.war /var/lib/tomcat10/webapps/ROOT.war
systemctl start tomcat10
```

Or deploy the older tag again: `deploy.sh l24-v7.04.1.0`.

Database, only if the old WAR cannot run against the migrated schema (this loses everything written since the dump):

```bash
systemctl stop tomcat10
gunzip -c /opt/locker24/backups/pre-deploy-<ref>-<time>.sql.gz | (cd /tmp && sudo -u postgres psql -d hmdm)
systemctl start tomcat10
```

## Notes

- Shared libraries: `hmdm_install.sh` also copies `server/target/shared-libs/*` into Tomcat's `lib/`. `deploy.sh` does not. If a release changes those libraries, copy them by hand and restart Tomcat.
- The nightly `backup.sh` is separate and unaffected. The pre-deploy dumps are never pruned; delete old ones in `/opt/locker24/backups/`.
- The Locker24 Health plugin's menu entry is controlled by its `plugins` row; see `deploy/server-setup.md`.
