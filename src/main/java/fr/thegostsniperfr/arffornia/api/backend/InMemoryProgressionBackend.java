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
import java.util.concurrent.TimeUnit;

/**
 * In-memory backend for GameTests and offline dev runs ({@code -Darffornia.backend=fake}).
 * Reads complete immediately; {@link #sendProgressionEvents} completes after a short delay, like a network call.
 * State is shared, so tests should use distinct ids.
 */
public final class InMemoryProgressionBackend implements ProgressionBackend {
    /** Delay applied to write calls to mimic network latency. */
    private static final long ASYNC_DELAY_MS = 50;

    private final Map<String, Long> activeProgressionByPlayer = new ConcurrentHashMap<>();
    private final Map<Long, Set<Integer>> completedByProgression = new ConcurrentHashMap<>();
    private final Map<Integer, ArfforniaApiDtos.MilestoneDetails> milestones = new ConcurrentHashMap<>();
    private final Map<String, Integer> targetByPlayer = new ConcurrentHashMap<>();
    private final Map<String, String> targetKeyByPlayer = new ConcurrentHashMap<>();
    private final Map<String, Integer> embedTokensByPlayer = new ConcurrentHashMap<>();
    private final Set<Long> retiredProgressions = ConcurrentHashMap.newKeySet();
    private final Map<Long, Set<String>> completedKeysByProgression = new ConcurrentHashMap<>();
    private final Map<Long, Integer> openStageByProgression = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> seenEventIds = new ConcurrentHashMap<>();
    private final Set<Long> unreachableProgressions = ConcurrentHashMap.newKeySet();
    private final Map<Long, List<ArfforniaApiDtos.ProgressionEvent>> receivedEvents = new ConcurrentHashMap<>();
    private final Map<String, ArfforniaApiDtos.ModelMilestone> modelMilestones = new ConcurrentHashMap<>();
    private final Set<String> refusedMilestones = ConcurrentHashMap.newKeySet();

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

    public InMemoryProgressionBackend createProgression(long progressionId) {
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

    /** Marks a progression as retired, as when its team is soft-deleted on the website. */
    public InMemoryProgressionBackend retire(long progressionId) {
        retiredProgressions.add(progressionId);
        return this;
    }

    public InMemoryProgressionBackend setOpenStage(long progressionId, int stage) {
        openStageByProgression.put(progressionId, stage);
        return this;
    }

    /** Adds a milestone to the published model (shared by every test, so use distinct keys). */
    public InMemoryProgressionBackend putModelMilestone(String key, int stage, String... requires) {
        modelMilestones.put(key, new ArfforniaApiDtos.ModelMilestone(key, stage, List.of(requires), null));
        return this;
    }

    /** Makes the events endpoint reject this milestone as not available, as the website does when its rule 3 disagrees with the game. */
    public InMemoryProgressionBackend refuseMilestone(String key) {
        refusedMilestones.add(key);
        return this;
    }

    public InMemoryProgressionBackend allowMilestone(String key) {
        refusedMilestones.remove(key);
        return this;
    }

    public ArfforniaApiDtos.ModelDocument model() {
        return new ArfforniaApiDtos.ModelDocument(1, List.copyOf(modelMilestones.values()));
    }

    /** While set, events for this progression get no answer, as when the website is down (per id, so parallel tests do not interfere). */
    public InMemoryProgressionBackend setUnreachable(long progressionId, boolean unreachable) {
        if (unreachable) {
            unreachableProgressions.add(progressionId);
        } else {
            unreachableProgressions.remove(progressionId);
        }
        return this;
    }

    public boolean isCompleted(long progressionId, String milestoneKey) {
        return completedKeysByProgression.getOrDefault(progressionId, Set.of()).contains(milestoneKey);
    }

    /** Every event the website accepted an answer for, duplicates included. */
    public List<ArfforniaApiDtos.ProgressionEvent> receivedEvents(long progressionId) {
        return List.copyOf(receivedEvents.getOrDefault(progressionId, List.of()));
    }

    public boolean isCompleted(long progressionId, int milestoneId) {
        return completedByProgression.getOrDefault(progressionId, Set.of()).contains(milestoneId);
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
                new ArfforniaApiDtos.ProgressionData((int) progressionId, new ArrayList<>(completed), null,
                        retiredProgressions.contains(progressionId)));
    }

    @Override
    public CompletableFuture<ArfforniaApiDtos.ProgressionState> fetchProgressionState(long progressionId) {
        if (!completedByProgression.containsKey(progressionId)) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.completedFuture(state(progressionId));
    }

