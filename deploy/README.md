# Deploying Locker24 MDM

CI (GitHub Actions, `.github/workflows/server-ci.yml`) builds and tests every push to `main` and `feat/**` and every pull request to `main` (docs-only pull requests excepted), and scans the git history for secrets with gitleaks (the repository is public). It publishes nothing. A separate `sql-checks` job runs `plugins/locker24-health/src/test/sql/*-check.sh` (Postgres 16 in Docker, about 10 seconds each) against the plugin's SQL.

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

Every push to `main` that passes Server CI is deployed by `.github/workflows/server-deploy.yml`. The workflow runs on `workflow_run` of "Server CI" (conclusion `success`, event `push`, branch `main`, same repository) and deploys the exact commit CI tested. It SSHes to `root@mdm.locker24.app` with a dedicated key whose `authorized_keys` entry runs only `/opt/locker24/deploy-forced.sh`. That script accepts nothing but `deployed` (prints the live commit) and `deploy <40-hex sha> <run id>-<attempt>` and `status <40-hex sha> <run id>-<attempt>` (the second part is a per-attempt nonce), starts `deploy.sh <sha>` as a transient systemd unit (so a dropped SSH connection never interrupts a swap), streams its log lines and exits with the deploy's exit code. If SSH drops, the workflow reconnects with `status <sha> <nonce>` for up to 40 minutes (`status` waits for a launch in progress, so it cannot read half-written state). The droplet's host key is pinned in the workflow.

Behaviour to know:

- Deploys queue (`concurrency: mdm-deploy`, no cancelling) and never run in parallel; the forced command also refuses while any `locker24-deploy-*` unit runs, and `deploy.sh` holds its flock.
- A push that only changes `docs/**` or `*.md` files is not deployed (no rebuild, no downtime): the workflow asks the droplet which commit is live (`deployed`, the last line of `/opt/locker24/last-deploy`) and `deploy/docs-only.sh` compares it with the new commit through the GitHub compare API. It skips only when the new commit is strictly ahead of the live one and every changed file (and every renamed-from path) is under `docs/` or ends in `.md`; if the live commit is unknown or unreachable, the API fails, the diverged/behind case, or 100+ files changed, it deploys. A manual run always deploys. Skipped runs say "Docs-only change ..., not deployed" and succeed. A CI success for a commit that is no longer the tip of `main` is skipped, so a late older run cannot overwrite a newer deploy. Server CI runs on every push to `main` (no `paths-ignore` there), so every tip of `main`, docs-only ones included, gets a CI run and a deploy of that exact commit.
- The forced command fetches `origin/main` and refuses any SHA that is not on it, so even a manual `force` deploy (or a leaked key) can only deploy code that went through `main`. To deploy a feature-branch commit, merge it to `main` first or run `deploy.sh` by hand.
- It also passes `JAVA_HOME` (resolved from `java` at start) to the deploy, since systemd gives it a minimal environment.
- The nonce makes the protocol idempotent: state is keyed by sha and nonce, so `status` never reports an earlier attempt's result (unknown nonce: exit 3). `deploy` with a nonce the host already knows follows that attempt instead of starting another; `deploy` of a sha that is already deploying (for example a re-run while the first run is still going) attaches to it; a different sha while one deploys is refused. Starting a deploy is serialised with a lock.
- Re-running a finished workflow redeploys the same commit (a rebuild and swap, harmless).
- The forced command also refuses a sha if a tag or branch of that name exists on the remote, and after the unit ends it checks that the checkout's HEAD is the requested sha (a mismatch is logged as WRONG COMMIT and the deploy reports failure). `deploy.sh` itself uses a 40-hex ref directly as a commit and checks HEAD after the checkout.
- The unit has `RuntimeMaxSec=45min`; when it runs out systemd sends SIGTERM, so `deploy.sh` rolls back (it gets up to 15 minutes to do so). The unit's own errors go to `/var/log/locker24-deploy.log`. `/run` is mounted noexec on Ubuntu, so the private copy of `deploy.sh` is run with `bash <copy>`.
- The workflow stops after 3 consecutive SSH authentication or host key failures instead of retrying for 40 minutes. A deploy exit code of 255 is reported as 254 so it cannot be taken for an SSH failure.
- Manual deploy or rollback: Actions -> "Server deploy" -> Run workflow, with the full SHA. A SHA that is not the tip of `main` needs `force`. Manual runs do not check that CI passed.
- The deploy runs a private copy of `deploy.sh` taken from the checkout when the deploy starts, so a changed `deploy.sh` takes effect from the deploy after the one that brings it. Re-copy `deploy-forced.sh` by hand when it changes.
- Remote output is wrapped in `::stop-commands::` so build output cannot inject workflow commands.
- Progress and the result are also in `/var/log/locker24-deploy.log`; `journalctl -u 'locker24-deploy-*'` shows the unit.

One-time setup:

