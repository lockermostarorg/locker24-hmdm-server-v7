package com.hmdm.plugins.locker24health.guice.module;

import com.hmdm.plugin.guice.module.AbstractPluginRestModule;
import com.hmdm.plugins.locker24health.HealthPluginConfigurationImpl;
import com.hmdm.plugins.locker24health.rest.HealthLicenseResource;
import com.hmdm.plugins.locker24health.rest.HealthPublicResource;

import java.util.Arrays;
import java.util.List;

public class HealthRestModule extends AbstractPluginRestModule {

    public HealthRestModule() {
    }

    @Override
    protected List<String> getProtectedResources() {
        return Arrays.asList(
                "/rest/private/plugin-locker24-health/*"
        );
    }

    @Override
    protected List<String> getPublicResources() {
        return Arrays.asList(
                "/rest/public/plugin-locker24-health/*"
        );
    }

    @Override
    protected String getPluginId() {
        return HealthPluginConfigurationImpl.PLUGIN_ID;
    }

    @Override
    protected void configureServlets() {
        super.configureServlets();
        this.bind(HealthLicenseResource.class);
        this.bind(HealthPublicResource.class);
    }
}
