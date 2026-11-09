package fr.thegostsniperfr.arffornia.shop;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardPlannerTest {
    private static final Predicate<RewardEntry> ALL_DELIVERABLE = e -> true;
    private static final ToIntFunction<RewardEntry> ONE_SLOT_PER_ITEM = e -> e instanceof RewardEntry.Item ? 1 : 0;

    private static PendingReward items(long id, int stacks) {
        return new PendingReward(id, "shop", java.util.Collections.nCopies(stacks, new RewardEntry.Item("minecraft:diamond", 1)));
    }

    @Test
    void claimsEverythingThatFits() {
        RewardPlanner.Plan plan = RewardPlanner.plan(List.of(items(1, 2), items(2, 3)), 5, ALL_DELIVERABLE, ONE_SLOT_PER_ITEM);

        assertEquals(List.of(1L, 2L), plan.toClaim().stream().map(PendingReward::id).toList());
        assertFalse(plan.leftForLater());
    }

    @Test
    void leavesForLaterWhatDoesNotFitButKeepsRewardsThatNeedNoSlot() {
        PendingReward permission = new PendingReward(3, "shop", List.of(new RewardEntry.Permission("cosmetic.hat", true)));

        RewardPlanner.Plan plan = RewardPlanner.plan(List.of(items(1, 2), items(2, 3), permission), 4, ALL_DELIVERABLE, ONE_SLOT_PER_ITEM);

        assertEquals(List.of(1L, 3L), plan.toClaim().stream().map(PendingReward::id).toList());
        assertTrue(plan.leftForLater());
    }

    @Test
    void neverClaimsARewardWithAnUnsupportedOrUndeliverableEntry() {
        PendingReward unknownType = new PendingReward(1, "shop", List.of(new RewardEntry.Item("minecraft:diamond", 1), new RewardEntry.Unsupported("pet", "unknown type")));
        PendingReward unknownItem = new PendingReward(2, "shop", List.of(new RewardEntry.Item("othermod:gone", 1)));
        PendingReward empty = new PendingReward(3, "shop", List.of());
        Predicate<RewardEntry> noOtherMod = e -> !(e instanceof RewardEntry.Item item && item.item().startsWith("othermod:"));

        RewardPlanner.Plan plan = RewardPlanner.plan(List.of(unknownType, unknownItem, empty), 36, noOtherMod, ONE_SLOT_PER_ITEM);

        assertTrue(plan.toClaim().isEmpty());
        assertEquals(List.of(1L, 2L, 3L), plan.undeliverable().stream().map(PendingReward::id).toList());
    }

    @Test
    void aRewardBiggerThanTheInventoryIsClaimedWhenTheInventoryIsEmpty() {
        RewardPlanner.Plan plan = RewardPlanner.plan(List.of(items(1, 100)), RewardPlanner.INVENTORY_SLOTS, ALL_DELIVERABLE, ONE_SLOT_PER_ITEM);

        assertEquals(List.of(1L), plan.toClaim().stream().map(PendingReward::id).toList());
    }
}
