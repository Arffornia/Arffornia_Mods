package fr.thegostsniperfr.arffornia.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import fr.thegostsniperfr.arffornia.api.service.ApiHttp;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class HttpRewardsClient implements RewardsClient {
    private final ApiHttp http;
    private final Logger logger;

    public HttpRewardsClient(ApiHttp http, Logger logger) {
        this.http = http;
        this.logger = logger;
    }

    static String compactUuid(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    @Override
    public CompletableFuture<PendingResult> fetchPending(UUID player) {
        String path = "/rewards/pending?player_uuid=" + compactUuid(player);
        return http.sendAuthenticated(http.authGet(path))
                .<PendingResult>thenApply(response -> {
                    if (response == null) {
                        return new PendingResult.Failed();
                    }
                    if (response.statusCode() == 404) {
                        return new PendingResult.NotLinked();
                    }
                    if (!ApiHttp.isSuccess(response.statusCode())) {
                        logger.error("Fetching pending rewards failed with status {}", response.statusCode());
                        return new PendingResult.Failed();
                    }
                    JsonObject body = parseObject(response.body());
                    JsonElement list = body == null ? null : body.get("rewards");
                    if (list == null || !list.isJsonArray()) {
                        logger.error("Pending rewards response has no rewards list");
                        return new PendingResult.Failed();
                    }
                    List<PendingReward> rewards = new ArrayList<>();
                    for (JsonElement element : list.getAsJsonArray()) {
                        PendingReward reward = parseReward(element);
                        if (reward != null) {
                            rewards.add(reward);
                        }
                    }
                    return new PendingResult.Found(rewards);
                })
                .exceptionally(ex -> {
                    logger.error("Fetching pending rewards failed: {}", ex.getMessage());
                    return new PendingResult.Failed();
                });
    }

    /**
     * Claims with one retry on a transport error or 5xx, using the same key: if the first attempt went through, the
     * retry is answered as a replay and the reward is still delivered once.
     */
    @Override
    public CompletableFuture<ClaimResult> claim(long rewardId, UUID player, String idempotencyKey) {
        return attemptClaim(rewardId, player, idempotencyKey).thenCompose(result -> result instanceof ClaimResult.Failed
                ? attemptClaim(rewardId, player, idempotencyKey)
                : CompletableFuture.completedFuture(result));
    }

    private CompletableFuture<ClaimResult> attemptClaim(long rewardId, UUID player, String idempotencyKey) {
        JsonObject body = new JsonObject();
        body.addProperty("player_uuid", compactUuid(player));
        String path = "/rewards/" + rewardId + "/claim";
        Function<String, HttpRequest> request = bearer -> http.request(path)
                .header("Authorization", "Bearer " + bearer)
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString(http.gson().toJson(body)))
                .build();

        return http.sendAuthenticated(request)
                .thenApply(response -> toClaimResult(rewardId, response))
                .exceptionally(ex -> {
                    logger.warn("Claim of reward {} failed: {}", rewardId, ex.getMessage());
                    return new ClaimResult.Failed();
                });
    }

    private ClaimResult toClaimResult(long rewardId, @Nullable HttpResponse<String> response) {
        if (response == null || response.statusCode() >= 500) {
            logger.warn("Claim of reward {} failed with status {}", rewardId, response == null ? "no token" : response.statusCode());
            return new ClaimResult.Failed();
        }
        if (!ApiHttp.isSuccess(response.statusCode())) {
            logger.warn("Claim of reward {} rejected with status {}", rewardId, response.statusCode());
            return new ClaimResult.Rejected(response.statusCode());
        }
        JsonObject body = parseObject(response.body());
        PendingReward reward = body == null ? null : parseReward(body.get("reward"));
        if (reward == null || reward.id() != rewardId) {
            logger.error("Claim of reward {} returned an unexpected body", rewardId);
            return new ClaimResult.Failed();
        }
        return new ClaimResult.Claimed(reward);
    }

    @Override
    public CompletableFuture<StreakResult> recordDailyConnection(UUID player) {
        JsonObject body = new JsonObject();
        body.addProperty("player_uuid", compactUuid(player));
        return http.sendAuthenticated(http.jsonPost("/rewards/day-streak", http.gson().toJson(body)))
                .thenApply(response -> {
                    if (response == null || !ApiHttp.isSuccess(response.statusCode())) {
                        logger.warn("Recording the daily connection failed with status {}", response == null ? "no token" : response.statusCode());
                        return (StreakResult) null;
                    }
                    JsonObject json = parseObject(response.body());
                    if (json == null || !json.has("day_streak") || !json.has("is_first_connection_today")) {
                        return null;
                    }
                    try {
                        return new StreakResult(json.get("day_streak").getAsInt(), json.get("is_first_connection_today").getAsBoolean());
                    } catch (RuntimeException e) {
                        return null;
                    }
                })
                .exceptionally(ex -> {
                    logger.warn("Recording the daily connection failed: {}", ex.getMessage());
                    return null;
                });
    }

    @Nullable
    private JsonObject parseObject(String body) {
        try {
            JsonElement json = http.gson().fromJson(body, JsonElement.class);
            return json != null && json.isJsonObject() ? json.getAsJsonObject() : null;
        } catch (JsonParseException e) {
            return null;
        }
    }

    @Nullable
    static PendingReward parseReward(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject json = element.getAsJsonObject();
        JsonElement id = json.get("id");
        JsonElement contents = json.get("contents");
        if (id == null || !id.isJsonPrimitive() || !id.getAsJsonPrimitive().isNumber() || contents == null || !contents.isJsonArray()) {
            return null;
        }
        List<RewardEntry> entries = new ArrayList<>();
        for (JsonElement entry : (JsonArray) contents) {
            entries.add(RewardEntry.parse(entry));
        }
        JsonElement source = json.get("source");
        return new PendingReward(id.getAsLong(), source != null && source.isJsonPrimitive() ? source.getAsString() : "unknown", List.copyOf(entries));
    }
}
