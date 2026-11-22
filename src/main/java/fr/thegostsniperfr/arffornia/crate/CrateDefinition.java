package fr.thegostsniperfr.arffornia.crate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A crate, read from {@code data/<ns>/arffornia/crate/<path>.json}. {@code rarity} is the crate's own rarity (beam
 * colour, key glint); {@code item_rarity} raises the rarity of a roll that contains one of those items. A roll's rarity
 * is the highest of the two. {@code preview} only knows {@code loot_table} for now; {@code calendar} only {@code streak}.
 */
public record CrateDefinition(ResourceLocation id, Component name, ResourceLocation lootTable, ResourceLocation keyModel,
                              CrateRarity rarity, Map<ResourceLocation, CrateRarity> itemRarity, String preview,
                              Optional<String> calendar) {

    public static final String CALENDAR_STREAK = "streak";

    record Json(Component name, ResourceLocation lootTable, ResourceLocation keyModel, CrateRarity rarity,
                Map<ResourceLocation, CrateRarity> itemRarity, String preview, Optional<String> calendar) {
        static final Codec<Json> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ComponentSerialization.CODEC.fieldOf("name").forGetter(Json::name),
                ResourceLocation.CODEC.fieldOf("loot_table").forGetter(Json::lootTable),
                ResourceLocation.CODEC.fieldOf("key_model").forGetter(Json::keyModel),
                CrateRarity.CODEC.optionalFieldOf("rarity", CrateRarity.COMMON).forGetter(Json::rarity),
                Codec.unboundedMap(ResourceLocation.CODEC, CrateRarity.CODEC).optionalFieldOf("item_rarity", Map.of()).forGetter(Json::itemRarity),
                Codec.STRING.optionalFieldOf("preview", "loot_table").forGetter(Json::preview),
                Codec.STRING.optionalFieldOf("calendar").forGetter(Json::calendar)
        ).apply(instance, Json::new));

        CrateDefinition withId(ResourceLocation id) {
            return new CrateDefinition(id, name, lootTable, keyModel, rarity, Map.copyOf(itemRarity), preview, calendar);
        }
    }

    /** What a client needs to draw keys and crates. */
    public record Client(ResourceLocation id, Component name, ResourceLocation keyModel, CrateRarity rarity) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Client> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Client::id,
                ComponentSerialization.STREAM_CODEC, Client::name,
                ResourceLocation.STREAM_CODEC, Client::keyModel,
                CrateRarity.STREAM_CODEC, Client::rarity,
                Client::new);
        public static final StreamCodec<RegistryFriendlyByteBuf, List<Client>> LIST_STREAM_CODEC = STREAM_CODEC.apply(ByteBufCodecs.list());
    }

    public Client client() {
        return new Client(id, name, keyModel, rarity);
    }

    public CrateRarity rarityOf(ItemStack stack) {
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return CrateRarity.max(rarity, itemRarity.getOrDefault(item, CrateRarity.COMMON));
    }

    public CrateRarity rarityOf(List<ItemStack> roll) {
        CrateRarity result = rarity;
        for (ItemStack stack : roll) {
            result = CrateRarity.max(result, rarityOf(stack));
        }
        return result;
    }

    public boolean hasStreakCalendar() {
        return calendar.filter(CALENDAR_STREAK::equals).isPresent();
    }
}
