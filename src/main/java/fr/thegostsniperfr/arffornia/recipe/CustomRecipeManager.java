package fr.thegostsniperfr.arffornia.recipe;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CustomRecipeManager {
    private static final Map<Integer, ArfforniaApiDtos.CustomRecipe> RECIPES_BY_MILESTONE_UNLOCK_ID = new ConcurrentHashMap<>();

    /**
     * This event fires once the server is fully started and after migration is complete.
     * This is the definitive time to load the custom recipes.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        loadRecipes();
    }

    /**
     * Fetches custom recipes from the API and caches them.
     * This is called on server start and after a successful migration.
     */
    public static void loadRecipes() {
        Arffornia.LOGGER.info("Fetching custom recipes from Arffornia API...");
        ArfforniaApiService.getInstance().fetchAllCustomRecipes().thenAccept(recipes -> {
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

    @Nullable
    public static ArfforniaApiDtos.CustomRecipe getRecipeByMilestoneUnlockId(int milestoneUnlockId) {
        return RECIPES_BY_MILESTONE_UNLOCK_ID.get(milestoneUnlockId);
    }

    public static Collection<ArfforniaApiDtos.CustomRecipe> getAllRecipes() {
        return Collections.unmodifiableCollection(RECIPES_BY_MILESTONE_UNLOCK_ID.values());
    }
}