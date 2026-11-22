package fr.thegostsniperfr.arffornia.stats;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.backend.ProgressionBackends;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import fr.thegostsniperfr.arffornia.progression.ModAttachments;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.Instant;
import java.util.List;

/**
 * Sends each player's daily playtime to the website (spec 019 Q5): the day's running total, every few minutes and
 * at logout. The website keeps the highest total it got per day, so a lost or replayed report costs nothing. The
 * total lives in a saved player attachment so a restart keeps counting from it. Runs on the server thread; the HTTP
 * call is a {@code CompletableFuture} that is never waited on.
 */
@EventBusSubscriber(modid = Arffornia.MODID)
public final class PlaytimeTracker {
    private static final int TICKS_BETWEEN_REPORTS = 20 * 60 * 5;

    private static int ticks;

    private PlaytimeTracker() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.setData(ModAttachments.PLAYTIME_COUNTED_UNTIL, System.currentTimeMillis());
            record(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            record(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks < TICKS_BETWEEN_REPORTS) {
            return;
        }
        ticks = 0;
        recordAll(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        recordAll(event.getServer());
    }

    private static void recordAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            record(player);
        }
    }

    private static void record(ServerPlayer player) {
        long nowMillis = System.currentTimeMillis();
        long countedUntil = player.getData(ModAttachments.PLAYTIME_COUNTED_UNTIL);
        player.setData(ModAttachments.PLAYTIME_COUNTED_UNTIL, nowMillis);
        Instant now = Instant.ofEpochMilli(nowMillis);
        Instant from = countedUntil > 0 ? Instant.ofEpochMilli(countedUntil) : now;
        List<DailyPlaytime> days = DailyPlaytime.advance(player.getData(ModAttachments.PLAYTIME), from, now);
        player.setData(ModAttachments.PLAYTIME, days.getLast());
        if (ProgressionBackends.isFake()) {
            return;
        }
        for (DailyPlaytime day : days) {
            if (day.seconds() > 0) {
                ArfforniaApiService.getInstance().sendDailyPlaytime(player.getUUID(), day.day(), day.seconds());
            }
        }
    }
}
