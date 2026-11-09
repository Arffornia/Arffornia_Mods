package fr.thegostsniperfr.arffornia.block.spaceelevator;

import com.google.gson.Gson;
import com.mojang.serialization.MapCodec;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.backend.ProgressionBackends;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.block.entity.SpaceElevatorBlockEntity;
import fr.thegostsniperfr.arffornia.progression.MachineProgressions;
import fr.thegostsniperfr.arffornia.progression.ProgressionSnapshot;
import fr.thegostsniperfr.arffornia.progression.Progressions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
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

import java.util.stream.Stream;

public class SpaceElevator extends BaseEntityBlock implements SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final MapCodec<SpaceElevator> CODEC = simpleCodec(SpaceElevator::new);
    private static final Gson GSON = new Gson();
    private static final VoxelShape SHAPE = Stream.of(
            Block.box(0, 1, 0, 16, 16, 16),
            Block.box(0, 0, 0, 2, 1, 2),
            Block.box(0, 0, 14, 2, 1, 16),
            Block.box(14, 0, 14, 16, 1, 16),
            Block.box(14, 0, 0, 16, 1, 2)
    ).reduce(Shapes::or).get();

    public SpaceElevator() {
        super(Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion()
        );
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, false));
    }

    public SpaceElevator(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, false));
    }

    // --- Waterlogging ---
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (!MachineProgressions.canPlace(ctx)) {
            return null;
        }
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
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new SpaceElevatorBlockEntity(pPos, pState);
    }

    @Override
    public boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
        BlockPos partPos = pPos.above();
        BlockState stateAbove = pLevel.getBlockState(partPos);
        boolean spaceIsFree = pLevel.isEmptyBlock(partPos) || stateAbove.canBeReplaced();
        return spaceIsFree && super.canSurvive(pState, pLevel, pPos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, BlockHitResult pHit) {
        if (!pLevel.isClientSide()) {
            if (pLevel.getBlockEntity(pPos) instanceof SpaceElevatorBlockEntity be && pPlayer instanceof ServerPlayer serverPlayer) {
                if (be.isLaunching()) {
                    Arffornia.LOGGER.warn("Player {} tried to access Space Elevator at {} while it was launching.", pPlayer.getName().getString(), pPos);
                    return InteractionResult.FAIL;
                }

                long progressionId = be.getLinkedProgressionId();
                if (!MachineProgressions.checkServing(serverPlayer, progressionId)) {
                    return InteractionResult.FAIL;
                }

                ProgressionSnapshot progression = Progressions.find(serverPlayer.server, progressionId).orElseThrow();
                Integer targetId = progression.currentMilestoneId();
                if (targetId == null) {
                    be.setCachedMilestoneDetails(null);
                    openMenu(serverPlayer, be, null, false);
                    return InteractionResult.sidedSuccess(false);
                }

                ProgressionBackends.get().fetchMilestoneDetails(targetId, serverPlayer.clientInformation().language())
                        .thenAcceptAsync(details -> {
                            if (pLevel.getBlockEntity(pPos) != be) {
                                return;
                            }
                            be.setCachedMilestoneDetails(details);
                            boolean isCompleted = details != null && Progressions.find(serverPlayer.server, progressionId)
                                    .map(current -> current.isCompleted(details.id())).orElse(false);
                            openMenu(serverPlayer, be, details, isCompleted);
                        }, pLevel.getServer());
            }
        }
        return InteractionResult.sidedSuccess(pLevel.isClientSide());
    }

    private void openMenu(ServerPlayer player, SpaceElevatorBlockEntity be, @Nullable fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos.MilestoneDetails details, boolean isCompleted) {
        player.getServer().execute(() -> {
            player.openMenu(be, buf -> {
                buf.writeBlockPos(be.getBlockPos());
                String json = details != null ? GSON.toJson(details) : "";
                buf.writeUtf(json);
                buf.writeBoolean(isCompleted);
            });
        });
    }

    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
        super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
        if (!pLevel.isClientSide) {
            BlockPos partPos = pPos.above();
            pLevel.setBlock(partPos, ModBlocks.SPACE_ELEVATOR_PART_BLOCK.get().defaultBlockState(), 3);

            if (pPlacer instanceof ServerPlayer player && pLevel.getBlockEntity(pPos) instanceof SpaceElevatorBlockEntity be) {
                be.linkToProgression(Progressions.activeProgressionOf(player));
            }
        }
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (!pState.is(pNewState.getBlock())) {
            if (pLevel.getBlockEntity(pPos) instanceof SpaceElevatorBlockEntity be && !be.isLaunching()) {
                NonNullList<ItemStack> items = NonNullList.create();
                for (int i = 0; i < be.itemHandler.getSlots(); i++) {
                    items.add(be.itemHandler.getStackInSlot(i));
                }

                Containers.dropContents(pLevel, pPos, items);
                pLevel.updateNeighbourForOutputSignal(pPos, this);
            }

            BlockPos partPos = pPos.above();
            if (pLevel.getBlockState(partPos).is(ModBlocks.SPACE_ELEVATOR_PART_BLOCK.get())) {
                pLevel.setBlock(partPos, pNewState.getFluidState().createLegacyBlock(), 35, 0);
            }
        }

        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
    }
}