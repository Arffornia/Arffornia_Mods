package fr.thegostsniperfr.arffornia.multiblock;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A block of a formed structure other than its master. It is drawn by the master's renderer, drops the block it
 * replaced and takes the whole structure apart when it goes.
 */
public class MultiblockPartBlock extends BaseEntityBlock {
    public static final MapCodec<MultiblockPartBlock> CODEC = simpleCodec(MultiblockPartBlock::new);

    public MultiblockPartBlock(Properties properties) {
        super(properties);
    }

    public MultiblockPartBlock() {
        this(Properties.of().mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL)
                .noOcclusion().pushReaction(PushReaction.BLOCK));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockPartBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part) {
            BlockPos master = part.master();
            return level.getBlockState(master).useWithoutItem(level, player, hit.withPosition(master));
        }
        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part) {
            MultiblockMachineBlockEntity machine = part.machine();
            if (machine != null) {
                machine.disassemble(pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MultiblockPartBlockEntity part) {
            return part.original().getDrops(params);
        }
        return List.of();
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part
                ? part.original().getCloneItemStack(target, level, pos, player) : ItemStack.EMPTY;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part
                ? part.original().getDestroyProgress(player, level, pos) : super.getDestroyProgress(state, player, level, pos);
    }
}
