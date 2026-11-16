package com.hmdm.plugins.locker24health.alert;

import com.google.inject.Singleton;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Singleton
public class WebhookSender {

    public static final Duration TIMEOUT = Duration.ofSeconds(5);

    private volatile HttpClient client;

    public int send(String url, String body) throws IOException, InterruptedException {
        URI uri = URI.create(url.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("webhook must use https");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Locker24-MDM")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private HttpClient client() {
        HttpClient c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    c = HttpClient.newBuilder()
                            .connectTimeout(TIMEOUT)
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build();
                    client = c;
                }
            }
        }
        return c;
    }
}
