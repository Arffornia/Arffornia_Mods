package fr.thegostsniperfr.arffornia.api.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.ApiText;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import org.jetbrains.annotations.Nullable;

import java.net.http.HttpRequest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static fr.thegostsniperfr.arffornia.config.ApiConfig.*;

/**
 * Service class for handling all HTTP requests to the Arffornia web backend.
 */
public class ArfforniaApiService {
    private static final ArfforniaApiService INSTANCE = new ArfforniaApiService();

    private final ApiHttp http = new ApiHttp(Arffornia.LOGGER, API_BASE_URL::get, API_CLIENT_ID::get, API_CLIENT_SECRET::get);
    private final Gson gson = http.gson();

    private ArfforniaApiService() {
    }

    public static ArfforniaApiService getInstance() {
        return INSTANCE;
    }

    private static String compactUuid(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    /**
     * Fetches the active progression ID for a specific player.
     *
     * @param playerUuid The UUID of the player.
     * @return A CompletableFuture containing the player's data including active progression ID, or null on failure.
     */
    public CompletableFuture<ArfforniaApiDtos.PlayerData> fetchPlayerData(String playerUuid) {
        return http.getJson("/profile/uuid/" + playerUuid, ArfforniaApiDtos.PlayerData.class, "player " + playerUuid);
    }

    /**
     * Fetches progression data, including the current target milestone.
     *
     * @param progressionId The ID of the progression.
     * @return A CompletableFuture containing the progression data, or null on failure.
     */
    public CompletableFuture<ArfforniaApiDtos.ProgressionData> fetchProgressionData(long progressionId) {
        return http.getJson("/progression/" + progressionId, ArfforniaApiDtos.ProgressionData.class, "progression " + progressionId);
    }

    /**
     * Fetches the PROGRESSION DATA for a specific player (completed milestones, target).
     *
     * @param playerUuid The UUID of the player.
     * @return A CompletableFuture containing the player's specific progress data, or null on failure.
     */
    public CompletableFuture<ArfforniaApiDtos.GraphData> fetchPlayerGraphData(String playerUuid) {
        return fetchPlayerGraphData(playerUuid, null);
    }

    /**
     * Same as {@link #fetchPlayerGraphData(String)}, asking the API for text in the player's language.
     *
     * @param minecraftLanguage the player's game language (e.g. {@code fr_fr}), or null for the API default.
     */
    public CompletableFuture<ArfforniaApiDtos.GraphData> fetchPlayerGraphData(String playerUuid, @Nullable String minecraftLanguage) {
        return http.getJson("/stages/player/get/" + playerUuid, ArfforniaApiDtos.GraphData.class, "player graph " + playerUuid,
                ApiText.toLanguageTag(minecraftLanguage));
    }

    /**
     * Fetches the detailed information for a single milestone asynchronously.
     *
     * @param nodeId The ID of the node to fetch.
     * @return A CompletableFuture containing the parsed milestone details, or null on failure.
     */
    public CompletableFuture<ArfforniaApiDtos.MilestoneDetails> fetchMilestoneDetails(int nodeId) {
        return fetchMilestoneDetails(nodeId, null);
    }

    /**
     * Same as {@link #fetchMilestoneDetails(int)}, asking the API for text in the player's language.
     *
     * @param minecraftLanguage the player's game language (e.g. {@code fr_fr}), or null for the API default.
     */
    public CompletableFuture<ArfforniaApiDtos.MilestoneDetails> fetchMilestoneDetails(int nodeId, @Nullable String minecraftLanguage) {
        return http.getJson("/milestone/get/" + nodeId, ArfforniaApiDtos.MilestoneDetails.class, "milestone " + nodeId,
                ApiText.toLanguageTag(minecraftLanguage));
    }

    /**
     * Sends an authenticated JSON POST and maps the outcome to a boolean (2xx = true).
     */
    private CompletableFuture<Boolean> postAndCheckSuccess(String path, JsonObject body, String actionName, @Nullable UUID playerUuid) {
        return sendAndCheckSuccess(http.jsonPost(path, gson.toJson(body)), actionName, playerUuid);
    }

    private CompletableFuture<Boolean> sendAndCheckSuccess(Function<String, HttpRequest> request, String actionName, @Nullable UUID playerUuid) {
        return http.sendAuthenticated(request)
                .thenApply(response -> {
                    if (response == null) {
                        Arffornia.LOGGER.error("API call to {} skipped for player {}: no service auth token.", actionName, playerUuid);
                        return false;
                    }
                    if (ApiHttp.isSuccess(response.statusCode())) {
                        return true;
                    }
                    if (response.statusCode() == 302) {
                        Arffornia.LOGGER.error("API call to {} failed for player {}. Status: 302 (Redirect). This often indicates an authentication or middleware issue on the web server. Ensure API routes return JSON errors, not redirects.", actionName, playerUuid);
                    } else {
                        Arffornia.LOGGER.error("API call to {} failed for player {}. Status: {}, Body: {}", actionName, playerUuid, response.statusCode(), response.body());
                    }
                    return false;
                })
                .exceptionally(ex -> {
                    Arffornia.LOGGER.error("API call to {} failed for player {} due to a network error: {}", actionName, playerUuid, ex.getMessage());
                    return false;
                });
    }

    /**
     * Tells the backend which party team the player was in at {@code observedAtMillis} (null = no party).
     * The backend ignores observations older than the last one it applied, so calls may arrive in any order.
     */
    public CompletableFuture<Boolean> syncPlayerTeam(UUID playerUuid, @Nullable UUID teamUuid, @Nullable String teamName, long observedAtMillis) {
        JsonObject body = new JsonObject();
        body.addProperty("player_uuid", compactUuid(playerUuid));
        body.addProperty("team_uuid", teamUuid != null ? teamUuid.toString() : null);
        body.addProperty("team_name", teamName);
        body.addProperty("observed_at", observedAtMillis);
        return sendAndCheckSuccess(http.jsonPut("/teams/player/sync", gson.toJson(body)), "teams/player/sync", playerUuid);
    }

    /**
     * Fetches the list of completed milestones for a player.
     *
     * @return A CompletableFuture containing the list of milestone IDs, or an empty list on failure.
     */
    public CompletableFuture<List<Integer>> listMilestones(UUID playerUuid) {
        JsonObject body = new JsonObject();
        body.addProperty("player_uuid", compactUuid(playerUuid));

        return http.sendAuthenticated(http.jsonPost("/progression/list", gson.toJson(body)))
                .thenApply(response -> {
                    if (response != null && response.statusCode() == 200) {
                        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                        List<Integer> ids = gson.fromJson(json.get("completed_milestones"), new TypeToken<List<Integer>>() {
                        }.getType());
                        return ids != null ? ids : Collections.<Integer>emptyList();
                    }
                    Arffornia.LOGGER.error("API call to listMilestones failed. Status: {}", response != null ? response.statusCode() : "no token");
                    return Collections.<Integer>emptyList();
                })
                .exceptionally(ex -> {
                    Arffornia.LOGGER.error("Failed to execute listMilestones network request", ex);
                    return Collections.emptyList();
                });
    }

    /**
     * Adds a milestone to a player's active progression.
     */
    public CompletableFuture<Boolean> addMilestone(UUID playerUuid, int milestoneId) {
        JsonObject body = new JsonObject();
        body.addProperty("player_uuid", compactUuid(playerUuid));
        body.addProperty("milestone_id", milestoneId);
        return postAndCheckSuccess("/progression/add", body, "addMilestone", playerUuid);
    }

    /**
     * Adds a milestone to a given progression. Fails when the progression is retired (its team was soft-deleted).
     */
    public CompletableFuture<Boolean> addMilestoneToProgression(long progressionId, int milestoneId) {
        JsonObject body = new JsonObject();
        body.addProperty("progression_id", progressionId);
        body.addProperty("milestone_id", milestoneId);
        return postAndCheckSuccess("/progression/add", body, "addMilestoneToProgression", null);
    }

    /**
     * Removes a milestone from a player's active progression.
     */
    public CompletableFuture<Boolean> removeMilestone(UUID playerUuid, int milestoneId) {
        JsonObject body = new JsonObject();
        body.addProperty("player_uuid", compactUuid(playerUuid));
        body.addProperty("milestone_id", milestoneId);
        return postAndCheckSuccess("/progression/remove", body, "removeMilestone", playerUuid);
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
        JsonObject body = new JsonObject();
        body.addProperty("uuid", compactUuid(playerUuid));
        body.addProperty("username", playerName);
        return postAndCheckSuccess("/player/ensure-exists", body, "ensurePlayerExists", playerUuid);
    }

    /**
     * Sets the player's targeted milestone via the API.
     *
     * @param playerUuid  The UUID of the player making the request.
     * @param milestoneId The ID of the milestone to target.
     * @return A CompletableFuture that resolves to true on success.
     */
    public CompletableFuture<Boolean> setTargetMilestone(UUID playerUuid, @Nullable Integer milestoneId) {
        JsonObject body = new JsonObject();
        body.addProperty("player_uuid", compactUuid(playerUuid));
        body.add("milestone_id", gson.toJsonTree(milestoneId));
        return postAndCheckSuccess("/progression/set-target", body, "setTargetMilestone", playerUuid);
    }

    /**
     * Adds an item unlock to a specific milestone via the API.
     * This is an admin-only action.
     *
     * @param milestoneId    The ID of the milestone to add to unlock to.
     * @param itemId         The registry name of the item (e.g., "minecraft:iron_ingot").
     * @param displayName    The generated display name (e.g., "Iron Ingot").
     * @param imagePath      The generated image path (e.g., "minecraft_iron_ingot").
     * @param recipesToBan   A list of recipe IDs to be banned when this item is unlocked.
     * @return A CompletableFuture that resolves to true on success.
     */
    public CompletableFuture<Boolean> addUnlockToMilestone(int milestoneId, String itemId, String displayName, String imagePath, List<String> recipesToBan, List<Map<String, Object>> ingredients, List<Map<String, Object>> result) {
        JsonObject body = new JsonObject();
        body.addProperty("item_id", itemId);
        body.addProperty("display_name", displayName);
        body.addProperty("image_path", imagePath);
        body.add("recipes_to_ban", gson.toJsonTree(recipesToBan));
        body.add("ingredients", gson.toJsonTree(ingredients));
        body.add("result", gson.toJsonTree(result));
        return postAndCheckSuccess("/milestones/" + milestoneId + "/add-unlock-from-game", body, "addUnlockToMilestone", null);
    }

    /**
     * Overwrites all requirements for a given milestone with a new set from in-game.
     * This is an admin-only action.
     *
     * @param milestoneId  The ID of the milestone to update.
     * @param requirements A list of maps, where each map represents a required item.
     * @return A CompletableFuture that resolves to true on success.
     */
    public CompletableFuture<Boolean> setMilestoneRequirements(int milestoneId, List<Map<String, Object>> requirements) {
        JsonObject body = new JsonObject();
        body.add("requirements", gson.toJsonTree(requirements));
        return postAndCheckSuccess("/milestones/" + milestoneId + "/set-requirements", body, "setMilestoneRequirements", null);
    }

    /**
     * Moves a milestone item (unlock or requirement) from one milestone to another via the API.
     * This is an admin-only action.
     *
     * @param fromMilestoneId The ID of the source milestone.
     * @param toMilestoneId   The ID of the destination milestone.
     * @param itemId          The registry name of the item to move.
     * @return A CompletableFuture that resolves to true on success.
     */
    public CompletableFuture<Boolean> moveMilestoneItem(int fromMilestoneId, int toMilestoneId, String itemId) {
        JsonObject body = new JsonObject();
        body.addProperty("from_milestone_id", fromMilestoneId);
        body.addProperty("to_milestone_id", toMilestoneId);
        body.addProperty("item_id", itemId);
        return postAndCheckSuccess("/milestone-items/move", body, "moveMilestoneItem", null);
    }

}
