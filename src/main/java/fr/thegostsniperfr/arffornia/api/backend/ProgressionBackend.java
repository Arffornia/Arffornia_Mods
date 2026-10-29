package fr.thegostsniperfr.arffornia.api.backend;

import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Server-side access to progression data used by gameplay code (blocks, block entities, payload handlers).
 * <p>
 * Production uses {@link HttpProgressionBackend} (the website API). Headless tests and offline dev runs use
 * {@link InMemoryProgressionBackend}, selected with {@code -Darffornia.backend=fake}. Obtain the active instance
 * with {@link ProgressionBackends#get()}.
 * <p>
 * Futures may complete on any thread: hop back to the server thread before touching the world.
 */
public interface ProgressionBackend {

    /** Player data (active progression id) by UUID without dashes, or null on failure. */
    CompletableFuture<ArfforniaApiDtos.PlayerData> fetchPlayerData(String compactPlayerUuid);

    /** Progression (completed milestones, current target), or null on failure. */
    CompletableFuture<ArfforniaApiDtos.ProgressionData> fetchProgressionData(long progressionId);

    /** Milestone details (requirements, unlocks), or null on failure. */
    CompletableFuture<ArfforniaApiDtos.MilestoneDetails> fetchMilestoneDetails(int milestoneId);

    /** Completes a milestone in the player's active progression. */
    CompletableFuture<Boolean> addMilestone(UUID playerUuid, int milestoneId);

    /** Sets (or clears, with null) the player's target milestone. */
    CompletableFuture<Boolean> setTargetMilestone(UUID playerUuid, @Nullable Integer milestoneId);

    /** All Crafter recipes (empty on failure). */
    CompletableFuture<List<ArfforniaApiDtos.CustomRecipe>> fetchAllCustomRecipes();

    /** Server-wide progression config (banned recipes; empty on failure). */
    CompletableFuture<ArfforniaApiDtos.ProgressionConfig> fetchProgressionConfig();
}
