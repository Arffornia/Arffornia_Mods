package fr.thegostsniperfr.arffornia.api.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.command.management.AddUnlockCommand;
import fr.thegostsniperfr.arffornia.recipe.CustomRecipeManager;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static fr.thegostsniperfr.arffornia.config.ApiConfig.*;

/**
 * Service class for handling all HTTP requests to the Arffornia web backend.
 */
public class ArfforniaApiService {
    private static final ArfforniaApiService INSTANCE = new ArfforniaApiService();

    private final ApiHttp http = new ApiHttp(Arffornia.LOGGER, API_BASE_URL::get, API_CLIENT_ID::get, API_CLIENT_SECRET::get);
    private final Gson gson = http.gson();
    private final ExecutorService migrationExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Arffornia-Migration-Thread"));

    private ArfforniaApiService() {
    }

    public static ArfforniaApiService getInstance() {
        return INSTANCE;
    }

    /**
     * Shuts down the migration executor service. Should be called on server stop.
     */
    public void shutdown() {
        migrationExecutor.shutdown();
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
        return http.getJson("/stages/player/get/" + playerUuid, ArfforniaApiDtos.GraphData.class, "player graph " + playerUuid);
    }

    /**
     * Fetches the detailed information for a single milestone asynchronously.
     *
     * @param nodeId The ID of the node to fetch.
     * @return A CompletableFuture containing the parsed milestone details, or null on failure.
     */
    public CompletableFuture<ArfforniaApiDtos.MilestoneDetails> fetchMilestoneDetails(int nodeId) {
        return http.getJson("/milestone/get/" + nodeId, ArfforniaApiDtos.MilestoneDetails.class, "milestone " + nodeId);
    }

    /**
     * Fetches the server's progression configuration, including the list of banned recipes.
     * This should be called once on server start or /reload.
     *
     * @return A CompletableFuture containing the parsed progression config (empty on failure).
     */
    public CompletableFuture<ArfforniaApiDtos.ProgressionConfig> fetchProgressionConfig() {
        return http.<ArfforniaApiDtos.ProgressionConfig>getJson("/progression/config", ArfforniaApiDtos.ProgressionConfig.class, "progression config")
                .thenApply(config -> {
                    if (config == null || config.bannedRecipes() == null) {
                        Arffornia.LOGGER.error("Failed to fetch progression config from API. Banning no recipes.");
                        return new ArfforniaApiDtos.ProgressionConfig(Collections.emptyList());
                    }
                    return config;
                });
    }

