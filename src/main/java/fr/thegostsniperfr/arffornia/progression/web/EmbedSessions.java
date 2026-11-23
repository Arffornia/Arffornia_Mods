package fr.thegostsniperfr.arffornia.progression.web;

import fr.thegostsniperfr.arffornia.api.backend.ProgressionBackends;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only tokens of the in-game progression page (spec 018 D2 a), minted by the website for the game server's
 * service account and kept in memory per online player. Tokens are never logged.
 */
public final class EmbedSessions {
    /** A token this close to expiry is replaced, so the page never starts a read with one about to lapse. */
    static final long MIN_REMAINING_SECONDS = 120;

    private static final Map<UUID, ArfforniaApiDtos.EmbedToken> TOKENS = new ConcurrentHashMap<>();
    private static final Map<UUID, CompletableFuture<ArfforniaApiDtos.EmbedToken>> MINTING = new ConcurrentHashMap<>();

    private EmbedSessions() {
    }

    /**
     * The player's token: the cached one unless {@code renew} or close to expiry, else a new one when {@code mayMint}.
     * Completes with null when there is none to give.
     */
    public static CompletableFuture<ArfforniaApiDtos.EmbedToken> forPlayer(UUID player, boolean renew, boolean mayMint) {
        ArfforniaApiDtos.EmbedToken cached = TOKENS.get(player);
        boolean fresh = cached != null && cached.expiresAt() - System.currentTimeMillis() / 1000 > MIN_REMAINING_SECONDS;
        if (fresh && !renew) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<ArfforniaApiDtos.EmbedToken> running = MINTING.get(player);
        if (running != null) {
            return running;
        }
        if (!mayMint) {
            return CompletableFuture.completedFuture(fresh ? cached : null);
        }
        CompletableFuture<ArfforniaApiDtos.EmbedToken> mint = ProgressionBackends.get().mintEmbedToken(player)
                .exceptionally(error -> null);
        MINTING.put(player, mint);
        mint.whenComplete((token, error) -> {
            MINTING.remove(player, mint);
            if (token != null) {
                TOKENS.put(player, token);
            }
        });
        return mint;
    }

    @Nullable
    static ArfforniaApiDtos.EmbedToken cached(UUID player) {
        return TOKENS.get(player);
    }

    public static void forget(UUID player) {
        TOKENS.remove(player);
    }
}
