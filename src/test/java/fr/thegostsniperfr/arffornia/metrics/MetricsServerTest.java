package fr.thegostsniperfr.arffornia.metrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsServerTest {

    @AfterEach
    void clean() {
        ArfforniaMetrics.reset();
    }

    @Test
    void servesTheCountersAsPrometheusText() throws Exception {
        ArfforniaMetrics.increment(ArfforniaMetrics.REWARD_CLAIMS, "result", "delivered");
        try (MetricsServer server = MetricsServer.start(0)) {
            HttpResponse<String> response = get(server, "GET");

            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/plain"));
            assertTrue(response.body().contains("arffornia_reward_claims_total{result=\"delivered\"} 1"));
        }
    }

    @Test
    void refusesWritingMethods() throws Exception {
        try (MetricsServer server = MetricsServer.start(0)) {
            assertEquals(405, get(server, "POST").statusCode());
        }
    }

    private static HttpResponse<String> get(MetricsServer server, String method) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/metrics"))
                .method(method, HttpRequest.BodyPublishers.noBody()).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
