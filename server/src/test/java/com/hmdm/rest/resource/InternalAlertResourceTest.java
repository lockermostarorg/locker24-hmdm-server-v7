package com.hmdm.rest.resource;

import com.hmdm.persistence.domain.Alert;
import com.hmdm.rest.filter.InternalIPFilter;
import com.hmdm.rest.json.Response;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.ForbiddenException;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class InternalAlertResourceTest {

    private static HttpServletRequest request(String remote, String uri, Map<String, String> headers) {
        return (HttpServletRequest) Proxy.newProxyInstance(InternalAlertResourceTest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getRemoteAddr":
                            return remote;
                        case "getLocalAddr":
                            return remote;
                        case "getRequestURI":
                            return uri;
                        case "getHeader":
                            return headers.get((String) args[0]);
                        default:
                            return null;
                    }
                });
    }

    private static InternalAlertResource resource() {
        return new InternalAlertResource(null, null, new InternalIPFilter("127.0.0.1"));
    }

    private static void assertForbidden(InternalAlertResource r, HttpServletRequest req) {
        try {
            r.triggerAlert(new Alert(), req);
            fail("expected 403");
        } catch (ForbiddenException e) {
            assertEquals(403, e.getResponse().getStatus());
        }
    }

    @Test
    public void publicAddressIsRefusedEvenWhenTheServletFilterWasSkipped() {
        assertForbidden(resource(), request("203.0.113.5", "/rest/internal;x=1/alert", Map.of()));
    }

    @Test
    public void spoofedForwardingHeadersAreIgnored() {
        assertForbidden(resource(), request("203.0.113.5", "/rest/internal;x=1/alert",
                Map.of("X-Forwarded-For", "127.0.0.1", "X-Real-IP", "127.0.0.1")));
    }

    @Test
    public void missingRequestIsRefused() {
        assertForbidden(resource(), null);
    }

    @Test
    public void swaggerConstructorIsLoopbackOnly() {
        assertForbidden(new InternalAlertResource(), request("203.0.113.5", "/rest/internal/alert", Map.of()));
    }

    @Test
    public void loopbackGetsPastTheCheck() {
        for (String remote : new String[]{"127.0.0.1", "::1", "::ffff:127.0.0.1"}) {
            Response r = resource().triggerAlert(new Alert(), request(remote, "/rest/internal/alert", Map.of()));
            assertEquals(remote, Response.ResponseStatus.ERROR, r.getStatus());
        }
    }
}
