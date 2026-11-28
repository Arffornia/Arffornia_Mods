package fr.thegostsniperfr.arffornia.multiblock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * The rule-based shapes a definition may declare in its {@code shape} object, by {@code type}. Templates are not here:
 * they come from the {@code structure} field, which names an NBT file.
 */
public final class MultiblockShapes {
    public static final ResourceLocation CUBOID = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "cuboid");

    private static final Map<ResourceLocation, MapCodec<? extends MultiblockShape>> TYPES = Map.of(CUBOID, CuboidShape.CODEC);

    public static final Codec<MultiblockShape> CODEC = ResourceLocation.CODEC
            .validate(type -> TYPES.containsKey(type) ? DataResult.success(type) : DataResult.error(() -> "unknown shape type " + type))
            .dispatch("type", MultiblockShapes::typeOf, TYPES::get);

    private MultiblockShapes() {
    }

    private static ResourceLocation typeOf(MultiblockShape shape) {
        if (shape instanceof CuboidShape) {
            return CUBOID;
        }
        throw new IllegalArgumentException("no data type for " + shape.getClass().getSimpleName());
    }
}
