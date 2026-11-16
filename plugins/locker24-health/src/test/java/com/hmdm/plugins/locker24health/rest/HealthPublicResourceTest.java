package com.hmdm.plugins.locker24health.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.Device;
import com.hmdm.plugin.service.PluginStatusCache;
import com.hmdm.plugins.locker24health.ingest.IngestService;
import com.hmdm.plugins.locker24health.model.ParsedBatch;
import com.hmdm.rest.json.Response;
import com.hmdm.security.SecurityContext;
import com.hmdm.util.CryptoUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HealthPublicResourceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SECRET = "s";

    private UnsecureDAO unsecureDAO;
    private PluginStatusCache pluginStatusCache;
    private IngestService ingest;
    private HealthPublicResource resource;
    private Device device;

    @Before
    public void setUp() {
        unsecureDAO = mock(UnsecureDAO.class);
        pluginStatusCache = mock(PluginStatusCache.class);
        ingest = mock(IngestService.class);
        resource = new HealthPublicResource(unsecureDAO, pluginStatusCache, ingest, SECRET);
        device = new Device();
        device.setId(11);
        device.setCustomerId(2);
        device.setNumber("M-01");
        when(unsecureDAO.getDeviceByNumber("M-01")).thenReturn(device);
        when(ingest.store(any(Device.class), any(ParsedBatch.class), anyLong())).thenReturn(1);
    }

    @After
    public void tearDown() {
        SecurityContext.release();
    }

    private static String envelope(int reports) {
        ObjectNode env = MAPPER.createObjectNode();
        env.put("sent_at", 1763290005000L);
        ArrayNode arr = env.putArray("reports");
        for (int i = 0; i < reports; i++) {
            ObjectNode r = arr.addObject();
            r.put("v", 1);
            r.put("ts", 1763290000000L + i);
            r.putObject("power").put("battery_pct", 55);
        }
        return env.toString();
    }

    private static InputStream stream(String body) {
        return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    private static String sig(String number) {
        return CryptoUtil.getSHA1String(SECRET + number);
    }

    private Response post(String number, String signature, String contentLength, InputStream body) {
        return resource.report(number, signature, contentLength, body);
    }

    private Response post(String number, String signature, String body) {
        return post(number, signature, null, stream(body));
    }

    private static void assertError(String message, Response r) {
        assertEquals(Response.ResponseStatus.ERROR, r.getStatus());
        assertEquals(message, r.getMessage());
    }

    private void assertNothingStored() {
        verify(ingest, never()).store(any(Device.class), any(ParsedBatch.class), anyLong());
    }

    private static InputStream unreadable() {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                throw new AssertionError("body must not be read");
            }
        };
    }

    @Test
    public void unknownDeviceRejected() {
        when(unsecureDAO.getDeviceByNumber("X")).thenReturn(null);

        Response r = post("X", sig("X"), null, unreadable());

        assertError("error.notfound.device", r);
        assertNothingStored();
    }

    @Test
    public void renamedDeviceFoundByOldNumber() {
        Device renamed = new Device();
        renamed.setId(12);
        renamed.setCustomerId(2);
        renamed.setNumber("M-01-NEW");
        when(unsecureDAO.getDeviceByOldNumber("M-01-OLD")).thenReturn(renamed);

        assertError("error.signature", post("M-01-OLD", sig("M-01-NEW"), null, unreadable()));
        assertNothingStored();

        Response r = post("M-01-OLD", sig("M-01-OLD"), envelope(1));

        assertEquals(Response.ResponseStatus.OK, r.getStatus());
        verify(ingest).store(eq(renamed), any(ParsedBatch.class), anyLong());
    }

    @Test
    public void unknownDeviceConsultsOldNumberBeforeRejecting() {
        post("X", sig("X"), null, unreadable());

        verify(unsecureDAO).getDeviceByOldNumber("X");
    }

    @Test
    public void deviceLookupFailureIsInternalErrorNotNotFound() {
        when(unsecureDAO.getDeviceByNumber("M-01")).thenThrow(new RuntimeException("pool exhausted"));

        Response r = post("M-01", sig("M-01"), null, unreadable());

        assertError("error.internal.server", r);
        assertNothingStored();
    }

    @Test
    public void printableMasksControlAndLineSeparators() {
        assertEquals("a?b?c?d?e", HealthPublicResource.printable("a\nb\u0085c\u2028d\u2029e", 64));
        assertEquals("abc", HealthPublicResource.printable("abcdef", 3));
        assertEquals("null", HealthPublicResource.printable(null, 64));
    }

    @Test
    public void signatureRequired() {
        assertError("error.signature", post("M-01", null, null, unreadable()));
        assertError("error.signature", post("M-01", sig("M-02"), null, unreadable()));
        assertError("error.signature", post("M-01", "", null, unreadable()));
        assertNothingStored();

        assertEquals(Response.ResponseStatus.OK, post("M-01", sig("M-01").toUpperCase(), envelope(1)).getStatus());
        assertEquals(Response.ResponseStatus.OK, post("M-01", sig("M-01").toLowerCase(), envelope(1)).getStatus());
    }

    @Test
    public void disabledPluginRejected() {
        when(pluginStatusCache.isPluginDisabled("locker24-health")).thenReturn(true);

        Response r = post("M-01", sig("M-01"), null, unreadable());

        assertError("error.resource.disabled", r);
        assertNothingStored();
    }

    @Test
    public void invalidBodyRejected() {
        assertError("error.validation", post("M-01", sig("M-01"), envelope(51)));
        assertError("error.validation", post("M-01", sig("M-01"), "{not json"));
        assertError("error.validation", post("M-01", sig("M-01"), ""));
        assertNothingStored();
    }

    @Test
    public void acceptedBatchIsStored() {
        when(ingest.store(any(Device.class), any(ParsedBatch.class), anyLong())).thenReturn(3);
        long before = System.currentTimeMillis();

        Response r = post("M-01", sig("M-01"), envelope(3));

        assertEquals(Response.ResponseStatus.OK, r.getStatus());
        assertEquals(3, ((Map<?, ?>) r.getData()).get("accepted"));
        ArgumentCaptor<ParsedBatch> batch = ArgumentCaptor.forClass(ParsedBatch.class);
        ArgumentCaptor<Long> receivedAt = ArgumentCaptor.forClass(Long.class);
        verify(ingest).store(eq(device), batch.capture(), receivedAt.capture());
        assertEquals(3, batch.getValue().getReports().size());
        assertEquals(1763290005000L, batch.getValue().getSentAt());
        long after = System.currentTimeMillis();
        long r0 = receivedAt.getValue();
        assertEquals(true, r0 >= before && r0 <= after);
    }

    @Test
    public void declaredOversizeBodyRejectedUnread() {
        Response r = post("M-01", sig("M-01"), "262145", unreadable());

        assertError("error.validation", r);
        assertNothingStored();
    }

    @Test
    public void oversizeBodyRejectedWhileReading() {
        String big = envelope(1);
        StringBuilder b = new StringBuilder(big);
        while (b.length() <= HealthPublicResource.MAX_BODY_BYTES) {
            b.append(' ');
        }

        Response r = post("M-01", sig("M-01"), b.toString());

        assertError("error.validation", r);
        assertNothingStored();
    }

    @Test
    public void multiByteBodyCappedInBytesNotChars() {
        String base = envelope(1);
        String pad = "é";
        int chars = (HealthPublicResource.MAX_BODY_BYTES - base.length()) / 2 + 1;
        String body = base.substring(0, base.length() - 1) + ",\"x\":\"" + pad.repeat(chars) + "\"}";

        Response r = post("M-01", sig("M-01"), body);

        assertError("error.validation", r);
        assertNothingStored();
    }

    @Test
    public void bodyExactlyAtCapAccepted() {
        StringBuilder b = new StringBuilder(envelope(1));
        while (b.length() < HealthPublicResource.MAX_BODY_BYTES) {
            b.append(' ');
        }

        Response r = post("M-01", sig("M-01"), String.valueOf(b.length()), stream(b.toString()));

        assertEquals(Response.ResponseStatus.OK, r.getStatus());
    }

    @Test
    public void unparseableContentLengthFallsBackToBoundedRead() {
        Response r = post("M-01", sig("M-01"), "abc", stream(envelope(1)));

        assertEquals(Response.ResponseStatus.OK, r.getStatus());
    }

    @Test
    public void storageFailureIsInternalErrorNotADropCode() {
        when(ingest.store(any(Device.class), any(ParsedBatch.class), anyLong()))
                .thenThrow(new RuntimeException("connection refused"));

        Response r = post("M-01", sig("M-01"), envelope(1));

        assertError("error.internal.server", r);
    }

    @Test
    public void bodyReadFailureIsInternalError() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("client went away");
            }
        };

        Response r = post("M-01", sig("M-01"), null, broken);

        assertError("error.internal.server", r);
        assertNothingStored();
    }

    @Test
    public void securityContextReleased() {
        post("M-01", sig("M-01"), envelope(1));
        assertNull(SecurityContext.get());

        when(pluginStatusCache.isPluginDisabled("locker24-health")).thenReturn(true);
        post("M-01", sig("M-01"), envelope(1));
        assertNull(SecurityContext.get());
    }
}
