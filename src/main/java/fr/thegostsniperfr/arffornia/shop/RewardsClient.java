package fr.thegostsniperfr.arffornia.shop;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** The website's reward endpoints. Futures never complete exceptionally: failures map to the documented results. */
public interface RewardsClient {

    sealed interface PendingResult {
        record Found(List<PendingReward> rewards) implements PendingResult {
        }

        record NotLinked() implements PendingResult {
        }

        record Failed() implements PendingResult {
        }
    }

    sealed interface ClaimResult {
        /** The reward is ours to deliver, exactly once, with these contents. */
        record Claimed(PendingReward reward) implements ClaimResult {
        }

        /** Someone else claimed it, or it is not claimable any more: deliver nothing. */
        record Rejected(int status) implements ClaimResult {
        }

        /** Unknown outcome (network, 5xx after retry): deliver nothing, the reward stays pending if not claimed. */
        record Failed() implements ClaimResult {
        }
    }

    record StreakResult(int dayStreak, boolean firstConnectionToday) {
    }

    CompletableFuture<PendingResult> fetchPending(UUID player);

    CompletableFuture<ClaimResult> claim(long rewardId, UUID player, String idempotencyKey);

    /** @return the streak, or {@code null} on failure */
    CompletableFuture<StreakResult> recordDailyConnection(UUID player);
}
