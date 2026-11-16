package com.hmdm.rest.resource;

import com.hmdm.notification.PushService;
import com.hmdm.persistence.ApplicationDAO;
import com.hmdm.persistence.ConfigurationDAO;
import com.hmdm.persistence.CustomerDAO;
import com.hmdm.persistence.UserDAO;
import com.hmdm.persistence.domain.Configuration;
import com.hmdm.persistence.domain.User;
import com.hmdm.persistence.domain.UserRole;
import com.hmdm.rest.json.Response;
import com.hmdm.rest.json.UpgradeConfigurationApplicationRequest;
import com.hmdm.security.SecurityContext;
import jakarta.ws.rs.WebApplicationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConfigurationUpgradePushTest {

    private static final int CONFIG = 5;
    private static final int APP = 10;

    private ConfigurationDAO configurationDAO;
    private PushService pushService;
    private ConfigurationResource resource;

    @Before
    public void setUp() {
        configurationDAO = mock(ConfigurationDAO.class);
        pushService = mock(PushService.class);
        resource = new ConfigurationResource(configurationDAO, mock(ApplicationDAO.class), pushService,
                mock(CustomerDAO.class), mock(UserDAO.class), "http://localhost");

        User user = new User();
        user.setId(3);
        user.setCustomerId(1);
        UserRole role = new UserRole();
        role.setSuperAdmin(true);
        user.setUserRole(role);
        SecurityContext.init(user);

        when(configurationDAO.hasConfigurationAccess(CONFIG)).thenReturn(true);
        Configuration configuration = new Configuration();
        configuration.setId(CONFIG);
        when(configurationDAO.getConfigurationById(CONFIG)).thenReturn(configuration);
    }

    @After
    public void tearDown() {
        SecurityContext.release();
    }

    private UpgradeConfigurationApplicationRequest request(Integer appId) {
        UpgradeConfigurationApplicationRequest request = new UpgradeConfigurationApplicationRequest();
        request.setConfigurationId(CONFIG);
        request.setApplicationId(appId);
        return request;
    }

    @Test
    public void anUpgradeNotifiesTheConfigurationAfterTheUpgrade() {
        when(configurationDAO.upgradeConfigurationApplication(CONFIG, APP)).thenReturn(true);

        Response response = resource.upgradeConfiguration(request(APP));

        assertEquals(Response.ResponseStatus.OK, response.getStatus());
        InOrder order = inOrder(configurationDAO, pushService);
        order.verify(configurationDAO).upgradeConfigurationApplication(CONFIG, APP);
        order.verify(pushService).notifyDevicesOnUpdate(CONFIG);
        verify(pushService, never()).notifyDevicesOnForceUpdate(anyInt());
    }

    @Test
    public void aRowThatIsNotBehindLatestIsANoOpWithoutPush() {
        when(configurationDAO.upgradeConfigurationApplication(CONFIG, APP)).thenReturn(false);

        Response response = resource.upgradeConfiguration(request(APP));

        assertEquals(Response.ResponseStatus.OK, response.getStatus());
        verify(pushService, never()).notifyDevicesOnUpdate(anyInt());
    }

    @Test
    public void aMissingApplicationIdIsABadRequest() {
        try {
            resource.upgradeConfiguration(request(null));
            fail("expected 400");
        } catch (WebApplicationException e) {
            assertEquals(400, e.getResponse().getStatus());
            assertEquals("error.application.id.required", ((Response) e.getResponse().getEntity()).getMessage());
        }
        verify(configurationDAO, never()).upgradeConfigurationApplication(anyInt(), anyInt());
    }

    @Test
    public void aDeadlockIsAConflict() {
        when(configurationDAO.upgradeConfigurationApplication(CONFIG, APP))
                .thenThrow(new RuntimeException("x", new SQLException("deadlock", "40P01")));

        try {
            resource.upgradeConfiguration(request(APP));
            fail("expected 409");
        } catch (WebApplicationException e) {
            assertEquals(409, e.getResponse().getStatus());
        }
        verify(pushService, never()).notifyDevicesOnUpdate(anyInt());
    }
}
