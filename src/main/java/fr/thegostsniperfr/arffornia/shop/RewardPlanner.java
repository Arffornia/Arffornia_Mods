package fr.thegostsniperfr.arffornia.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Chooses which pending rewards to claim now: a reward is claimed only when all of it is deliverable and fits in the
 * inventory (spec 020, "Inventory room"). A reward that cannot fit even an empty inventory stays pending for good.
 */
public final class RewardPlanner {

    /** The inventory the plan fills, reward by reward. */
    public interface Room {
        /** @return true when the reward could never fit, not even in an empty inventory */
        boolean exceedsEmptyInventory(PendingReward reward);

        /** Takes the room the reward needs and returns 0, or returns the slots missing and takes nothing. */
        int reserve(PendingReward reward);
    }

    /**
     * @param slotsToFree the fewest slots to free for the next reward left waiting to fit, 0 when none is left waiting
     */
    public record Plan(List<PendingReward> toClaim, List<PendingReward> undeliverable, List<PendingReward> tooLarge, int slotsToFree) {
        public boolean leftForLater() {
            return slotsToFree > 0;
        }
    }

    private RewardPlanner() {
    }

    public static Plan plan(List<PendingReward> rewards, Predicate<RewardEntry> deliverable, Room room) {
        List<PendingReward> toClaim = new ArrayList<>();
        List<PendingReward> undeliverable = new ArrayList<>();
        List<PendingReward> tooLarge = new ArrayList<>();
        int slotsToFree = 0;

        for (PendingReward reward : rewards) {
            if (!reward.isFullySupported() || !reward.contents().stream().allMatch(deliverable)) {
                undeliverable.add(reward);
                continue;
            }
            if (room.exceedsEmptyInventory(reward)) {
                tooLarge.add(reward);
                continue;
            }
            int missing = room.reserve(reward);
            if (missing > 0) {
                slotsToFree = slotsToFree == 0 ? missing : Math.min(slotsToFree, missing);
                continue;
            }
            toClaim.add(reward);
        }
        return new Plan(List.copyOf(toClaim), List.copyOf(undeliverable), List.copyOf(tooLarge), slotsToFree);
    }
}
