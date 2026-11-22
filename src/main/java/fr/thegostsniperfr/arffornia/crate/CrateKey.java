package fr.thegostsniperfr.arffornia.crate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** The data a key carries: the crate it opens and the player it is bound to (spec 020 D1). */
public record CrateKey(ResourceLocation crate, UUID owner, String ownerName) {
    public static final Codec<CrateKey> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("crate").forGetter(CrateKey::crate),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(CrateKey::owner),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(CrateKey::ownerName)
    ).apply(instance, CrateKey::new));

    public static final StreamCodec<ByteBuf, CrateKey> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, CrateKey::crate,
            UUIDUtil.STREAM_CODEC, CrateKey::owner,
            ByteBufCodecs.STRING_UTF8, CrateKey::ownerName,
            CrateKey::new);

    public boolean isOwnedBy(UUID player) {
        return owner.equals(player);
    }
}
