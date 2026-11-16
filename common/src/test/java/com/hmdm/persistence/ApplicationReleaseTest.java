package com.hmdm.persistence;

import com.hmdm.persistence.domain.Application;
import com.hmdm.persistence.domain.ApplicationVersion;
import com.hmdm.persistence.domain.User;
import com.hmdm.persistence.domain.UserRole;
import com.hmdm.persistence.mapper.ApplicationMapper;
import com.hmdm.rest.json.ApplicationConfigurationLink;
import com.hmdm.rest.json.ApplicationVersionConfigurationLink;
import com.hmdm.rest.json.LinkConfigurationsToAppRequest;
import com.hmdm.rest.json.LinkConfigurationsToAppVersionRequest;
import com.hmdm.security.SecurityContext;
import com.hmdm.util.APKFileAnalyzer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ApplicationReleaseTest {

    private static final int APP = 10;
    private static final int LATEST = 101;
    private static final int NEW_VERSION = 102;
    private static final int OLDER = 100;
    private static final int USER = 3;

    private ApplicationMapper mapper;
    private ApplicationDAO dao;

    @Before
    public void setUp() {
        mapper = mock(ApplicationMapper.class);
        dao = new ApplicationDAO(mapper, mock(CustomerDAO.class), "/tmp", "http://localhost", "",
                mock(APKFileAnalyzer.class));

        User user = new User();
        user.setId(USER);
        user.setCustomerId(1);
        user.setUserRole(new UserRole());
        SecurityContext.init(user);

        when(mapper.findById(APP)).thenReturn(application(LATEST));
        when(mapper.getDuplicateVersionForApp(eq(APP), anyInt(), anyString())).thenReturn(0);
        doAnswer(invocation -> {
            ((ApplicationVersion) invocation.getArguments()[0]).setId(NEW_VERSION);
            return 1;
        }).when(mapper).insertApplicationVersion(any(ApplicationVersion.class));
        when(mapper.moveFollowersToLatest(APP, null)).thenReturn(Arrays.asList(7, 8));
    }

    @After
    public void tearDown() {
        SecurityContext.release();
    }

    @Test
    public void uploadOnScheduleReleasesAndMovesFollowersUnderTheApplicationLock() {
        List<Integer> moved = dao.insertApplicationVersionWithRelease(newVersion(), "schedule", USER);

        assertEquals(Arrays.asList(7, 8), moved);
        ApplicationVersion inserted = insertedVersion();
        assertEquals("released", inserted.getReleaseState());
        assertEquals("schedule", inserted.getReleaseMode());
        assertEquals(Integer.valueOf(USER), inserted.getReleasedBy());
        assertNotNull(inserted.getReleasedAt());
        InOrder order = inOrder(mapper);
        order.verify(mapper).lockApplication(APP);
        order.verify(mapper).insertApplicationVersion(any(ApplicationVersion.class));
        order.verify(mapper).recalculateLatestVersion(APP);
        order.verify(mapper).moveFollowersToLatest(APP, null);
    }

    @Test
    public void uploadImmediatelyRecordsTheMode() {
        dao.insertApplicationVersionWithRelease(newVersion(), "immediate", USER);

        assertEquals("immediate", insertedVersion().getReleaseMode());
    }

    @Test
    public void uploadOnHoldInsertsAHeldVersionAndMovesNothing() {
        List<Integer> moved = dao.insertApplicationVersionWithRelease(newVersion(), "hold", USER);

        assertTrue(moved.isEmpty());
        ApplicationVersion inserted = insertedVersion();
        assertEquals("held", inserted.getReleaseState());
        assertNull(inserted.getReleasedAt());
        verify(mapper, never()).moveFollowersToLatest(any(), any());
    }

    @Test
    public void firstVersionOfAnAppIsAlwaysReleased() {
        when(mapper.findById(APP)).thenReturn(application(null));

        dao.insertApplicationVersionWithRelease(newVersion(), "hold", USER);

        assertEquals("released", insertedVersion().getReleaseState());
        verify(mapper).moveFollowersToLatest(APP, null);
    }

    @Test
    public void missingModeMeansSchedule() {
        dao.insertApplicationVersionWithRelease(newVersion(), null, USER);

        assertEquals("schedule", insertedVersion().getReleaseMode());
    }

    @Test
    public void legacyInsertIsAnUploadOnSchedule() {
        int id = dao.insertApplicationVersion(newVersion());

        assertEquals(NEW_VERSION, id);
        assertEquals("schedule", insertedVersion().getReleaseMode());
        verify(mapper).moveFollowersToLatest(APP, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownModeIsRejected() {
        dao.insertApplicationVersionWithRelease(newVersion(), "later", USER);
    }

    @Test
    public void releasingAHeldVersionMarksItAndMovesFollowers() {
        when(mapper.findVersionById(NEW_VERSION)).thenReturn(version(NEW_VERSION, "held"));

        List<Integer> moved = dao.releaseVersion(NEW_VERSION, "immediate", USER);

        assertEquals(Arrays.asList(7, 8), moved);
        InOrder order = inOrder(mapper);
        order.verify(mapper).lockApplication(APP);
        order.verify(mapper).releaseApplicationVersion(eq(NEW_VERSION), eq("immediate"), eq(USER), anyLong());
        order.verify(mapper).recalculateLatestVersion(APP);
        order.verify(mapper).moveFollowersToLatest(APP, null);
    }

    @Test
    public void releasingAnAlreadyReleasedVersionKeepsItsRecordAndStillReconciles() {
        when(mapper.findVersionById(NEW_VERSION)).thenReturn(version(NEW_VERSION, "released"));
        when(mapper.moveFollowersToLatest(APP, null)).thenReturn(Collections.emptyList());

        List<Integer> moved = dao.releaseVersion(NEW_VERSION, "schedule", USER);

        assertTrue(moved.isEmpty());
        verify(mapper, never()).releaseApplicationVersion(anyInt(), anyString(), anyInt(), anyLong());
        verify(mapper).moveFollowersToLatest(APP, null);
    }

    @Test
    public void releaseRejectsHold() {
        when(mapper.findVersionById(NEW_VERSION)).thenReturn(version(NEW_VERSION, "held"));
        try {
            dao.releaseVersion(NEW_VERSION, "hold", USER);
            fail();
        } catch (IllegalArgumentException expected) {
            verify(mapper, never()).moveFollowersToLatest(any(), any());
        }
    }

    @Test
    public void moveFollowersLocksTheApplicationFirst() {
        List<Integer> moved = dao.moveFollowersToLatest(APP);

        assertEquals(Arrays.asList(7, 8), moved);
        InOrder order = inOrder(mapper);
        order.verify(mapper).lockApplication(APP);
        order.verify(mapper).moveFollowersToLatest(APP, null);
    }

    @Test
    public void editingAVersionNeverMovesConfigurations() {
        ApplicationVersion stored = version(LATEST, "released");
        when(mapper.findVersionById(LATEST)).thenReturn(stored);
        ApplicationVersion edited = version(LATEST, null);
        edited.setVersion("9.9");

        dao.updateApplicationVersion(edited);

        verify(mapper, never()).moveFollowersToLatest(any(), any());
    }

    @Test
    public void deletingTheLastReleasedVersionIsRefusedWhileOtherVersionsExist() {
        ApplicationVersion last = version(LATEST, "released");
        when(mapper.findVersionById(LATEST)).thenReturn(last);
        when(mapper.countReleasedVersions(APP)).thenReturn(1);
        when(mapper.countVersions(APP)).thenReturn(2);

        try {
            dao.removeApplicationVersionById(LATEST);
            fail();
        } catch (LastReleasedVersionException expected) {
            verify(mapper, never()).removeApplicationVersionById(any());
        }
    }

    @Test
    public void deletingAHeldVersionIsAllowed() {
        when(mapper.findVersionById(NEW_VERSION)).thenReturn(version(NEW_VERSION, "held"));
        when(mapper.countReleasedVersions(APP)).thenReturn(1);
        when(mapper.countVersions(APP)).thenReturn(2);

        dao.removeApplicationVersionById(NEW_VERSION);

        verify(mapper).removeApplicationVersionById(NEW_VERSION);
        verify(mapper, never()).moveFollowersToLatest(any(), any());
    }

    @Test
    public void linkingTheLatestVersionFollowsAndAnOlderVersionPins() {
        when(mapper.findVersionById(LATEST)).thenReturn(version(LATEST, "released"));
        when(mapper.findVersionById(OLDER)).thenReturn(version(OLDER, "released"));
        User user = SecurityContext.get().getCurrentUser().get();

        dao.updateApplicationVersionConfigurations(versionLinkRequest(LATEST), user);
        dao.updateApplicationVersionConfigurations(versionLinkRequest(OLDER), user);

        verify(mapper).insertApplicationVersionConfigurations(eq(APP), eq(LATEST), eq(true), anyListOf(ApplicationVersionConfigurationLink.class));
        verify(mapper).insertApplicationVersionConfigurations(eq(APP), eq(OLDER), eq(false), anyListOf(ApplicationVersionConfigurationLink.class));
    }

    @Test
    public void linkingTheAppLinksItsLatestVersionAndFollows() {
        LinkConfigurationsToAppRequest request = new LinkConfigurationsToAppRequest();
        request.setApplicationId(APP);
        ApplicationConfigurationLink link = new ApplicationConfigurationLink();
        link.setApplicationId(APP);
        link.setConfigurationId(7);
        link.setAction(1);
        request.setConfigurations(Collections.singletonList(link));

        dao.updateApplicationConfigurations(request);

        InOrder order = inOrder(mapper);
        order.verify(mapper).lockApplication(APP);
        order.verify(mapper).insertApplicationConfigurations(eq(APP), eq(LATEST), eq(true), anyListOf(ApplicationConfigurationLink.class));
        verify(mapper).syncMainAndContentFollowFlagsForCustomer(1);
    }

    private static LinkConfigurationsToAppVersionRequest versionLinkRequest(int versionId) {
        LinkConfigurationsToAppVersionRequest request = new LinkConfigurationsToAppVersionRequest();
        request.setApplicationVersionId(versionId);
        ApplicationVersionConfigurationLink link = new ApplicationVersionConfigurationLink();
        link.setApplicationId(APP);
        link.setConfigurationId(7);
        link.setApplicationVersionId(versionId);
        link.setAction(1);
        request.setConfigurations(Collections.singletonList(link));
        return request;
    }

    private ApplicationVersion insertedVersion() {
        ArgumentCaptor<ApplicationVersion> captor = ArgumentCaptor.forClass(ApplicationVersion.class);
        verify(mapper).insertApplicationVersion(captor.capture());
        return captor.getValue();
    }

    private static Application application(Integer latest) {
        Application a = new Application();
        a.setId(APP);
        a.setCustomerId(1);
        a.setPkg("com.example.a");
        a.setLatestVersion(latest);
        return a;
    }

    private static ApplicationVersion newVersion() {
        ApplicationVersion v = new ApplicationVersion();
        v.setApplicationId(APP);
        v.setVersion("2.0");
        v.setUrl("http://localhost/a.apk");
        return v;
    }

    private static ApplicationVersion version(int id, String state) {
        ApplicationVersion v = newVersion();
        v.setId(id);
        v.setReleaseState(state);
        return v;
    }
}
