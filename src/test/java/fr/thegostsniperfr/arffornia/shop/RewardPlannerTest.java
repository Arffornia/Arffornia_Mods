package fr.thegostsniperfr.arffornia.shop;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardPlannerTest {
    private static final Predicate<RewardEntry> ALL_DELIVERABLE = e -> true;

    /** One slot per item entry, 36 slots in all. */
    private static final class CountingRoom implements RewardPlanner.Room {
        int free;

        CountingRoom(int free) {
            this.free = free;
        }

        private static int slots(PendingReward reward) {
            return (int) reward.contents().stream().filter(e -> e instanceof RewardEntry.Item || e instanceof RewardEntry.Key).count();
        }

        @Override
        public boolean exceedsEmptyInventory(PendingReward reward) {
            return slots(reward) > InventoryRoom.MAIN_SLOTS;
        }

        @Override
        public int reserve(PendingReward reward) {
            int needed = slots(reward);
            if (needed > free) {
                return needed - free;
            }
            free -= needed;
            return 0;
        }
    }

    private static PendingReward items(long id, int stacks) {
        return new PendingReward(id, "shop", Collections.nCopies(stacks, new RewardEntry.Item("minecraft:diamond", 1)));
    }

    @Test
    void claimsEverythingThatFits() {
        RewardPlanner.Plan plan = RewardPlanner.plan(List.of(items(1, 2), items(2, 3)), ALL_DELIVERABLE, new CountingRoom(5));

        assertEquals(List.of(1L, 2L), plan.toClaim().stream().map(PendingReward::id).toList());
        assertFalse(plan.leftForLater());
    }

    @Test
    void leavesForLaterWhatDoesNotFitAndSaysHowManySlotsToFree() {
        PendingReward permission = new PendingReward(3, "shop", List.of(new RewardEntry.Permission("cosmetic.hat", true)));

        RewardPlanner.Plan plan = RewardPlanner.plan(List.of(items(1, 2), items(2, 3), permission), ALL_DELIVERABLE, new CountingRoom(4));

        assertEquals(List.of(1L, 3L), plan.toClaim().stream().map(PendingReward::id).toList());
        assertEquals(1, plan.slotsToFree());
    }

    @Test
    void neverClaimsARewardWithAnUnsupportedOrUndeliverableEntry() {
        PendingReward unknownType = new PendingReward(1, "shop", List.of(new RewardEntry.Item("minecraft:diamond", 1), new RewardEntry.Unsupported("pet", "unknown type")));
        PendingReward unknownItem = new PendingReward(2, "shop", List.of(new RewardEntry.Item("othermod:gone", 1)));
        PendingReward empty = new PendingReward(3, "shop", List.of());
        Predicate<RewardEntry> noOtherMod = e -> !(e instanceof RewardEntry.Item item && item.item().startsWith("othermod:"));

        RewardPlanner.Plan plan = RewardPlanner.plan(List.of(unknownType, unknownItem, empty), noOtherMod, new CountingRoom(36));

        assertTrue(plan.toClaim().isEmpty());
        assertEquals(List.of(1L, 2L, 3L), plan.undeliverable().stream().map(PendingReward::id).toList());
    }

    @Test
    void aRewardBiggerThanTheInventoryStaysPendingEvenWhenTheInventoryIsEmpty() {
        RewardPlanner.Plan plan = RewardPlanner.plan(List.of(items(1, 100), items(2, 1)), ALL_DELIVERABLE, new CountingRoom(36));

        assertEquals(List.of(2L), plan.toClaim().stream().map(PendingReward::id).toList());
        assertEquals(List.of(1L), plan.tooLarge().stream().map(PendingReward::id).toList());
        assertFalse(plan.leftForLater());
    }
}
