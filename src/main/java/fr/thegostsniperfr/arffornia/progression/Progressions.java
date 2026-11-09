package fr.thegostsniperfr.arffornia.progression;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.backend.ProgressionBackends;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.network.ClientboundProgressionStatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-side progression state (Arffornia_Mods#18, #29). Gameplay reads it synchronously; the website is only
 * called to refresh it, on login, team change and milestone changes. Call every method on the server thread.
 * <p>
 * A machine belongs to the progression it was placed for. It serves that progression while the progression is known
 * and not retired, whatever team its placer is in now.
 */
public final class Progressions {
    private static final Set<Long> FIRST_FETCHES = ConcurrentHashMap.newKeySet();
    // Concurrent refreshes of the same key can answer out of order: only a response newer than the last applied one wins.
    private static final AtomicLong REQUESTS = new AtomicLong();
    private static final Map<Long, Long> PROGRESSION_APPLIED = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PLAYER_APPLIED = new ConcurrentHashMap<>();

    private Progressions() {
    }

    public static long activeProgressionOf(ServerPlayer player) {
        return player.getData(ModAttachments.ACTIVE_PROGRESSION);
    }

    public static Optional<ProgressionSnapshot> find(MinecraftServer server, long progressionId) {
        if (progressionId == ModAttachments.NO_PROGRESSION) {
            return Optional.empty();
        }
        return ProgressionStore.get(server).find(progressionId);
    }

    /** True when a machine placed for this progression may run. Unknown progressions fail closed. */
    public static boolean isServing(MinecraftServer server, long progressionId) {
        return find(server, progressionId).filter(snapshot -> !snapshot.retired()).isPresent();
    }

    public static boolean isUnlocked(MinecraftServer server, long progressionId, int milestoneId) {
        return find(server, progressionId).filter(snapshot -> !snapshot.retired() && snapshot.isCompleted(milestoneId)).isPresent();
    }

    /** Fetches a progression this server has never seen, for example one a machine from an older save was placed for. */
    public static void ensureKnown(MinecraftServer server, long progressionId) {
        if (progressionId != ModAttachments.NO_PROGRESSION && ProgressionStore.get(server).find(progressionId).isEmpty()
                && FIRST_FETCHES.add(progressionId)) {
            refreshProgression(server, progressionId).whenComplete((ignored, error) -> FIRST_FETCHES.remove(progressionId));
        }
    }

    /**
     * Reads the player's active progression from the website, then refreshes it and, after a team change, the
     * progression the player left (it may have just been retired).
     */
    public static CompletableFuture<Void> refreshPlayer(ServerPlayer player) {
        MinecraftServer server = player.server;
        String compactUuid = player.getUUID().toString().replace("-", "");
        long request = REQUESTS.incrementAndGet();
        return ProgressionBackends.get().fetchPlayerData(compactUuid).thenComposeAsync(playerData -> {
            if (!isNewest(PLAYER_APPLIED, player.getUUID(), request)) {
                return CompletableFuture.completedFuture(null);
            }
            if (playerData == null) {
                Arffornia.LOGGER.warn("Could not refresh the progression of {}; keeping the last known one.", player.getName().getString());
                return CompletableFuture.completedFuture(null);
            }
            long previous = activeProgressionOf(player);
            long current = playerData.activeProgressionId();
            player.setData(ModAttachments.ACTIVE_PROGRESSION, current);
            CompletableFuture<Void> refreshCurrent = refreshProgression(server, current);
            if (previous != current && previous != ModAttachments.NO_PROGRESSION) {
                return CompletableFuture.allOf(refreshCurrent, refreshProgression(server, previous));
            }
            return refreshCurrent;
        }, server);
    }

    /** Re-reads one progression from the website, stores it and syncs it to the online players it is active for. */
    public static CompletableFuture<Void> refreshProgression(MinecraftServer server, long progressionId) {
        long request = REQUESTS.incrementAndGet();
        return ProgressionBackends.get().fetchProgressionData(progressionId)
                .thenAcceptAsync(data -> {
                    if (!isNewest(PROGRESSION_APPLIED, progressionId, request)) {
                        return;
                    }
                    if (data == null) {
                        Arffornia.LOGGER.warn("Could not refresh progression {}; keeping the last known state.", progressionId);
                        return;
                    }
                    store(server, toSnapshot(progressionId, data));
                }, server);
    }

    /** Re-reads every stored progression, so machines learn about retirements that happened while the server was down. */
    public static void refreshAll(MinecraftServer server) {
        ProgressionStore.get(server).knownIds().forEach(id -> refreshProgression(server, id));
    }

    public static void store(MinecraftServer server, ProgressionSnapshot snapshot) {
        ProgressionStore.get(server).put(snapshot);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (activeProgressionOf(player) == snapshot.id()) {
                PacketDistributor.sendToPlayer(player, new ClientboundProgressionStatePacket(snapshot));
            }
        }
    }

    /** Sends the player's last known state, without waiting for the website. */
    public static void sendCachedState(ServerPlayer player) {
        find(player.server, activeProgressionOf(player))
                .ifPresent(snapshot -> PacketDistributor.sendToPlayer(player, new ClientboundProgressionStatePacket(snapshot)));
    }

    /** Records {@code request} as applied for {@code key} unless a newer request was already applied. */
    private static <K> boolean isNewest(Map<K, Long> applied, K key, long request) {
        long winner = applied.merge(key, request, Math::max);
        return winner == request;
    }

    /** Records a milestone the website just confirmed, so checks see it before the next refresh answers. */
    public static void recordCompleted(MinecraftServer server, long progressionId, int milestoneId) {
        find(server, progressionId).ifPresent(snapshot -> {
            PROGRESSION_APPLIED.merge(progressionId, REQUESTS.incrementAndGet(), Math::max);
            Set<Integer> completed = new HashSet<>(snapshot.completedMilestones());
            completed.add(milestoneId);
            store(server, new ProgressionSnapshot(progressionId, completed, snapshot.currentMilestoneId(), snapshot.retired()));
        });
    }

    private static ProgressionSnapshot toSnapshot(long progressionId, ArfforniaApiDtos.ProgressionData data) {
        Set<Integer> completed = data.completedMilestones() != null ? new HashSet<>(data.completedMilestones()) : Set.of();
        return new ProgressionSnapshot(progressionId, completed, data.currentMilestoneId(), data.retired());
    }
}