1. The key pair is generated by the controller (the public key file `mdm_deploy_key.pub` comes from there); never commit the private key. To generate one yourself: `ssh-keygen -t ed25519 -N "" -C github-deploy -f mdm_deploy_key`.
2. Put the key in a GitHub Environment so that only runs on `main` can read it: Settings -> Environments -> New environment `mdm-production`; under "Deployment branches and tags" choose "Selected branches and tags" and add `main`; do not add required reviewers (deploys are automatic). In that environment add the environment secret `MDM_DEPLOY_SSH_KEY` (the whole private key file), then delete any repository-level secret of the same name (Settings -> Secrets and variables -> Actions) and the local private key. The deploy job declares `environment: mdm-production`.
3. On the droplet install the forced command script, then the key (one line; replace `<PUBLIC KEY>` with the key material from `mdm_deploy_key.pub`, the part after `ssh-ed25519`):

```bash
install -o root -g root -m 755 /opt/locker24/src/locker24-hmdm-server-v7/deploy/deploy-forced.sh /opt/locker24/deploy-forced.sh
echo 'command="/opt/locker24/deploy-forced.sh",restrict ssh-ed25519 <PUBLIC KEY> github-deploy' >> /root/.ssh/authorized_keys
```

   `restrict` turns off port, agent and X11 forwarding, pty allocation and user rc in one go (OpenSSH 7.2+; Ubuntu 24.04 is fine). It is equivalent to `no-port-forwarding,no-agent-forwarding,no-X11-forwarding,no-pty,no-user-rc`.
4. Check that root may log in with a key: `sshd -T | grep -i permitrootlogin` (`prohibit-password` is fine).
5. Test: Actions -> Server deploy -> Run workflow with the SHA currently deployed (the tip of `main`). It rebuilds and swaps; the job log shows the deploy.

Tests: `deploy/test/run-forced-test.sh` (Docker, stubbed systemd, git and deploy.sh, `/run` noexec) and `deploy/test/e2e/run.sh` (privileged Docker: systemd as PID 1, real sshd, real git origin, the real `deploy.sh` and `deploy-forced.sh`; only mvn, Tomcat, pg_dump and the health URL are stubbed).

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

## Post-deploy checks

The health plugin's pop-ups go through Tomcat's internal alert endpoint, which accepts only loopback callers. On every new server, and after every deploy:

1. `internal.url` must point at loopback. On the droplet `ROOT.xml` sets no `internal.url`, so the default `http://127.0.0.1:8080` applies, and the 8080 connector must listen on loopback (on mdm it listens on `[::ffff:127.0.0.1]:8080` only, which the filter accepts as loopback). Check:
   ```bash
   grep -i internal /var/lib/tomcat10/conf/Catalina/localhost/ROOT.xml   # nothing, or an http://127.0.0.1:8080 URL (not "localhost": it can resolve to ::1, where 8080 does not listen)
   ss -ltnp | grep ':8080'                                      # a 127.0.0.1 or ::ffff:127.0.0.1 address only
   ```
