package com.hmdm.plugins.locker24health;

import com.google.inject.Module;
import com.hmdm.plugin.PluginConfiguration;
import com.hmdm.plugin.PluginTaskModule;
import com.hmdm.plugins.locker24health.guice.module.HealthLiquibaseModule;
import com.hmdm.plugins.locker24health.guice.module.HealthPersistenceModule;
import com.hmdm.plugins.locker24health.guice.module.HealthRestModule;
import com.hmdm.plugins.locker24health.guice.module.HealthTaskModule;

import jakarta.servlet.ServletContext;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class HealthPluginConfigurationImpl implements PluginConfiguration {

    public static final String PLUGIN_ID = "locker24-health";
    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Sarajevo");

    public HealthPluginConfigurationImpl() {
    }

    @Override
    public String getPluginId() {
        return PLUGIN_ID;
    }

    @Override
    public String getRootPackage() {
        return "com.hmdm.plugins.locker24health";
    }

    @Override
    public List<Module> getPluginModules(ServletContext context) {
        List<Module> modules = new ArrayList<>();
        modules.add(new HealthLiquibaseModule(context));
        modules.add(new HealthPersistenceModule(context));
        modules.add(new HealthRestModule());
        return modules;
    }

    @Override
    public Optional<List<Class<? extends PluginTaskModule>>> getTaskModules(ServletContext context) {
        List<Class<? extends PluginTaskModule>> modules = new ArrayList<>();
        modules.add(HealthTaskModule.class);
        return Optional.of(modules);
    }
}