    @Override
    public CompletableFuture<ArfforniaApiDtos.ModelReply> fetchProgressionModel(@Nullable String etag) {
        return CompletableFuture.completedFuture(new ArfforniaApiDtos.ModelReply(200, "test-" + modelMilestones.size(), model()));
    }

    /** Same answers as the website: duplicate by event id or by milestone key, 409 for a retired progression. */
    @Override
    public CompletableFuture<ArfforniaApiDtos.EventsReply> sendProgressionEvents(long progressionId, List<ArfforniaApiDtos.ProgressionEvent> events) {
        return CompletableFuture.supplyAsync(() -> {
            if (unreachableProgressions.contains(progressionId)) {
                return ArfforniaApiDtos.EventsReply.noAnswer();
            }
            if (!completedByProgression.containsKey(progressionId)) {
                return new ArfforniaApiDtos.EventsReply(404, null);
            }
            if (retiredProgressions.contains(progressionId)) {
                return new ArfforniaApiDtos.EventsReply(409, null);
            }
            Set<String> seen = seenEventIds.computeIfAbsent(progressionId, id -> ConcurrentHashMap.newKeySet());
            Set<String> done = completedKeysByProgression.computeIfAbsent(progressionId, id -> ConcurrentHashMap.newKeySet());
            List<ArfforniaApiDtos.EventResult> results = new ArrayList<>();
            for (ArfforniaApiDtos.ProgressionEvent event : events) {
                receivedEvents.computeIfAbsent(progressionId, id -> new CopyOnWriteArrayList<>()).add(event);
                if (refusedMilestones.contains(event.milestone())) {
                    results.add(new ArfforniaApiDtos.EventResult(event.id(), ArfforniaApiDtos.EventResult.REJECTED, "milestone_unavailable"));
                    continue;
                }
                boolean applied = seen.add(event.id()) && done.add(event.milestone());
                results.add(new ArfforniaApiDtos.EventResult(event.id(),
                        applied ? ArfforniaApiDtos.EventResult.APPLIED : ArfforniaApiDtos.EventResult.DUPLICATE, null));
            }
            return new ArfforniaApiDtos.EventsReply(200, new ArfforniaApiDtos.EventsResponse(results, state(progressionId)));
        }, CompletableFuture.delayedExecutor(ASYNC_DELAY_MS, TimeUnit.MILLISECONDS));
    }

    private ArfforniaApiDtos.ProgressionState state(long progressionId) {
        List<ArfforniaApiDtos.CompletedMilestone> completed = completedKeysByProgression.getOrDefault(progressionId, Set.of()).stream()
                .sorted().map(key -> new ArfforniaApiDtos.CompletedMilestone(key, "2026-01-01T00:00:00Z")).toList();
        return new ArfforniaApiDtos.ProgressionState(progressionId, 1, retiredProgressions.contains(progressionId),
                openStageByProgression.getOrDefault(progressionId, 1), completed);
    }

    @Override
    public CompletableFuture<ArfforniaApiDtos.MilestoneDetails> fetchMilestoneDetails(int milestoneId) {
        return CompletableFuture.completedFuture(milestones.get(milestoneId));
    }

    @Override
    public CompletableFuture<Boolean> setTargetMilestone(UUID playerUuid, @Nullable Integer milestoneId) {
        targetKeyByPlayer.remove(compact(playerUuid));
        if (milestoneId == null) {
            targetByPlayer.remove(compact(playerUuid));
        } else {
            targetByPlayer.put(compact(playerUuid), milestoneId);
        }
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Boolean> setTargetMilestoneKey(UUID playerUuid, String milestoneKey) {
        targetKeyByPlayer.put(compact(playerUuid), milestoneKey);
        targetByPlayer.remove(compact(playerUuid));
        return CompletableFuture.completedFuture(true);
    }

    /** The key last targeted through {@link #setTargetMilestoneKey}, or null after a clear or when none was. */
    @Nullable
    public String targetKeyOf(UUID playerUuid) {
        return targetKeyByPlayer.get(compact(playerUuid));
    }

    /** Offline dev runs point the page at a local website ({@code php artisan serve}). */
    @Override
    public CompletableFuture<ArfforniaApiDtos.EmbedToken> mintEmbedToken(UUID playerUuid) {
        int minted = embedTokensByPlayer.merge(compact(playerUuid), 1, Integer::sum);
        String url = System.getProperty("arffornia.embedUrl", "http://127.0.0.1:8000/embed/progression");
        return CompletableFuture.completedFuture(new ArfforniaApiDtos.EmbedToken("fake-embed-token-" + minted, System.currentTimeMillis() / 1000 + 900, url));
    }

    public int embedTokensMinted(UUID playerUuid) {
        return embedTokensByPlayer.getOrDefault(compact(playerUuid), 0);
    }
}
