package fr.thegostsniperfr.arffornia.recipe;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.api.backend.ProgressionBackends;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CustomRecipeManager {
    private static final Map<Integer, ArfforniaApiDtos.CustomRecipe> RECIPES_BY_MILESTONE_UNLOCK_ID = new ConcurrentHashMap<>();

    /**
     * Fetches custom recipes from the API and caches them.
     * Called once, asynchronously, from {@code Arffornia.onServerStarted}, and again after a migration run.
     */
    public static CompletableFuture<Void> loadRecipes() {
        Arffornia.LOGGER.info("Fetching custom recipes from Arffornia API...");
        return ProgressionBackends.get().fetchAllCustomRecipes().thenAccept(recipes -> {
            RECIPES_BY_MILESTONE_UNLOCK_ID.clear();
            if (recipes != null && !recipes.isEmpty()) {
                RECIPES_BY_MILESTONE_UNLOCK_ID.putAll(
                        recipes.stream().collect(Collectors.toMap(
                                ArfforniaApiDtos.CustomRecipe::milestoneUnlockId,
                                Function.identity(),
                                (r1, r2) -> r1
                        ))
                );
                Arffornia.LOGGER.info("Successfully loaded and cached {} custom recipes.", RECIPES_BY_MILESTONE_UNLOCK_ID.size());
            } else {
                Arffornia.LOGGER.warn("No custom recipes were loaded from the API.");
            }
        });
    }

    /** Adds or replaces one recipe in the server cache. Intended for GameTests only. */
    public static void putRecipeForTesting(ArfforniaApiDtos.CustomRecipe recipe) {
        RECIPES_BY_MILESTONE_UNLOCK_ID.put(recipe.milestoneUnlockId(), recipe);
    }

    @Nullable
    public static ArfforniaApiDtos.CustomRecipe getRecipeByMilestoneUnlockId(int milestoneUnlockId) {
        return RECIPES_BY_MILESTONE_UNLOCK_ID.get(milestoneUnlockId);
    }

    public static Collection<ArfforniaApiDtos.CustomRecipe> getAllRecipes() {
        return Collections.unmodifiableCollection(RECIPES_BY_MILESTONE_UNLOCK_ID.values());
    }
}