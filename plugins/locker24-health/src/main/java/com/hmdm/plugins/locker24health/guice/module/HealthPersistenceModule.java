package com.hmdm.plugins.locker24health.guice.module;

import com.hmdm.guice.module.AbstractPersistenceModule;

import jakarta.servlet.ServletContext;

public class HealthPersistenceModule extends AbstractPersistenceModule {

    public HealthPersistenceModule(ServletContext context) {
        super(context);
    }

    @Override
    protected String getMapperPackageName() {
        return "com.hmdm.plugins.locker24health.persistence.mapper";
    }

    @Override
    protected String getDomainObjectsPackageName() {
        return "com.hmdm.plugins.locker24health.persistence.domain";
    }
}
