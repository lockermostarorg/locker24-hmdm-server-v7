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
}
