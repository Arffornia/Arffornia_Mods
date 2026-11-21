package fr.thegostsniperfr.arffornia.shop;

import java.util.List;

/** A reward waiting on the website for a player, with its parsed entries. */
public record PendingReward(long id, String source, List<RewardEntry> contents) {
    public boolean isFullySupported() {
        return !contents.isEmpty() && contents.stream().noneMatch(e -> e instanceof RewardEntry.Unsupported);
    }
}
