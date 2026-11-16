package com.hmdm.plugins.locker24health.settings;

import com.hmdm.plugins.locker24health.rest.json.HealthSettings;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZoneId;

public final class SettingsValidator {

    public static final int MAX_WEBHOOK_LENGTH = 1024;
    public static final int MAX_TIME_ZONE_LENGTH = 64;

    private SettingsValidator() {
    }

    public static void validate(HealthSettings settings) {
        String url = settings.getWebhookUrl();
        if (url != null && !url.isEmpty()) {
            validateWebhook(url);
        }
        String tz = settings.getTimeZone();
        if (tz != null && (tz.isEmpty() || tz.length() > MAX_TIME_ZONE_LENGTH || !ZoneId.getAvailableZoneIds().contains(tz))) {
            throw new IllegalArgumentException("unknown time zone");
        }
    }

    private static void validateWebhook(String url) {
        if (url.length() > MAX_WEBHOOK_LENGTH) {
            throw new IllegalArgumentException("webhook too long");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("invalid webhook");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isEmpty()
                || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException("webhook must be an https url");
        }
    }
}
