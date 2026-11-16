package com.hmdm.notification;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.hmdm.notification.persistence.domain.PushMessage;
import com.hmdm.notification.persistence.mapper.NotificationMapper;
import com.hmdm.persistence.ConfigurationDAO;
import com.hmdm.persistence.DeviceDAO;
import com.hmdm.persistence.domain.Configuration;
import com.hmdm.persistence.domain.Device;
import org.mybatis.guice.transactional.Transactional;

import java.util.List;

@Singleton
public class PushService {

    private final PushSender pushSenderMqtt;
    private final PushSender pushSenderPolling;
    private final ConfigurationDAO configurationDAO;
    private final DeviceDAO deviceDAO;

    @Inject
    public PushService(@Named("MQTT") PushSender pushSenderMqtt, @Named("Polling") PushSender pushSenderPolling,
                       ConfigurationDAO configurationDAO, DeviceDAO deviceDAO) {
        this.pushSenderMqtt = pushSenderMqtt;
        this.pushSenderPolling = pushSenderPolling;
        this.configurationDAO = configurationDAO;
        this.deviceDAO = deviceDAO;
    }

    // Use both ways to send a message, because the decision how to receive messages is done on the device (configuration)
    public int send(PushMessage message) {
        pushSenderMqtt.send(message);
        return pushSenderPolling.send(message);
    }

    /**
     * <p>Sends the messages on configuration update for the devices related to specified configuration.</p>
     *
     * @param configurationId an ID of updated configuration.
     */
    @Transactional
    public int notifyDevicesOnUpdate(Integer configurationId) {
        return notifyDevices(configurationId, PushMessage.TYPE_CONFIG_UPDATED);
    }

    /**
     * <p>Sends the message that asks the devices to install application updates at once.</p>
     *
     * @param configurationId an ID of the configuration whose devices are notified.
     * @return the number of devices a message was sent to.
     */
    @Transactional
    public int notifyDevicesOnForceUpdate(Integer configurationId) {
        return notifyDevices(configurationId, PushMessage.TYPE_FORCE_APP_UPDATE);
    }

    /**
     * <p>Sends the configuration update message to the devices of a configuration of any customer. For releases of
     * common applications, whose followers belong to other customers than the releasing user.</p>
     */
    @Transactional
    public int notifyAllDevicesOnUpdate(Integer configurationId) {
        return notifyDevices(this.deviceDAO.getDeviceIdsByAnyCustomerConfigurationId(configurationId),
                PushMessage.TYPE_CONFIG_UPDATED);
    }

    @Transactional
    public int notifyAllDevicesOnForceUpdate(Integer configurationId) {
        return notifyDevices(this.deviceDAO.getDeviceIdsByAnyCustomerConfigurationId(configurationId),
                PushMessage.TYPE_FORCE_APP_UPDATE);
    }

    private int notifyDevices(Integer configurationId, String messageType) {
        final Configuration configuration = this.configurationDAO.getConfigurationById(configurationId);
        if (configuration == null) {
            return 0;
        }
        return notifyDevices(this.deviceDAO.getDeviceIdsByConfigurationId(configurationId), messageType);
    }

    private int notifyDevices(List<Device> devices, String messageType) {
        devices.forEach(device -> {
            PushMessage message = new PushMessage();
            message.setDeviceId(device.getId());
            message.setMessageType(messageType);

            this.send(message);
        });
        return devices.size();
    }

    /**
     * <p>Sends the message on application settings update to specified device.</p>
     *
     * @param deviceId an ID of device to be notified.
     */
    @Transactional
    public void notifyDeviceOnSettingUpdate(Integer deviceId) {
        sendSimpleMessage(deviceId, PushMessage.TYPE_CONFIG_UPDATED);
    }

    /**
     * <p>Sends the message on application settings update to specified device.</p>
     *
     * @param deviceId an ID of device to be notified.
     */
    @Transactional
    public void notifyDeviceOnApplicationSettingUpdate(Integer deviceId) {
        sendSimpleMessage(deviceId, PushMessage.TYPE_APP_CONFIG_UPDATED);
    }

    /**
     * <p>Sends the simple message of a certain type to specified device.</p>
     *
     * @param deviceId an ID of device to be notified.
     * @param messageType Message type
     */
    public void sendSimpleMessage(Integer deviceId, String messageType) {
        final Device dbDevice = this.deviceDAO.getDeviceById(deviceId);
        if (dbDevice != null) {
            PushMessage message = new PushMessage();
            message.setDeviceId(dbDevice.getId());
            message.setMessageType(messageType);

            this.send(message);
        }
    }
}
