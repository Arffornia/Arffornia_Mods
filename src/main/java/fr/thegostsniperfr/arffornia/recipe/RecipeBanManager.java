package fr.thegostsniperfr.arffornia.recipe;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RecipeBanManager {

    private static final Set<ResourceLocation> BANNED_RECIPE_IDS = ConcurrentHashMap.newKeySet();

    /**
     * Checks if a given recipe ID is in the ban list.
     *
     * @param recipeId The unique ResourceLocation of the recipe to check.
     * @return true if the recipe is banned, false otherwise.
     */
    public static boolean isBanned(ResourceLocation recipeId) {
        return BANNED_RECIPE_IDS.contains(recipeId);
    }

    /**
     * This event listener registers our custom ReloadListener.
     * The listener will be responsible for fetching API data and applying the bans
     * after the server has loaded all its data packs (including recipes).
     */
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<Set<ResourceLocation>>() {

            /**
             * This method runs in a background thread. It's the perfect place for
             * network requests or heavy processing. Here we fetch the banned recipes from the API.
             */
            @Override
            protected Set<ResourceLocation> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                Arffornia.LOGGER.info("Fetching banned recipe list from API...");
                try {
                    // .join() blocks the current thread until the Future is complete.
                    // This is safe here because we are on a worker thread, not the main server thread.
                    return Arffornia.ARFFORNA_API_SERVICE.fetchProgressionConfig().join().bannedRecipes()
                            .stream()
                            .map(ResourceLocation::parse)
                            .collect(Collectors.toSet());
                } catch (Exception e) {
                    Arffornia.LOGGER.error("Failed to fetch banned recipes from API. No recipes will be banned.", e);
                    return Set.of(); // Return an empty set on failure
                }
            }

            /**
             * This method runs on the main server thread after prepare() is complete.
             * It applies the data we fetched.
             */
            @Override
            protected void apply(Set<ResourceLocation> apiBannedRecipes, ResourceManager resourceManager, ProfilerFiller profiler) {
                // Update the main ban list with the fresh data from the API
                BANNED_RECIPE_IDS.clear();
                BANNED_RECIPE_IDS.addAll(apiBannedRecipes);
                Arffornia.LOGGER.info("Loaded {} banned recipes from API.", BANNED_RECIPE_IDS.size());

                RecipeManager manager = event.getServerResources().getRecipeManager();

                Collection<RecipeHolder<?>> currentRecipes = manager.getRecipes();
                int originalCount = currentRecipes.size();

                List<RecipeHolder<?>> filteredRecipes = currentRecipes.stream()
                        .filter(recipe -> !RecipeBanManager.isBanned(recipe.id()))
                        .collect(Collectors.toList());

                manager.replaceRecipes(filteredRecipes);

                int removedCount = originalCount - filteredRecipes.size();
                if (removedCount > 0) {
                    Arffornia.LOGGER.info("Recipe ban process complete. Removed {} recipes.", removedCount);
                }
            }
        });
    }
}