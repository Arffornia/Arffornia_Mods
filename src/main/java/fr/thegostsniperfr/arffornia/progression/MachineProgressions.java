package fr.thegostsniperfr.arffornia.progression;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.BlockPlaceContext;

import java.util.Optional;

/**
 * Rules shared by machines that belong to a progression (Crafter, Space Elevator, future machines).
 */
public final class MachineProgressions {
    private MachineProgressions() {
    }

    /**
     * A machine may only be placed by a player whose active progression is known, so it is never left unlinked.
     * The client cannot tell and always predicts success; the server corrects it.
     */
    public static boolean canPlace(BlockPlaceContext context) {
        if (context.getLevel().isClientSide()) {
            return true;
        }
        if (context.getPlayer() instanceof ServerPlayer player
                && Progressions.activeProgressionOf(player) != ModAttachments.NO_PROGRESSION) {
            return true;
        }
        if (context.getPlayer() != null) {
            context.getPlayer().sendSystemMessage(Component.translatable("chat.arffornia.machine.no_progression").withStyle(ChatFormatting.RED));
        }
        return false;
    }

    /**
     * True when the machine may run for {@code progressionId}; otherwise tells {@code player} why not.
     */
    public static boolean checkServing(ServerPlayer player, long progressionId) {
        if (progressionId == ModAttachments.NO_PROGRESSION) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.machine.not_linked").withStyle(ChatFormatting.RED));
            return false;
        }
        Optional<ProgressionSnapshot> snapshot = Progressions.find(player.server, progressionId);
        if (snapshot.isEmpty()) {
            Progressions.ensureKnown(player.server, progressionId);
            player.sendSystemMessage(Component.translatable("chat.arffornia.machine.loading").withStyle(ChatFormatting.YELLOW));
            return false;
        }
        if (snapshot.get().retired()) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.machine.retired").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }
}
