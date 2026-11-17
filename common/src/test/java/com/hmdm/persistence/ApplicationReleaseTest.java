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

import java.io.File;
import java.nio.file.Files;
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
    private CustomerDAO customerDAO;
    private ApplicationDAO dao;

    @Before
    public void setUp() {
        mapper = mock(ApplicationMapper.class);
        customerDAO = mock(CustomerDAO.class);
        dao = new ApplicationDAO(mapper, customerDAO, "/tmp", "http://localhost", "",
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
        assertEquals("schedule", insertedVersion().getReleaseMode());
        verify(mapper).moveFollowersToLatest(APP, null);
    }

    @Test
    public void uploadToAnotherCustomersApplicationIsRefusedBeforeAnythingMoves() {
        Application foreign = application(LATEST);
        foreign.setCustomerId(2);
        when(mapper.findById(APP)).thenReturn(foreign);

        try {
            dao.insertApplicationVersionWithRelease(newVersion(), "immediate", USER);
            fail();
        } catch (com.hmdm.security.SecurityException expected) {
            verify(mapper, never()).lockApplication(anyInt());
            verify(mapper, never()).insertApplicationVersion(any(ApplicationVersion.class));
            verify(mapper, never()).moveFollowersToLatest(any(), any());
        }
    }

    @Test
    public void uploadToAnotherCustomersApplicationIsRefusedForAnUnknownApplicationToo() {
        when(mapper.findById(APP)).thenReturn(null);

        try {
            dao.insertApplicationVersionWithRelease(newVersion(), "schedule", USER);
            fail();
        } catch (DAOException expected) {
            verify(mapper, never()).insertApplicationVersion(any(ApplicationVersion.class));
        }
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
        InOrder order = inOrder(mapper);
        order.verify(mapper).lockApplication(APP);
        order.verify(mapper).updateApplicationVersion(any(ApplicationVersion.class));
        order.verify(mapper).recalculateLatestVersion(APP);
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
    public void versionLinkDialogKeepsStoredFlagsOfAlreadyLinkedConfigurations() {
        when(mapper.findVersionById(LATEST)).thenReturn(version(LATEST, "released"));
        when(mapper.getVersionConfigurationRows(LATEST)).thenReturn(Arrays.asList(
                storedRow(7, 1, false), storedRow(9, 0, true)));
        User user = SecurityContext.get().getCurrentUser().get();
        LinkConfigurationsToAppVersionRequest request = new LinkConfigurationsToAppVersionRequest();
        request.setApplicationVersionId(LATEST);
        request.setConfigurations(Arrays.asList(versionLink(7, LATEST, 1), versionLink(8, LATEST, 1),
                versionLink(9, LATEST, 1)));

        dao.updateApplicationVersionConfigurations(request, user);

        assertEquals(Arrays.asList(7), linkedConfigurations(LATEST, false));
        assertEquals(Arrays.asList(8, 9), linkedConfigurations(LATEST, true));
    }

    @Test
    public void versionLinkDialogPinsNewLinksToAnOlderVersion() {
        when(mapper.findVersionById(OLDER)).thenReturn(version(OLDER, "released"));
        when(mapper.getVersionConfigurationRows(OLDER)).thenReturn(Arrays.asList(storedRow(7, 1, true)));
        User user = SecurityContext.get().getCurrentUser().get();
        LinkConfigurationsToAppVersionRequest request = new LinkConfigurationsToAppVersionRequest();
        request.setApplicationVersionId(OLDER);
        request.setConfigurations(Arrays.asList(versionLink(7, OLDER, 1), versionLink(8, OLDER, 1)));

        dao.updateApplicationVersionConfigurations(request, user);

        assertEquals(Arrays.asList(7), linkedConfigurations(OLDER, true));
        assertEquals(Arrays.asList(8), linkedConfigurations(OLDER, false));
    }

    @Test
    public void uploadOfACommonAppByANonSuperAdminIsRefusedBeforeLocking() {
        Application common = application(LATEST);
        common.setCommonApplication(true);
        when(mapper.findById(APP)).thenReturn(common);
        try {
            dao.insertApplicationVersionWithRelease(newVersion(), "schedule", USER);
            fail();
        } catch (CommonAppAccessException expected) {
            verify(mapper, never()).lockApplication(anyInt());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void uploadWithoutAnApplicationIdIsRejected() {
        ApplicationVersion v = newVersion();
        v.setApplicationId(null);
        dao.insertApplicationVersionWithRelease(v, "schedule", USER);
    }

    @Test
    public void deletingACommonVersionByANonSuperAdminIsRefusedBeforeLocking() {
        ApplicationVersion v = version(NEW_VERSION, "held");
        v.setCommonApplication(true);
        when(mapper.findVersionById(NEW_VERSION)).thenReturn(v);
        try {
            dao.removeApplicationVersionById(NEW_VERSION);
            fail();
        } catch (com.hmdm.security.SecurityException expected) {
            verify(mapper, never()).lockApplication(anyInt());
        }
    }

    @Test
    public void turningAnAppIntoCommonLocksEverySamePackageAppFirst() {
        when(mapper.findAllByPackageId("com.example.a")).thenReturn(Arrays.asList(application(11, LATEST), application(APP, LATEST)));
        com.hmdm.persistence.domain.Customer customer = new com.hmdm.persistence.domain.Customer();
        customer.setId(1);
        when(customerDAO.findById(anyInt())).thenReturn(customer);

        dao.turnApplicationIntoCommon_Transaction(APP, new java.util.HashMap<>());

        InOrder order = inOrder(mapper);
        order.verify(mapper).lockApplication(APP);
        order.verify(mapper).lockApplication(11);
        order.verify(mapper).insertApplication(any(Application.class));
    }

    @Test
    public void appLinkDialogChecksAccessBeforeLocking() {
        Application foreign = application(LATEST);
        foreign.setCustomerId(2);
        when(mapper.findById(APP)).thenReturn(foreign);
        LinkConfigurationsToAppRequest request = new LinkConfigurationsToAppRequest();
        request.setApplicationId(APP);
        request.setConfigurations(Collections.emptyList());
        try {
            dao.updateApplicationConfigurations(request);
            fail();
        } catch (com.hmdm.security.SecurityException expected) {
            verify(mapper, never()).lockApplication(anyInt());
        }
    }

    @Test
    public void checkForUpdatesRelinkKeepsFlagsAndReleasesAHeldTarget() {
        when(mapper.findVersionById(OLDER)).thenReturn(version(OLDER, "released"));
        when(mapper.findVersionById(LATEST)).thenReturn(version(LATEST, "held"));
        when(mapper.getVersionConfigurationRows(OLDER)).thenReturn(Arrays.asList(storedRow(7, 1, false)));
        when(mapper.getVersionConfigurationRows(LATEST)).thenReturn(Arrays.asList(storedRow(8, 1, true)));
        ApplicationVersionConfigurationLink pinnedOnOld = versionLink(7, OLDER, 1);
        pinnedOnOld.setId(50);
        ApplicationVersionConfigurationLink notOnOld = versionLink(8, OLDER, 0);
        ApplicationVersionConfigurationLink followerOnNew = versionLink(8, LATEST, 1);
        followerOnNew.setId(60);
        when(mapper.getApplicationVersionConfigurationsWithCandidates(1, USER, APP, OLDER))
                .thenReturn(Arrays.asList(pinnedOnOld, notOnOld));
        when(mapper.getApplicationVersionConfigurationsWithCandidates(1, USER, APP, LATEST))
                .thenReturn(Arrays.asList(versionLink(7, LATEST, 0), followerOnNew));
        User user = SecurityContext.get().getCurrentUser().get();

        List<Integer> changed = dao.updateConfigurationsToVersion(OLDER, LATEST, user);

        assertEquals(Arrays.asList(7, 8), changed);
        verify(mapper).releaseApplicationVersion(eq(LATEST), eq("schedule"), eq(USER), anyLong());
        assertEquals(Arrays.asList(7), linkedConfigurations(LATEST, false));
        assertEquals(Arrays.asList(8), linkedConfigurations(LATEST, true));
        assertEquals(1, linkedActions(LATEST, true).get(0).intValue());
    }

    @Test
    public void checkForUpdatesDownloadOnlyHoldsAndUpdateReleasesOnSchedule() {
        assertEquals("hold", FollowLatestRules.modeForCheckForUpdates(false));
        assertEquals("schedule", FollowLatestRules.modeForCheckForUpdates(true));
    }

    @SuppressWarnings("unchecked")
    private List<Integer> linkedActions(int versionId, boolean follow) {
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper, org.mockito.Mockito.atLeast(0)).insertApplicationVersionConfigurations(eq(APP), eq(versionId),
                eq(follow), captor.capture());
        List<Integer> actions = new java.util.ArrayList<>();
        for (Object list : captor.getAllValues()) {
            for (ApplicationVersionConfigurationLink link : (List<ApplicationVersionConfigurationLink>) list) {
                actions.add(link.getAction());
            }
        }
        return actions;
    }

    @SuppressWarnings("unchecked")
    private List<Integer> linkedConfigurations(int versionId, boolean follow) {
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(mapper, org.mockito.Mockito.atLeast(0)).insertApplicationVersionConfigurations(eq(APP), eq(versionId),
                eq(follow), captor.capture());
        List<Integer> ids = new java.util.ArrayList<>();
        for (Object list : captor.getAllValues()) {
            for (ApplicationVersionConfigurationLink link : (List<ApplicationVersionConfigurationLink>) list) {
                ids.add(link.getConfigurationId());
            }
        }
        java.util.Collections.sort(ids);
        return ids;
    }

    private static ApplicationVersionConfigurationLink versionLink(int configId, int versionId, int action) {
        ApplicationVersionConfigurationLink link = new ApplicationVersionConfigurationLink();
        link.setApplicationId(APP);
        link.setConfigurationId(configId);
        link.setApplicationVersionId(versionId);
        link.setAction(action);
        return link;
    }

    private static Application storedRow(int configId, int action, boolean follow) {
        Application row = new Application();
        row.setId(configId);
        row.setAction(action);
        row.setFollowLatest(follow);
        return row;
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
        return application(APP, latest);
    }

    private static Application application(int id, Integer latest) {
        Application a = new Application();
        a.setId(id);
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

    @Test
    public void releasingAHeldVersionPutsPinnedCanariesBackOnFollowingInTheSameTransaction() {
        when(mapper.findVersionById(NEW_VERSION)).thenReturn(version(NEW_VERSION, "held"));
        when(mapper.repinVersionToFollowing(NEW_VERSION)).thenReturn(Arrays.asList(9, 7));

        List<Integer> moved = dao.releaseVersion(NEW_VERSION, "schedule", USER);

        assertEquals(Arrays.asList(7, 8, 9), moved);
        InOrder order = inOrder(mapper);
        order.verify(mapper).lockApplication(APP);
        order.verify(mapper).releaseApplicationVersion(eq(NEW_VERSION), eq("schedule"), eq(USER), anyLong());
        order.verify(mapper).repinVersionToFollowing(NEW_VERSION);
        order.verify(mapper).recalculateLatestVersion(APP);
        order.verify(mapper).moveFollowersToLatest(APP, null);
    }

    @Test
    public void releasingAnAlreadyReleasedVersionDoesNotUnpinAnything() {
        when(mapper.findVersionById(NEW_VERSION)).thenReturn(version(NEW_VERSION, "released"));

        dao.releaseVersion(NEW_VERSION, "schedule", USER);

        verify(mapper, never()).repinVersionToFollowing(anyInt());
    }

    @Test
    public void configurationsOnAVersionAreListedAfterTheWriteGuard() {
        when(mapper.findVersionById(NEW_VERSION)).thenReturn(version(NEW_VERSION, "released"));
        when(mapper.getConfigurationIdsOnVersion(NEW_VERSION)).thenReturn(Arrays.asList(4, 5));

        assertEquals(Arrays.asList(4, 5), dao.getConfigurationsOnVersion(NEW_VERSION));

        Application foreign = application(LATEST);
        foreign.setCustomerId(2);
        when(mapper.findById(APP)).thenReturn(foreign);
        try {
            dao.getConfigurationsOnVersion(NEW_VERSION);
            fail();
        } catch (com.hmdm.security.SecurityException expected) {
            verify(mapper, org.mockito.Mockito.times(1)).getConfigurationIdsOnVersion(NEW_VERSION);
        }
    }

    private void existingVersion(int code, String url) {
        when(mapper.getDuplicateVersionForApp(APP, -1, "2.0")).thenReturn(55);
        ApplicationVersion existing = version(55, "released");
        existing.setVersionCode(code);
        existing.setUrl(url);
        when(mapper.findVersionById(55)).thenReturn(existing);
    }

    @Test
    public void aVersionStringThatAlreadyExistsIsRefusedWhateverTheMode() {
        existingVersion(5, "http://localhost/files/a.apk");
        ApplicationVersion incoming = newVersion();
        incoming.setVersionCode(6);
        incoming.setUrl("http://localhost/files/a.apk");

        for (String mode : new String[]{"hold", "schedule", "immediate"}) {
            try {
                dao.insertApplicationVersionWithRelease(incoming, mode, USER);
                fail();
            } catch (VersionExistsException expected) {
            }
        }
        verify(mapper, never()).insertApplicationVersion(any(ApplicationVersion.class));
        verify(mapper, never()).updateApplicationVersion(any(ApplicationVersion.class));
        verify(mapper, never()).releaseApplicationVersion(anyInt(), anyString(), anyInt(), anyLong());
        verify(mapper, never()).moveFollowersToLatest(any(), any());
    }

    @Test
    public void theIdenticalVersionIsAnIdempotentNoOp() {
        existingVersion(5, "http://localhost/files/a.apk");
        ApplicationVersion incoming = newVersion();
        incoming.setVersionCode(5);
        incoming.setUrl("http://localhost/files/a.apk");

        List<Integer> moved = dao.insertApplicationVersionWithRelease(incoming, "immediate", USER);

        assertTrue(moved.isEmpty());
        assertEquals(Integer.valueOf(55), incoming.getId());
        verify(mapper, never()).insertApplicationVersion(any(ApplicationVersion.class));
        verify(mapper, never()).updateApplicationVersion(any(ApplicationVersion.class));
        verify(mapper, never()).moveFollowersToLatest(any(), any());
    }

    private ApplicationDAO fileDao(File files, APKFileAnalyzer analyzer) {
        com.hmdm.persistence.domain.Customer customer = new com.hmdm.persistence.domain.Customer();
        customer.setId(1);
        customer.setFilesDir("c");
        when(customerDAO.findById(1)).thenReturn(customer);
        com.hmdm.rest.json.APKFileDetails details = new com.hmdm.rest.json.APKFileDetails();
        details.setVersion("2.0");
        details.setVersionCode(5);
        when(analyzer.analyzeFile(anyString())).thenReturn(details);
        return new ApplicationDAO(mapper, customerDAO, files.getAbsolutePath(), "http://localhost", "", analyzer);
    }

    private ApplicationVersion uploadedFile(File tmp, byte[] content) throws Exception {
        Files.write(tmp.toPath(), content);
        ApplicationVersion incoming = newVersion();
        incoming.setFilePath(tmp.getAbsolutePath());
        return incoming;
    }

    @Test
    public void anUploadedFileWithTheSameVersionCodeAndSizeIsTheIdenticalFile() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        File stored = new File(files, "c/a.apk");
        stored.getParentFile().mkdirs();
        Files.write(stored.toPath(), new byte[]{9, 9, 9});
        existingVersion(5, "http://localhost/files/c/a.apk");
        File tmp = File.createTempFile("upload", ".apk");
        ApplicationDAO fileDao = fileDao(files, mock(APKFileAnalyzer.class));

        List<Integer> moved = fileDao.insertApplicationVersionWithRelease(uploadedFile(tmp, new byte[]{9, 9, 9}), "schedule", USER);

        assertTrue(moved.isEmpty());
        assertTrue(stored.exists());
        assertEquals(false, tmp.exists());
        verify(mapper, never()).insertApplicationVersion(any(ApplicationVersion.class));
    }

    @Test
    public void sameCodeAndSizeButDifferentBytesIsRefused() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        File stored = new File(files, "c/a.apk");
        stored.getParentFile().mkdirs();
        Files.write(stored.toPath(), new byte[]{1, 2, 3});
        existingVersion(5, "http://localhost/files/c/a.apk");
        File tmp = File.createTempFile("upload", ".apk");
        ApplicationDAO fileDao = fileDao(files, mock(APKFileAnalyzer.class));

        try {
            fileDao.insertApplicationVersionWithRelease(uploadedFile(tmp, new byte[]{9, 9, 9}), "schedule", USER);
            fail();
        } catch (VersionExistsException expected) {
            assertEquals(3, stored.length());
            verify(mapper, never()).insertApplicationVersion(any(ApplicationVersion.class));
        }
    }

    @Test
    public void anArchitectureUploadWithAnotherVersionCodeIsRefused() {
        existingVersion(5, "http://localhost/files/a.apk");
        ApplicationVersion incoming = newVersion();
        incoming.setArch("arm64-v8a");
        incoming.setSplit(true);
        incoming.setVersionCode(6);
        incoming.setUrlArm64("http://localhost/files/a64.apk");

        try {
            dao.insertApplicationVersionWithRelease(incoming, "schedule", USER);
            fail();
        } catch (VersionExistsException expected) {
            verify(mapper, never()).updateApplicationVersion(any(ApplicationVersion.class));
        }
    }

    @Test
    public void anUploadedFileOfAnotherSizeIsRefusedAndTheStoredFileIsKept() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        File stored = new File(files, "c/a.apk");
        stored.getParentFile().mkdirs();
        Files.write(stored.toPath(), new byte[]{1, 2, 3});
        existingVersion(5, "http://localhost/files/c/a.apk");
        File tmp = File.createTempFile("upload", ".apk");
        ApplicationDAO fileDao = fileDao(files, mock(APKFileAnalyzer.class));

        try {
            fileDao.insertApplicationVersionWithRelease(uploadedFile(tmp, new byte[]{9, 9, 9, 9}), "schedule", USER);
            fail();
        } catch (VersionExistsException expected) {
            assertEquals(3, stored.length());
            verify(mapper, never()).insertApplicationVersion(any(ApplicationVersion.class));
        }
    }

    @Test
    public void anAdditionalArchitectureOfAnExistingVersionStillMerges() {
        existingVersion(5, "http://localhost/files/a.apk");
        ApplicationVersion incoming = newVersion();
        incoming.setArch("arm64-v8a");
        incoming.setSplit(true);
        incoming.setVersionCode(5);
        incoming.setUrlArm64("http://localhost/files/a64.apk");
        when(mapper.getDuplicateVersionForApp(APP, -1, "2.0")).thenReturn(55);

        dao.insertApplicationVersionWithRelease(incoming, "schedule", USER);

        verify(mapper).updateApplicationVersion(incoming);
    }

    @Test
    public void theFirstVersionOfAWebApplicationCarriesReleaseMetadata() {
        Application web = new Application();
        web.setUrl("http://example.com");
        web.setName("web");
        doAnswer(invocation -> {
            ((Application) invocation.getArguments()[0]).setId(APP);
            return 1;
        }).when(mapper).insertApplication(any(Application.class));

        dao.insertWebApplication(web);

        ApplicationVersion inserted = insertedVersion();
        assertEquals("released", inserted.getReleaseState());
        assertNotNull(inserted.getReleasedAt());
        assertEquals("schedule", inserted.getReleaseMode());
        assertEquals(Integer.valueOf(USER), inserted.getReleasedBy());
    }

    private File storedApk(File files, String name, byte[] content) throws Exception {
        File stored = new File(files, "c/" + name);
        stored.getParentFile().mkdirs();
        Files.write(stored.toPath(), content);
        return stored;
    }

    private File tmpUpload(String name, byte[] content) throws Exception {
        File tmp = com.hmdm.util.FileUtil.createTempFile(name);
        Files.write(tmp.toPath(), content);
        return tmp;
    }

    @Test
    public void aSecondVersionWithTheSameFileNameKeepsTheFirstVersionsFile() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        File first = storedApk(files, "a.apk", new byte[]{1, 2, 3});
        when(mapper.countVersionsReferencingFile("http://localhost/files/c/a.apk")).thenReturn(1);
        ApplicationDAO fileDao = fileDao(files, mock(APKFileAnalyzer.class));
        ApplicationVersion incoming = newVersion();
        incoming.setUrl(null);
        incoming.setFilePath(tmpUpload("a.apk", new byte[]{9, 9, 9, 9}).getAbsolutePath());

        fileDao.insertApplicationVersionWithRelease(incoming, "hold", USER);

        assertEquals(3, first.length());
        File second = new File(files, "c/a-5.apk");
        assertTrue(second.exists());
        assertEquals(4, second.length());
        assertEquals("http://localhost/files/c/a-5.apk", insertedVersion().getUrl());
        verify(mapper, never()).updateApplicationVersion(any(ApplicationVersion.class));
    }

    @Test
    public void aFileNoVersionReferencesIsStillReplaced() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        File first = storedApk(files, "a.apk", new byte[]{1, 2, 3});
        when(mapper.countVersionsReferencingFile("http://localhost/files/c/a.apk")).thenReturn(0);
        ApplicationDAO fileDao = fileDao(files, mock(APKFileAnalyzer.class));
        ApplicationVersion incoming = newVersion();
        incoming.setUrl(null);
        incoming.setFilePath(tmpUpload("a.apk", new byte[]{9, 9, 9, 9}).getAbsolutePath());

        fileDao.insertApplicationVersionWithRelease(incoming, "schedule", USER);

        assertEquals(4, first.length());
        assertEquals("http://localhost/files/c/a.apk", insertedVersion().getUrl());
    }

    @Test
    public void whenTheCodeNameIsTakenTooTheContentHashMakesTheNameUnique() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        storedApk(files, "a.apk", new byte[]{1});
        File withCode = storedApk(files, "a-5.apk", new byte[]{2});
        when(mapper.countVersionsReferencingFile("http://localhost/files/c/a.apk")).thenReturn(1);
        when(mapper.countVersionsReferencingFile("http://localhost/files/c/a-5.apk")).thenReturn(1);
        ApplicationDAO fileDao = fileDao(files, mock(APKFileAnalyzer.class));
        ApplicationVersion incoming = newVersion();
        incoming.setUrl(null);
        incoming.setFilePath(tmpUpload("a.apk", new byte[]{9, 9}).getAbsolutePath());

        fileDao.insertApplicationVersionWithRelease(incoming, "hold", USER);

        assertEquals(1, withCode.length());
        assertTrue(insertedVersion().getUrl(), insertedVersion().getUrl().matches("http://localhost/files/c/a-5-[0-9a-f]{8}\\.apk"));
    }
}
