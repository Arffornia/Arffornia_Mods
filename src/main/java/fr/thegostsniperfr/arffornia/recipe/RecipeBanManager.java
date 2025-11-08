package fr.thegostsniperfr.arffornia.recipe;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RecipeBanManager {

    private static Collection<RecipeHolder<?>> originalRecipes = Collections.emptyList();

    /**
     * Provides access to the original, unmodified recipe list for other parts of the mod, like migration.
     * @return A collection of all recipes as loaded from data packs.
     */
    public static Collection<RecipeHolder<?>> getOriginalRecipes() {
        return originalRecipes;
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new SimplePreparableReloadListener<Set<ResourceLocation>>() {

            @Override
            protected Set<ResourceLocation> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                // This part runs on a worker thread.
                Arffornia.LOGGER.info("Fetching banned recipe list from API...");
                try {
                    return ArfforniaApiService.getInstance().fetchProgressionConfig().join().bannedRecipes()
                            .stream()
                            .map(ResourceLocation::parse)
                            .collect(Collectors.toSet());
                } catch (Exception e) {
                    Arffornia.LOGGER.error("Failed to fetch banned recipes from API. No recipes will be banned.", e);
                    return Set.of();
                }
            }

            @Override
            protected void apply(Set<ResourceLocation> apiBannedRecipes, ResourceManager resourceManager, ProfilerFiller profiler) {
                RecipeManager manager = event.getServerResources().getRecipeManager();

                RecipeBanManager.originalRecipes = new ArrayList<>(manager.getRecipes());
                Arffornia.LOGGER.info("Cached {} original recipes before banning.", RecipeBanManager.originalRecipes.size());

                int originalCount = RecipeBanManager.originalRecipes.size();
                List<RecipeHolder<?>> filteredRecipes = RecipeBanManager.originalRecipes.stream()
                        .filter(recipe -> !apiBannedRecipes.contains(recipe.id()))
                        .collect(Collectors.toList());

                manager.replaceRecipes(filteredRecipes);

                int removedCount = originalCount - filteredRecipes.size();
                if (removedCount > 0) {
                    Arffornia.LOGGER.info("Recipe ban process complete. Removed {} recipes from active manager.", removedCount);
                }
            }
        });
    }
}