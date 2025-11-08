package fr.thegostsniperfr.arffornia.block.crafterblock;

import com.google.gson.Gson;
import com.mojang.serialization.MapCodec;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.block.entity.CrafterBlockEntity;
import fr.thegostsniperfr.arffornia.block.entity.ModBlockEntities;
import fr.thegostsniperfr.arffornia.recipe.CustomRecipeManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class CrafterBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final MapCodec<CrafterBlock> CODEC = simpleCodec(CrafterBlock::new);
    private static final Gson GSON = new Gson();
    private static final VoxelShape SHAPE = Stream.of(
            Block.box(0, 1, 0, 16, 16, 16),
            Block.box(0, 0, 0, 2, 1, 2),
            Block.box(0, 0, 14, 2, 1, 16),
            Block.box(14, 0, 14, 16, 1, 16),
            Block.box(14, 0, 0, 16, 1, 2)
    ).reduce(Shapes::or).get();

    public CrafterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, false));
    }

    public CrafterBlock() {
        this(Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion()
        );
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        FluidState fluidState = ctx.getLevel().getFluidState(ctx.getClickedPos());
        return this.defaultBlockState().setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return false;
    }

    @Override
    public boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
        BlockPos partPos = pPos.above();
        BlockState stateAbove = pLevel.getBlockState(partPos);
        return pLevel.isEmptyBlock(partPos) || stateAbove.canBeReplaced();
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, BlockHitResult pHit) {
        if (!pLevel.isClientSide()) {
            if (pLevel.getBlockEntity(pPos) instanceof CrafterBlockEntity be && pPlayer instanceof ServerPlayer serverPlayer) {
                long progressionId = be.getLinkedProgressionId();

                if (progressionId == -1) {
                    serverPlayer.sendSystemMessage(Component.literal("Crafter is not linked to any progression.").withStyle(ChatFormatting.RED));
                    return InteractionResult.FAIL;
                }

                ArfforniaApiService.getInstance().fetchProgressionData(progressionId).thenAcceptAsync(progressionData -> {
                    // Check whether the block has been destroyed during the API fetch
                    if (!(pLevel.getBlockEntity(pPos) instanceof CrafterBlockEntity)) {
                        return;
                    }

                    Set<Integer> unlockedMilestoneIds;
                    if (progressionData != null && progressionData.completedMilestones() != null) {
                        unlockedMilestoneIds = new HashSet<>(progressionData.completedMilestones());
                    } else {
                        Arffornia.LOGGER.warn("Could not fetch progression data for ID {}. No recipes will be shown.", progressionId);
                        unlockedMilestoneIds = Collections.emptySet();
                    }

                    Arffornia.LOGGER.info("Server: Opening crafter for progression ID {}. Sending unlocked milestone IDs: {}",
                            progressionId, unlockedMilestoneIds);

                    serverPlayer.openMenu(be, buf -> {
                        buf.writeBlockPos(pPos);
                        buf.writeVarIntArray(unlockedMilestoneIds.stream().mapToInt(Integer::intValue).toArray());
                    });
                }, pLevel.getServer());
            }
        }
        return InteractionResult.sidedSuccess(pLevel.isClientSide());
    }

    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
        super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
        if (!pLevel.isClientSide) {
            pLevel.setBlock(pPos.above(), ModBlocks.CRAFTER_PART_BLOCK.get().defaultBlockState(), 3);
            if (pPlacer instanceof Player player && pLevel.getBlockEntity(pPos) instanceof CrafterBlockEntity be) {
                be.setOwner(player);
            }
        }
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (!pState.is(pNewState.getBlock())) {
            if (pLevel.getBlockEntity(pPos) instanceof CrafterBlockEntity be) {
                Containers.dropContents(pLevel, pPos, be.getInventoryForDrop());
                pLevel.updateNeighbourForOutputSignal(pPos, this);
            }

            BlockPos partPos = pPos.above();
            if (pLevel.getBlockState(partPos).is(ModBlocks.CRAFTER_PART_BLOCK.get())) {
                pLevel.setBlock(partPos, pNewState.getFluidState().createLegacyBlock(), 35, 0);
            }
        }

        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new CrafterBlockEntity(pPos, pState);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        if (pLevel.isClientSide()) {
            return null;
        }
        return createTickerHelper(pBlockEntityType, ModBlockEntities.CRAFTER_BE.get(), CrafterBlockEntity::tick);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }
}
