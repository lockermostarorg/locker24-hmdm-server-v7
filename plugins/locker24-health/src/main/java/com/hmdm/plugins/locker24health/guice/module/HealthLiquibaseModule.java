package com.hmdm.plugins.locker24health.guice.module;

import com.hmdm.guice.module.AbstractLiquibaseModule;

import jakarta.servlet.ServletContext;

public class HealthLiquibaseModule extends AbstractLiquibaseModule {

    public HealthLiquibaseModule(ServletContext context) {
        super(context);
    }

    @Override
    protected String getChangeLogResourcePath() {
        return "classpath:liquibase/locker24-health.changelog.xml";
    }
}
