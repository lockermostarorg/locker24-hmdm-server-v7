package com.hmdm.plugins.locker24health.alert;

import com.google.inject.Singleton;
import com.hmdm.plugins.locker24health.settings.NetworkGuard;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Singleton
public class WebhookSender {

    public static final Duration TIMEOUT = Duration.ofSeconds(5);
    public static final Duration TOTAL_TIMEOUT = Duration.ofSeconds(12);
    public static final long MAX_RESPONSE_BYTES = 65_536L;

    private final Duration timeout;
    private final Duration totalTimeout;
    private final Resolver resolver;
    private volatile HttpClient client;

    interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    public WebhookSender() {
        this(TIMEOUT, TOTAL_TIMEOUT);
    }

    WebhookSender(Duration timeout, Duration totalTimeout) {
        this(timeout, totalTimeout, InetAddress::getAllByName);
    }

    WebhookSender(Duration timeout, Duration totalTimeout, Resolver resolver) {
        this.timeout = timeout;
        this.totalTimeout = totalTimeout;
        this.resolver = resolver;
    }

    public int send(String url, String body) throws IOException, InterruptedException {
        URI uri = URI.create(url.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("webhook must use https");
        }
        checkTarget(uri);
        return post(uri, body);
    }

    void checkTarget(URI uri) throws IOException {
        String host = uri.getHost();
        if (host == null) {
            throw new IOException("webhook target not allowed");
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new IOException("webhook host did not resolve");
        }
        for (InetAddress a : addresses) {
            if (NetworkGuard.isBlocked(a)) {
                throw new IOException("webhook target not allowed");
            }
        }
    }

    int post(URI uri, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Locker24-MDM")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        CompletableFuture<HttpResponse<Void>> pending =
                client().sendAsync(request, info -> new BoundedDiscard(MAX_RESPONSE_BYTES));
        try {
            return pending.get(totalTimeout.toMillis(), TimeUnit.MILLISECONDS).statusCode();
        } catch (TimeoutException e) {
            pending.cancel(true);
            throw new IOException("webhook did not finish in time");
        } catch (ExecutionException e) {
            throw new IOException("webhook request failed: " + e.getCause().getClass().getSimpleName());
        }
    }

    private HttpClient client() {
        HttpClient c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    c = HttpClient.newBuilder()
                            .connectTimeout(timeout)
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build();
                    client = c;
                }
            }
        }
        return c;
    }

    static final class BoundedDiscard implements HttpResponse.BodySubscriber<Void> {

        private final long max;
        private final CompletableFuture<Void> done = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private long seen;

        BoundedDiscard(long max) {
            this.max = max;
        }

        @Override
        public CompletionStage<Void> getBody() {
            return done;
        }

        @Override
        public void onSubscribe(Flow.Subscription s) {
            subscription = s;
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            for (ByteBuffer b : items) {
                seen += b.remaining();
            }
            if (seen > max && !done.isDone()) {
                subscription.cancel();
                done.complete(null);
            }
        }

        @Override
        public void onError(Throwable t) {
            done.completeExceptionally(t);
        }

        @Override
        public void onComplete() {
            done.complete(null);
        }
    }
}
