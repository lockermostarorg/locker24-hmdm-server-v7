package com.hmdm.persistence;

import com.hmdm.event.EventService;
import com.hmdm.persistence.domain.Configuration;
import com.hmdm.persistence.domain.User;
import com.hmdm.persistence.domain.UserRole;
import com.hmdm.persistence.mapper.ApplicationMapper;
import com.hmdm.persistence.mapper.ConfigurationMapper;
import com.hmdm.security.SecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConfigurationUpgradeGuardTest {

    private static final int CONFIG = 5;
    private static final int APP = 10;

    private ConfigurationMapper configurationMapper;
    private ApplicationMapper applicationMapper;
    private ConfigurationDAO dao;

    @Before
    public void setUp() {
        configurationMapper = mock(ConfigurationMapper.class);
        applicationMapper = mock(ApplicationMapper.class);
        dao = new ConfigurationDAO(configurationMapper, applicationMapper, mock(ApplicationSettingDAO.class),
                mock(ConfigurationFileDAO.class), "http://localhost", mock(EventService.class));
        User user = new User();
        user.setId(3);
        user.setCustomerId(1);
        user.setUserRole(new UserRole());
        SecurityContext.init(user);
        Configuration configuration = new Configuration();
        configuration.setId(CONFIG);
        configuration.setCustomerId(1);
        when(configurationMapper.getConfigurationById(CONFIG)).thenReturn(configuration);
    }

    @After
    public void tearDown() {
        SecurityContext.release();
    }

    private void counts(int behind, int atOrAbove, int onLatest) {
        when(configurationMapper.countInstallRowsBehindLatest(CONFIG, APP)).thenReturn(behind);
        when(configurationMapper.countInstallRowsAtOrAboveLatest(CONFIG, APP)).thenReturn(atOrAbove);
        when(configurationMapper.countInstallRowsOnLatest(CONFIG, APP)).thenReturn(onLatest);
    }

    @Test
    public void aRowOnlyBehindLatestIsUpgradedAfterTheApplicationLock() {
        counts(1, 0, 0);

        assertTrue(dao.upgradeConfigurationApplication(CONFIG, APP));

        InOrder order = inOrder(applicationMapper, configurationMapper);
        order.verify(applicationMapper).lockApplication(APP);
        order.verify(configurationMapper).upgradeConfigurationApplication(CONFIG, APP);
    }

    @Test
    public void aRowOnLatestIsNeverPassedToTheFunctionWhateverMainAndContentAre() {
        counts(0, 1, 1);
        when(configurationMapper.upgradeMainAndContentToLatest(CONFIG, APP)).thenReturn(1);

        assertTrue(dao.upgradeConfigurationApplication(CONFIG, APP));

        verify(configurationMapper, never()).upgradeConfigurationApplication(CONFIG, APP);
        verify(configurationMapper).upgradeMainAndContentToLatest(CONFIG, APP);
    }

    @Test
    public void aBehindRowNextToARowOnLatestIsNotPassedToTheFunction() {
        counts(1, 1, 1);

        dao.upgradeConfigurationApplication(CONFIG, APP);

        verify(configurationMapper, never()).upgradeConfigurationApplication(CONFIG, APP);
    }

    @Test
    public void aCanaryAheadOfLatestIsUntouched() {
        counts(0, 1, 0);

        assertFalse(dao.upgradeConfigurationApplication(CONFIG, APP));

        verify(configurationMapper, never()).upgradeConfigurationApplication(CONFIG, APP);
        verify(configurationMapper, never()).upgradeMainAndContentToLatest(CONFIG, APP);
    }

    @Test
    public void aMainAppBehindWithoutAnInstallRowDoesNotTriggerTheFunction() {
        counts(0, 0, 0);

        assertFalse(dao.upgradeConfigurationApplication(CONFIG, APP));

        verify(configurationMapper, never()).upgradeConfigurationApplication(CONFIG, APP);
    }
}
