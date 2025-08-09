package fr.thegostsniperfr.arffornia.api.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static fr.thegostsniperfr.arffornia.config.ApiConfig.*;

/**
 * Handles HTTP requests and JSON parsing.
 */
public class ArfforniaApiService {
    private static final ArfforniaApiService INSTANCE = new ArfforniaApiService();

    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    private final AtomicReference<String> serviceAuthToken = new AtomicReference<>(null);

    private ArfforniaApiService() {
    }

    public static ArfforniaApiService getInstance() {
        return INSTANCE;
    }

    private HttpRequest buildRequest(URI uri, String token, JsonObject body) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
    }

    /**
     * Fetches the active progression ID for a specific player.
     *
     * @param playerUuid The UUID of the player.
     * @return A CompletableFuture containing the player's data including active progression ID.
     */
    public CompletableFuture<ArfforniaApiDtos.PlayerData> fetchPlayerData(String playerUuid) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL.get() + "/profile/uuid/" + playerUuid))
                .header("Accept", "application/json")
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(json -> gson.fromJson(json, ArfforniaApiDtos.PlayerData.class))
                .exceptionally(ex -> {
                    Arffornia.LOGGER.error("Failed to fetch player data from API for UUID {}: {}", playerUuid, ex.getMessage());
                    return null;
                });
    }

    /**
     * Fetches progression data, including the current target milestone.
     *
     * @param progressionId The ID of the progression.
     * @return A CompletableFuture containing the progression data.
     */
    public CompletableFuture<ArfforniaApiDtos.ProgressionData> fetchProgressionData(long progressionId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL.get() + "/progression/" + progressionId))
                .header("Accept", "application/json")
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(json -> gson.fromJson(json, ArfforniaApiDtos.ProgressionData.class))
                .exceptionally(ex -> {
                    Arffornia.LOGGER.error("Failed to fetch progression data from API for ID {}: {}", progressionId, ex.getMessage());
                    return null;
                });
    }

    /**
     * Fetches the PROGRESSION DATA for a specific player (completed milestones, target).
     *
     * @param playerUuid The UUID of the player.
     * @return A CompletableFuture containing the player's specific progress data.
     */
    public CompletableFuture<ArfforniaApiDtos.GraphData> fetchPlayerGraphData(String playerUuid) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL.get() + "/stages/player/get/" + playerUuid))
                .header("Accept", "application/json")
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(json -> gson.fromJson(json, ArfforniaApiDtos.GraphData.class))
                .exceptionally(ex -> {
                    Arffornia.LOGGER.error("Failed to fetch player graph data from API for UUID {}: {}", playerUuid, ex.getMessage());
                    return null;
                });
    }

    /**
     * Fetches the detailed information for a single milestone asynchronously.
     *
     * @param nodeId The ID of the node to fetch.
     * @return A CompletableFuture containing the parsed milestone details.
     */
    public CompletableFuture<ArfforniaApiDtos.MilestoneDetails> fetchMilestoneDetails(int nodeId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL.get() + "/milestone/get/" + nodeId))
                .header("Accept", "application/json")
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(json -> gson.fromJson(json, ArfforniaApiDtos.MilestoneDetails.class))
                .exceptionally(ex -> {
                    Arffornia.LOGGER.error("Failed to fetch milestone details from API for node {}: {}", nodeId, ex.getMessage());
                    return null;
                });
    }

    /**
     * Fetches the server's progression configuration, including the list of banned recipes.
     * This should be called once on server start or /reload.
     *
     * @return A CompletableFuture containing the parsed progression config.
     */
    public CompletableFuture<ArfforniaApiDtos.ProgressionConfig> fetchProgressionConfig() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL.get() + "/progression/config"))
                .header("Accept", "application/json")
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(json -> gson.fromJson(json, ArfforniaApiDtos.ProgressionConfig.class))
                .exceptionally(ex -> {
                    Arffornia.LOGGER.error("Failed to fetch progression config from API: {}. Banning no recipes.", ex.getMessage());
                    return new ArfforniaApiDtos.ProgressionConfig(Collections.emptyList());
                });
    }

    /**
     * Retrieves an authentication token for the game server.
     * The token is cached.
     *
     * @return A CompletableFuture containing the token, or null on failure.
     */
    private CompletableFuture<String> getServiceAuthToken() {
        if (serviceAuthToken.get() != null) {
            return CompletableFuture.completedFuture(serviceAuthToken.get());
        }

        String clientId = API_CLIENT_ID.get();
        String clientSecret = API_CLIENT_SECRET.get();

        JsonObject body = new JsonObject();
        body.addProperty("client_id", clientId);
        body.addProperty("client_secret", clientSecret);

        HttpRequest request = this.buildRequest(URI.create(API_BASE_URL.get() + "/auth/token/svc"), "", body);

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
                        return null;
                    }
                })
                .exceptionally(ex -> {
                    Arffornia.LOGGER.error("Could not connect to API to get service auth token: {}", ex.getMessage());
                    return null;
                });
    }

    /**
     * Notifies the backend that a player has joined a team.
     */
    public void sendPlayerJoinedTeam(UUID playerUuid, UUID teamUuid, String teamName) {
        getServiceAuthToken().thenAccept(token -> {
            if (token == null) {
                Arffornia.LOGGER.warn("Cannot send player-joined-team update, no auth token available.");
                return;
            }

            JsonObject body = new JsonObject();
            body.addProperty("player_uuid", playerUuid.toString().replace("-", ""));
            body.addProperty("team_uuid", teamUuid.toString());
            body.addProperty("team_name", teamName);

            HttpRequest request = this.buildRequest(URI.create(API_BASE_URL.get() + "/teams/player/join"), token, body);

            client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            Arffornia.LOGGER.error("API call to player/join failed with status: {}", response.statusCode());
                        }
                    })
                    .exceptionally(ex -> {
                        Arffornia.LOGGER.error("Failed to send player join team update network request", ex);
                        return null;
                    });
        });
    }

    /**
     * Notifies the backend that a player has left a team.
     */
    public void sendPlayerLeftTeam(UUID playerUuid) {
        getServiceAuthToken().thenAccept(token -> {
            if (token == null) {
                Arffornia.LOGGER.warn("Cannot send player-left-team update, no auth token available.");
                return;
            }
            JsonObject body = new JsonObject();
            body.addProperty("player_uuid", playerUuid.toString().replace("-", ""));

            HttpRequest request = this.buildRequest(URI.create(API_BASE_URL.get() + "/teams/player/leave"), token, body);

            client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            Arffornia.LOGGER.error("API call to player/leave failed with status: {}", response.statusCode());
                        }
                    })
                    .exceptionally(ex -> {
                        Arffornia.LOGGER.error("Failed to send player left team update network request", ex);
                        return null;
                    });
        });
    }

    /**
     * Fetches the list of completed milestones for a player.
     *
     * @return A CompletableFuture containing the list of milestone IDs, or an empty list on failure.
     */
    public CompletableFuture<List<Integer>> listMilestones(UUID playerUuid) {
        return getServiceAuthToken().thenCompose(token -> {
            if (token == null) {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            JsonObject body = new JsonObject();
            body.addProperty("player_uuid", playerUuid.toString().replace("-", ""));

            HttpRequest request = this.buildRequest(URI.create(API_BASE_URL.get() + "/progression/list"), token, body);

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                            return gson.fromJson(json.get("completed_milestones"), new com.google.gson.reflect.TypeToken<List<Integer>>() {
                            }.getType());
                        }

                        Arffornia.LOGGER.error("API call to listMilestones failed. Status: {}, Body: {}", response.statusCode(), response.body());
                        return Collections.<Integer>emptyList();
                    })
                    .exceptionally(ex -> {
                        Arffornia.LOGGER.error("Failed to execute listMilestones network request", ex);
                        return Collections.emptyList();
                    });
        });
    }

    /**
     * Adds a milestone to a player's active progression.
     */
    public CompletableFuture<Boolean> addMilestone(UUID playerUuid, int milestoneId) {
        return getServiceAuthToken().thenCompose(token -> {
            if (token == null) return CompletableFuture.completedFuture(false);

            JsonObject body = new JsonObject();
            body.addProperty("player_uuid", playerUuid.toString().replace("-", ""));
            body.addProperty("milestone_id", milestoneId);

            HttpRequest request = this.buildRequest(URI.create(API_BASE_URL.get() + "/progression/add"), token, body);

            return sendRequestAndCheckSuccess(request, "addMilestone", playerUuid);
        });
    }

    /**
     * Removes a milestone from a player's active progression.
     */
    public CompletableFuture<Boolean> removeMilestone(UUID playerUuid, int milestoneId) {
        return getServiceAuthToken().thenCompose(token -> {
            if (token == null) return CompletableFuture.completedFuture(false);

            JsonObject body = new JsonObject();
            body.addProperty("player_uuid", playerUuid.toString().replace("-", ""));
            body.addProperty("milestone_id", milestoneId);

            HttpRequest request = this.buildRequest(URI.create(API_BASE_URL.get() + "/progression/remove"), token, body);

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
                })
                .exceptionally(ex -> {
                    Arffornia.LOGGER.error("API call to {} failed for player {} due to a network error: {}", actionName, playerUuid, ex.getMessage());
                    return false;
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
            if (token == null) {
                Arffornia.LOGGER.error("Cannot ensure player exists, auth token is null.");
                return CompletableFuture.completedFuture(false);
            }

            JsonObject body = new JsonObject();
            body.addProperty("uuid", playerUuid.toString().replace("-", ""));
            body.addProperty("username", playerName);

            HttpRequest request = this.buildRequest(URI.create(API_BASE_URL.get() + "/player/ensure-exists"), token, body);

            return sendRequestAndCheckSuccess(request, "ensurePlayerExists", playerUuid);
        });
    }

    /**
     * Sets the player's targeted milestone via the API.
     *
     * @param playerUuid  The UUID of the player making the request.
     * @param milestoneId The ID of the milestone to target.
     * @return A CompletableFuture that resolves to true on success.
     */
    public CompletableFuture<Boolean> setTargetMilestone(UUID playerUuid, int milestoneId) {
        return getServiceAuthToken().thenCompose(token -> {
            if (token == null) return CompletableFuture.completedFuture(false);

            JsonObject body = new JsonObject();
            body.addProperty("player_uuid", playerUuid.toString().replace("-", ""));
            body.addProperty("milestone_id", milestoneId);

            HttpRequest request = this.buildRequest(URI.create(API_BASE_URL.get() + "/progression/set-target"), token, body);

            return sendRequestAndCheckSuccess(request, "setTargetMilestone", playerUuid);
        });
    }

    /**
     * Fetches all custom recipes from the API.
     *
     * @return A CompletableFuture containing the list of all custom recipes.
     */
    public CompletableFuture<List<ArfforniaApiDtos.CustomRecipe>> fetchAllCustomRecipes() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL.get() + "/recipes"))
                .header("Accept", "application/json")
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .<List<ArfforniaApiDtos.CustomRecipe>>thenApply(jsonResponse -> {
                    String json = jsonResponse.body();
                    Type listType = new TypeToken<ArrayList<ArfforniaApiDtos.CustomRecipe>>() {
                    }.getType();
                    return gson.fromJson(json, listType);
                })
                .exceptionally(ex -> {
                    Arffornia.LOGGER.error("Failed to fetch all custom recipes from API: {}", ex.getMessage());
                    return Collections.emptyList();
                });
    }
}