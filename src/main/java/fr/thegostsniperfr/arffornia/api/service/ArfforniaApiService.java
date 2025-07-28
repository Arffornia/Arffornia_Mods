package fr.thegostsniperfr.arffornia.api.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles HTTP requests and JSON parsing.
 */
public class ArfforniaApiService {

    private static final String API_BASE_URL = "http://127.0.0.1:8000/api"; // TODO Add this in config file

    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    private final AtomicReference<String> serviceAuthToken = new AtomicReference<>(null);

    /**
     * Fetches the PROGRESSION DATA for a specific player (completed milestones, target).
     *
     * @param playerUuid The UUID of the player.
     * @return A CompletableFuture containing the player's specific progress data.
     */
    public CompletableFuture<ArfforniaApiDtos.GraphData> fetchPlayerGraphData(String playerUuid) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/stages/player/get/" + playerUuid))
                .header("Accept", "application/json")
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(json -> gson.fromJson(json, ArfforniaApiDtos.GraphData.class));
    }

    /**
     * Fetches the detailed information for a single milestone asynchronously.
     * @param nodeId The ID of the node to fetch.
     * @return A CompletableFuture containing the parsed milestone details.
     */
    public CompletableFuture<ArfforniaApiDtos.MilestoneDetails> fetchMilestoneDetails(int nodeId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/milestone/get/" + nodeId))
                .header("Accept", "application/json")
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(json -> gson.fromJson(json, ArfforniaApiDtos.MilestoneDetails.class));
    }

    /**
     * Fetches the server's progression configuration, including the list of banned recipes.
     * This should be called once on server start or /reload.
     *
     * @return A CompletableFuture containing the parsed progression config.
     */
    public CompletableFuture<ArfforniaApiDtos.ProgressionConfig> fetchProgressionConfig() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/progression/config"))
                .header("Accept", "application/json")
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(json -> gson.fromJson(json, ArfforniaApiDtos.ProgressionConfig.class));
    }

    /**
     * Retrieves an authentication token for the game server.
     * The token is cached.
     * @return A CompletableFuture containing the token.
     */
    private CompletableFuture<String> getServiceAuthToken() {
        if (serviceAuthToken.get() != null) {
            return CompletableFuture.completedFuture(serviceAuthToken.get());
        }

        String svcId = "minecraft-server-svc";
        String svcSecret = "minecraft-server-svc"; // TODO Add this in config file ...

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("svc_id", svcId);
        requestBody.addProperty("secret", svcSecret);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/auth/token/svc"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        JsonObject jsonResponse = gson.fromJson(response.body(), JsonObject.class);
                        String token = jsonResponse.get("token").getAsString();
                        serviceAuthToken.set(token);
                        Arffornia.LOGGER.info("Successfully authenticated service account.");
                        return token;
                    } else {
                        Arffornia.LOGGER.error("Failed to authenticate service account. Status: {}, Body: {}", response.statusCode(), response.body());
                        throw new RuntimeException("Authentication failed for service account.");
                    }
                });
    }

    /**
     * Notifies the backend that a player has joined a team.
     */
    public void sendPlayerJoinedTeam(UUID playerUuid, UUID teamUuid, String teamName) {
        Arffornia.ARFFORNA_API_SERVICE.getServiceAuthToken().thenAccept(token -> {
            JsonObject body = new JsonObject();
            body.addProperty("player_uuid", playerUuid.toString().replace("-", ""));
            body.addProperty("team_uuid", teamUuid.toString());
            body.addProperty("team_name", teamName);


            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "/teams/player/join"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        if(response.statusCode() != 200) {
                            Arffornia.LOGGER.error("API call to player/join failed with status: {}", response.statusCode());
                        }
                    });
        }).exceptionally(ex -> {
            Arffornia.LOGGER.error("Failed to send player join team update", ex);
            return null;
        });
    }

    /**
     * Notifies the backend that a player has left a team.
     */
    public void sendPlayerLeftTeam(UUID playerUuid) {
        Arffornia.ARFFORNA_API_SERVICE.getServiceAuthToken().thenAccept(token -> {
            JsonObject body = new JsonObject();
            body.addProperty("player_uuid", playerUuid.toString().replace("-", ""));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "/teams/player/leave"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        if(response.statusCode() != 200) {
                            Arffornia.LOGGER.error("API call to player/leave failed with status: {}", response.statusCode());
                        }
                    });
        }).exceptionally(ex -> {
            Arffornia.LOGGER.error("Failed to send player left team update", ex);
            return null;
        });
    }

    /**
     * Fetches the list of completed milestones for a player.
     * @return A CompletableFuture containing the list of milestone IDs.
     */
    public CompletableFuture<List<Integer>> listMilestones(UUID playerUuid) {
        return getServiceAuthToken().thenCompose(token -> {
            JsonObject body = new JsonObject();
            body.addProperty("player_uuid", playerUuid.toString().replace("-", ""));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "/progression/list"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                            // The rest of the parsing logic to extract the list of integers
                            return gson.fromJson(json.get("completed_milestones"), new com.google.gson.reflect.TypeToken<List<Integer>>() {
                            }.getType());
                        }
                        return Collections.emptyList();
                    });
        });
    }

    /**
     * Adds a milestone to a player's active progression.
     */
    public CompletableFuture<Boolean> addMilestone(UUID playerUuid, int milestoneId) {
        return getServiceAuthToken().thenCompose(token -> {
            JsonObject body = new JsonObject();
            body.addProperty("player_uuid", playerUuid.toString().replace("-", ""));
            body.addProperty("milestone_id", milestoneId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "/progression/add"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();

            return sendRequestAndCheckSuccess(request, "addMilestone", playerUuid);
        });
    }

    /**
     * Removes a milestone from a player's active progression.
     */
    public CompletableFuture<Boolean> removeMilestone(UUID playerUuid, int milestoneId) {
        return getServiceAuthToken().thenCompose(token -> {
            JsonObject body = new JsonObject();
            body.addProperty("player_uuid", playerUuid.toString().replace("-", ""));
            body.addProperty("milestone_id", milestoneId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "/progression/remove"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();

            return sendRequestAndCheckSuccess(request, "removeMilestone", playerUuid);
        });
    }

    private CompletableFuture<Boolean> sendRequestAndCheckSuccess(HttpRequest request, String actionName, UUID playerUuid) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() <= 299) {
                        return true;
                    } else {
                        Arffornia.LOGGER.error("API call to {} failed for player {}. Status: {}, Body: {}", actionName, playerUuid, response.statusCode(), response.body());
                        return false;
                    }
                });
    }

    /**
     * Calls the backend to ensure a player exists in the database.
     * Creates the player if they don't. This should be called on player login.
     *
     * @param playerUuid The player's UUID.
     * @param playerName The player's current username.
     * @return A CompletableFuture that resolves to true if the player exists or was created successfully.
     */
    public CompletableFuture<Boolean> ensurePlayerExists(UUID playerUuid, String playerName) {
        return getServiceAuthToken().thenCompose(token -> {
            JsonObject body = new JsonObject();
            body.addProperty("uuid", playerUuid.toString().replace("-", ""));
            body.addProperty("username", playerName);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "/player/ensure-exists"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();

            return sendRequestAndCheckSuccess(request, "ensurePlayerExists", playerUuid);
        });
    }

    /**
     * Sets the player's targeted milestone via the API.
     *
     * @param milestoneId The ID of the milestone to target.
     * @param playerAuthToken The player's personal Sanctum token.
     * @return A CompletableFuture that resolves to true on success.
     */
    public CompletableFuture<Boolean> setTargetMilestone(int milestoneId, String playerAuthToken, String playerUuid) {
        JsonObject body = new JsonObject();
        body.addProperty("milestone_id", milestoneId);
        body.addProperty("player_uuid", playerUuid);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/progression/set-target"))
                .header("Authorization", "Bearer " + playerAuthToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() >= 200 && response.statusCode() <= 299);
    }
}