package fr.thegostsniperfr.arffornia.block.crafterblock;

import fr.thegostsniperfr.arffornia.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.stream.Stream;

public class CrafterBlock extends Block {

    private static final VoxelShape SHAPE = Stream.of(
            Block.box(0, 1, 0, 16, 16, 16),
            Block.box(0, 0, 0, 2, 1, 2),
            Block.box(0, 0, 14, 2, 1, 16),
            Block.box(14, 0, 14, 16, 1, 16),
            Block.box(14, 0, 0, 16, 1, 2)
    ).reduce(Shapes::or).get();

    public CrafterBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion()
        );
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
        super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
        if (!pLevel.isClientSide) {
            BlockPos partPos = pPos.above();
            pLevel.setBlock(partPos, ModBlocks.CRAFTER_PART_BLOCK.get().defaultBlockState(), 3);
        }
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (!pState.is(pNewState.getBlock())) {
            BlockPos partPos = pPos.above();
            if (pLevel.getBlockState(partPos).is(ModBlocks.CRAFTER_PART_BLOCK.get())) {
                pLevel.setBlock(partPos, pNewState.getFluidState().createLegacyBlock(), 35, 0);
            }
        }
        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
    }
}