package fr.thegostsniperfr.arffornia.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A block a structure expects at a position. */
public record BlockPlacement(BlockPos pos, BlockState state) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BlockPlacement> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BlockPlacement::pos,
            ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), BlockPlacement::state,
            BlockPlacement::new);
}
