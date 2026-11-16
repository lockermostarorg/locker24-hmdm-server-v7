package com.hmdm.plugins.locker24health.alert;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WebhookSenderTest {

    private HttpServer server;
    private ExecutorService pool;
    private WebhookSender sender;

    @Before
    public void setUp() throws IOException {
        pool = Executors.newCachedThreadPool();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(pool);
        server.createContext("/ok", ex -> {
            ex.getRequestBody().readAllBytes();
            ex.sendResponseHeaders(204, -1);
            ex.close();
        });
        server.createContext("/huge", ex -> {
            ex.getRequestBody().readAllBytes();
            ex.sendResponseHeaders(200, 0);
            byte[] chunk = new byte[16_384];
            try (OutputStream out = ex.getResponseBody()) {
                for (int i = 0; i < 2_000; i++) {
                    out.write(chunk);
                }
            } catch (IOException ignored) {
            }
        });
        server.createContext("/drip", ex -> {
            ex.getRequestBody().readAllBytes();
            ex.sendResponseHeaders(200, 0);
            try (OutputStream out = ex.getResponseBody()) {
                for (int i = 0; i < 600; i++) {
                    out.write('x');
                    out.flush();
                    Thread.sleep(100);
                }
            } catch (IOException | InterruptedException ignored) {
            }
        });
        server.start();
        sender = new WebhookSender(Duration.ofSeconds(1), Duration.ofMillis(1500));
    }

    @After
    public void tearDown() {
        server.stop(0);
        pool.shutdownNow();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    @Test
    public void returnsTheStatusCode() throws Exception {
        assertEquals(204, sender.post(uri("/ok"), "{}"));
    }

    @Test
    public void largeResponseBodyIsCutOff() throws Exception {
        long start = System.nanoTime();
        assertEquals(200, sender.post(uri("/huge"), "{}"));
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1_500);
    }

    @Test
    public void slowBodyIsBoundedByTheTotalTimeout() throws Exception {
        long start = System.nanoTime();
        try {
            sender.post(uri("/drip"), "{}");
            fail("expected a timeout");
        } catch (IOException e) {
            assertFalse(e.getMessage().contains("127.0.0.1"));
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertTrue("took " + ms + " ms", ms >= 1_400 && ms < 4_000);
    }

    @Test
    public void onlyHttpsAndTheUrlNeverAppearsInTheError() throws Exception {
        try {
            sender.send("http://hooks.example/secret-token", "{}");
            fail("expected a refusal");
        } catch (IllegalArgumentException e) {
            assertFalse(e.getMessage().contains("secret-token"));
        }
    }

    @Test
    public void discardStopsReadingAfterTheLimit() {
        WebhookSender.BoundedDiscard d = new WebhookSender.BoundedDiscard(10);
        AtomicBoolean cancelled = new AtomicBoolean();
        d.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
                cancelled.set(true);
            }
        });
        d.onNext(List.of(ByteBuffer.allocate(6)));
        assertFalse(cancelled.get());
        assertFalse(d.getBody().toCompletableFuture().isDone());
        d.onNext(List.of(ByteBuffer.allocate(6)));
        assertTrue(cancelled.get());
        assertTrue(d.getBody().toCompletableFuture().isDone());
    }
}
