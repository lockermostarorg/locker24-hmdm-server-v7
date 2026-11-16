package com.hmdm.rest.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InternalIPFilterTest {

    private static final String PUBLIC_IP = "203.0.113.5";
    private static final Map<String, String> SPOOFED = Map.of(
            "X-Forwarded-For", "127.0.0.1",
            "X-Real-IP", "127.0.0.1",
            "Forwarded", "for=127.0.0.1");

    private static HttpServletRequest request(String remote, String local, Map<String, String> headers) {
        return (HttpServletRequest) Proxy.newProxyInstance(InternalIPFilterTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getRemoteAddr":
                            return remote;
                        case "getLocalAddr":
                            return local;
                        case "getHeader":
                            return headers.get((String) args[0]);
                        default:
                            return null;
                    }
                });
    }

    private static final class Outcome {
        final AtomicBoolean reached = new AtomicBoolean();
        final AtomicInteger status = new AtomicInteger();
    }

    private static Outcome run(InternalIPFilter filter, String remote, String local, Map<String, String> headers)
            throws Exception {
        Outcome o = new Outcome();
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                InternalIPFilterTest.class.getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("sendError")) {
                        o.status.set((Integer) args[0]);
                    }
                    return null;
                });
        FilterChain chain = (req, res) -> o.reached.set(true);
        filter.doFilter(request(remote, local, headers), response, chain);
        return o;
    }

    @Test
    public void loopbackPasses() throws Exception {
        InternalIPFilter filter = new InternalIPFilter("127.0.0.1");
        for (String remote : new String[]{"127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "127.0.0.2", "::ffff:127.0.0.1"}) {
            Outcome o = run(filter, remote, "127.0.0.1", Map.of());
            assertTrue(remote, o.reached.get());
            assertEquals(remote, 0, o.status.get());
        }
    }

    @Test
    public void publicAddressIsRejectedBeforeTheResource() throws Exception {
        Outcome o = run(new InternalIPFilter("127.0.0.1"), PUBLIC_IP, "10.0.0.2", Map.of());
        assertFalse(o.reached.get());
        assertEquals(403, o.status.get());
    }

    @Test
    public void spoofedHeadersFromAPublicAddressAreIgnored() throws Exception {
        Outcome o = run(new InternalIPFilter("127.0.0.1"), PUBLIC_IP, "10.0.0.2", SPOOFED);
        assertFalse(o.reached.get());
        assertEquals(403, o.status.get());
    }

    @Test
    public void spoofedHeadersAreIgnoredEvenWhenRemoteEqualsLocal() throws Exception {
        Outcome o = run(new InternalIPFilter("127.0.0.1"), "10.0.0.2", "10.0.0.2", SPOOFED);
        assertFalse(o.reached.get());
        assertEquals(403, o.status.get());
    }

    @Test
    public void configuredSourceIsAllowedToo() throws Exception {
        InternalIPFilter filter = new InternalIPFilter("10.0.0.0/24");
        assertTrue(run(filter, "10.0.0.7", "10.0.0.2", Map.of()).reached.get());
        assertFalse(run(filter, "10.0.1.7", "10.0.0.2", Map.of()).reached.get());
    }

    @Test
    public void blankOrMissingConfigurationMeansLoopbackOnly() throws Exception {
        for (InternalIPFilter filter : new InternalIPFilter[]{new InternalIPFilter(""), new InternalIPFilter(" "),
                new InternalIPFilter((String) null), new InternalIPFilter()}) {
            assertFalse(run(filter, PUBLIC_IP, "10.0.0.2", Map.of()).reached.get());
            assertTrue(run(filter, "127.0.0.1", "127.0.0.1", Map.of()).reached.get());
        }
    }

    @Test
    public void nonLiteralOrEmptyAddressesAreRejected() throws Exception {
        InternalIPFilter filter = new InternalIPFilter("127.0.0.1");
        for (String remote : new String[]{null, "", "localhost", "evil.example"}) {
            assertFalse(String.valueOf(remote), run(filter, remote, "127.0.0.1", Map.of()).reached.get());
        }
    }
}
