package fr.thegostsniperfr.arffornia.api.service;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.helpers.NOPLogger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiHttpTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger authCalls = new AtomicInteger();
    private final AtomicInteger actionCalls = new AtomicInteger();
    private volatile int actionStatus = 200;
    private volatile int authStatus = 200;
    private volatile long authDelayMs = 0;
    private volatile String publicBody = "{\"token\":\"x\"}";
    private volatile int publicStatus = 200;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/api/auth/token/svc", exchange -> {
            int n = authCalls.incrementAndGet();
            sleep(authDelayMs);
            respond(exchange, authStatus, "{\"token\":\"token-" + n + "\"}");
        });
        server.createContext("/api/action", exchange -> {
            actionCalls.incrementAndGet();
            respond(exchange, actionStatus, "{}");
        });
        server.createContext("/api/public", exchange -> respond(exchange, publicStatus, publicBody));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ApiHttp http() {
        return new ApiHttp(NOPLogger.NOP_LOGGER, () -> baseUrl, () -> "id", () -> "secret");
    }

    private static HttpResponse<String> post(ApiHttp http) throws Exception {
        return http.sendAuthenticated(http.jsonPost("/action", "{}")).get(20, TimeUnit.SECONDS);
    }

    @Test
    void reusesCachedToken() throws Exception {
        ApiHttp http = http();
        assertEquals(200, post(http).statusCode());
        assertEquals(200, post(http).statusCode());
        assertEquals(1, authCalls.get());
        assertEquals(2, actionCalls.get());
    }

    @Test
    void unauthorizedTriggersExactlyOneReauthAndRetry() throws Exception {
        actionStatus = 401;
        HttpResponse<String> response = post(http());
        assertEquals(401, response.statusCode());
        assertEquals(2, authCalls.get());
        assertEquals(2, actionCalls.get());
    }

    @Test
    void recoversAfterTokenIsRevoked() throws Exception {
        ApiHttp http = http();
        assertEquals(200, post(http).statusCode());
        actionStatus = 401;
        assertEquals(401, post(http).statusCode());
        actionStatus = 200;
        assertEquals(200, post(http).statusCode());
        assertEquals(3, authCalls.get());
    }

    @Test
    void failedAuthenticationReturnsNullWithoutCallingTheEndpoint() throws Exception {
        authStatus = 403;
        assertNull(post(http()));
        assertEquals(0, actionCalls.get());
    }

    @Test
    void concurrentCallersShareOneAuthentication() throws Exception {
        authDelayMs = 300;
        ApiHttp http = http();
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            futures.add(http.sendAuthenticated(http.jsonPost("/action", "{}")));
        }
        for (CompletableFuture<HttpResponse<String>> f : futures) {
            assertEquals(200, f.get(20, TimeUnit.SECONDS).statusCode());
        }
        assertEquals(1, authCalls.get());
    }

    @Test
    void getJsonParsesSuccessAndReturnsNullOnErrorStatusOrBadJson() throws Exception {
        ApiHttp http = http();
        JsonObject ok = http.<JsonObject>getJson("/public", JsonObject.class, "test").get(20, TimeUnit.SECONDS);
        assertNotNull(ok);
        assertEquals("x", ok.get("token").getAsString());

        publicStatus = 500;
        assertNull(http.<JsonObject>getJson("/public", JsonObject.class, "test").get(20, TimeUnit.SECONDS));

        publicStatus = 200;
        publicBody = "<html>not json";
        assertNull(http.<JsonObject>getJson("/public", JsonObject.class, "test").get(20, TimeUnit.SECONDS));
    }

    @Test
    void getJsonReturnsNullWhenServerIsUnreachable() throws Exception {
        ApiHttp http = new ApiHttp(NOPLogger.NOP_LOGGER, () -> "http://127.0.0.1:1/api", () -> "id", () -> "secret");
        assertNull(http.<JsonObject>getJson("/public", JsonObject.class, "test").get(20, TimeUnit.SECONDS));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