    /**
     * Sends an authenticated JSON POST and maps the outcome to a boolean (2xx = true).
     */
    private CompletableFuture<Boolean> postAndCheckSuccess(String path, JsonObject body, String actionName, @Nullable UUID playerUuid) {
        return http.sendAuthenticated(http.jsonPost(path, gson.toJson(body)))
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
     * Notifies the backend that a player has joined a team.
     */
    public void sendPlayerJoinedTeam(UUID playerUuid, UUID teamUuid, String teamName) {
        JsonObject body = new JsonObject();
        body.addProperty("player_uuid", compactUuid(playerUuid));
        body.addProperty("team_uuid", teamUuid.toString());
        body.addProperty("team_name", teamName);
        postAndCheckSuccess("/teams/player/join", body, "player/join", playerUuid);
    }

    /**
     * Notifies the backend that a player has left a team.
     */
    public void sendPlayerLeftTeam(UUID playerUuid) {
        JsonObject body = new JsonObject();
        body.addProperty("player_uuid", compactUuid(playerUuid));
        postAndCheckSuccess("/teams/player/leave", body, "player/leave", playerUuid);
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
     * Fetches all custom recipes from the API.
     *
     * @return A CompletableFuture containing the list of all custom recipes (empty on failure).
     */
    public CompletableFuture<List<ArfforniaApiDtos.CustomRecipe>> fetchAllCustomRecipes() {
        Type listType = new TypeToken<ArrayList<ArfforniaApiDtos.CustomRecipe>>() {}.getType();
        return http.<List<ArfforniaApiDtos.CustomRecipe>>getJson("/recipes", listType, "custom recipes")
                .thenApply(recipes -> recipes != null ? recipes : Collections.<ArfforniaApiDtos.CustomRecipe>emptyList());
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

    public void runRecipeMigration(MinecraftServer server, Collection<RecipeHolder<?>> allRecipes) {
        migrationExecutor.submit(() -> {
            try {
                // 1. Get the list of items that need a recipe from the API
                HttpResponse<String> response = http.sendAuthenticated(http.authGet("/migration/items-to-migrate")).join();
                if (response == null) {
                    Arffornia.LOGGER.error("Recipe Sync failed: Could not get auth token.");
                    return;
                }

                if (response.statusCode() != 200) {
                    Arffornia.LOGGER.error("Recipe Sync failed: Could not get item list from API. Status: {}", response.statusCode());
                    return;
                }

                Type listType = new TypeToken<List<String>>() {}.getType();
                List<String> itemsToMigrate = gson.fromJson(response.body(), listType);

                if (itemsToMigrate == null || itemsToMigrate.isEmpty()) {
                    Arffornia.LOGGER.info("Recipe Sync: No items require a recipe sync from the API.");
                    return;
                }
                Arffornia.LOGGER.info("Recipe Sync: Found {} items to sync. Preparing batch payload...", itemsToMigrate.size());

                List<Map<String, Object>> batchPayload = new ArrayList<>();
                RegistryAccess registryAccess = server.registryAccess();

                // 2. Find the vanilla recipe for each item
                for (String itemId : itemsToMigrate) {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                    if (item == Items.AIR) {
                        Arffornia.LOGGER.warn("Recipe Sync: Skipping unknown item_id '{}'", itemId);
                        continue;
                    }

                    Optional<RecipeHolder<?>> recipeHolderOpt = AddUnlockCommand.findBestCraftingRecipeFor(allRecipes, new ItemStack(item), server.overworld());

                    if (recipeHolderOpt.isEmpty()) {
                        Arffornia.LOGGER.warn("Recipe Sync: No vanilla crafting recipe found for '{}'. It might be from a different recipe type (smelting, etc.) or added by a mod in a non-standard way.", itemId);
                        continue;
                    }

                    Map<String, Object> recipePayload = AddUnlockCommand.convertRecipeToPayload(recipeHolderOpt.get().value(), registryAccess);
                    if (recipePayload != null) {
                        recipePayload.put("item_id", itemId); // Add the item_id for the backend to identify the unlock
                        batchPayload.add(recipePayload);
                    } else {
                        Arffornia.LOGGER.warn("Recipe Sync: Unsupported recipe type for '{}'", itemId);
                    }
                }

                if (batchPayload.isEmpty()) {
                    Arffornia.LOGGER.info("Recipe Sync complete. No valid vanilla recipes found for the requested items.");
                    return;
                }

                // 3. Send the batch of found recipes to the API
                Arffornia.LOGGER.info("Recipe Sync: Sending batch of {} recipes to the API...", batchPayload.size());
                JsonObject finalPayload = new JsonObject();
                finalPayload.add("recipes", gson.toJsonTree(batchPayload));

                HttpResponse<String> batchResponse = http.sendAuthenticated(
                        http.jsonPost("/migration/submit-batch-recipes", gson.toJson(finalPayload))).join();

                if (batchResponse != null && ApiHttp.isSuccess(batchResponse.statusCode())) {
                    Arffornia.LOGGER.info("Recipe Sync: Batch request successful!");
                    Arffornia.LOGGER.warn("Recipe sync on startup was successful. It is recommended to set 'migrateOnStartup' to 'false' in the config to prevent unnecessary checks on every launch.");
                } else {
                    Arffornia.LOGGER.error("Recipe Sync: Batch request failed. Status: {}", batchResponse != null ? batchResponse.statusCode() : "no token");
                }

            } catch (Exception e) {
                Arffornia.LOGGER.error("A critical error occurred during the recipe sync process.", e);
            } finally {
                CustomRecipeManager.loadRecipes().join();
            }
        });
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
