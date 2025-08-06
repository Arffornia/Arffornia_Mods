package fr.thegostsniperfr.arffornia.block.spaceelevator;

import fr.thegostsniperfr.arffornia.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.stream.Stream;

public class SpaceElevatorPartBlock extends Block {

    private static final VoxelShape SHAPE_PART = Stream.of(
            Block.box(2, 0, 7, 4, 2, 9),      // y: 16-16=0, 18-16=2
            Block.box(12, 0, 7, 14, 2, 9),     // y: 16-16=0, 18-16=2
            Block.box(1, 2, 7, 15, 4, 9),      // y: 18-16=2, 20-16=4
            Block.box(0, 2, 2, 16, 4, 7),      // y: 18-16=2, 20-16=4
            Block.box(0, 2, 9, 16, 4, 14)       // y: 18-16=2, 20-16=4
    ).reduce(Shapes::or).get();


    public SpaceElevatorPartBlock() {
        super(Properties.of()
                .strength(3.5F, 6.0F)
                .noOcclusion()
                .requiresCorrectToolForDrops());
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE_PART;
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (!pLevel.isClientSide() && !pState.is(pNewState.getBlock())) {
            BlockPos masterPos = pPos.below();
            BlockState masterState = pLevel.getBlockState(masterPos);

            if (masterState.is(ModBlocks.SPACE_ELEVATOR.get())) {
                pLevel.destroyBlock(masterPos, true);
            }
        }
        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack pStack, BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (pLevel.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }

        BlockPos masterPos = pPos.below();
        BlockState masterState = pLevel.getBlockState(masterPos);

        return masterState.useItemOn(pStack, pLevel, pPlayer, pHand, pHit.withPosition(masterPos));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, BlockHitResult pHit) {
        if (pLevel.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockPos masterPos = pPos.below();
        BlockState masterState = pLevel.getBlockState(masterPos);
        return masterState.useWithoutItem(pLevel, pPlayer, pHit.withPosition(masterPos));
    }

    @Override
    public void attack(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer) {
        if (!pLevel.isClientSide) {
            BlockPos masterPos = pPos.below();
            BlockState masterState = pLevel.getBlockState(masterPos);
            masterState.attack(pLevel, masterPos, pPlayer);
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader pLevel, BlockPos pPos, BlockState pState) {
        return new ItemStack(ModBlocks.SPACE_ELEVATOR.get());
    }

    @Override
    public String getDescriptionId() {
        return ModBlocks.SPACE_ELEVATOR.get().getDescriptionId();
    }
}