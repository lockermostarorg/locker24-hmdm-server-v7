# Locker24 changes to upstream files

Our own code lives in `plugins/locker24-health`. These are the only upstream files we change. Re-apply them by hand when merging a new upstream release.

| File | Change | Why |
|---|---|---|
| `plugins/pom.xml` | One line in `<modules>`: `<module>locker24-health</module>` | Builds the fleet health plugin with the other plugins |
| `server/pom.xml` | One single-line dependency after `xtra`, in the same one-line format as the other plugins: `<dependency><groupId>com.hmdm.plugin</groupId><artifactId>locker24-health</artifactId><version>0.1.0</version><scope>runtime</scope></dependency>` | Puts the plugin jar into `launcher.war`, where the server finds it at startup |
| `server/pom.xml` | A second `<repository>` in `<repositories>`: `prod-public-maven`, `https://prod.h-mdm.com/api/v4/projects/21/packages/maven` (same as in `plugins/pom.xml`) | `com.hmdm.plugin:platform` resolves only from there; without it the `server` module fails in CI and on a clean machine |
