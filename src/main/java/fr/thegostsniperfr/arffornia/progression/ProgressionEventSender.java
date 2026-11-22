package fr.thegostsniperfr.arffornia.progression;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.backend.ProgressionBackends;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Records milestones completed in game and sends them to the website's idempotent events endpoint (spec 017).
 * <p>
 * The world is the runtime authority: a completion applies to the progression at once and waits in the
 * {@link ProgressionEventOutbox} until the website acknowledges it. A failed call is retried with a growing delay;
 * a website outage never locks or unlocks anything. Stages are opened by the website; the game learns it from the
 * state that comes back with each answer. Every method runs on the server thread.
 */
@EventBusSubscriber(modid = Arffornia.MODID)
public final class ProgressionEventSender {
    static final int MAX_BATCH = 100;
    private static final long FIRST_RETRY_MS = 5_000;
    private static final long MAX_RETRY_MS = 300_000;
    private static final int TICKS_BETWEEN_CHECKS = 20;

    private record Retry(int failures, long notBefore) {
    }

    private static final Set<Long> IN_FLIGHT = new HashSet<>();
    private static final Map<Long, Retry> RETRIES = new HashMap<>();
    // Keys the website does not know (the datapack is ahead of or behind the published model): not sent again until the next data reload.
    private static final Set<String> UNKNOWN_KEYS = new HashSet<>();

    private ProgressionEventSender() {
    }

    /** The player met a milestone's validation: it is done for their active progression, now, and the website is told. */
    public static void milestoneCompleted(ServerPlayer player, String milestoneKey) {
        MinecraftServer server = player.server;
        long progressionId = Progressions.activeProgressionOf(player);
        if (progressionId == ModAttachments.NO_PROGRESSION || UNKNOWN_KEYS.contains(milestoneKey)) {
            return;
        }
        Optional<ProgressionSnapshot> snapshot = Progressions.find(server, progressionId);
        if (snapshot.map(known -> known.retired() || known.isCompleted(milestoneKey)).orElse(false)) {
            return;
        }
        ProgressionEventOutbox outbox = ProgressionEventOutbox.get(server);
        if (outbox.pendingKeys(progressionId).contains(milestoneKey)) {
            return;
        }
        outbox.add(progressionId, UUID.randomUUID().toString().replace("-", ""), milestoneKey, System.currentTimeMillis(),
                player.getUUID().toString().replace("-", ""));
        if (snapshot.isPresent()) {
            Progressions.recordCompletedKey(server, progressionId, milestoneKey);
        } else {
            Progressions.ensureKnown(server, progressionId);
        }
        send(server, progressionId);
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        IN_FLIGHT.clear();
        RETRIES.clear();
        UNKNOWN_KEYS.clear();
    }

    @SubscribeEvent
    public static void onDataReload(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            UNKNOWN_KEYS.clear();
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % TICKS_BETWEEN_CHECKS == 0) {
            sendDue(event.getServer(), System.currentTimeMillis());
        }
    }

    private static void sendDue(MinecraftServer server, long now) {
        for (long progressionId : ProgressionEventOutbox.get(server).progressionIds()) {
            Retry retry = RETRIES.get(progressionId);
            if (retry == null || now >= retry.notBefore()) {
                send(server, progressionId);
            }
        }
    }

    /** Sends the oldest pending events of one progression now, whatever the retry delay. At most one call per progression is in flight. */
    public static void send(MinecraftServer server, long progressionId) {
        ProgressionEventOutbox outbox = ProgressionEventOutbox.get(server);
        List<ProgressionEventOutbox.Pending> batch = outbox.oldest(progressionId, MAX_BATCH);
        if (batch.isEmpty() || IN_FLIGHT.contains(progressionId)) {
            return;
        }
        if (Progressions.find(server, progressionId).map(ProgressionSnapshot::retired).orElse(false)) {
            Arffornia.LOGGER.warn("Dropping {} progression events of retired progression {}.", batch.size(), progressionId);
            outbox.remove(ids(outbox.oldest(progressionId, Integer.MAX_VALUE)));
            RETRIES.remove(progressionId);
            return;
        }
        IN_FLIGHT.add(progressionId);
        ProgressionBackends.get().sendProgressionEvents(progressionId, batch.stream().map(ProgressionEventOutbox.Pending::toDto).toList())
                .thenAcceptAsync(reply -> {
                    IN_FLIGHT.remove(progressionId);
                    if (handle(server, progressionId, batch, reply)) {
                        send(server, progressionId);
                    }
                }, server)
                .exceptionally(error -> {
                    server.execute(() -> IN_FLIGHT.remove(progressionId));
                    Arffornia.LOGGER.error("Handling the events answer for progression {} failed", progressionId, error);
                    return null;
                });
    }

    /** @return true when more events of this progression are waiting and may be sent right away */
    private static boolean handle(MinecraftServer server, long progressionId, List<ProgressionEventOutbox.Pending> batch,
                                  ArfforniaApiDtos.EventsReply reply) {
        ProgressionEventOutbox outbox = ProgressionEventOutbox.get(server);
        int status = reply.status();
        if (status >= 200 && status < 300 && reply.body() != null && reply.body().results() != null) {
            Map<String, ProgressionEventOutbox.Pending> sent = new HashMap<>();
            batch.forEach(event -> sent.put(event.id(), event));
            Set<String> answered = new HashSet<>();
            for (ArfforniaApiDtos.EventResult result : reply.body().results()) {
                ProgressionEventOutbox.Pending event = sent.get(result.id());
                if (event == null) {
                    continue;
                }
                answered.add(event.id());
                if (ArfforniaApiDtos.EventResult.REJECTED.equals(result.status())) {
                    Arffornia.LOGGER.warn("The website rejected milestone {} for progression {}: {}", event.milestoneKey(), progressionId, result.reason());
                    if ("unknown_milestone".equals(result.reason())) {
                        UNKNOWN_KEYS.add(event.milestoneKey());
                    }
                }
            }
            outbox.remove(answered);
            RETRIES.remove(progressionId);
            if (reply.body().state() != null) {
                Progressions.applyKeyedState(server, progressionId, reply.body().state());
            }
            return !answered.isEmpty() && !outbox.oldest(progressionId, 1).isEmpty();
        }
        if (status == 400 || status == 404 || status == 422) {
            // The website will never accept this batch: keeping it would block every later event of the progression.
            Arffornia.LOGGER.error("The website refused {} progression events for progression {} (status {}); dropping them.", batch.size(), progressionId, status);
            outbox.remove(ids(batch));
            RETRIES.remove(progressionId);
            return !outbox.oldest(progressionId, 1).isEmpty();
        }
        if (status == 409) {
            // Retired, or no model published yet: a refresh says which; a retired progression's events are dropped on the next try.
            Progressions.refreshProgression(server, progressionId);
        }
        Retry previous = RETRIES.get(progressionId);
        int failures = previous == null ? 1 : previous.failures() + 1;
        long delay = Math.min(MAX_RETRY_MS, FIRST_RETRY_MS << Math.min(failures - 1, 16));
        RETRIES.put(progressionId, new Retry(failures, System.currentTimeMillis() + delay));
        Arffornia.LOGGER.warn("Could not send {} progression events for progression {} (status {}); retrying in {} s.",
                batch.size(), progressionId, status == 0 ? "no answer" : status, delay / 1000);
        return false;
    }

    private static Set<String> ids(List<ProgressionEventOutbox.Pending> events) {
        Set<String> ids = new HashSet<>();
        events.forEach(event -> ids.add(event.id()));
        return ids;
    }
}
