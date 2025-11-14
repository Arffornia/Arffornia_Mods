package fr.thegostsniperfr.arffornia.recipe;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ClientRecipeCache {
    private static final Map<Integer, List<ArfforniaApiDtos.CustomRecipe>> RECIPES_BY_MILESTONE_ID = new ConcurrentHashMap<>();

    public static void loadRecipes() {
        Arffornia.LOGGER.info("Client: Fetching all custom recipes for caching...");
        ArfforniaApiService.getInstance().fetchAllCustomRecipes().thenAccept(recipes -> {
            RECIPES_BY_MILESTONE_ID.clear();
            if (recipes != null && !recipes.isEmpty()) {
                RECIPES_BY_MILESTONE_ID.putAll(
                        recipes.stream().collect(Collectors.groupingBy(
                                ArfforniaApiDtos.CustomRecipe::milestoneId
                        ))
                );
                Arffornia.LOGGER.info("Client: Successfully cached {} custom recipes grouped into {} milestone IDs.",
                        recipes.size(), RECIPES_BY_MILESTONE_ID.size());
            } else {
                Arffornia.LOGGER.warn("Client: No custom recipes were loaded from the API.");
            }
        });
    }

    public static List<ArfforniaApiDtos.CustomRecipe> getRecipesByMilestoneIds(Set<Integer> unlockedMilestoneIds) {
        Arffornia.LOGGER.info("ClientRecipeCache: Filtering against a cache of size {}. Unlocked milestone IDs received: {}",
                RECIPES_BY_MILESTONE_ID.size(), unlockedMilestoneIds);

        if (unlockedMilestoneIds == null || unlockedMilestoneIds.isEmpty()) {
            return Collections.emptyList();
        }

        return unlockedMilestoneIds.stream()
                .map(id -> RECIPES_BY_MILESTONE_ID.getOrDefault(id, Collections.emptyList()))
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * Returns a flat list of all custom recipes currently held in the client-side cache.
     * @return A collection of all cached recipes.
     */
    public static Collection<ArfforniaApiDtos.CustomRecipe> getAllCachedRecipes() {
        return RECIPES_BY_MILESTONE_ID.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }
}