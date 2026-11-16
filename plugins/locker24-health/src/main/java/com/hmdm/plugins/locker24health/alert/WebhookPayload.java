package com.hmdm.plugins.locker24health.alert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Locale;

public final class WebhookPayload {

    public static final String SOURCE = "locker24-mdm";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WebhookPayload() {
    }

    public static String build(List<AlertEvent> events, long sentAt) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("source", SOURCE);
        root.put("sent_at", sentAt);
        ArrayNode out = root.putArray("events");
        for (AlertEvent e : events) {
            ObjectNode n = out.addObject();
            n.put("event", e.cleared() ? "cleared" : "raised");
            n.put("severity", e.severity() != null ? e.severity().name().toLowerCase(Locale.ROOT) : null);
            n.put("condition", e.condition() != null ? e.condition().code() : null);
            n.put("device_id", e.deviceId());
            n.put("device_number", e.deviceNumber());
            n.put("configuration", e.configuration());
            n.put("locker_short_uid", e.lockerShortUid());
            n.put("message", AlertText.message(e));
            n.put("at", e.at());
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("webhook payload not serializable", ex);
        }
    }
}
