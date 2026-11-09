package fr.thegostsniperfr.arffornia.multiblock;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.machine.MachineBlock;
import fr.thegostsniperfr.arffornia.network.ClientboundMultiblocksPacket;
import fr.thegostsniperfr.arffornia.progression.MachineProgressions;
import fr.thegostsniperfr.arffornia.progression.ModAttachments;
import fr.thegostsniperfr.arffornia.progression.Progressions;
import fr.thegostsniperfr.arffornia.progression.Stages;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

/**
 * Forms structures with the Wrench. A structure forms for the progression of the player who wrenches it, only while
 * that progression is known, not retired and has unlocked the definition's stage; the machine then belongs to it (#18).
 */
@EventBusSubscriber(modid = Arffornia.MODID)
public final class Multiblocks {
    public enum Result { FORMED, NO_PROGRESSION, NOT_SERVING, LOCKED, INCOMPLETE }

    private Multiblocks() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new MultiblockLoader());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ClientboundMultiblocksPacket packet = ClientboundMultiblocksPacket.of(MultiblockDefinitions.server());
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, packet));
    }

    /** Empty when the block at {@code pos} triggers no structure. */
    public static Optional<Result> tryForm(ServerLevel level, BlockPos pos, ServerPlayer player) {
        return MultiblockDefinitions.forTrigger(level, level.getBlockState(pos).getBlock())
                .map(definition -> tryForm(level, pos, player, definition));
    }

    public static Result tryForm(ServerLevel level, BlockPos pos, ServerPlayer player, MultiblockDefinition definition) {
        long progressionId = Progressions.activeProgressionOf(player);
        if (progressionId == ModAttachments.NO_PROGRESSION) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.machine.no_progression").withStyle(ChatFormatting.RED));
            return Result.NO_PROGRESSION;
        }
        if (!MachineProgressions.checkServing(player, progressionId)) {
            return Result.NOT_SERVING;
        }
        if (!Stages.isUnlocked(level.getServer(), progressionId, definition.stage())) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.multiblock.locked", definition.master().getName()).withStyle(ChatFormatting.RED));
            return Result.LOCKED;
        }
        Optional<Formation> formation = definition.shape().match(level, pos);
        if (formation.isEmpty()) {
            int missing = definition.shape().missing(level, pos, player.getDirection().getOpposite()).size();
            player.sendSystemMessage(Component.translatable("chat.arffornia.multiblock.incomplete", definition.master().getName(), missing)
                    .withStyle(ChatFormatting.YELLOW));
            return Result.INCOMPLETE;
        }
        form(level, definition, formation.get(), progressionId);
        player.sendSystemMessage(Component.translatable("chat.arffornia.multiblock.formed", definition.master().getName()).withStyle(ChatFormatting.GREEN));
        return Result.FORMED;
    }

    /** Only a player on the progression a structure belongs to may change its ports. */
    public static boolean mayConfigure(ServerPlayer player, MultiblockPartBlockEntity part) {
        MultiblockMachineBlockEntity machine = part.machine();
        if (machine == null) {
            return false;
        }
        if (Progressions.activeProgressionOf(player) != machine.linkedProgressionId()) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.multiblock.not_yours").withStyle(ChatFormatting.RED));
            return false;
        }
        return MachineProgressions.checkServing(player, machine.linkedProgressionId());
    }

    private static void form(ServerLevel level, MultiblockDefinition definition, Formation formation, long progressionId) {
        BlockState trigger = level.getBlockState(formation.trigger());
        for (BlockPos pos : formation.positions()) {
            if (pos.equals(formation.trigger())) {
                continue;
            }
            BlockState original = level.getBlockState(pos);
            level.setBlock(pos, ModBlocks.MULTIBLOCK_PART.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part) {
                part.formed(original, formation.trigger());
            }
        }
        BlockState master = definition.master().defaultBlockState().setValue(MachineBlock.FACING, formation.facing());
        level.setBlock(formation.trigger(), master, Block.UPDATE_ALL);
        if (level.getBlockEntity(formation.trigger()) instanceof MultiblockMachineBlockEntity machine) {
            machine.formed(trigger, formation, progressionId);
            level.sendBlockUpdated(formation.trigger(), master, master, Block.UPDATE_CLIENTS);
        }
        // Neighbours may have cached "no capability" while the parts were being placed, before the master existed.
        formation.positions().forEach(level::invalidateCapabilities);
    }

    /**
     * What a formed block drops: the block it replaced, under that block's own tool rule, so a pickaxe-only casing
     * does not drop to a bare hand once formed.
     */
    static List<ItemStack> dropsOf(BlockState original, LootParams.Builder params) {
        if (original.isAir()) {
            return List.of();
        }
        ItemStack tool = params.getOptionalParameter(LootContextParams.TOOL);
        if (original.requiresCorrectToolForDrops() && (tool == null || !tool.isCorrectToolForDrops(original))) {
            return List.of();
        }
        return original.getDrops(params);
    }

    /** How fast a formed block breaks: like the block it replaced, or like itself before the client knows that block. */
    static float destroyProgress(BlockState original, Player player, BlockGetter level, BlockPos pos, float fallback) {
        return original.isAir() ? fallback : original.getDestroyProgress(player, level, pos);
    }
}
