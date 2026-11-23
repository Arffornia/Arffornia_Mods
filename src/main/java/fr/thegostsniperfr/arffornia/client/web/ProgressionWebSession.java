package fr.thegostsniperfr.arffornia.client.web;

import fr.thegostsniperfr.arffornia.network.ServerboundEmbedSessionPacket;
import fr.thegostsniperfr.arffornia.network.ServerboundTargetMilestoneKeyPacket;
import fr.thegostsniperfr.arffornia.progression.web.ProgressionWebHooks;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Client side of the in-game progression page, without the browser: the page URL and the player's read-only token
 * from the server, and the page's pending questions (a token, a target). Runs on the client thread.
 */
public final class ProgressionWebSession implements ProgressionWebHooks.Client {
    public record Session(String token, long expiresAt) {
        @Override
        public String toString() {
            return "Session[expiresAt=" + expiresAt + "]";
        }
    }

    public static final ProgressionWebSession INSTANCE = new ProgressionWebSession();

    private static final long TIMEOUT_MS = 15_000;
    private static final long MIN_REMAINING_SECONDS = 60;

    @Nullable private String url;
    @Nullable private Session session;
    private final List<Consumer<Optional<Session>>> sessionWaiters = new ArrayList<>();
    private long sessionAskedAt;
    @Nullable private Consumer<Boolean> targetWaiter;
    @Nullable private String targetKey;
    private long targetAskedAt;
    private Runnable onUrl = () -> {
    };

    private ProgressionWebSession() {
    }

    /** Called once the page URL is known, to load the page off-screen. */
    public void onUrl(Runnable listener) {
        onUrl = listener;
    }

    @Nullable
    public String url() {
        return url;
    }

    /** The page origin the bridge answers, or null while no URL is known. */
    @Nullable
    public String origin() {
        return originOf(url);
    }

    @Nullable
    public static String originOf(@Nullable String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("https") && !scheme.equals("http") || uri.getHost() == null) {
                return null;
            }
            return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void requestSession(boolean renew, Consumer<Optional<Session>> done) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        if (!renew && session != null && session.expiresAt() - nowSeconds > MIN_REMAINING_SECONDS) {
            done.accept(Optional.of(session));
            return;
        }
        sessionWaiters.add(done);
        if (sessionWaiters.size() == 1 || System.currentTimeMillis() - sessionAskedAt > TIMEOUT_MS) {
            sessionAskedAt = System.currentTimeMillis();
            PacketDistributor.sendToServer(new ServerboundEmbedSessionPacket(renew));
        }
    }

    @Override
    public void onSession(String pageUrl, String token, long expiresAt) {
        boolean urlChanged = false;
        if (originOf(pageUrl) != null && !pageUrl.equals(url)) {
            url = pageUrl;
            urlChanged = true;
        }
        session = token.isEmpty() ? null : new Session(token, expiresAt);
        List<Consumer<Optional<Session>>> waiters = List.copyOf(sessionWaiters);
        sessionWaiters.clear();
        waiters.forEach(waiter -> waiter.accept(Optional.ofNullable(session)));
        if (urlChanged) {
            onUrl.run();
        }
    }

    /** One target at a time; a second request while one is pending is refused. */
    public void requestTarget(@Nullable String milestoneKey, Consumer<Boolean> done) {
        if (targetWaiter != null) {
            done.accept(false);
            return;
        }
        targetWaiter = done;
        targetKey = milestoneKey;
        targetAskedAt = System.currentTimeMillis();
        PacketDistributor.sendToServer(new ServerboundTargetMilestoneKeyPacket(Optional.ofNullable(milestoneKey)));
    }

    @Override
    public void onTargetResult(@Nullable String milestoneKey, boolean accepted) {
        if (targetWaiter == null || !Objects.equals(milestoneKey, targetKey)) {
            return;
        }
        Consumer<Boolean> waiter = targetWaiter;
        targetWaiter = null;
        targetKey = null;
        waiter.accept(accepted);
    }

    /** Fails questions the server never answered (an old server, a lost packet). */
    public void tick() {
        long now = System.currentTimeMillis();
        if (!sessionWaiters.isEmpty() && now - sessionAskedAt > TIMEOUT_MS) {
            List<Consumer<Optional<Session>>> waiters = List.copyOf(sessionWaiters);
            sessionWaiters.clear();
            waiters.forEach(waiter -> waiter.accept(Optional.empty()));
        }
        if (targetWaiter != null && now - targetAskedAt > TIMEOUT_MS) {
            onTargetResult(targetKey, false);
        }
    }

    /** On leaving a server: the next one may serve another site. */
    public void clear() {
        url = null;
        session = null;
        List<Consumer<Optional<Session>>> waiters = List.copyOf(sessionWaiters);
        sessionWaiters.clear();
        waiters.forEach(waiter -> waiter.accept(Optional.empty()));
        if (targetWaiter != null) {
            onTargetResult(targetKey, false);
        }
    }

    /** The page language: the game's when the website has it. */
    public static String pageLocale() {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        return language.startsWith("fr") ? "fr" : "en";
    }
}
