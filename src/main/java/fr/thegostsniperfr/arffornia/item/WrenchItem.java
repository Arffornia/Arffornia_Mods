package fr.thegostsniperfr.arffornia.item;

import fr.thegostsniperfr.arffornia.multiblock.MultiblockDefinitions;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockPartBlockEntity;
import fr.thegostsniperfr.arffornia.multiblock.Multiblocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Forms a structure when used on its trigger block, and switches a formed port's face on or off. Runs before the
 * block's own interaction, so wrenching a formed part does not open the machine.
 */
public class WrenchItem extends Item {
    public WrenchItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part && part.port().isPresent()) {
            if (context.getPlayer() instanceof ServerPlayer player) {
                part.cycleSide(context.getClickedFace()).ifPresent(mode -> player.displayClientMessage(Component.translatable(
                        "chat.arffornia.multiblock.port_side",
                        Component.translatable("gui.arffornia.machine.face." + context.getClickedFace().getSerializedName()),
                        Component.translatable(mode.translationKey())), true));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (MultiblockDefinitions.forTrigger(level, level.getBlockState(pos).getBlock()).isPresent()) {
            if (context.getPlayer() instanceof ServerPlayer player && level instanceof ServerLevel serverLevel) {
                Multiblocks.tryForm(serverLevel, pos, player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }
}
