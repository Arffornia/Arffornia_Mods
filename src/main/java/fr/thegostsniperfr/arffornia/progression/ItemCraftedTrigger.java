package fr.thegostsniperfr.arffornia.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * {@code arffornia:item_crafted}: the player crafted an item matching one of {@code items}. Vanilla only has
 * {@code minecraft:recipe_crafted}, keyed by recipe id; milestones of type {@code craft} name items, so this trigger
 * fires from NeoForge's {@code ItemCraftedEvent}, which crafting result slots post whatever the mod.
 */
public final class ItemCraftedTrigger extends SimpleCriterionTrigger<ItemCraftedTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ItemStack crafted) {
        trigger(player, instance -> instance.matches(crafted));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, List<ItemPredicate> items) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                ItemPredicate.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(TriggerInstance::items)
        ).apply(instance, TriggerInstance::new));

        /** An empty list matches any crafted item. */
        public boolean matches(ItemStack crafted) {
            return !crafted.isEmpty() && (items.isEmpty() || items.stream().anyMatch(predicate -> predicate.test(crafted)));
        }
    }
}
