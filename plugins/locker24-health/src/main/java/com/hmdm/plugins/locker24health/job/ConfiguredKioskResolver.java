package com.hmdm.plugins.locker24health.job;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.Application;
import com.hmdm.persistence.domain.ApplicationVersion;
import com.hmdm.persistence.domain.Configuration;
import com.hmdm.persistence.mapper.ConfigurationMapper;
import com.hmdm.plugins.locker24health.status.ConfiguredKiosk;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Singleton
public class ConfiguredKioskResolver {

    private static final int ACTION_INSTALL = 1;
    private static final DateTimeFormatter HOURS_MINUTES = DateTimeFormatter.ofPattern("H:mm");

    private final UnsecureDAO unsecureDAO;
    private final ConfigurationMapper configurationMapper;

    @Inject
    public ConfiguredKioskResolver(UnsecureDAO unsecureDAO, ConfigurationMapper configurationMapper) {
        this.unsecureDAO = unsecureDAO;
        this.configurationMapper = configurationMapper;
    }

    public ConfiguredKiosk resolve(Integer configurationId) {
        if (configurationId == null) {
            return null;
        }
        Configuration configuration = unsecureDAO.getConfigurationById(configurationId);
        if (configuration == null || configuration.getContentAppId() == null) {
            return null;
        }
        ApplicationVersion content = unsecureDAO.findApplicationVersionById(configuration.getContentAppId());
        if (content == null || content.getApplicationId() == null) {
            return null;
        }
        Application app = unsecureDAO.findApplicationById(content.getApplicationId());
        if (app == null || app.getPkg() == null) {
            return null;
        }
        return build(app.getPkg(), configurationMapper.getPlainConfigurationAppsOptimized(configurationId),
                configuration.isScheduleAppUpdate(), configuration.getAppUpdateFrom(), configuration.getAppUpdateTo());
    }

    static ConfiguredKiosk build(String pkg, List<Application> apps, boolean scheduleAppUpdate, String from, String to) {
        String version = null;
        if (apps != null) {
            for (Application a : apps) {
                if (pkg.equals(a.getPkg()) && a.getAction() == ACTION_INSTALL) {
                    version = a.isSkipVersion() ? null : a.getVersion();
                    break;
                }
            }
        }
        return new ConfiguredKiosk(pkg, version, scheduleAppUpdate, parseTime(from), parseTime(to));
    }

    static LocalTime parseTime(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        try {
            return LocalTime.parse(t, HOURS_MINUTES);
        } catch (DateTimeParseException e) {
            try {
                return LocalTime.parse(t);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }
}
