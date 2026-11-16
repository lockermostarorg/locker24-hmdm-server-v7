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

## Automatic deploys from GitHub

Every push to `main` that passes Server CI is deployed by `.github/workflows/server-deploy.yml`. The workflow runs on `workflow_run` of "Server CI" (conclusion `success`, event `push`, branch `main`, same repository) and deploys the exact commit CI tested. It SSHes to `root@mdm.locker24.app` with a dedicated key whose `authorized_keys` entry runs only `/opt/locker24/deploy-forced.sh`. That script accepts nothing but `deploy <40-hex sha>` and `status <40-hex sha>`, starts `deploy.sh <sha>` as a transient systemd unit (so a dropped SSH connection never interrupts a swap), streams its log lines and exits with the deploy's exit code. If SSH drops, the workflow reconnects with `status <sha>` for up to 40 minutes. The droplet's host key is pinned in the workflow.

Behaviour to know:

- Deploys queue (`concurrency: mdm-deploy`, no cancelling) and never run in parallel; the forced command also refuses while any `locker24-deploy-*` unit runs, and `deploy.sh` holds its flock.
- A CI success for a commit that is no longer the tip of `main` is skipped, so a late older run cannot overwrite a newer deploy. Docs-only pushes do not run CI, hence do not deploy.
- Re-running a finished workflow redeploys the same commit (a rebuild and swap, harmless).
- Manual deploy or rollback: Actions -> "Server deploy" -> Run workflow, with the full SHA. A SHA that is not the tip of `main` needs `force`. Manual runs do not check that CI passed.
- The deploy runs a private copy of `deploy.sh` taken from the checkout when the deploy starts, so a changed `deploy.sh` takes effect from the deploy after the one that brings it. Re-copy `deploy-forced.sh` by hand when it changes.
- Remote output is wrapped in `::stop-commands::` so build output cannot inject workflow commands.
- Progress and the result are also in `/var/log/locker24-deploy.log`; `journalctl -u 'locker24-deploy-*'` shows the unit.

One-time setup:

1. Generate a dedicated key pair on a trusted machine (never commit it): `ssh-keygen -t ed25519 -N "" -C github-deploy -f mdm_deploy_key`.
2. GitHub repo Settings -> Secrets and variables -> Actions -> New repository secret `MDM_DEPLOY_SSH_KEY`, value = the whole private key file. Delete the local private key afterwards.
3. On the droplet install the forced command script, then the key (one line; replace `<PUBLIC KEY>` with the key material from `mdm_deploy_key.pub`, the part after `ssh-ed25519`):

```bash
install -o root -g root -m 755 /opt/locker24/src/locker24-hmdm-server-v7/deploy/deploy-forced.sh /opt/locker24/deploy-forced.sh
echo 'command="/opt/locker24/deploy-forced.sh",restrict ssh-ed25519 <PUBLIC KEY> github-deploy' >> /root/.ssh/authorized_keys
```

   `restrict` turns off port, agent and X11 forwarding, pty allocation and user rc in one go (OpenSSH 7.2+; Ubuntu 24.04 is fine). It is equivalent to `no-port-forwarding,no-agent-forwarding,no-X11-forwarding,no-pty,no-user-rc`.
4. Check that root may log in with a key: `sshd -T | grep -i permitrootlogin` (`prohibit-password` is fine).
5. Test: Actions -> Server deploy -> Run workflow with the SHA currently deployed (the tip of `main`). It rebuilds and swaps; the job log shows the deploy.

Tests for the forced command (Docker, stubbed systemd and deploy.sh): `deploy/test/run-forced-test.sh`.

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
- The `build` job uses `paths-ignore` for docs-only changes. If `build` ever becomes a required status check, docs-only pull requests would wait for it forever; adjust the filter then.
- Gitleaks: `.gitleaksignore` suppresses one fingerprint, the sample `jwt.secretkey` in Headwind's `server/build.properties.example`.
- The nightly `backup.sh` is separate and unaffected.
- The Locker24 Health plugin's menu entry is controlled by its `plugins` row; see `deploy/server-setup.md`.
