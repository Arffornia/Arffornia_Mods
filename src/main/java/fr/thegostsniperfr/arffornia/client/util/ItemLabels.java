package fr.thegostsniperfr.arffornia.client.util;

import fr.thegostsniperfr.arffornia.api.ApiText;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Labels for items referenced by API data (milestone unlocks and requirements). */
public final class ItemLabels {
    private ItemLabels() {
    }

    /**
     * The item's own name in the player's language when the game knows {@code itemId}; otherwise the
     * display name stored by the API (English, generated server-side), or the id.
     */
    public static Component of(@Nullable String itemId, @Nullable String apiDisplayName) {
        ResourceLocation id = itemId == null ? null : ResourceLocation.tryParse(itemId);
        if (id != null) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
            if (item.isPresent() && item.get() != Items.AIR) {
                return new ItemStack(item.get()).getHoverName();
            }
        }
        return Component.literal(ApiText.itemFallbackName(itemId, apiDisplayName));
    }
}
