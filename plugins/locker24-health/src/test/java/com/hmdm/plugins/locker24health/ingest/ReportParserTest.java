package com.hmdm.plugins.locker24health.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hmdm.plugins.locker24health.model.ParsedBatch;
import com.hmdm.plugins.locker24health.model.ParsedReport;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ReportParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ObjectNode golden() throws Exception {
        try (InputStream in = ReportParserTest.class.getResourceAsStream("/report-v1.json")) {
            return (ObjectNode) MAPPER.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String envelope(JsonNode... reports) {
        ObjectNode env = MAPPER.createObjectNode();
        env.put("sent_at", 1763290005000L);
        ArrayNode arr = env.putArray("reports");
        for (JsonNode r : reports) {
            arr.add(r);
        }
        return env.toString();
    }

    private static ParsedReport parseOne(ObjectNode report) {
        return ReportParser.parse(envelope(report)).getReports().get(0);
    }

    private static void assertRejected(String body) {
        try {
            ReportParser.parse(body);
            fail("expected ValidationException");
        } catch (ValidationException e) {
            assertEquals("error.validation", e.getMessage());
        }
    }

    @Test
    public void parsesTheContract() throws Exception {
        ParsedBatch batch = ReportParser.parse(envelope(golden()));
        assertEquals(1763290005000L, batch.getSentAt());
        assertEquals(1, batch.getReports().size());
        ParsedReport r = batch.getReports().get(0);
        assertEquals(1763290000000L, r.getTs());
        assertEquals("schedule", r.getTrigger());
        assertEquals(Integer.valueOf(87), r.getBatteryPct());
        assertEquals(Boolean.TRUE, r.getCharging());
        assertEquals(Float.valueOf(31.5f), r.getTemperatureC());
        assertEquals("samsung", r.getManufacturer());
        assertEquals("SM-X110", r.getModel());
        assertEquals(Integer.valueOf(40960), r.getFreeStorageMb());
        assertEquals(Integer.valueOf(-61), r.getRssiDbm());
        assertEquals(Boolean.TRUE, r.getKioskRequired());
        assertEquals(Boolean.TRUE, r.getKioskRunning());
        assertEquals(Boolean.FALSE, r.getKioskAdminUnlocked());
        assertEquals("com.hpmoqa.tablet_client", r.getKioskAppPackage());
        assertEquals("1.2.8", r.getKioskAppVersion());
        assertEquals("6.40-l24.5", r.getAgentVersion());
        assertEquals("HP01", r.getLockerShortUid());
        assertEquals(Integer.valueOf(2), r.getAppErrors24h());
        assertEquals(Long.valueOf(1763289950000L), r.getAppPublishedAt());
        assertEquals("unlock HTTP 503", r.getLastApiError());
        assertEquals(golden().get("power"), MAPPER.readTree(r.getJson()).get("power"));
    }

    @Test
    public void rejectsBadEnvelopes() throws Exception {
        ObjectNode v2 = golden();
        v2.put("v", 2);
        assertRejected(envelope(v2));

        assertRejected("{\"sent_at\":1,\"reports\":[]}");

        JsonNode[] many = new JsonNode[51];
        for (int i = 0; i < many.length; i++) {
            many[i] = golden();
        }
        assertRejected(envelope(many));

        ObjectNode noTs = golden();
        noTs.remove("ts");
        assertRejected(envelope(noTs));

        ObjectNode big = golden();
        big.put("padding", new String(new char[262_144]).replace('\0', 'x'));
        assertRejected(envelope(big));

        assertRejected("{not json");
        assertRejected("{\"reports\":[]}");
        assertRejected(null);
    }

    @Test
    public void outOfRangeBecomesNull() throws Exception {
        ObjectNode r = golden();
        ((ObjectNode) r.get("power")).put("battery_pct", 101);
        assertNull(parseOne(r).getBatteryPct());
        ((ObjectNode) r.get("power")).put("battery_pct", -1);
        assertNull(parseOne(r).getBatteryPct());
        ((ObjectNode) r.get("power")).put("temperature_c", 120.5);
        assertNull(parseOne(r).getTemperatureC());
        ((ObjectNode) r.get("network")).put("rssi_dbm", 5);
        assertNull(parseOne(r).getRssiDbm());
        ((ObjectNode) r.get("system")).put("free_storage_mb", -3);
        assertNull(parseOne(r).getFreeStorageMb());
    }

    @Test
    public void stringsTruncated() throws Exception {
        ObjectNode r = golden();
        String long300 = new String(new char[300]).replace('\0', 'm');
        ((ObjectNode) r.get("system")).put("model", long300);
        ((ObjectNode) r.get("app")).put("last_api_error", long300);
        ParsedReport p = parseOne(r);
        assertEquals(64, p.getModel().length());
        assertEquals(200, p.getLastApiError().length());
    }

    @Test
    public void missingGroupsAreNull() throws Exception {
        ObjectNode r = golden();
        r.remove("app");
        r.remove("network");
        ParsedReport p = parseOne(r);
        assertNull(p.getLockerShortUid());
        assertNull(p.getAppErrors24h());
        assertNull(p.getAppPublishedAt());
        assertNull(p.getRssiDbm());
        assertNull(p.getMqttConnected());
        assertEquals(Integer.valueOf(87), p.getBatteryPct());
    }

    @Test
    public void nullGroupsAndWrongTypesAreNull() throws Exception {
        ObjectNode r = golden();
        r.putNull("app");
        ((ObjectNode) r.get("power")).put("charging", "yes");
        ((ObjectNode) r.get("power")).put("battery_pct", "87");
        ParsedReport p = parseOne(r);
        assertNull(p.getLockerShortUid());
        assertNull(p.getCharging());
        assertNull(p.getBatteryPct());
        assertFalse(p.getJson().isEmpty());
        assertTrue(p.getJson().startsWith("{"));
    }

    @Test
    public void nonObjectReportRejected() {
        assertRejected("{\"sent_at\":1,\"reports\":[5]}");
    }

    @Test
    public void jsonIsRebuiltFromValidatedFields() throws Exception {
        ObjectNode r = golden();
        r.put("evil", "x");
        ((ObjectNode) r.get("system")).put("unknown_key", 1);
        ((ObjectNode) r.get("system")).put("model", new String(new char[300]).replace('\0', 'm'));
        ((ObjectNode) r.get("power")).put("battery_pct", 101);
        JsonNode out = MAPPER.readTree(parseOne(r).getJson());
        assertFalse(out.has("evil"));
        assertFalse(out.get("system").has("unknown_key"));
        assertEquals(64, out.get("system").get("model").asText().length());
        assertTrue(out.get("power").get("battery_pct").isNull());
        assertEquals(87, MAPPER.readTree(parseOne(golden()).getJson()).get("power").get("battery_pct").asInt());
        assertEquals(golden().get("kiosk"), MAPPER.readTree(parseOne(golden()).getJson()).get("kiosk"));
    }

    @Test
    public void controlCharactersStripped() throws Exception {
        ObjectNode r = golden();
        ((ObjectNode) r.get("system")).put("model", "SM\u0000-X\u001f1\u007f10");
        assertEquals("SM-X110", parseOne(r).getModel());
        assertFalse(parseOne(r).getJson().contains("\\u0000"));
    }

    @Test
    public void truncationIsSurrogateSafe() throws Exception {
        ObjectNode r = golden();
        String s = new String(new char[63]).replace('\0', 'a') + "\uD83D\uDE00";
        ((ObjectNode) r.get("system")).put("model", s);
        String m = parseOne(r).getModel();
        assertEquals(63, m.length());
        assertFalse(Character.isHighSurrogate(m.charAt(m.length() - 1)));
    }

    @Test
    public void trailingTokensRejected() throws Exception {
        String one = envelope(golden());
        assertRejected(one + one);
    }

    @Test
    public void hugeVersionRejected() throws Exception {
        ObjectNode r = golden();
        r.put("v", 4294967297L);
        assertRejected(envelope(r));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void reportListUnmodifiable() throws Exception {
        ReportParser.parse(envelope(golden())).getReports().clear();
    }
}
