package com.hmdm.plugins.locker24health.persistence;

import com.hmdm.plugins.locker24health.persistence.mapper.HealthMapper;
import org.apache.ibatis.session.Configuration;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HealthMapperTimeoutTest {

    @Test
    public void everyJobStatementHasATimeout() {
        Configuration configuration = new Configuration();
        configuration.addMapper(HealthMapper.class);
        for (String id : new String[]{"selectEvaluationRows", "selectOpenAlerts", "selectSettings", "updateEvaluation",
                "insertAlert", "escalateAlert", "touchAlert", "clearAlert", "deleteHistoryBefore",
                "deleteClearedAlertsBefore", "selectFleet", "selectDeviceAccess", "selectHistoryPoints",
                "selectOfflineGaps", "selectReceivedBounds", "selectLatestReceived", "selectLastReceivedBefore", "selectCustomerSettings", "upsertCustomerSettings"}) {
            Integer timeout = configuration.getMappedStatement(HealthMapper.class.getName() + "." + id).getTimeout();
            assertEquals(id, Integer.valueOf(HealthMapper.TIMEOUT_SECONDS), timeout);
        }
        assertEquals(30, HealthMapper.TIMEOUT_SECONDS);
    }
}
