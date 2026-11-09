package fr.thegostsniperfr.arffornia.multiblock;

import fr.thegostsniperfr.arffornia.machine.MachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/**
 * The block a structure's trigger becomes once formed. It holds the {@link MultiblockMachineBlockEntity}, is drawn by
 * its renderer and drops the trigger block it replaced.
 */
public abstract class MultiblockMasterBlock extends MachineBlock {
    protected MultiblockMasterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MultiblockMachineBlockEntity machine) {
            machine.disassemble(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MultiblockMachineBlockEntity machine) {
            return machine.original().getDrops(params);
        }
        return List.of();
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return level.getBlockEntity(pos) instanceof MultiblockMachineBlockEntity machine
                ? machine.original().getCloneItemStack(target, level, pos, player) : ItemStack.EMPTY;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MultiblockMachineBlockEntity machine
                ? machine.original().getDestroyProgress(player, level, pos) : super.getDestroyProgress(state, player, level, pos);
    }
}
