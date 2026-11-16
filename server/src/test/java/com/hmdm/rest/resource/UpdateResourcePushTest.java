package com.hmdm.rest.resource;

import com.hmdm.notification.PushService;
import com.hmdm.persistence.ApplicationDAO;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.ApplicationVersion;
import com.hmdm.persistence.domain.User;
import com.hmdm.persistence.domain.UserRole;
import com.hmdm.rest.json.UpdateEntry;
import com.hmdm.rest.json.UpdateRequest;
import com.hmdm.security.SecurityContext;
import com.hmdm.util.APKFileAnalyzer;
import com.hmdm.util.StatsSender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UpdateResourcePushTest {

    private ApplicationDAO applicationDAO;
    private PushService pushService;
    private UpdateResource resource;

    @Before
    public void setUp() throws Exception {
        applicationDAO = mock(ApplicationDAO.class);
        pushService = mock(PushService.class);
        resource = new UpdateResource(Files.createTempDirectory("update-resource").toString(), "http://localhost",
                applicationDAO, mock(UnsecureDAO.class), mock(StatsSender.class), mock(APKFileAnalyzer.class), pushService);

        User user = new User();
        user.setId(3);
        user.setCustomerId(1);
        UserRole role = new UserRole();
        role.setSuperAdmin(true);
        user.setUserRole(role);
        SecurityContext.init(user);

        when(applicationDAO.findApplicationVersion("com.example.app", "1.0")).thenReturn(version(100));
        when(applicationDAO.findApplicationVersion("com.example.app", "2.0")).thenReturn(version(101));
    }

    @After
    public void tearDown() {
        SecurityContext.release();
    }

    private static ApplicationVersion version(int id) {
        ApplicationVersion version = new ApplicationVersion();
        version.setId(id);
        return version;
    }

    private UpdateRequest request(boolean update) {
        UpdateEntry entry = new UpdateEntry();
        entry.setPkg("com.example.app");
        entry.setCurrentVersion("1.0");
        entry.setVersion("2.0");
        entry.setOutdated(true);
        entry.setDownloaded(true);
        UpdateRequest request = new UpdateRequest();
        request.setUpdate(update);
        request.setUpdates(Arrays.asList(entry));
        return request;
    }

    @Test
    public void checkForUpdatesWithUpdateNotifiesTheMovedConfigurations() {
        when(applicationDAO.updateConfigurationsToVersion(eq(100), eq(101), any(User.class)))
                .thenReturn(Arrays.asList(7, 8));

        resource.downloadUpdates(request(true));

        InOrder order = inOrder(applicationDAO, pushService);
        order.verify(applicationDAO).updateConfigurationsToVersion(eq(100), eq(101), any(User.class));
        order.verify(pushService).notifyDevicesOnUpdate(7);
        order.verify(pushService).notifyDevicesOnUpdate(8);
        verify(pushService, never()).notifyDevicesOnForceUpdate(anyInt());
    }

    @Test
    public void aConfigurationMovedTwiceIsNotifiedOnce() {
        when(applicationDAO.updateConfigurationsToVersion(eq(100), eq(101), any(User.class)))
                .thenReturn(Arrays.asList(7, 7));

        resource.downloadUpdates(request(true));

        verify(pushService, org.mockito.Mockito.times(1)).notifyDevicesOnUpdate(7);
    }

    @Test
    public void downloadOnlyMovesAndNotifiesNothing() {
        resource.downloadUpdates(request(false));

        verify(applicationDAO, never()).updateConfigurationsToVersion(anyInt(), anyInt(), any(User.class));
        verify(pushService, never()).notifyDevicesOnUpdate(anyInt());
    }

    @Test
    public void noMovedConfigurationsMeansNoPush() {
        when(applicationDAO.updateConfigurationsToVersion(eq(100), eq(101), any(User.class)))
                .thenReturn(Collections.<Integer>emptyList());

        resource.downloadUpdates(request(true));

        verify(pushService, never()).notifyDevicesOnUpdate(anyInt());
    }
}
