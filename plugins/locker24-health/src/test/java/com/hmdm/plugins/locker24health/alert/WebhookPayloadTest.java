package com.hmdm.plugins.locker24health.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdm.plugins.locker24health.status.Condition;
import com.hmdm.plugins.locker24health.status.Severity;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WebhookPayloadTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long SENT = 1_763_290_060_000L;

    private static Set<String> keys(JsonNode n) {
        Set<String> out = new TreeSet<>();
        Iterator<String> it = n.fieldNames();
        it.forEachRemaining(out::add);
        return out;
    }

    private static List<String> keyOrder(JsonNode n) {
        List<String> out = new ArrayList<>();
        n.fieldNames().forEachRemaining(out::add);
        return out;
    }

    @Test
    public void exactShape() throws Exception {
        AlertEvent offline = new AlertEvent(1, 5, "M-01", "hpmo-qa", "HP01", Condition.OFFLINE, Severity.RED, false,
                "no report for 11 min", SENT, null);
        String body = WebhookPayload.build(List.of(offline), SENT);
        JsonNode root = MAPPER.readTree(body);
        assertEquals(List.of("source", "sent_at", "events"), keyOrder(root));
        assertEquals("locker24-mdm", root.get("source").asText());
        assertEquals(SENT, root.get("sent_at").asLong());
        JsonNode e = root.get("events").get(0);
        assertEquals(List.of("event", "severity", "condition", "device_id", "device_number", "configuration",
                "locker_short_uid", "message", "at"), keyOrder(e));
        assertEquals("raised", e.get("event").asText());
        assertEquals("red", e.get("severity").asText());
        assertEquals("offline", e.get("condition").asText());
        assertEquals(5, e.get("device_id").asInt());
        assertEquals("M-01", e.get("device_number").asText());
        assertEquals("hpmo-qa", e.get("configuration").asText());
        assertEquals("HP01", e.get("locker_short_uid").asText());
        assertEquals("M-01 (hpmo-qa, locker HP01): offline", e.get("message").asText());
        assertEquals(SENT, e.get("at").asLong());
    }

    @Test
    public void sentAtIsPassedThrough() throws Exception {
        assertEquals(42L, MAPPER.readTree(WebhookPayload.build(List.of(), 42L)).get("sent_at").asLong());
        assertEquals(0, MAPPER.readTree(WebhookPayload.build(List.of(), 42L)).get("events").size());
    }

    @Test
    public void clearedAndSummaryEvents() throws Exception {
        AlertEvent cleared = new AlertEvent(1, 5, "M-01", "hpmo-qa", null, Condition.NOT_CHARGING, Severity.YELLOW, true,
                null, SENT, null);
        AlertEvent summary = new AlertEvent(1, null, null, null, null, null, Severity.YELLOW, false, null, SENT,
                "6 more tablets need attention: offline 6");
        JsonNode events = MAPPER.readTree(WebhookPayload.build(List.of(cleared, summary), SENT)).get("events");
        assertEquals("cleared", events.get(0).get("event").asText());
        assertEquals("yellow", events.get(0).get("severity").asText());
        assertEquals("M-01 (hpmo-qa): cleared not_charging", events.get(0).get("message").asText());
        assertTrue(events.get(0).get("locker_short_uid").isNull());
        JsonNode s = events.get(1);
        assertEquals(keys(events.get(0)), keys(s));
        assertEquals("raised", s.get("event").asText());
        assertTrue(s.get("condition").isNull());
        assertTrue(s.get("device_id").isNull());
        assertTrue(s.get("device_number").isNull());
        assertEquals("6 more tablets need attention: offline 6", s.get("message").asText());
    }

    @Test
    public void neverHeadwind() {
        AlertEvent offline = new AlertEvent(1, 5, "M-01", "hpmo-qa", "HP01", Condition.OFFLINE, Severity.RED, false,
                null, SENT, null);
        String body = WebhookPayload.build(List.of(offline), SENT);
        assertFalse(body.toLowerCase().contains("headwind"));
    }
}
