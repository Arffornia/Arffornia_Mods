package fr.thegostsniperfr.arffornia.shop;

import java.util.List;
import java.util.Map;

/**
 * A reward waiting on the website for a player, with its parsed entries. {@code announce} marks a shop purchase to
 * tell the whole server about, and {@code label} is the item name by language code ("en", "fr").
 */
public record PendingReward(long id, String source, List<RewardEntry> contents, boolean announce, Map<String, String> label) {
    public PendingReward(long id, String source, List<RewardEntry> contents) {
        this(id, source, contents, false, Map.of());
    }

    public boolean isFullySupported() {
        return !contents.isEmpty() && contents.stream().noneMatch(e -> e instanceof RewardEntry.Unsupported);
    }
}
