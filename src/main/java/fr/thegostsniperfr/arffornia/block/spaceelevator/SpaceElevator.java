package fr.thegostsniperfr.arffornia.block.spaceelevator;

import com.google.gson.Gson;
import com.mojang.serialization.MapCodec;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.block.entity.SpaceElevatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class SpaceElevator extends BaseEntityBlock {

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
    }

    public SpaceElevator(Properties properties) {
        super(properties);
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
    protected InteractionResult useWithoutItem(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, BlockHitResult pHit) {
        if (!pLevel.isClientSide()) {
            if (pLevel.getBlockEntity(pPos) instanceof SpaceElevatorBlockEntity be && pPlayer instanceof ServerPlayer serverPlayer) {
                if (be.isLaunching()) {
                    Arffornia.LOGGER.warn("Player {} tried to access Space Elevator at {} while it was launching.", pPlayer.getName().getString(), pPos);
                    return InteractionResult.FAIL;
                }

                long progressionId = be.getLinkedProgressionId();
                if (progressionId == -1) {
                    Arffornia.LOGGER.error("Space Elevator at {} has no linked progression ID.", pPos);
                    return InteractionResult.FAIL;
                }

                Arffornia.ARFFORNA_API_SERVICE.fetchProgressionData(progressionId)
                        .thenCompose(progressionData -> {
                            if (progressionData == null) {
                                Arffornia.LOGGER.warn("Failed to fetch progression data for ID {}", progressionId);
                                be.setCachedMilestoneDetails(null);
                                return CompletableFuture.completedFuture(null);
                            }

                            if (progressionData.currentMilestoneId() == null) {
                                be.setCachedMilestoneDetails(null);
                                return CompletableFuture.completedFuture(new Object[]{progressionData, null});
                            }

                            return Arffornia.ARFFORNA_API_SERVICE.fetchMilestoneDetails(progressionData.currentMilestoneId())
                                    .thenApply(details -> new Object[]{progressionData, details});
                        })
                        .thenAcceptAsync(data -> {
                            if (data == null) {
                                openMenu(serverPlayer, be, null, false);
                                return;
                            }

                            var progressionData = (fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos.ProgressionData) data[0];
                            var details = (fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos.MilestoneDetails) data[1];

                            be.setCachedMilestoneDetails(details);
                            boolean isCompleted = details != null && progressionData.completedMilestones().contains(details.id());
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

            if (pPlacer instanceof Player player) {
                BlockEntity entity = pLevel.getBlockEntity(pPos);
                if (entity instanceof SpaceElevatorBlockEntity be) {
                    be.setOwner(player);
                }
            }
        }
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (!pState.is(pNewState.getBlock())) {
            BlockEntity blockEntity = pLevel.getBlockEntity(pPos);
            if (blockEntity instanceof SpaceElevatorBlockEntity be && !be.isLaunching()) {
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