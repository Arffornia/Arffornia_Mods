package fr.thegostsniperfr.arffornia.api.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * HTTP transport for the Arffornia API: timeouts, status checks and service-account authentication.
 * <p>
 * Authenticated calls reuse a cached token. A 401 clears it, re-authenticates once and retries once.
 * Concurrent callers share a single in-flight authentication.
 * This class has no Minecraft dependency so it can be unit tested against a stub server.
 */
public final class ApiHttp {
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient client;
    private final Gson gson = new Gson();
    private final Logger logger;
    private final Supplier<String> baseUrl;
    private final Supplier<String> clientId;
    private final Supplier<String> clientSecret;
    private final AtomicReference<String> token = new AtomicReference<>(null);
    private final AtomicReference<CompletableFuture<String>> pendingAuth = new AtomicReference<>(null);

    public ApiHttp(Logger logger, Supplier<String> baseUrl, Supplier<String> clientId, Supplier<String> clientSecret) {
        this.logger = logger;
        this.baseUrl = baseUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    public Gson gson() {
        return gson;
    }

    public URI uri(String path) {
        return URI.create(baseUrl.get() + path);
    }

    /** Request builder with the request timeout and JSON {@code Accept} header. */
    public HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder()
                .uri(uri(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json");
    }

    public CompletableFuture<HttpResponse<String>> send(HttpRequest request) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    public static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    /**
     * Public GET returning the parsed body, or {@code null} on network error, non-2xx status or invalid JSON.
     */
    public <T> CompletableFuture<T> getJson(String path, Type type, String what) {
        return send(request(path).GET().build())
                .thenApply(response -> {
                    if (!isSuccess(response.statusCode())) {
                        logger.error("API GET {} for {} failed with status {}", path, what, response.statusCode());
                        return (T) null;
                    }
                    try {
                        return gson.<T>fromJson(response.body(), type);
                    } catch (JsonParseException e) {
                        logger.error("API GET {} for {} returned invalid JSON: {}", path, what, e.getMessage());
                        return (T) null;
                    }
                })
                .exceptionally(ex -> {
                    logger.error("API GET {} for {} failed: {}", path, what, ex.getMessage());
                    return null;
                });
    }

    /**
     * Sends an authenticated request built by {@code requestForToken}. On 401 the token is invalidated and the
     * request is retried exactly once with a fresh token.
     *
     * @return the response, or {@code null} when no token could be obtained. Network errors complete exceptionally.
     */
    public CompletableFuture<HttpResponse<String>> sendAuthenticated(Function<String, HttpRequest> requestForToken) {
        return sendAuthenticated(requestForToken, true);
    }

    private CompletableFuture<HttpResponse<String>> sendAuthenticated(Function<String, HttpRequest> requestForToken, boolean retry) {
        return getToken().thenCompose(currentToken -> {
            if (currentToken == null) {
                return CompletableFuture.completedFuture(null);
            }
            return send(requestForToken.apply(currentToken)).thenCompose(response -> {
                if (response.statusCode() == 401) {
                    token.compareAndSet(currentToken, null);
                    if (retry) {
                        logger.warn("API rejected the service token (401). Re-authenticating once.");
                        return sendAuthenticated(requestForToken, false);
                    }
                }
                return CompletableFuture.completedFuture(response);
            });
        });
    }

    /** Builds an authenticated JSON POST for {@code path}. */
    public Function<String, HttpRequest> jsonPost(String path, String jsonBody) {
        return bearer -> request(path)
                .header("Authorization", "Bearer " + bearer)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }

    /** Builds an authenticated GET for {@code path}. */
    public Function<String, HttpRequest> authGet(String path) {
        return bearer -> request(path)
                .header("Authorization", "Bearer " + bearer)
                .GET()
                .build();
    }

    /** Cached token, or a new one. Completes with {@code null} if authentication fails. */
    public CompletableFuture<String> getToken() {
        String cached = token.get();
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<String> mine = new CompletableFuture<>();
        if (!pendingAuth.compareAndSet(null, mine)) {
            CompletableFuture<String> existing = pendingAuth.get();
            return existing != null ? existing : getToken();
        }
        authenticate().whenComplete((result, error) -> {
            pendingAuth.compareAndSet(mine, null);
            mine.complete(error == null ? result : null);
        });
        return mine;
    }

    private CompletableFuture<String> authenticate() {
        JsonObject body = new JsonObject();
        body.addProperty("client_id", clientId.get());
        body.addProperty("client_secret", clientSecret.get());

        HttpRequest request = request("/auth/token/svc")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        return send(request)
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        String newToken = extractToken(response.body());
                        if (newToken != null) {
                            token.set(newToken);
                            logger.info("Successfully authenticated service account.");
                            return newToken;
                        }
                        logger.error("Service account authentication response did not contain a token.");
                        return (String) null;
                    }
                    logger.error("Failed to authenticate service account. Status: {}", response.statusCode());
                    return (String) null;
                })
                .exceptionally(ex -> {
                    logger.error("Could not connect to API to get service auth token: {}", ex.getMessage());
                    return null;
                });
    }

    @Nullable
    private String extractToken(String body) {
        try {
            JsonObject json = gson.fromJson(body, JsonObject.class);
            return json != null && json.has("token") && !json.get("token").isJsonNull() ? json.get("token").getAsString() : null;
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
            return null;
        }
    }
}
