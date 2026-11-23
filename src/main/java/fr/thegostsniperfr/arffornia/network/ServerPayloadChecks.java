package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.progression.ProgressionModel;
import fr.thegostsniperfr.arffornia.progression.ProgressionSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Shared server-side checks for serverbound payloads that act on a block through its menu.
 */
public final class ServerPayloadChecks {
    /** Maximum distance, in blocks, between the player and the block centre. */
    public static final double MAX_INTERACTION_DISTANCE = 8.0;

    /** Spec 017 milestone key, same as the website ({@code Keys::MILESTONE_PATTERN}). */
    private static final Pattern MILESTONE_KEY = Pattern.compile("[a-z0-9_]+(/[a-z0-9_]+)?");
    private static final int MAX_MILESTONE_KEY_LENGTH = 128;

    private static final Map<String, Long> LAST_REQUEST = new ConcurrentHashMap<>();

    private ServerPayloadChecks() {
    }

    /**
     * @return true if {@code player} has {@code menu} open for {@code blockEntity}, the block entity is the one at
     * {@code claimedPos} in the player's level, the menu is still valid and the player is within
     * {@link #MAX_INTERACTION_DISTANCE} of the block.
     */
    public static boolean isInteractingWith(ServerPlayer player, AbstractContainerMenu menu,
                                            @Nullable BlockEntity blockEntity, BlockPos claimedPos) {
        if (blockEntity == null || blockEntity.isRemoved() || player.containerMenu != menu) {
            return false;
        }
        if (blockEntity.getLevel() != player.level() || !blockEntity.getBlockPos().equals(claimedPos)) {
            return false;
        }
        if (!menu.stillValid(player)) {
            return false;
        }
        return player.distanceToSqr(Vec3.atCenterOf(claimedPos)) <= MAX_INTERACTION_DISTANCE * MAX_INTERACTION_DISTANCE;
    }

    public static boolean isMilestoneKey(@Nullable String key) {
        return key != null && key.length() <= MAX_MILESTONE_KEY_LENGTH && MILESTONE_KEY.matcher(key).matches();
    }

    /**
     * Rule 3 of spec 017, the one the Space Elevator delivery checks (docs ADR 0017): only a milestone that is not done,
     * whose stage is open and whose prerequisites are all done, can be targeted. Before the keyed state was read once
     * nothing can be targeted, since "done" is unknown.
     */
    public static boolean canTarget(ProgressionModel model, ProgressionSnapshot progression, String key) {
        return isMilestoneKey(key) && !progression.retired() && progression.isKeyedStateKnown()
                && !progression.isCompleted(key) && model.isAvailable(key, progression);
    }

    /** At most one request of this kind per player every {@code cooldownMs}; payloads that reach the website go through it. */
    public static boolean allowRequest(ServerPlayer player, String kind, long cooldownMs) {
        long now = System.currentTimeMillis();
        String slot = player.getUUID() + "|" + kind;
        Long last = LAST_REQUEST.get(slot);
        if (last != null && now - last < cooldownMs) {
            return false;
        }
        LAST_REQUEST.put(slot, now);
        return true;
    }

    public static void forget(UUID player) {
        String prefix = player + "|";
        LAST_REQUEST.keySet().removeIf(slot -> slot.startsWith(prefix));
    }
}
