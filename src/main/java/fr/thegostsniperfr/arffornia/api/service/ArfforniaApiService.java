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

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static fr.thegostsniperfr.arffornia.config.ApiConfig.*;

/**
 * Service class for handling all HTTP requests to the Arffornia web backend.
 */
public class ArfforniaApiService {
    private static final ArfforniaApiService INSTANCE = new ArfforniaApiService();

    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    private final ExecutorService migrationExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Arffornia-Migration-Thread"));

    private final AtomicReference<String> serviceAuthToken = new AtomicReference<>(null);

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

    private HttpRequest buildRequest(URI uri, String token, String jsonBody) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL.get() + "/auth/token/svc"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
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

            HttpRequest request = this.buildRequest(URI.create(API_BASE_URL.get() + "/teams/player/join"), token, gson.toJson(body));

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

            HttpRequest request = this.buildRequest(URI.create(API_BASE_URL.get() + "/teams/player/leave"), token, gson.toJson(body));

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

            HttpRequest request = this.buildRequest(URI.create(API_BASE_URL.get() + "/progression/list"), token, gson.toJson(body));

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

            HttpRequest request = this.buildRequest(URI.create(API_BASE_URL.get() + "/progression/add"), token, gson.toJson(body));

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

            HttpRequest request = this.buildRequest(URI.create(API_BASE_URL.get() + "/progression/remove"), token, gson.toJson(body));

            return sendRequestAndCheckSuccess(request, "removeMilestone", playerUuid);
        });
    }

    private CompletableFuture<Boolean> sendRequestAndCheckSuccess(HttpRequest request, String actionName, UUID playerUuid) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() <= 299) {
                        return true;
                    } else {
                        if(response.statusCode() == 302) {
                            Arffornia.LOGGER.error("API call to {} failed for player {}. Status: 302 (Redirect). This often indicates an authentication or middleware issue on the web server. Ensure API routes return JSON errors, not redirects.", actionName, playerUuid);
                        } else {
                            Arffornia.LOGGER.error("API call to {} failed for player {}. Status: {}, Body: {}", actionName, playerUuid, response.statusCode(), response.body());
                        }
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

            HttpRequest request = this.buildRequest(URI.create(API_BASE_URL.get() + "/player/ensure-exists"), token, gson.toJson(body));

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

            HttpRequest request = this.buildRequest(URI.create(API_BASE_URL.get() + "/progression/set-target"), token, gson.toJson(body));

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
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        Arffornia.LOGGER.error("Failed to fetch all custom recipes. Status: {}", response.statusCode());
                        return Collections.<ArfforniaApiDtos.CustomRecipe>emptyList();
                    }
                    Type listType = new TypeToken<ArrayList<ArfforniaApiDtos.CustomRecipe>>() {}.getType();
                    return gson.fromJson(response.body(), listType);
                })
                .exceptionally(ex -> {
                    Arffornia.LOGGER.error("Failed to fetch all custom recipes from API: {}", ex.getMessage());
                    return Collections.emptyList();
                });
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
        return getServiceAuthToken().thenCompose(token -> {
            if (token == null) {
                Arffornia.LOGGER.error("Cannot add unlock, service auth token is null.");
                return CompletableFuture.completedFuture(false);
            }

            JsonObject body = new JsonObject();
            body.addProperty("item_id", itemId);
            body.addProperty("display_name", displayName);
            body.addProperty("image_path", imagePath);
            body.add("recipes_to_ban", gson.toJsonTree(recipesToBan));
            body.add("ingredients", gson.toJsonTree(ingredients));
            body.add("result", gson.toJsonTree(result));

            HttpRequest request = this.buildRequest(
                    URI.create(API_BASE_URL.get() + "/milestones/" + milestoneId + "/add-unlock-from-game"),
                    token,
                    gson.toJson(body)
            );

            return sendRequestAndCheckSuccess(request, "addUnlockToMilestone", null);
        });
    }

    public void runRecipeMigration(MinecraftServer server, Collection<RecipeHolder<?>> allRecipes) {
        migrationExecutor.submit(() -> {
            try {
                String token = getServiceAuthToken().join();
                if (token == null) {
                    Arffornia.LOGGER.error("Recipe Sync failed: Could not get auth token.");
                    return;
                }

                // 1. Get the list of items that need a recipe from the API
                HttpRequest getRequest = HttpRequest.newBuilder()
                        .uri(URI.create(API_BASE_URL.get() + "/migration/items-to-migrate"))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(getRequest, HttpResponse.BodyHandlers.ofString());

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

                HttpRequest batchRequest = this.buildRequest(
                        URI.create(API_BASE_URL.get() + "/migration/submit-batch-recipes"),
                        token,
                        gson.toJson(finalPayload)
                );

                HttpResponse<String> batchResponse = client.send(batchRequest, HttpResponse.BodyHandlers.ofString());

                if (batchResponse.statusCode() >= 200 && batchResponse.statusCode() < 300) {
                    Arffornia.LOGGER.info("Recipe Sync: Batch request successful!");
                    Arffornia.LOGGER.warn("Recipe sync on startup was successful. It is recommended to set 'migrateOnStartup' to 'false' in the config to prevent unnecessary checks on every launch.");
                } else {
                    Arffornia.LOGGER.error("Recipe Sync: Batch request failed. Status: {}, Body: {}", batchResponse.statusCode(), batchResponse.body());
                }

            } catch (Exception e) {
                Arffornia.LOGGER.error("A critical error occurred during the recipe sync process.", e);
            } finally {
                CustomRecipeManager.loadRecipes();
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
        return getServiceAuthToken().thenCompose(token -> {
            if (token == null) {
                Arffornia.LOGGER.error("Cannot set milestone requirements, service auth token is null.");
                return CompletableFuture.completedFuture(false);
            }

            JsonObject body = new JsonObject();
            body.add("requirements", gson.toJsonTree(requirements));

            HttpRequest request = this.buildRequest(
                    URI.create(API_BASE_URL.get() + "/milestones/" + milestoneId + "/set-requirements"),
                    token,
                    gson.toJson(body)
            );

            return sendRequestAndCheckSuccess(request, "setMilestoneRequirements", null);
        });
    }
}