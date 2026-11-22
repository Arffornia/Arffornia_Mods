package fr.thegostsniperfr.arffornia.item;

import fr.thegostsniperfr.arffornia.crate.CrateDefinition;
import fr.thegostsniperfr.arffornia.crate.CrateDefinitions;
import fr.thegostsniperfr.arffornia.crate.CrateKey;
import fr.thegostsniperfr.arffornia.crate.CrateRarity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One item for every crate's key: the {@link CrateKey} component names the crate, which gives the name and model. */
public class CrateKeyItem extends Item {
    public CrateKeyItem() {
        super(new Item.Properties().stacksTo(64));
    }

    /** Keys bound to {@code owner}, split into full stacks. */
    public static List<ItemStack> stacks(ResourceLocation crate, UUID owner, String ownerName, int count) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int remaining = count; remaining > 0; remaining -= 64) {
            stacks.add(stack(crate, owner, ownerName, Math.min(64, remaining)));
        }
        return stacks;
    }

    public static List<ItemStack> stacks(ResourceLocation crate, ServerPlayer owner, int count) {
        return stacks(crate, owner.getUUID(), owner.getGameProfile().getName(), count);
    }

    public static ItemStack stack(ResourceLocation crate, UUID owner, String ownerName, int count) {
        ItemStack stack = new ItemStack(ModItems.CRATE_KEY.get(), count);
        stack.set(ModDataComponents.CRATE_KEY.get(), new CrateKey(crate, owner, ownerName));
        return stack;
    }

    @Nullable
    public static CrateKey keyOf(ItemStack stack) {
        return stack.is(ModItems.CRATE_KEY.get()) ? stack.get(ModDataComponents.CRATE_KEY.get()) : null;
    }

    @Nullable
    private static CrateDefinition.Client crateOf(ItemStack stack) {
        CrateKey key = stack.get(ModDataComponents.CRATE_KEY.get());
        return key == null ? null : CrateDefinitions.find(key.crate());
    }

    @Override
    public Component getName(ItemStack stack) {
        CrateDefinition.Client crate = crateOf(stack);
        if (crate == null) {
            return super.getName(stack);
        }
        return Component.translatable("item.arffornia.crate_key.named", crate.name()).withStyle(crate.rarity().chatColour());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CrateDefinition.Client crate = crateOf(stack);
        return crate != null && crate.rarity().atLeast(CrateRarity.RARE);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CrateKey key = stack.get(ModDataComponents.CRATE_KEY.get());
        if (key == null) {
            tooltip.add(Component.translatable("item.arffornia.crate_key.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        CrateDefinition.Client crate = CrateDefinitions.find(key.crate());
        tooltip.add(Component.translatable("item.arffornia.crate_key.opens",
                crate != null ? crate.name() : Component.translatable("item.arffornia.crate_key.unknown_crate")).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.arffornia.crate_key.owner",
                key.ownerName().isEmpty() ? key.owner().toString() : key.ownerName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.arffornia.crate_key.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
