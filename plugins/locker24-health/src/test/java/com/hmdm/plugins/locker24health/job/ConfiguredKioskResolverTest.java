package com.hmdm.plugins.locker24health.job;

import com.hmdm.persistence.domain.Application;
import com.hmdm.plugins.locker24health.status.ConfiguredKiosk;
import org.junit.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ConfiguredKioskResolverTest {

    private static final String PKG = "com.hpmoqa.tablet_client";

    private static Application app(String pkg, String version, int action, boolean skip) {
        Application a = new Application();
        a.setPkg(pkg);
        a.setVersion(version);
        a.setAction(action);
        a.setSkipVersion(skip);
        return a;
    }

    @Test
    public void picksTheInstallRowOfTheContentApp() {
        List<Application> apps = List.of(
                app("app.locker24.mdm", "6.40", 1, false),
                app(PKG, "1.2.7", 0, false),
                app(PKG, "1.2.8", 1, false),
                app(PKG, "1.2.6", 2, false));
        ConfiguredKiosk k = ConfiguredKioskResolver.build(PKG, apps, true, "02:00", "04:30");
        assertEquals(PKG, k.pkg());
        assertEquals("1.2.8", k.version());
        assertTrue(k.scheduleAppUpdate());
        assertEquals(LocalTime.of(2, 0), k.from());
        assertEquals(LocalTime.of(4, 30), k.to());
    }

    @Test
    public void skipVersionMeansNoConfiguredVersion() {
        ConfiguredKiosk k = ConfiguredKioskResolver.build(PKG, List.of(app(PKG, "1.2.8", 1, true)), false, null, null);
        assertNull(k.version());
        assertFalse(k.scheduleAppUpdate());
    }

    @Test
    public void notInstalledByTheConfigurationMeansNoVersion() {
        assertNull(ConfiguredKioskResolver.build(PKG, List.of(app(PKG, "1.2.8", 2, false)), false, null, null).version());
        assertNull(ConfiguredKioskResolver.build(PKG, List.of(), false, null, null).version());
    }

    @Test
    public void parsesWindowTimesLeniently() {
        assertEquals(LocalTime.of(4, 30), ConfiguredKioskResolver.parseTime("4:30"));
        assertEquals(LocalTime.of(23, 5), ConfiguredKioskResolver.parseTime(" 23:05 "));
        assertEquals(LocalTime.of(1, 2, 3), ConfiguredKioskResolver.parseTime("01:02:03"));
        assertNull(ConfiguredKioskResolver.parseTime(null));
        assertNull(ConfiguredKioskResolver.parseTime(""));
        assertNull(ConfiguredKioskResolver.parseTime("25:99"));
        assertNull(ConfiguredKioskResolver.parseTime("soon"));
    }
}
