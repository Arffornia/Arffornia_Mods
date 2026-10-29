package fr.thegostsniperfr.arffornia.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared server-side checks for serverbound payloads that act on a block through its menu.
 */
public final class ServerPayloadChecks {
    /** Maximum distance, in blocks, between the player and the block centre. */
    public static final double MAX_INTERACTION_DISTANCE = 8.0;

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
}
