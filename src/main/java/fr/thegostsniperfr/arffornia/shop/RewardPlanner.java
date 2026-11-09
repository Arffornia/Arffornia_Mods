package fr.thegostsniperfr.arffornia.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Chooses which pending rewards to claim now, so that what is claimed is deliverable and fits the inventory. */
public final class RewardPlanner {

    public record Plan(List<PendingReward> toClaim, List<PendingReward> undeliverable, boolean leftForLater) {
    }

    private RewardPlanner() {
    }

    public static Plan plan(List<PendingReward> rewards, int freeSlots, Predicate<RewardEntry> deliverable, ToIntFunction<RewardEntry> slotsNeeded) {
        List<PendingReward> toClaim = new ArrayList<>();
        List<PendingReward> undeliverable = new ArrayList<>();
        boolean leftForLater = false;
        int slotsLeft = freeSlots;

        for (PendingReward reward : rewards) {
            if (!reward.isFullySupported() || !reward.contents().stream().allMatch(deliverable)) {
                undeliverable.add(reward);
                continue;
            }
            int needed = reward.contents().stream().mapToInt(slotsNeeded).sum();
            if (needed > slotsLeft) {
                leftForLater = true;
                continue;
            }
            slotsLeft -= needed;
            toClaim.add(reward);
        }
        return new Plan(List.copyOf(toClaim), List.copyOf(undeliverable), leftForLater);
    }
}
