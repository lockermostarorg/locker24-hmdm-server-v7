# Locker24 changes to upstream files

Our own code lives in `plugins/locker24-health`. These are the only upstream files we change. Re-apply them by hand when merging a new upstream release.

| File | Change | Why |
|---|---|---|
| `plugins/pom.xml` | One line in `<modules>`: `<module>locker24-health</module>` | Builds the fleet health plugin with the other plugins |
| `server/pom.xml` | One single-line dependency after `xtra`, in the same one-line format as the other plugins: `<dependency><groupId>com.hmdm.plugin</groupId><artifactId>locker24-health</artifactId><version>0.1.0</version><scope>runtime</scope></dependency>` | Puts the plugin jar into `launcher.war`, where the server finds it at startup |
| `server/pom.xml` | A second `<repository>` in `<repositories>`: `prod-public-maven`, `https://prod.h-mdm.com/api/v4/projects/21/packages/maven` (same as in `plugins/pom.xml`), snapshots disabled | `com.hmdm.plugin:platform` resolves only from there; without it the `server` module fails in CI and on a clean machine |
| `.gitignore` | One line: `.pnpm-store/` | The xtra plugin's pnpm install writes a store into the repo root during the build |
| `.gitignore` | Two lines after `test/`: `!plugins/**/src/test/` and `!plugins/**/src/test/**` | The Angular `test/` rule also ignored the Java test sources of our plugin |
| `.gitignore` | Two lines after the plugin exceptions: `!common/src/test/` and `!common/src/test/**` | Same `test/` rule; keeps the core unit test for the internal IP filter in the repo |
| `server/src/main/java/com/hmdm/guice/Initializer.java` | **Upstream security fix** (candidate to send back to Headwind). One line in `getModules()` after `PrivateRestModule`: `modules.add(new InternalRestModule());` | Upstream never installs `InternalRestModule`, so `/rest/internal/alert` (registered by Jersey's `com.hmdm` package scan) was reachable from the internet without authentication; anyone could insert alerts and pop-ups for any customer. The module puts `InternalIPFilter` in front of `/rest/internal/*` |
| `common/src/main/java/com/hmdm/rest/filter/InternalIPFilter.java` | **Upstream security fix** (candidate to send back to Headwind). Decides only on `request.getRemoteAddr()`: always allows loopback (127.0.0.0/8, `::1`), plus `internal.source.ip` if set; never reads `X-Real-IP`/`X-Forwarded-For`; a blank setting means loopback only; non-literal or missing addresses are refused. Test: `common/src/test/java/com/hmdm/rest/filter/InternalIPFilterTest.java` | Upstream used `BaseIPFilter.match(request)`, which trusts `X-Real-IP` whenever remote = local address (spoofable by a client on the host), rejects `::1` (IPv4-only matcher), and allows everyone when `internal.source.ip` is blank |
