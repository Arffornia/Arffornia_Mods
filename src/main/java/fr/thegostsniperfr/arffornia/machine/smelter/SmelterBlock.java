package fr.thegostsniperfr.arffornia.machine.smelter;

import com.mojang.serialization.MapCodec;
import fr.thegostsniperfr.arffornia.block.entity.ModBlockEntities;
import fr.thegostsniperfr.arffornia.machine.MachineTypes;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockMachineBlockEntity;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockMasterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** The formed Smelter: a 3x3x3 kiln that runs {@code arffornia:machine} recipes whose machine is {@code arffornia:smelter}. */
public class SmelterBlock extends MultiblockMasterBlock {
    public static final MapCodec<SmelterBlock> CODEC = simpleCodec(SmelterBlock::new);

    public SmelterBlock(Properties properties) {
        super(properties);
    }

    public SmelterBlock() {
        this(Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3.5F, 6.0F).sound(SoundType.STONE)
                .noOcclusion().pushReaction(PushReaction.BLOCK).lightLevel(state -> 7));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockMachineBlockEntity(ModBlockEntities.SMELTER_BE.get(), MachineTypes.SMELTER, pos, state);
    }
}
