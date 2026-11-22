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
 * Reads complete immediately; {@link #addMilestoneToProgression} completes after a short delay, like a network call.
 * State is shared, so tests should use distinct ids.
 */
public final class InMemoryProgressionBackend implements ProgressionBackend {
    /** Delay applied to write calls to mimic network latency. */
    private static final long ASYNC_DELAY_MS = 50;

    /** A recorded {@link #addMilestoneToProgression} call. */
    public record AddMilestoneCall(long progressionId, int milestoneId) {
    }

    private final Map<String, Long> activeProgressionByPlayer = new ConcurrentHashMap<>();
    private final Map<Long, Set<Integer>> completedByProgression = new ConcurrentHashMap<>();
    private final Map<Integer, ArfforniaApiDtos.MilestoneDetails> milestones = new ConcurrentHashMap<>();
    private final Map<String, Integer> targetByPlayer = new ConcurrentHashMap<>();
    private final List<AddMilestoneCall> addMilestoneCalls = new CopyOnWriteArrayList<>();
    private final Set<Integer> failingMilestones = ConcurrentHashMap.newKeySet();
    private final Set<Long> retiredProgressions = ConcurrentHashMap.newKeySet();
    private final Map<Long, Set<String>> completedKeysByProgression = new ConcurrentHashMap<>();
    private final Map<Long, Integer> openStageByProgression = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> seenEventIds = new ConcurrentHashMap<>();
    private final Set<Long> unreachableProgressions = ConcurrentHashMap.newKeySet();
    private final Map<Long, List<ArfforniaApiDtos.ProgressionEvent>> receivedEvents = new ConcurrentHashMap<>();

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

    /** Makes {@link #addMilestoneToProgression} report failure for this milestone id (per id, so parallel tests do not interfere). */
    public InMemoryProgressionBackend failAddMilestoneFor(int milestoneId) {
        failingMilestones.add(milestoneId);
        return this;
    }

    public InMemoryProgressionBackend setOpenStage(long progressionId, int stage) {
        openStageByProgression.put(progressionId, stage);
        return this;
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

    /**
     * Completes asynchronously after a short delay, like the real HTTP call, so callers' in-flight guards are exercised.
     */
    @Override
    public CompletableFuture<Boolean> addMilestoneToProgression(long progressionId, int milestoneId) {
        addMilestoneCalls.add(new AddMilestoneCall(progressionId, milestoneId));
        return CompletableFuture.supplyAsync(() -> {
            if (failingMilestones.contains(milestoneId) || retiredProgressions.contains(progressionId)
                    || !completedByProgression.containsKey(progressionId)) {
                return false;
            }
            completeMilestone(progressionId, milestoneId);
            return true;
        }, CompletableFuture.delayedExecutor(ASYNC_DELAY_MS, TimeUnit.MILLISECONDS));
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
}
