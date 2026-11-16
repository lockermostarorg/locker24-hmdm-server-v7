package com.hmdm.rest.resource;

import com.hmdm.notification.PushService;
import com.hmdm.persistence.ApplicationDAO;
import com.hmdm.persistence.ConfigurationDAO;
import com.hmdm.persistence.LastReleasedVersionException;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.Application;
import com.hmdm.persistence.domain.ApplicationVersion;
import com.hmdm.persistence.domain.User;
import com.hmdm.persistence.domain.UserRole;
import com.hmdm.rest.json.LinkConfigurationsToAppRequest;
import com.hmdm.rest.json.LinkConfigurationsToAppVersionRequest;
import com.hmdm.rest.json.ReleaseSummary;
import com.hmdm.rest.json.ReleaseVersionRequest;
import com.hmdm.rest.json.Response;
import com.hmdm.security.SecurityContext;
import jakarta.ws.rs.WebApplicationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.nio.file.Files;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ApplicationReleaseResourceTest {

    private static final int APP = 10;
    private static final int VERSION = 101;

    private ApplicationDAO applicationDAO;
    private PushService pushService;
    private ApplicationResource resource;

    @Before
    public void setUp() throws Exception {
        applicationDAO = mock(ApplicationDAO.class);
        pushService = mock(PushService.class);
        resource = new ApplicationResource(applicationDAO, mock(ConfigurationDAO.class), mock(UnsecureDAO.class),
                pushService, Files.createTempDirectory("release-resource").toString());

        User user = new User();
        user.setId(3);
        user.setLogin("admin");
        user.setCustomerId(1);
        UserRole role = new UserRole();
        role.setSuperAdmin(true);
        user.setUserRole(role);
        SecurityContext.init(user);

        Application application = new Application();
        application.setId(APP);
        application.setPkg("com.example.app");
        when(applicationDAO.findById(APP)).thenReturn(application);
    }

    @After
    public void tearDown() {
        SecurityContext.release();
    }

    private ApplicationVersion upload(String mode) {
        ApplicationVersion version = new ApplicationVersion();
        version.setApplicationId(APP);
        version.setVersion("2.0");
        version.setReleaseMode(mode);
        return version;
    }

    private void insertReturns(String mode, List<Integer> moved) {
        when(applicationDAO.insertApplicationVersionWithRelease(any(ApplicationVersion.class), eq(mode), eq(3)))
                .thenAnswer(invocation -> {
                    ((ApplicationVersion) invocation.getArguments()[0]).setId(VERSION);
                    return moved;
                });
    }

    private void storedAs(String state, String mode) {
        ApplicationVersion saved = new ApplicationVersion();
        saved.setId(VERSION);
        saved.setApplicationId(APP);
        saved.setVersion("2.0");
        saved.setReleaseState(state);
        saved.setReleaseMode(mode);
        when(applicationDAO.findApplicationVersionById(anyInt())).thenReturn(saved);
    }

    private ReleaseSummary summary(Response response) {
        assertEquals(Response.ResponseStatus.OK, response.getStatus());
        return (ReleaseSummary) response.getData();
    }

    private void assertHttp(int status, String key, Runnable call) {
        try {
            call.run();
            fail("expected HTTP " + status);
        } catch (WebApplicationException e) {
            assertEquals(status, e.getResponse().getStatus());
            Response body = (Response) e.getResponse().getEntity();
            assertEquals(Response.ResponseStatus.ERROR, body.getStatus());
            assertEquals(key, body.getMessage());
        }
    }

    @Test
    public void scheduleNotifiesEachMovedConfigurationAndNeverForces() {
        insertReturns("schedule", Arrays.asList(5, 6));
        storedAs("released", "schedule");
        when(pushService.notifyDevicesOnUpdate(5)).thenReturn(4);
        when(pushService.notifyDevicesOnUpdate(6)).thenReturn(2);

        ReleaseSummary result = summary(resource.updateApplicationVersion(upload("schedule")));

        assertEquals(2, result.getConfigurations());
        assertEquals(6, result.getDevices());
        assertEquals("schedule", result.getMode());
        verify(pushService).notifyDevicesOnUpdate(5);
        verify(pushService).notifyDevicesOnUpdate(6);
        verify(pushService, never()).notifyDevicesOnForceUpdate(anyInt());
    }

    @Test
    public void immediateSendsUpdateThenForcePerConfigurationAfterTheMove() {
        insertReturns("immediate", Arrays.asList(5, 6));
        storedAs("released", "immediate");

        ReleaseSummary result = summary(resource.updateApplicationVersion(upload("immediate")));

        assertEquals("immediate", result.getMode());
        InOrder order = inOrder(applicationDAO, pushService);
        order.verify(applicationDAO).insertApplicationVersionWithRelease(any(ApplicationVersion.class), eq("immediate"), eq(3));
        order.verify(pushService).notifyDevicesOnUpdate(5);
        order.verify(pushService).notifyDevicesOnForceUpdate(5);
        order.verify(pushService).notifyDevicesOnUpdate(6);
        order.verify(pushService).notifyDevicesOnForceUpdate(6);
    }

    @Test
    public void holdPushesNothingAndReportsZeros() {
        insertReturns("hold", Collections.<Integer>emptyList());
        storedAs("held", null);

        ReleaseSummary result = summary(resource.updateApplicationVersion(upload("hold")));

        assertEquals(0, result.getConfigurations());
        assertEquals(0, result.getDevices());
        assertEquals("hold", result.getMode());
        verify(pushService, never()).notifyDevicesOnUpdate(anyInt());
        verify(pushService, never()).notifyDevicesOnForceUpdate(anyInt());
    }

    @Test
    public void aFirstVersionUploadedAsHoldIsReportedWithItsRealMode() {
        insertReturns("hold", Collections.<Integer>emptyList());
        storedAs("released", "hold");

        ReleaseSummary result = summary(resource.updateApplicationVersion(upload("hold")));

        assertEquals(0, result.getConfigurations());
        assertEquals("hold", result.getMode());
        verify(pushService, never()).notifyDevicesOnUpdate(anyInt());
    }

    @Test
    public void whenOnlyPinnedConfigurationsExistNothingIsPushed() {
        insertReturns("immediate", Collections.<Integer>emptyList());
        storedAs("released", "immediate");

        ReleaseSummary result = summary(resource.updateApplicationVersion(upload("immediate")));

        assertEquals(0, result.getConfigurations());
        assertEquals("immediate", result.getMode());
        verify(pushService, never()).notifyDevicesOnUpdate(anyInt());
        verify(pushService, never()).notifyDevicesOnForceUpdate(anyInt());
    }

    @Test
    public void absentModeBehavesAsSchedule() {
        insertReturns("schedule", Arrays.asList(5));
        storedAs("released", "schedule");

        ReleaseSummary result = summary(resource.updateApplicationVersion(upload(null)));

        assertEquals("schedule", result.getMode());
        verify(pushService).notifyDevicesOnUpdate(5);
        verify(pushService, never()).notifyDevicesOnForceUpdate(anyInt());
    }

    @Test
    public void unknownModeIsABadRequestAndUploadsNothing() {
        assertHttp(400, "error.release.mode.invalid", () -> resource.updateApplicationVersion(upload("now")));
        verify(applicationDAO, never()).insertApplicationVersionWithRelease(any(ApplicationVersion.class), anyString(), anyInt());
    }

    @Test
    public void editingAnExistingVersionPushesNothing() {
        ApplicationVersion version = upload("immediate");
        version.setId(VERSION);

        Response response = resource.updateApplicationVersion(version);

        assertEquals(Response.ResponseStatus.OK, response.getStatus());
        verify(applicationDAO).updateApplicationVersion(version);
        verify(pushService, never()).notifyDevicesOnUpdate(anyInt());
    }

    private ReleaseVersionRequest mode(String mode) {
        ReleaseVersionRequest request = new ReleaseVersionRequest();
        request.setMode(mode);
        return request;
    }

    @Test
    public void releasingAHeldVersionOnScheduleNotifiesAfterTheTransaction() {
        storedAs("held", null);
        when(applicationDAO.releaseVersion(VERSION, "schedule", 3)).thenReturn(Arrays.asList(5, 6));
        when(pushService.notifyDevicesOnUpdate(5)).thenReturn(1);
        when(pushService.notifyDevicesOnUpdate(6)).thenReturn(1);

        ReleaseSummary result = summary(resource.releaseApplicationVersion(VERSION, mode("schedule")));

        assertEquals(2, result.getConfigurations());
        assertEquals(2, result.getDevices());
        assertEquals("schedule", result.getMode());
        InOrder order = inOrder(applicationDAO, pushService);
        order.verify(applicationDAO).releaseVersion(VERSION, "schedule", 3);
        order.verify(pushService).notifyDevicesOnUpdate(5);
        order.verify(pushService).notifyDevicesOnUpdate(6);
        verify(pushService, never()).notifyDevicesOnForceUpdate(anyInt());
    }

    @Test
    public void releasingImmediatelyForcesAfterTheUpdatePush() {
        storedAs("held", null);
        when(applicationDAO.releaseVersion(VERSION, "immediate", 3)).thenReturn(Arrays.asList(5));

        ReleaseSummary result = summary(resource.releaseApplicationVersion(VERSION, mode("immediate")));

        assertEquals("immediate", result.getMode());
        InOrder order = inOrder(pushService);
        order.verify(pushService).notifyDevicesOnUpdate(5);
        order.verify(pushService).notifyDevicesOnForceUpdate(5);
    }

    @Test
    public void releasingAReleasedVersionIsABadRequest() {
        storedAs("released", "schedule");

        assertHttp(400, "error.version.already.released", () -> resource.releaseApplicationVersion(VERSION, mode("schedule")));
        verify(applicationDAO, never()).releaseVersion(anyInt(), anyString(), anyInt());
        verify(pushService, never()).notifyDevicesOnUpdate(anyInt());
    }

    @Test
    public void releaseModesOtherThanScheduleAndImmediateAreBadRequests() {
        storedAs("held", null);

        assertHttp(400, "error.release.mode.invalid", () -> resource.releaseApplicationVersion(VERSION, mode("hold")));
        assertHttp(400, "error.release.mode.invalid", () -> resource.releaseApplicationVersion(VERSION, mode(null)));
        assertHttp(400, "error.release.mode.invalid", () -> resource.releaseApplicationVersion(VERSION, null));
        verify(applicationDAO, never()).releaseVersion(anyInt(), anyString(), anyInt());
    }

    @Test
    public void releasingAnUnknownVersionIsNotFound() {
        when(applicationDAO.findApplicationVersionById(anyInt())).thenReturn(null);

        assertHttp(404, "error.notfound.object", () -> resource.releaseApplicationVersion(VERSION, mode("schedule")));
    }

    @Test
    public void aFailingPushDoesNotFailTheCommittedReleaseNorSkipOtherConfigurations() {
        storedAs("held", null);
        when(applicationDAO.releaseVersion(VERSION, "schedule", 3)).thenReturn(Arrays.asList(5, 6));
        when(pushService.notifyDevicesOnUpdate(5)).thenThrow(new RuntimeException("mqtt down"));
        when(pushService.notifyDevicesOnUpdate(6)).thenReturn(3);

        ReleaseSummary result = summary(resource.releaseApplicationVersion(VERSION, mode("schedule")));

        assertEquals(2, result.getConfigurations());
        assertEquals(3, result.getDevices());
        verify(pushService, times(1)).notifyDevicesOnUpdate(6);
    }

    @Test
    public void aDeadlockOnReleaseIsAConflict() {
        storedAs("held", null);
        when(applicationDAO.releaseVersion(VERSION, "schedule", 3))
                .thenThrow(new RuntimeException("wrapped", new SQLException("deadlock detected", "40P01")));

        assertHttp(409, "error.concurrent.update", () -> resource.releaseApplicationVersion(VERSION, mode("schedule")));
        verify(pushService, never()).notifyDevicesOnUpdate(anyInt());
    }

    @Test
    public void aSerializationFailureOnUploadIsAConflict() {
        when(applicationDAO.insertApplicationVersionWithRelease(any(ApplicationVersion.class), anyString(), anyInt()))
                .thenThrow(new RuntimeException("a", new RuntimeException("b", new SQLException("could not serialize", "40001"))));

        assertHttp(409, "error.concurrent.update", () -> resource.updateApplicationVersion(upload("schedule")));
    }

    @Test
    public void otherFailuresStayInternalErrors() {
        storedAs("held", null);
        when(applicationDAO.releaseVersion(VERSION, "schedule", 3)).thenThrow(new RuntimeException("boom"));

        Response response = resource.releaseApplicationVersion(VERSION, mode("schedule"));

        assertEquals(Response.ResponseStatus.ERROR, response.getStatus());
        assertEquals("error.internal.server", response.getMessage());
    }

    @Test
    public void deletingTheLastReleasedVersionIsABadRequest() {
        storedAs("released", "schedule");
        org.mockito.Mockito.doThrow(new LastReleasedVersionException(VERSION))
                .when(applicationDAO).removeApplicationVersionByIdWithAPKFile(VERSION);

        assertHttp(400, "error.application.version.last.released", () -> resource.removeApplicationVersion(VERSION));
    }

    @Test
    public void linkDialogWithoutAnApplicationIsABadRequest() {
        LinkConfigurationsToAppRequest request = new LinkConfigurationsToAppRequest();
        request.setConfigurations(new ArrayList<>());

        assertHttp(400, "error.application.id.required", () -> resource.updateApplicationConfigurations(request));
        verify(applicationDAO, never()).updateApplicationConfigurations(any(LinkConfigurationsToAppRequest.class));
    }

    @Test
    public void versionLinkDialogWithoutAVersionIsABadRequest() {
        LinkConfigurationsToAppVersionRequest request = new LinkConfigurationsToAppVersionRequest();
        request.setConfigurations(new ArrayList<>());

        assertHttp(400, "error.application.version.id.required", () -> resource.updateApplicationVersionConfigurations(request));
    }

    @Test
    public void aDeadlockInTheLinkDialogsIsAConflict() {
        LinkConfigurationsToAppRequest app = new LinkConfigurationsToAppRequest();
        app.setApplicationId(APP);
        app.setConfigurations(new ArrayList<>());
        org.mockito.Mockito.doThrow(new RuntimeException("x", new SQLException("deadlock", "40P01")))
                .when(applicationDAO).updateApplicationConfigurations(any(LinkConfigurationsToAppRequest.class));
        assertHttp(409, "error.concurrent.update", () -> resource.updateApplicationConfigurations(app));

        LinkConfigurationsToAppVersionRequest version = new LinkConfigurationsToAppVersionRequest();
        version.setApplicationVersionId(VERSION);
        version.setConfigurations(new ArrayList<>());
        org.mockito.Mockito.doThrow(new RuntimeException("x", new SQLException("deadlock", "40P01")))
                .when(applicationDAO).updateApplicationVersionConfigurations(any(LinkConfigurationsToAppVersionRequest.class), any(User.class));
        assertHttp(409, "error.concurrent.update", () -> resource.updateApplicationVersionConfigurations(version));
    }
}
