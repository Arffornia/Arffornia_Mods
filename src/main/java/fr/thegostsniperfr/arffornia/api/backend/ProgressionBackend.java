package fr.thegostsniperfr.arffornia.api.backend;

import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import org.jetbrains.annotations.Nullable;

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

    /** Progression (completed milestones, current target, retired), or null on failure. */
    CompletableFuture<ArfforniaApiDtos.ProgressionData> fetchProgressionData(long progressionId);

    /** Milestone details (requirements, unlocks), or null on failure. */
    CompletableFuture<ArfforniaApiDtos.MilestoneDetails> fetchMilestoneDetails(int milestoneId);

    /** Same as {@link #fetchMilestoneDetails(int)}, with text in the given game language (e.g. {@code fr_fr}) when the backend supports it. */
    default CompletableFuture<ArfforniaApiDtos.MilestoneDetails> fetchMilestoneDetails(int milestoneId, String minecraftLanguage) {
        return fetchMilestoneDetails(milestoneId);
    }

    /** Completes a milestone in a given progression (false when it is unknown or retired). */
    CompletableFuture<Boolean> addMilestoneToProgression(long progressionId, int milestoneId);

    /** Sets (or clears, with null) the player's target milestone. */
    CompletableFuture<Boolean> setTargetMilestone(UUID playerUuid, @Nullable Integer milestoneId);
}
