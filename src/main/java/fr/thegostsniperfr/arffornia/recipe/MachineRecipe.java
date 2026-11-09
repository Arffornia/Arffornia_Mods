package fr.thegostsniperfr.arffornia.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A recipe processed by an Arffornia machine, loaded from {@code type: arffornia:machine} data files.
 *
 * @param machine     the machine that runs it, e.g. {@code arffornia:crafter}
 * @param stage       the progression stage key that unlocks it; empty means always available
 * @param ingredients inputs, each with a count that may be spread over several slots
 * @param results     outputs, the first one is the main result
 * @param energy      total FE for one craft
 * @param time        ticks for one craft
 */
public record MachineRecipe(ResourceLocation machine, Optional<String> stage, List<SizedIngredient> ingredients,
                            List<ItemStack> results, int energy, int time) implements Recipe<MachineRecipeInput> {

    public static final int DEFAULT_TIME = 200;

    public static final MapCodec<MachineRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("machine").forGetter(MachineRecipe::machine),
            ExtraCodecs.NON_EMPTY_STRING.optionalFieldOf("stage").forGetter(MachineRecipe::stage),
            ExtraCodecs.nonEmptyList(SizedIngredient.NESTED_CODEC.listOf()).fieldOf("ingredients").forGetter(MachineRecipe::ingredients),
            ExtraCodecs.nonEmptyList(ItemStack.STRICT_CODEC.listOf()).fieldOf("results").forGetter(MachineRecipe::results),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("energy", 0).forGetter(MachineRecipe::energy),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(MachineRecipe::time)
    ).apply(instance, MachineRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, MachineRecipe::machine,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), MachineRecipe::stage,
            SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), MachineRecipe::ingredients,
            ItemStack.LIST_STREAM_CODEC, MachineRecipe::results,
            ByteBufCodecs.VAR_INT, MachineRecipe::energy,
            ByteBufCodecs.VAR_INT, MachineRecipe::time,
            MachineRecipe::new);

    public MachineRecipe {
        ingredients = List.copyOf(ingredients);
        results = results.stream().map(ItemStack::copy).toList();
    }

    /**
     * How many items to take from each slot to satisfy every ingredient, or empty when the slots fall short.
     * A slot's items are never counted twice. Ingredients matching the fewest slots are served first, so a tag
     * ingredient does not eat the items a specific ingredient needs.
     */
    public Optional<int[]> allocate(List<ItemStack> slots) {
        int[] available = new int[slots.size()];
        for (int i = 0; i < available.length; i++) {
            available[i] = slots.get(i).getCount();
        }
        List<SizedIngredient> mostSpecificFirst = ingredients.stream()
                .sorted(Comparator.comparingLong(ingredient -> slots.stream()
                        .filter(stack -> !stack.isEmpty() && ingredient.ingredient().test(stack)).count()))
                .toList();
        int[] taken = new int[slots.size()];
        for (SizedIngredient ingredient : mostSpecificFirst) {
            int needed = ingredient.count();
            for (int slot = 0; slot < available.length && needed > 0; slot++) {
                ItemStack stack = slots.get(slot);
                if (available[slot] == 0 || stack.isEmpty() || !ingredient.ingredient().test(stack)) {
                    continue;
                }
                int take = Math.min(needed, available[slot]);
                available[slot] -= take;
                taken[slot] += take;
                needed -= take;
            }
            if (needed > 0) {
                return Optional.empty();
            }
        }
        return Optional.of(taken);
    }

    public boolean isUnlockedBy(Set<String> unlockedStages) {
        return stage.isEmpty() || unlockedStages.contains(stage.get());
    }

    public ItemStack mainResult() {
        return results.getFirst();
    }

    @Override
    public boolean matches(MachineRecipeInput input, Level level) {
        return allocate(input.items()).isPresent();
    }

    @Override
    public ItemStack assemble(MachineRecipeInput input, HolderLookup.Provider registries) {
        return mainResult().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return mainResult();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        ingredients.forEach(sized -> list.add(sized.ingredient()));
        return list;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.MACHINE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.MACHINE_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<MachineRecipe> {
        @Override
        public MapCodec<MachineRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