2. The endpoint must refuse outside callers. From your own machine, not the droplet:
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' -X POST https://mdm.locker24.app/rest/internal/alert -H 'Content-Type: application/json' -d '{}'       # 403
   curl -s -o /dev/null -w '%{http_code}\n' -X POST 'https://mdm.locker24.app/rest/internal;x=1/alert' -H 'Content-Type: application/json' -d '{}'  # 400 or 403
   ```
   A 200 means the endpoint is open: roll back and investigate.
3. The first real health pop-up after the deploy still arrives in the panel (the loopback call is allowed).
4. The plugin checks and the smoke script in `docs/locker24-health.md` (tables, `plugins` row, Fleet health opens, `deploy/health-smoke.sh`).
5. All other plugin menus are still present (Device Info, Device Log, Messaging, Push, Audit, ...). If they are gone, the health dashboard remote failed to load: see "Rollback" below.
6. The health job runs. Core swallows a failing task module (one line on stderr, Tomcat starts normally, the smoke test still passes), so check it explicitly:
   ```bash
   grep 'Health evaluation scheduled' /var/lib/tomcat10/logs/catalina.out | tail -n 2     # a line from the current start; no "Failed to instantiate and initialize plugin task module" after it
   # 2 minutes after the start: no device that reports may still be 'unknown' (the panel shows a row as unknown while `evaluated_at` is NULL; devices that never reported have no row)
   sudo -u postgres psql hmdm -c "SELECT d.number, l.received_at FROM locker24_device_health_latest l JOIN devices d ON d.id=l.device_id WHERE l.evaluated_at IS NULL AND l.received_at > (extract(epoch FROM now())*1000)::bigint - 600000"
   ```
   The query must return no rows. About 30 minutes after the start the `not_charging` pop-up of M-01 must arrive if M-01 is unplugged; until it does, the deploy is not verified (it proves the job, the alert service and the loopback call).

## Rollback

Previous version (WAR only):

```bash
systemctl stop tomcat10
rm -rf /var/lib/tomcat10/webapps/ROOT
cp /opt/locker24/backups/ROOT-before-<ref>-<time>.war /var/lib/tomcat10/webapps/ROOT.war
systemctl start tomcat10
```

Or deploy the older tag again: `deploy/deploy.sh l24-v7.04.1.0`.

**Rolling back to a build before the health dashboard (changeset `-08`).** Changeset `-08` sets `baseUrl` of the `locker24-health` plugin row. An older WAR has no `locker24-health` remote, so the panel's federation init fails and EVERY plugin menu (Device Info, Device Log, Messaging, Push, Audit, ...) disappears. `deploy.sh`'s automatic rollback checks the restored WAR (with python3, else `jar`, else `unzip`; `unzip` is optional, and if none works it prints the SQL) for `META-INF/resources/plugins/locker24-health/remoteEntry.json` inside its `WEB-INF/lib/*locker24-health*.jar` and, if it is missing, runs the one statement below itself and says so loudly (if it cannot check, it prints the statement). For a manual rollback, or whenever the plugin menus vanish, run it yourself, reload the panel, and restart Tomcat if the menus do not come back:

```bash
sudo -u postgres psql -d hmdm -v ON_ERROR_STOP=1 -c "UPDATE plugins SET baseUrl=NULL, baseUrlDev=NULL WHERE identifier='locker24-health'"
```

It touches only the `locker24-health` row. Deploying a dashboard build again does not rerun `-08`; restore the row with `UPDATE plugins SET baseUrl='/plugins/locker24-health', baseUrlDev='http://localhost:4224' WHERE identifier='locker24-health'`.

A manual WAR rollback must also record itself, so the docs-only skip compares against the right commit (sha of the restored WAR if you know it, else `unknown`; with an unknown sha the skip does not match and the next push is deployed, which is safe):

```bash
echo "$(date +%Y%m%d-%H%M%S) ref=manual-rollback sha=<40-hex sha of the restored WAR, or 0000000000000000000000000000000000000000> note=manual" >> /opt/locker24/last-deploy
```

If you rolled back to a build without the health agent code, also remember the agent-side leftovers (queue file, prefs, periodic job) are only cancelled by a health-free agent build.

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

## Removing Headwind demo data

Headwind's installer seeds demo applications, five demo configurations and a demo device `h0001` (`install/sql/hmdm_init.en.sql`). `deploy/cleanup-demo-data.sh` removes what is safe to remove. Run it once as root on the droplet, after the deploy:

```bash
sudo bash /opt/locker24/src/locker24-hmdm-server-v7/deploy/cleanup-demo-data.sh            # prints the plan, asks y/N
sudo bash /opt/locker24/src/locker24-hmdm-server-v7/deploy/cleanup-demo-data.sh --yes      # no prompt
sudo bash /opt/locker24/src/locker24-hmdm-server-v7/deploy/cleanup-demo-data.sh --db NAME  # default: hmdm
```

It deletes, in one transaction: demo device `h0001` if it never enrolled; the five demo configurations (matched by id and name) that no device uses (current or previous configuration); demo applications (matched by id and package) that nothing references. It keeps a demo configuration that is the default for new devices (`settings.newDeviceConfigurationId`) or whose rows, main app or content app differ from the seed (listed as "edited, kept"). It keeps any application used by a remaining configuration (rows, main or content app), with a version uploaded after install, or with a custom log rule or setting, and never touches `app.locker24.mdm`. The plan lists what is deleted and what is kept and why. The plan also counts cascaded rows (versions, configuration rows, device log records, ...). Apply locks the affected tables, recomputes the set and rolls back with exit 4 if it differs from the confirmed plan. Answering `n` exits 3. A second run prints `No Headwind demo data found` or `Nothing to delete`. Take a database backup first (`deploy/backup.sh`). Check script: `deploy/test/cleanup-demo-data-check.sh` (Docker).

## Notes and known risks

- The build runs as root, so a malicious build step or dependency would run as root. Accepted for now; move the build to an unprivileged build user later.
- Shared libraries: `hmdm_install.sh` also copies `server/target/shared-libs/*` into Tomcat's `lib/`. `deploy.sh` does not. If a release changes those libraries, copy them by hand and restart Tomcat.
- Server CI uses `paths-ignore` for docs-only changes on pull requests only; pushes (to `main` and `feat/**`) always run it, because a deploy needs a CI run for every tip of `main`. If `build` ever becomes a required status check, docs-only pull requests would wait for it forever; adjust the filter then.
- Gitleaks: `.gitleaksignore` suppresses one fingerprint, the sample `jwt.secretkey` in Headwind's `server/build.properties.example`.
- The MDM has no nginx: iptables redirects 443 to Tomcat's 8443, which serves the panel and the public ingest directly. There is no rate limit on `/rest/public/plugin-locker24-health/report/{number}` (see the known gaps in `docs/locker24-health.md`).
- The nightly `backup.sh` is separate and unaffected.
- The Locker24 Health plugin's menu entry is controlled by its `plugins` row; see `deploy/server-setup.md`.
