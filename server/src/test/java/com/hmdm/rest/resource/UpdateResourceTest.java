package com.hmdm.rest.resource;

import com.hmdm.rest.json.Response;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class UpdateResourceTest {

    private final AtomicInteger outboundLookups = new AtomicInteger();
    private ProxySelector previous;

    @Before
    public void watchOutboundConnections() {
        previous = ProxySelector.getDefault();
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<java.net.Proxy> select(URI uri) {
                outboundLookups.incrementAndGet();
                return Collections.singletonList(java.net.Proxy.NO_PROXY);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            }
        });
    }

    @After
    public void restore() {
        ProxySelector.setDefault(previous);
    }

    @Test
    public void checkIsDisabledAndMakesNoOutboundCall() {
        Response response = new UpdateResource().checkUpdates();

        assertEquals(Response.ERROR("error.updates.disabled").getStatus(), response.getStatus());
        assertEquals("error.updates.disabled", response.getMessage());
        assertEquals(0, outboundLookups.get());
    }

    @Test
    public void downloadIsDisabledAndMakesNoOutboundCall() {
        Response response = new UpdateResource().downloadUpdates();

        assertEquals(Response.ERROR("error.updates.disabled").getStatus(), response.getStatus());
        assertEquals("error.updates.disabled", response.getMessage());
        assertEquals(0, outboundLookups.get());
    }
}
