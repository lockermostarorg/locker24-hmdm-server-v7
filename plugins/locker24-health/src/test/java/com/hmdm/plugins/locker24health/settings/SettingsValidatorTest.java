package com.hmdm.plugins.locker24health.settings;

import com.hmdm.plugins.locker24health.rest.json.HealthSettings;
import org.junit.Test;

import static org.junit.Assert.fail;

public class SettingsValidatorTest {

    private static HealthSettings s(String url, String tz) {
        HealthSettings h = new HealthSettings();
        h.setWebhookUrl(url);
        h.setTimeZone(tz);
        return h;
    }

    private static void rejected(HealthSettings h) {
        try {
            SettingsValidator.validate(h);
            fail("expected rejection");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void absentWebhookKeepsAndEmptyClearsBothValid() {
        SettingsValidator.validate(s(null, null));
        SettingsValidator.validate(s("", null));
    }

    @Test
    public void httpsWebhookIsOk() {
        SettingsValidator.validate(s("https://hooks.slack.com/services/x", null));
    }

    @Test
    public void nonHttpsWebhookIsRejected() {
        rejected(s("http://x", null));
        rejected(s("ftp://x", null));
        rejected(s("https://", null));
        rejected(s("not a url", null));
        rejected(s("https://user:pw@hooks.slack.com/x", null));
    }

    @Test
    public void webhookOver1024CharsIsRejected() {
        StringBuilder ok = new StringBuilder("https://x.example/");
        while (ok.length() < 1024) {
            ok.append('a');
        }
        SettingsValidator.validate(s(ok.toString(), null));
        rejected(s(ok + "a", null));
    }

    @Test
    public void timeZoneMustBeARegionId() {
        SettingsValidator.validate(s(null, "Europe/Sarajevo"));
        rejected(s(null, "Mars/Base"));
        rejected(s(null, ""));
        rejected(s(null, "+02:00"));
    }

    @Test
    public void internalAddressesAreRejected() {
        for (String host : new String[]{"localhost", "foo.localhost", "127.0.0.1", "127.1.2.3", "10.0.0.5", "172.16.0.1",
                "172.31.255.255", "192.168.1.1", "169.254.169.254", "0.0.0.0", "100.64.0.1", "[::1]", "[::]",
                "[fc00::1]", "[fd12:3456::1]", "[fe80::1]", "[::ffff:127.0.0.1]", "2130706433", "0x7f.0.0.1"}) {
            rejected(s("https://" + host + "/hook", null));
        }
    }

    @Test
    public void publicLiteralsAndNamesAreAccepted() {
        SettingsValidator.validate(s("https://8.8.8.8/hook", null));
        SettingsValidator.validate(s("https://172.32.0.1/hook", null));
        SettingsValidator.validate(s("https://hooks.slack.com/services/x", null));
        SettingsValidator.validate(s("https://[2001:4860:4860::8888]/hook", null));
    }
}
