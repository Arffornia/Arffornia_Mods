package fr.thegostsniperfr.arffornia.recipe;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What {@code addunlock} posts to the website: the original recipe encoded with its own codec, so tags, components
 * and counts survive, and the {@code arffornia:machine} body proposed from it.
 */
public final class RecipeCapture {
    private static final ResourceLocation CRAFTER = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "crafter");

    public record Captured(JsonElement recipeJson, JsonElement machineRecipeJson) {
    }

    private RecipeCapture() {
    }

    public static Optional<Captured> capture(Recipe<?> recipe, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        Optional<JsonElement> original = Recipe.CODEC.encodeStart(ops, recipe).result();
        if (original.isEmpty()) {
            return Optional.empty();
        }

        Map<JsonElement, int[]> counts = new LinkedHashMap<>();
        Map<JsonElement, Ingredient> byJson = new LinkedHashMap<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            Optional<JsonElement> encoded = Ingredient.CODEC.encodeStart(ops, ingredient).result();
            if (encoded.isEmpty()) {
                return Optional.empty();
            }
            counts.computeIfAbsent(encoded.get(), key -> new int[1])[0]++;
            byJson.putIfAbsent(encoded.get(), ingredient);
        }
        List<SizedIngredient> sized = new ArrayList<>();
        counts.forEach((json, count) -> sized.add(new SizedIngredient(byJson.get(json), count[0])));
        ItemStack result = recipe.getResultItem(registries);
        if (sized.isEmpty() || result.isEmpty()) {
            return Optional.empty();
        }

        MachineRecipe machine = new MachineRecipe(CRAFTER, Optional.empty(), sized, List.of(result.copy()), 0, MachineRecipe.DEFAULT_TIME);
        return Recipe.CODEC.encodeStart(ops, machine).result().map(json -> new Captured(original.get(), json));
    }
}
