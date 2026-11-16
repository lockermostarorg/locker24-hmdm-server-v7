package com.hmdm.rest.resource;

import com.hmdm.notification.PushSender;
import com.hmdm.notification.PushService;
import com.hmdm.notification.persistence.domain.PushMessage;
import com.hmdm.persistence.ConfigurationDAO;
import com.hmdm.persistence.DeviceDAO;
import com.hmdm.persistence.domain.Device;
import com.hmdm.security.SecurityException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PushServiceUnscopedTest {

    private static final int FOREIGN_CONFIG = 40;
    private static final String PAYLOAD = "{\"apps\":[{\"pkg\":\"a.b\",\"version\":\"2.0\"}],\"issuedAt\":7}";

    private PushSender mqtt;
    private PushSender polling;
    private ConfigurationDAO configurationDAO;
    private DeviceDAO deviceDAO;
    private PushService service;

    @Before
    public void setUp() {
        mqtt = mock(PushSender.class);
        polling = mock(PushSender.class);
        configurationDAO = mock(ConfigurationDAO.class);
        deviceDAO = mock(DeviceDAO.class);
        com.hmdm.persistence.domain.User user = new com.hmdm.persistence.domain.User();
        user.setId(3);
        user.setCustomerId(1);
        user.setUserRole(new com.hmdm.persistence.domain.UserRole());
        com.hmdm.security.SecurityContext.init(user);
        service = new PushService(mqtt, polling, configurationDAO, deviceDAO);

        when(configurationDAO.getConfigurationById(FOREIGN_CONFIG)).thenThrow(SecurityException.onApplicationAccessViolation(1));
        Device a = new Device();
        a.setId(1);
        Device b = new Device();
        b.setId(2);
        when(deviceDAO.getDeviceIdsByAnyCustomerConfigurationId(FOREIGN_CONFIG)).thenReturn(Arrays.asList(a, b));
    }

    @org.junit.After
    public void tearDown() {
        com.hmdm.security.SecurityContext.release();
    }

    @Test
    public void theScopedPathCannotReachAnotherCustomersConfiguration() {
        try {
            service.notifyDevicesOnUpdate(FOREIGN_CONFIG);
            fail();
        } catch (SecurityException expected) {
            verify(mqtt, times(0)).send(any(PushMessage.class));
        }
    }

    @Test
    public void theUnscopedPathNotifiesEveryDeviceOfAnyCustomersConfiguration() {
        assertEquals(2, service.notifyAllDevicesOnUpdate(FOREIGN_CONFIG));
        assertEquals(2, service.notifyAllDevicesOnForceUpdate(FOREIGN_CONFIG, PAYLOAD));

        ArgumentCaptor<PushMessage> sent = ArgumentCaptor.forClass(PushMessage.class);
        verify(mqtt, times(4)).send(sent.capture());
        assertEquals(PushMessage.TYPE_CONFIG_UPDATED, sent.getAllValues().get(0).getMessageType());
        assertEquals(PushMessage.TYPE_FORCE_APP_UPDATE, sent.getAllValues().get(2).getMessageType());
        verify(polling, times(4)).send(any(PushMessage.class));
    }

    @Test
    public void theForceMessageCarriesThePayloadAndTheUpdateMessageDoesNot() {
        service.notifyAllDevicesOnUpdate(FOREIGN_CONFIG);
        service.notifyAllDevicesOnForceUpdate(FOREIGN_CONFIG, PAYLOAD);

        ArgumentCaptor<PushMessage> sent = ArgumentCaptor.forClass(PushMessage.class);
        verify(polling, times(4)).send(sent.capture());
        assertEquals(null, sent.getAllValues().get(0).getPayload());
        assertEquals(PAYLOAD, sent.getAllValues().get(3).getPayload());
        assertTrue(sent.getAllValues().get(3).toJsonString().contains("issuedAt"));
    }

    @Test
    public void thePayloadHasTheAgreedShape() {
        assertEquals(PAYLOAD, PushService.forceUpdatePayload("a.b", "2.0", 7));
        assertEquals("{\"apps\":[{\"pkg\":\"a.b\",\"version\":\"1.0-\\\"x\\\"\"}],\"issuedAt\":5}",
                PushService.forceUpdatePayload("a.b", "1.0-\"x\"", 5));
    }
}
