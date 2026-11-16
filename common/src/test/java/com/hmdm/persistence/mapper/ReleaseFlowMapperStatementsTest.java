package com.hmdm.persistence.mapper;

import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReleaseFlowMapperStatementsTest {

    private Configuration configuration;

    @Before
    public void setUp() {
        configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAliases("com.hmdm.persistence.domain");
        configuration.addMapper(ApplicationMapper.class);
        configuration.addMapper(ConfigurationMapper.class);
    }

    private BoundSql bound(String id, Object... keyValues) {
        MapperMethod.ParamMap<Object> params = new MapperMethod.ParamMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        MappedStatement statement = configuration.getMappedStatement(id);
        return statement.getBoundSql(params);
    }

    @Test
    public void moveCallsTheStoredFunctionWithBothParameters() {
        BoundSql sql = bound("com.hmdm.persistence.mapper.ApplicationMapper.moveFollowersToLatest",
                "appId", 5, "configId", null);

        assertTrue(sql.getSql(), sql.getSql().contains("mdm_move_followers_to_latest(?, ?)"));
        assertEquals(2, sql.getParameterMappings().size());
        assertTrue(configuration.getMappedStatement("com.hmdm.persistence.mapper.ApplicationMapper.moveFollowersToLatest")
                .isFlushCacheRequired());
    }

    @Test
    public void lockTakesARowLockAndIsNeverServedFromCache() {
        MappedStatement statement = configuration.getMappedStatement("com.hmdm.persistence.mapper.ApplicationMapper.lockApplication");

        assertTrue(statement.getBoundSql(Collections.singletonMap("id", 5)).getSql().endsWith("FOR NO KEY UPDATE"));
        assertTrue(statement.isFlushCacheRequired());
    }

    @Test
    public void latestVersionsExpandsTheIdList() {
        BoundSql sql = bound("com.hmdm.persistence.mapper.ApplicationMapper.getLatestVersions",
                "ids", Arrays.asList(1, 2, 3));

        assertTrue(sql.getSql(), sql.getSql().replace(" ", "").contains("IN(?,?,?)"));
        assertEquals(3, sql.getParameterMappings().size());
    }

    @Test
    public void linkInsertsTakeTheFollowFlagAsAParameter() {
        BoundSql sql = bound("com.hmdm.persistence.mapper.ApplicationMapper.insertApplicationVersionConfigurations",
                "applicationId", 1, "versionId", 2, "followLatest", true,
                "confs", Collections.singletonList(new com.hmdm.rest.json.ApplicationVersionConfigurationLink()));

        assertTrue(sql.getParameterMappings().stream().anyMatch(m -> m.getProperty().equals("followLatest")));
    }

    @Test
    public void versionLookupsParse() {
        assertTrue(bound("com.hmdm.persistence.mapper.ApplicationMapper.getApplicationOfVersion", "id", 1)
                .getSql().contains("latestVersion"));
        assertTrue(bound("com.hmdm.persistence.mapper.ApplicationMapper.getVersionConfigurationRows", "id", 1)
                .getSql().contains("applicationVersionId"));
    }

    @Test
    public void configurationStatementsParse() {
        assertTrue(bound("com.hmdm.persistence.mapper.ConfigurationMapper.getConfigurationFollowRows", "id", 1)
                .getSql().contains("followLatest"));
        assertTrue(bound("com.hmdm.persistence.mapper.ConfigurationMapper.syncMainAndContentFollowFlags", "id", 1)
                .getSql().contains("bool_or"));
    }
}
