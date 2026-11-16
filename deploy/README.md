# Deploying Locker24 MDM

CI (GitHub Actions, `.github/workflows/server-ci.yml`) builds and tests every push to `main` and `feat/**` and every pull request to `main`, and scans the git history for secrets with gitleaks (the repository is public). It publishes nothing. Until Task 6 adds tests, the Locker24 plugin has no tests, so CI runs only Headwind's own.

Deploying is done on the droplet: `deploy/deploy.sh <ref>` fetches the code, builds it with Maven, backs up, swaps the WAR and checks that the server is back.

## First run (no key needed)

The repository is public, so the droplet clones it over HTTPS without credentials.

```bash
mkdir -p /opt/locker24/src
git clone https://github.com/lockermostarorg/locker24-hmdm-server-v7.git /opt/locker24/src/locker24-hmdm-server-v7
cd /opt/locker24/src/locker24-hmdm-server-v7
git checkout l24-v7.04.1.1        # or the branch or SHA to deploy
deploy/deploy.sh --dry-run l24-v7.04.1.1
deploy/deploy.sh l24-v7.04.1.1
```

Always run the script from inside that checkout (`/opt/locker24/src/locker24-hmdm-server-v7/deploy/deploy.sh`); there is no second copy. Run it in tmux or screen, because the build takes about 17 minutes. Output is also written to `/var/log/locker24-deploy.log`.

The build needs `server/build.properties` and `plugins/audit/build.properties` (git-ignored; they hold the database password and the rebranding). The script never overwrites them. If the fresh checkout has none, it copies them once from `/root/locker24-hmdm-server-v7`. Without that old checkout, create them as in `deploy/server-setup.md` section 2.

`REPO_URL` is a single variable at the top of `deploy.sh`. Change it if the repository is moved or renamed.

## Optional: deploy key for a private repository

Only needed if the repository ever becomes private and `REPO_URL` is set to the SSH form (`git@github.com:<owner>/<repo>.git`). With an SSH URL the script uses `/root/.ssh/locker24_deploy_key` with `StrictHostKeyChecking=yes`.

1. On the droplet: `ssh-keygen -t ed25519 -f /root/.ssh/locker24_deploy_key -N ""`, then `cat /root/.ssh/locker24_deploy_key.pub`.
2. In GitHub, repo Settings -> Deploy keys -> Add deploy key. Paste the `.pub` line and leave "Allow write access" off.
3. Trust GitHub's host key once. Fetch it, compare its fingerprint with GitHub's published ed25519 fingerprint `SHA256:+DiY3wvvV6TuJJhbpZisF/zLDA0zPMSvHdkr4UvCOqU`, and only then add it:

```bash
ssh-keyscan -t ed25519 github.com > /tmp/gh.key
ssh-keygen -lf /tmp/gh.key          # must show the fingerprint above
cat /tmp/gh.key >> /root/.ssh/known_hosts
```

4. Check: `ssh -i /root/.ssh/locker24_deploy_key -o IdentitiesOnly=yes -T git@github.com`.

## Release a version

Tag the commit on your machine. The tag is the Headwind version plus our counter:

```bash
git tag l24-v7.04.1.1 && git push origin l24-v7.04.1.1
```

## Deploy

```bash
deploy/deploy.sh --dry-run l24-v7.04.1.1   # prints the steps, checks the ref, changes nothing
deploy/deploy.sh l24-v7.04.1.1
```

`<ref>` is a tag, a remote branch, or a commit SHA (7 to 40 hex digits) for a test deploy. The server is down only for the swap (about a minute, plus the Liquibase migration). The script:

1. clones the repo if missing, 2. fetches and checks out the ref (refuses a dirty tree or untracked files), 3. builds with `mvn -B -DskipTests clean install` and records the built commit in `server/target/l24-build-sha`, 4. checks for 3 GB of free space and dumps the database to `/opt/locker24/backups/pre-deploy-<ref>-<time>.sql.gz`, 5. copies the current WAR to `/opt/locker24/backups/ROOT-before-<ref>-<time>.war` and prunes dumps and WAR backups older than 14 days (always keeping the newest 3 of each), 6. stops Tomcat, installs the WAR, removes Tomcat's completion signal file and starts Tomcat, 7. waits up to 180 s for the signal file to read `OK`, `https://mdm.locker24.app/` to return 200 and port 31000 to listen, 8. prints the ref and SHA and appends them to `/opt/locker24/last-deploy`.

`--skip-build` installs the WAR from the last build, for a retry. It refuses unless the last build was made from the commit that is checked out now.

If the new server does not come up, or the script gets SIGHUP, SIGINT or SIGTERM during steps 6 and 7, it restores the previous WAR and says so. It never restores the database by itself: Liquibase may already have migrated it, and the old WAR normally still runs against a migrated schema. It prints the restore commands for the case where it does not.

## Rollback

Previous version (WAR only):

```bash
systemctl stop tomcat10
rm -rf /var/lib/tomcat10/webapps/ROOT
cp /opt/locker24/backups/ROOT-before-<ref>-<time>.war /var/lib/tomcat10/webapps/ROOT.war
systemctl start tomcat10
```

Or deploy the older tag again: `deploy/deploy.sh l24-v7.04.1.0`.

Database, only if the old WAR cannot run against the migrated schema (this loses everything written since the dump). Do not restore with `pg_dump --clean` output over the live database: after the health plugin's migration the DROPs fail on the new foreign keys and psql would carry on. Recreate the database instead:

```bash
systemctl stop tomcat10
(cd /tmp && sudo -u postgres pg_dump -Fc hmdm > /root/hmdm-before-restore.dump)   # optional, current state
(cd /tmp && sudo -u postgres dropdb hmdm && sudo -u postgres createdb -O hmdm hmdm)
gunzip -c /opt/locker24/backups/pre-deploy-<ref>-<time>.sql.gz | (cd /tmp && sudo -u postgres psql -v ON_ERROR_STOP=1 --single-transaction -d hmdm)
cp /opt/locker24/backups/ROOT-before-<ref>-<time>.war /var/lib/tomcat10/webapps/ROOT.war
rm -rf /var/lib/tomcat10/webapps/ROOT
systemctl start tomcat10
```

## Notes and known risks

- The build runs as root, so a malicious build step or dependency would run as root. Accepted for now; move the build to an unprivileged build user later.
- Shared libraries: `hmdm_install.sh` also copies `server/target/shared-libs/*` into Tomcat's `lib/`. `deploy.sh` does not. If a release changes those libraries, copy them by hand and restart Tomcat.
- The nightly `backup.sh` is separate and unaffected.
- The Locker24 Health plugin's menu entry is controlled by its `plugins` row; see `deploy/server-setup.md`.
