package fr.thegostsniperfr.arffornia.crate;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** The opening sequence timeline (spec 020, "Opening sequence"), in ticks, and what every viewer is shown. */
public final class CrateSequence {
    public static final int KEY_IN = 0;
    public static final int CLICK = 8;
    public static final int CHARGE = 10;
    public static final int ROULETTE = 30;
    public static final int REVEAL = 90;
    public static final int FLY = 110;
    public static final int END = 120;
    /** The client keeps drawing the lid closing for a moment after the server is done. */
    public static final int CLIENT_END = END + 12;

    private CrateSequence() {
    }

    /**
     * @param roulette the items the roulette cycles through, the last one being the result
     * @param rarity   the roll's rarity; {@code crateRarity} is the crate's own (beam colour while charging)
     */
    public record Show(int openerId, ItemStack key, List<ItemStack> roulette, List<ItemStack> result, CrateRarity crateRarity, CrateRarity rarity) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Show> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Show::openerId,
                ItemStack.OPTIONAL_STREAM_CODEC, Show::key,
                ItemStack.OPTIONAL_LIST_STREAM_CODEC, Show::roulette,
                ItemStack.OPTIONAL_LIST_STREAM_CODEC, Show::result,
                CrateRarity.STREAM_CODEC, Show::crateRarity,
                CrateRarity.STREAM_CODEC, Show::rarity,
                Show::new);

        public ItemStack shown() {
            return result.isEmpty() ? ItemStack.EMPTY : result.getFirst();
        }
    }

    /** Fast, then slowing down like a slot machine: 0 at the start of the roulette, 1 when it stops. */
    public static double rouletteProgress(float tick) {
        double x = Math.clamp((tick - ROULETTE) / (double) (REVEAL - ROULETTE), 0.0, 1.0);
        return 1 - Math.pow(1 - x, 3);
    }

    /** Position on the reel: an integer {@code i} means item {@code i} is in front. */
    public static double reelPosition(Show show, float tick) {
        return (show.roulette().size() - 1) * rouletteProgress(tick);
    }
}
