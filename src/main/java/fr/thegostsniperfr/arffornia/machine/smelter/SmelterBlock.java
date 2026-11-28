package fr.thegostsniperfr.arffornia.machine.smelter;

import com.mojang.serialization.MapCodec;
import fr.thegostsniperfr.arffornia.block.entity.ModBlockEntities;
import fr.thegostsniperfr.arffornia.machine.MachineTypes;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockMachineBlockEntity;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockMasterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The formed Smelter: a 3x3x3 beehive kiln (Arffornia_Assets {@code smelter_beehive_s1}) that runs {@code arffornia:machine} recipes whose machine is {@code arffornia:smelter}. */
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

    /** Smoke from the chimney and flames at the firebox while it smelts; positions from the S1 model's {@code meta.fx}. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof MultiblockMachineBlockEntity machine) || !machine.working()) {
            return;
        }
        AABB bounds = machine.bounds();
        Vec3 centre = bounds.getCenter();
        Direction front = state.getValue(FACING);
        Vec3 chimney = new Vec3(centre.x - front.getStepX() * 13 / 16.0, bounds.minY + 46.5 / 16.0, centre.z - front.getStepZ() * 13 / 16.0);
        level.addParticle(ParticleTypes.LARGE_SMOKE, chimney.x + random.nextGaussian() * 0.08, chimney.y, chimney.z + random.nextGaussian() * 0.08,
                0.0, 0.07, 0.0);
        if (random.nextInt(4) == 0) {
            Vec3 firebox = new Vec3(centre.x + front.getStepX() * 19.5 / 16.0, bounds.minY + 0.5, centre.z + front.getStepZ() * 19.5 / 16.0);
            level.addParticle(ParticleTypes.SMALL_FLAME, firebox.x + (random.nextDouble() - 0.5) * 0.4, firebox.y, firebox.z + (random.nextDouble() - 0.5) * 0.4,
                    0.0, 0.01, 0.0);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockMachineBlockEntity(ModBlockEntities.SMELTER_BE.get(), MachineTypes.SMELTER, pos, state);
    }
}
