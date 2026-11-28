package fr.thegostsniperfr.arffornia.gametest;

import com.google.gson.JsonObject;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.recipe.RecipeCapture;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Arffornia.MODID)
@PrefixGameTestTemplate(false)
public final class RecipeCaptureGameTests {
    private static final String PLATFORM = "platform";

    private static ShapelessRecipe tagRecipe() {
        return new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.STICK, 4),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(ItemTags.PLANKS), Ingredient.of(ItemTags.PLANKS), Ingredient.of(Items.REDSTONE)));
    }

    @GameTest(template = PLATFORM)
    public static void captureKeepsTheTagOfTheOriginalRecipe(GameTestHelper helper) {
        var captured = RecipeCapture.capture(tagRecipe(), helper.getLevel().registryAccess());

        helper.assertTrue(captured.isPresent(), "A shapeless recipe should be captured");
        JsonObject machine = captured.get().machineRecipeJson().getAsJsonObject();
        JsonObject planks = machine.getAsJsonArray("ingredients").get(0).getAsJsonObject();
        helper.assertTrue("minecraft:planks".equals(planks.getAsJsonObject("ingredient").get("tag").getAsString()),
                "The tag ingredient must stay a tag: " + planks);
        helper.assertTrue(planks.get("count").getAsInt() == 2, "Equal ingredients must be merged into one count");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void captureProposesAMachineRecipeAndKeepsTheOriginalType(GameTestHelper helper) {
        var captured = RecipeCapture.capture(tagRecipe(), helper.getLevel().registryAccess()).orElseThrow();

        JsonObject machine = captured.machineRecipeJson().getAsJsonObject();
        helper.assertTrue("arffornia:machine".equals(machine.get("type").getAsString()), "Proposed body is an arffornia:machine recipe");
        helper.assertTrue("minecraft:crafting_shapeless".equals(captured.recipeJson().getAsJsonObject().get("type").getAsString()),
                "The original recipe keeps its own type");
        helper.assertTrue(machine.getAsJsonArray("results").get(0).getAsJsonObject().get("count").getAsInt() == 4, "Result count is kept");
        helper.succeed();
    }
}
