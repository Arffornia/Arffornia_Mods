package fr.thegostsniperfr.arffornia.api.backend;

import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory backend for GameTests and offline dev runs ({@code -Darffornia.backend=fake}).
 * All futures complete immediately. State is shared, so tests should use distinct ids.
 */
public final class InMemoryProgressionBackend implements ProgressionBackend {

    /** A recorded {@link #addMilestone} call. */
    public record AddMilestoneCall(UUID playerUuid, int milestoneId) {
    }

    private final Map<String, Long> activeProgressionByPlayer = new ConcurrentHashMap<>();
    private final Map<Long, Set<Integer>> completedByProgression = new ConcurrentHashMap<>();
    private final Map<Integer, ArfforniaApiDtos.MilestoneDetails> milestones = new ConcurrentHashMap<>();
    private final Map<Integer, ArfforniaApiDtos.CustomRecipe> recipesByUnlockId = new ConcurrentHashMap<>();
    private final Map<String, Integer> targetByPlayer = new ConcurrentHashMap<>();
    private final List<String> bannedRecipes = new CopyOnWriteArrayList<>();
    private final List<AddMilestoneCall> addMilestoneCalls = new CopyOnWriteArrayList<>();
    private volatile boolean failAddMilestone = false;

    public static String compact(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    // ---- test setup -------------------------------------------------------------------------------------------

    /** Links a player to a progression (their "active progression"). */
    public InMemoryProgressionBackend linkPlayer(UUID player, long progressionId) {
        activeProgressionByPlayer.put(compact(player), progressionId);
        completedByProgression.computeIfAbsent(progressionId, id -> ConcurrentHashMap.newKeySet());
        return this;
    }

    public InMemoryProgressionBackend completeMilestone(long progressionId, int milestoneId) {
        completedByProgression.computeIfAbsent(progressionId, id -> ConcurrentHashMap.newKeySet()).add(milestoneId);
        return this;
    }

    public InMemoryProgressionBackend putMilestone(ArfforniaApiDtos.MilestoneDetails details) {
        milestones.put(details.id(), details);
        return this;
    }

    public InMemoryProgressionBackend putRecipe(ArfforniaApiDtos.CustomRecipe recipe) {
        recipesByUnlockId.put(recipe.milestoneUnlockId(), recipe);
        return this;
    }

    public void setFailAddMilestone(boolean fail) {
        this.failAddMilestone = fail;
    }

    public boolean isCompleted(long progressionId, int milestoneId) {
        return completedByProgression.getOrDefault(progressionId, Set.of()).contains(milestoneId);
    }

    public List<AddMilestoneCall> addMilestoneCalls() {
        return Collections.unmodifiableList(addMilestoneCalls);
    }

    // ---- ProgressionBackend -----------------------------------------------------------------------------------

    @Override
    public CompletableFuture<ArfforniaApiDtos.PlayerData> fetchPlayerData(String compactPlayerUuid) {
        Long progressionId = activeProgressionByPlayer.get(compactPlayerUuid);
        return CompletableFuture.completedFuture(progressionId == null ? null : new ArfforniaApiDtos.PlayerData(progressionId));
    }

    @Override
    public CompletableFuture<ArfforniaApiDtos.ProgressionData> fetchProgressionData(long progressionId) {
        Set<Integer> completed = completedByProgression.get(progressionId);
        if (completed == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.completedFuture(
                new ArfforniaApiDtos.ProgressionData((int) progressionId, new ArrayList<>(completed), null));
    }

    @Override
    public CompletableFuture<ArfforniaApiDtos.MilestoneDetails> fetchMilestoneDetails(int milestoneId) {
        return CompletableFuture.completedFuture(milestones.get(milestoneId));
    }

    @Override
    public CompletableFuture<Boolean> addMilestone(UUID playerUuid, int milestoneId) {
        addMilestoneCalls.add(new AddMilestoneCall(playerUuid, milestoneId));
        Long progressionId = activeProgressionByPlayer.get(compact(playerUuid));
        if (failAddMilestone || progressionId == null) {
            return CompletableFuture.completedFuture(false);
        }
        completeMilestone(progressionId, milestoneId);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Boolean> setTargetMilestone(UUID playerUuid, @Nullable Integer milestoneId) {
        if (milestoneId == null) {
            targetByPlayer.remove(compact(playerUuid));
        } else {
            targetByPlayer.put(compact(playerUuid), milestoneId);
        }
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<List<ArfforniaApiDtos.CustomRecipe>> fetchAllCustomRecipes() {
        return CompletableFuture.completedFuture(new ArrayList<>(recipesByUnlockId.values()));
    }

    @Override
    public CompletableFuture<ArfforniaApiDtos.ProgressionConfig> fetchProgressionConfig() {
        return CompletableFuture.completedFuture(new ArfforniaApiDtos.ProgressionConfig(new ArrayList<>(bannedRecipes)));
    }
}
