package com.hmdm.persistence;

import com.hmdm.event.EventService;
import com.hmdm.persistence.domain.Application;
import com.hmdm.persistence.domain.Configuration;
import com.hmdm.persistence.domain.Customer;
import com.hmdm.persistence.domain.User;
import com.hmdm.persistence.domain.UserRole;
import com.hmdm.persistence.mapper.ApplicationMapper;
import com.hmdm.persistence.mapper.ConfigurationMapper;
import com.hmdm.persistence.mapper.CustomerMapper;
import com.hmdm.persistence.mapper.DeviceMapper;
import com.hmdm.security.SecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConfigurationFollowLatestTest {

    private static final int CUSTOMER = 1;
    private static final int CONFIG = 7;
    private static final int APP_PINNED = 10;
    private static final int APP_FOLLOWING = 11;
    private static final int APP_NEW = 12;

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
        user.setCustomerId(CUSTOMER);
        UserRole role = new UserRole();
        user.setUserRole(role);
        SecurityContext.init(user);

        when(applicationMapper.getLatestVersions(anyListOf(Integer.class))).thenReturn(Arrays.asList(
                latest(APP_PINNED, 102), latest(APP_FOLLOWING, 202), latest(APP_NEW, 302)));
    }

    @After
    public void tearDown() {
        SecurityContext.release();
    }

    @Test
    public void savingWithNullFollowLatestKeepsStoredPin() {
        Configuration stored = configuration();
        stored.setFollowLatestMainApp(false);
        stored.setFollowLatestContentApp(false);
        when(configurationMapper.getConfigurationById(CONFIG)).thenReturn(stored);
        when(configurationMapper.getConfigurationFollowRows(CONFIG)).thenReturn(Arrays.asList(
                row(APP_PINNED, 101, false), row(APP_FOLLOWING, 202, true)));

        Configuration incoming = configuration();
        incoming.setFollowLatestMainApp(null);
        incoming.setFollowLatestContentApp(null);
        incoming.setApplications(new ArrayList<>(Arrays.asList(
                row(APP_PINNED, 101, null), row(APP_FOLLOWING, 202, null))));

        dao.updateConfiguration(incoming);

        Map<Integer, Application> saved = savedRows();
        assertEquals(Boolean.FALSE, saved.get(APP_PINNED).isFollowLatest());
        assertEquals(Boolean.TRUE, saved.get(APP_FOLLOWING).isFollowLatest());
        ArgumentCaptor<Configuration> config = ArgumentCaptor.forClass(Configuration.class);
        verify(configurationMapper).updateConfiguration(config.capture());
        assertEquals(Boolean.FALSE, config.getValue().getFollowLatestMainApp());
        assertEquals(Boolean.FALSE, config.getValue().getFollowLatestContentApp());
    }

    @Test
    public void savingWithExplicitFollowLatestOverridesStoredValue() {
        when(configurationMapper.getConfigurationById(CONFIG)).thenReturn(configuration());
        when(configurationMapper.getConfigurationFollowRows(CONFIG)).thenReturn(Arrays.asList(
                row(APP_PINNED, 101, false), row(APP_FOLLOWING, 202, true)));

        Configuration incoming = configuration();
        incoming.setApplications(new ArrayList<>(Arrays.asList(
                row(APP_PINNED, 101, true), row(APP_FOLLOWING, 202, false))));

        dao.updateConfiguration(incoming);

        Map<Integer, Application> saved = savedRows();
        assertEquals(Boolean.TRUE, saved.get(APP_PINNED).isFollowLatest());
        assertEquals(Boolean.FALSE, saved.get(APP_FOLLOWING).isFollowLatest());
    }

    @Test
    public void savingWithNullFollowLatestAndAnotherVersionPinsTheRow() {
        when(configurationMapper.getConfigurationById(CONFIG)).thenReturn(configuration());
        when(configurationMapper.getConfigurationFollowRows(CONFIG)).thenReturn(Arrays.asList(
                row(APP_FOLLOWING, 202, true)));

        Configuration incoming = configuration();
        incoming.setApplications(new ArrayList<>(Collections.singletonList(row(APP_FOLLOWING, 201, null))));

        dao.updateConfiguration(incoming);

        assertEquals(Boolean.FALSE, savedRows().get(APP_FOLLOWING).isFollowLatest());
    }

    @Test
    public void savingNewRowDefaultsToFollow() {
        when(configurationMapper.getConfigurationById(CONFIG)).thenReturn(configuration());
        when(configurationMapper.getConfigurationFollowRows(CONFIG)).thenReturn(Collections.emptyList());

        Configuration incoming = configuration();
        incoming.setApplications(new ArrayList<>(Arrays.asList(
                row(APP_NEW, null, null), row(APP_FOLLOWING, 202, null), row(APP_PINNED, 101, null))));

        dao.updateConfiguration(incoming);

        Map<Integer, Application> saved = savedRows();
        assertEquals(Boolean.TRUE, saved.get(APP_NEW).isFollowLatest());
        assertEquals(Boolean.TRUE, saved.get(APP_FOLLOWING).isFollowLatest());
        assertEquals(Boolean.FALSE, saved.get(APP_PINNED).isFollowLatest());
        ArgumentCaptor<Configuration> config = ArgumentCaptor.forClass(Configuration.class);
        verify(configurationMapper).updateConfiguration(config.capture());
        assertEquals(Boolean.TRUE, config.getValue().getFollowLatestMainApp());
    }

    @Test
    public void savingSyncsMainAndContentFlagsAndMovesNoOtherFollower() {
        when(configurationMapper.getConfigurationById(CONFIG)).thenReturn(configuration());
        when(configurationMapper.getConfigurationFollowRows(CONFIG)).thenReturn(Arrays.asList(row(APP_FOLLOWING, 201, true)));
        Configuration incoming = configuration();
        incoming.setApplications(new ArrayList<>(Arrays.asList(row(APP_NEW, null, null), row(APP_FOLLOWING, 201, null))));

        dao.updateConfiguration(incoming);

        verify(applicationMapper, never()).moveFollowersToLatest(any(), any());
        verify(applicationMapper, never()).moveRowToLatest(anyInt(), anyInt());
        verify(configurationMapper).syncMainAndContentFollowFlags(CONFIG);
    }

    @Test
    public void echoedFollowWithAnotherVersionThatIsNotLatestPinsTheRow() {
        when(configurationMapper.getConfigurationById(CONFIG)).thenReturn(configuration());
        when(configurationMapper.getConfigurationFollowRows(CONFIG)).thenReturn(Arrays.asList(row(APP_FOLLOWING, 202, true)));
        Configuration incoming = configuration();
        incoming.setApplications(new ArrayList<>(Collections.singletonList(row(APP_FOLLOWING, 201, true))));

        dao.updateConfiguration(incoming);

        assertEquals(Boolean.FALSE, savedRows().get(APP_FOLLOWING).isFollowLatest());
        verify(applicationMapper, never()).moveRowToLatest(anyInt(), anyInt());
    }

    @Test
    public void echoedFollowWithTheLatestVersionFollows() {
        when(configurationMapper.getConfigurationById(CONFIG)).thenReturn(configuration());
        when(configurationMapper.getConfigurationFollowRows(CONFIG)).thenReturn(Arrays.asList(row(APP_FOLLOWING, 201, true)));
        Configuration incoming = configuration();
        incoming.setApplications(new ArrayList<>(Collections.singletonList(row(APP_FOLLOWING, 202, true))));

        dao.updateConfiguration(incoming);

        assertEquals(Boolean.TRUE, savedRows().get(APP_FOLLOWING).isFollowLatest());
    }

    @Test
    public void turningFollowOnFollowsAndMovesTheRowEvenWithAnOlderVersion() {
        when(configurationMapper.getConfigurationById(CONFIG)).thenReturn(configuration());
        when(configurationMapper.getConfigurationFollowRows(CONFIG)).thenReturn(Arrays.asList(row(APP_PINNED, 100, false)));
        Configuration incoming = configuration();
        incoming.setApplications(new ArrayList<>(Collections.singletonList(row(APP_PINNED, 101, true))));

        dao.updateConfiguration(incoming);

        assertEquals(Boolean.TRUE, savedRows().get(APP_PINNED).isFollowLatest());
        InOrder order = inOrder(configurationMapper, applicationMapper);
        order.verify(configurationMapper).insertConfigurationApplications(eq(CONFIG), anyListOf(Application.class));
        order.verify(applicationMapper).moveRowToLatest(CONFIG, APP_PINNED);
        order.verify(applicationMapper).recheckConfigurationMainApplication(CONFIG);
    }

    @Test
    public void saveLocksEveryApplicationInAscendingOrderBeforeTouchingTheConfiguration() {
        when(configurationMapper.getConfigurationById(CONFIG)).thenReturn(configuration());
        when(configurationMapper.getConfigurationFollowRows(CONFIG)).thenReturn(Arrays.asList(row(APP_FOLLOWING, 202, true)));
        Configuration incoming = configuration();
        incoming.setApplications(new ArrayList<>(Arrays.asList(row(APP_NEW, null, null), row(APP_PINNED, 101, false))));

        dao.updateConfiguration(incoming);

        InOrder order = inOrder(applicationMapper, configurationMapper);
        order.verify(applicationMapper).lockApplication(APP_PINNED);
        order.verify(applicationMapper).lockApplication(APP_FOLLOWING);
        order.verify(applicationMapper).lockApplication(APP_NEW);
        order.verify(configurationMapper).updateConfiguration(any(Configuration.class));
        order.verify(configurationMapper).removeConfigurationApplicationsById(CONFIG);
    }

    @Test
    public void newConfigurationRowsDefaultToFollowAndKeepExplicitFlags() {
        Configuration incoming = configuration();
        incoming.setId(null);
        incoming.setFollowLatestMainApp(null);
        incoming.setApplications(new ArrayList<>(Arrays.asList(
                row(APP_NEW, null, null), row(APP_PINNED, 102, false), row(APP_FOLLOWING, 201, true))));

        dao.insertConfiguration(incoming);

        Map<Integer, Application> saved = savedRows(null);
        assertEquals(Boolean.TRUE, saved.get(APP_NEW).isFollowLatest());
        assertEquals(Boolean.FALSE, saved.get(APP_PINNED).isFollowLatest());
        assertEquals(Boolean.TRUE, saved.get(APP_FOLLOWING).isFollowLatest());
        assertEquals(Boolean.TRUE, incoming.getFollowLatestMainApp());
        InOrder order = inOrder(applicationMapper, configurationMapper);
        order.verify(applicationMapper).lockApplication(APP_PINNED);
        order.verify(applicationMapper).lockApplication(APP_FOLLOWING);
        order.verify(applicationMapper).lockApplication(APP_NEW);
        order.verify(configurationMapper).insertConfiguration(any(Configuration.class));
        verify(applicationMapper).moveRowToLatest(any(), eq(APP_FOLLOWING));
        verify(applicationMapper, never()).moveRowToLatest(any(), eq(APP_PINNED));
    }

    @Test
    public void copyConfigurationKeepsFlags() {
        Configuration source = configuration();
        source.setFollowLatestMainApp(false);
        source.setFollowLatestContentApp(true);
        Application pinned = row(APP_PINNED, 101, false);
        pinned.setCommonApplication(true);
        Application following = row(APP_FOLLOWING, 202, true);
        following.setCommonApplication(true);
        when(configurationMapper.getConfigurationById(CONFIG)).thenReturn(source);
        when(configurationMapper.getPlainConfigurationApplications(eq(CUSTOMER), anyString(), eq(CONFIG)))
                .thenReturn(new ArrayList<>(Arrays.asList(pinned, following)));
        CustomerDAO customerDAO = new CustomerDAO(mock(CustomerMapper.class), configurationMapper, applicationMapper,
                mock(DeviceMapper.class), mock(UserDAO.class), mock(CommonDAO.class), mock(ApplicationSettingDAO.class),
                "/tmp", 2, mock(EventService.class));
        Customer target = new Customer();
        target.setId(5);

        customerDAO.copyConfigurationForCustomer(target, CUSTOMER, CONFIG);

        InOrder order = inOrder(applicationMapper, configurationMapper);
        order.verify(applicationMapper).lockApplication(APP_PINNED);
        order.verify(applicationMapper).lockApplication(APP_FOLLOWING);
        order.verify(configurationMapper).insertConfiguration(any(Configuration.class));

        ArgumentCaptor<Configuration> config = ArgumentCaptor.forClass(Configuration.class);
        verify(configurationMapper).insertConfiguration(config.capture());
        assertEquals(Boolean.FALSE, config.getValue().getFollowLatestMainApp());
        assertEquals(Boolean.TRUE, config.getValue().getFollowLatestContentApp());
        Map<Integer, Application> saved = savedRows(null);
        assertEquals(Boolean.FALSE, saved.get(APP_PINNED).isFollowLatest());
        assertEquals(Boolean.TRUE, saved.get(APP_FOLLOWING).isFollowLatest());
    }

    @Test
    public void copyThroughConfigurationInsertKeepsFlags() {
        Configuration copy = configuration().newCopy();
        copy.setFollowLatestMainApp(false);
        copy.setApplications(new ArrayList<>(Arrays.asList(row(APP_PINNED, 101, false), row(APP_FOLLOWING, 202, true))));

        dao.insertConfiguration(copy);

        Map<Integer, Application> saved = savedRows(null);
        assertEquals(Boolean.FALSE, saved.get(APP_PINNED).isFollowLatest());
        assertEquals(Boolean.TRUE, saved.get(APP_FOLLOWING).isFollowLatest());
        assertEquals(Boolean.FALSE, copy.getFollowLatestMainApp());
    }

    @Test
    public void linkDialogFollowsOnlyWhenLatestChosen() {
        assertTrue(FollowLatestRules.followForLinkedVersion(5, 5));
        assertFalse(FollowLatestRules.followForLinkedVersion(4, 5));
        assertFalse(FollowLatestRules.followForLinkedVersion(6, 5));
        assertFalse(FollowLatestRules.followForLinkedVersion(5, null));
    }

    private Map<Integer, Application> savedRows() {
        return savedRows(CONFIG);
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Application> savedRows(Integer configId) {
        ArgumentCaptor<List> rows = ArgumentCaptor.forClass(List.class);
        if (configId == null) {
            verify(configurationMapper).insertConfigurationApplications(any(), rows.capture());
        } else {
            verify(configurationMapper).insertConfigurationApplications(eq(configId), rows.capture());
        }
        return ((List<Application>) rows.getValue()).stream()
                .collect(Collectors.toMap(Application::getId, Function.identity()));
    }

    private static Configuration configuration() {
        Configuration c = new Configuration();
        c.setId(CONFIG);
        c.setCustomerId(CUSTOMER);
        c.setApplications(new ArrayList<>());
        return c;
    }

    private static Application row(int appId, Integer versionId, Boolean followLatest) {
        Application a = new Application();
        a.setId(appId);
        a.setUsedVersionId(versionId);
        a.setAction(1);
        a.setFollowLatest(followLatest);
        return a;
    }

    private static Application latest(int appId, int versionId) {
        Application a = new Application();
        a.setId(appId);
        a.setLatestVersion(versionId);
        return a;
    }
}
