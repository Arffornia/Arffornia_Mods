package fr.thegostsniperfr.arffornia.client.screen.title;

import com.mojang.logging.LogUtils;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import org.slf4j.Logger;

/**
 * The title screen's live data (spec 007 item 7): the first server of the player's list (the launcher
 * writes it, so it follows the chosen environment), pinged like the server list, and the player's
 * progression and the latest news headline from the website (the headline cached on disk for offline
 * starts). Nothing blocks the render thread; anything slower than 3 s counts as
 * unavailable.
 */
final class TitleStatus {
    enum Server { CHECKING, ONLINE, OFFLINE, NONE }

    static final long TIMEOUT_MS = 3000;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ExecutorService PINGS = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Arffornia title ping");
        t.setDaemon(true);
        return t;
    });
    // Fetched once per game session: reopening the title screen must not hit the website again.
    private static CompletableFuture<ArfforniaApiDtos.ProgressionState> progression;
    private static CompletableFuture<ArfforniaApiDtos.NewsHeadline> news;

    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private final MotdStore motdStore;
    private final NewsStore newsStore;
    @Nullable private final ArfforniaApiDtos.NewsHeadline cachedNews;
    @Nullable private final ServerData server;
    private final long startedAt = Util.getMillis();
    private volatile boolean pinged;
    private volatile boolean failed;

    TitleStatus(Minecraft minecraft, MotdStore motdStore, NewsStore newsStore) {
        this.motdStore = motdStore;
        this.newsStore = newsStore;
        this.cachedNews = newsStore.read().orElse(null);
        ServerList list = new ServerList(minecraft);
        list.load();
        this.server = list.size() > 0 ? list.get(0) : null;
        if (server != null) {
            ServerData target = server;
            PINGS.execute(() -> {
                try {
                    pinger.pingServer(target, () -> {}, this::onPinged);
                } catch (Exception e) {
                    LOGGER.debug("Title screen ping of {} failed: {}", target.ip, e.toString());
                    failed = true;
                }
            });
        }
        if (progression == null) {
            String uuid = minecraft.getUser().getProfileId().toString().replace("-", "");
            progression = ArfforniaApiService.getInstance().http()
                    .<ArfforniaApiDtos.ProgressionState>getJson("/players/" + uuid + "/progression/state", ArfforniaApiDtos.ProgressionState.class, "title screen progression")
                    .orTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .exceptionally(e -> null);
        }
        if (news == null) {
            news = ArfforniaApiService.getInstance().http()
                    .<ArfforniaApiDtos.NewsFeed>getJson("/news/latest", ArfforniaApiDtos.NewsFeed.class, "title screen news")
                    .orTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .thenApply(feed -> feed == null || feed.news() == null ? null
                            : feed.news().stream().filter(NewsStore::isValid).findFirst().orElse(null))
                    .exceptionally(e -> null);
            news.thenAccept(headline -> {
                if (headline != null) newsStore.write(headline);
            });
        }
    }

    private void onPinged() {
        pinged = true;
        if (server != null && server.motd != null) {
            MotdText.splash(server.motd.getString()).ifPresent(motdStore::write);
        }
    }

    void tick() {
        pinger.tick();
    }

    void close() {
        pinger.removeAll();
    }

    @Nullable
    ServerData server() {
        return server;
    }

    Server serverState() {
        if (server == null) return Server.NONE;
        // The pinger calls back only after a full status + pong exchange.
        if (pinged) return Server.ONLINE;
        if (failed || Util.getMillis() - startedAt > TIMEOUT_MS) return Server.OFFLINE;
        return Server.CHECKING;
    }

    int onlinePlayers() {
        return server != null && server.players != null ? server.players.online() : 0;
    }

    @Nullable
    ArfforniaApiDtos.ProgressionState progression() {
        return progression != null && progression.isDone() ? progression.getNow(null) : null;
    }

    @Nullable
    ArfforniaApiDtos.NewsHeadline news() {
        ArfforniaApiDtos.NewsHeadline fresh = news != null && news.isDone() ? news.getNow(null) : null;
        return fresh != null ? fresh : cachedNews;
    }
}
