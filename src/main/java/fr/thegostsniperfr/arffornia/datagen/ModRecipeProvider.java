package fr.thegostsniperfr.arffornia.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

public final class ModRecipeProvider extends RecipeProvider {
    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModBlocks.CRAFTER_BLOCK.get())
                .pattern("CIC").pattern("ILI").pattern("BFB")
                .define('C', Items.CRAFTING_TABLE).define('I', Items.IRON_BLOCK).define('L', Items.LAVA_BUCKET)
                .define('F', Items.BLAST_FURNACE).define('B', Items.IRON_BLOCK)
                .unlockedBy("has_blast_furnace", has(Items.BLAST_FURNACE))
                .save(output);
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.MACHINE_CASING.get(), 4)
                .pattern("ISI").pattern("S S").pattern("ISI")
                .define('I', Items.IRON_INGOT).define('S', Items.STONE)
                .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
                .save(output);
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, ModBlocks.SMELTER_CONTROLLER.get())
                .pattern("BBB").pattern("BFB").pattern("BRB")
                .define('B', Items.BRICKS).define('F', Items.BLAST_FURNACE).define('R', Items.REDSTONE)
                .unlockedBy("has_blast_furnace", has(Items.BLAST_FURNACE))
                .save(output);
        port(output, ModBlocks.ITEM_INPUT_PORT.get(), Items.HOPPER);
        port(output, ModBlocks.ITEM_OUTPUT_PORT.get(), Items.DROPPER);
        port(output, ModBlocks.ENERGY_INPUT_PORT.get(), Items.REDSTONE_BLOCK);
    }

    private static void port(RecipeOutput output, net.minecraft.world.level.ItemLike port, net.minecraft.world.level.ItemLike top) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, port)
                .pattern("T").pattern("C")
                .define('T', top).define('C', ModBlocks.MACHINE_CASING.get())
                .unlockedBy("has_machine_casing", has(ModBlocks.MACHINE_CASING.get()))
                .save(output);
    }

    /**
     * The Space Elevator needs SophisticatedStorage's iron chest, an item that does not exist when datagen runs, so
     * the recipe is written as JSON, conditional on that mod.
     */
    public static final class SpaceElevatorRecipe implements DataProvider {
        private final PackOutput output;

        public SpaceElevatorRecipe(PackOutput output) {
            this.output = output;
        }

        @Override
        public CompletableFuture<?> run(CachedOutput cache) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "minecraft:crafting_shaped");
            json.addProperty("category", "misc");
            JsonArray conditions = new JsonArray();
            JsonObject condition = new JsonObject();
            condition.addProperty("type", "neoforge:mod_loaded");
            condition.addProperty("modid", "sophisticatedstorage");
            conditions.add(condition);
            json.add("neoforge:conditions", conditions);
            JsonArray pattern = new JsonArray();
            for (String row : new String[]{"DID", "ICI", "BCB"}) {
                pattern.add(row);
            }
            json.add("pattern", pattern);
            JsonObject key = new JsonObject();
            key.add("C", item("sophisticatedstorage:iron_chest"));
            key.add("I", item("minecraft:iron_ingot"));
            key.add("B", item("minecraft:iron_block"));
            key.add("D", item("minecraft:diamond"));
            json.add("key", key);
            JsonObject result = new JsonObject();
            result.addProperty("id", Arffornia.MODID + ":space_elevator");
            result.addProperty("count", 1);
            json.add("result", result);
            return DataProvider.saveStable(cache, json, output.getOutputFolder(PackOutput.Target.DATA_PACK)
                    .resolve(Arffornia.MODID).resolve("recipe/space_elevator.json"));
        }

        private static JsonObject item(String id) {
            JsonObject ingredient = new JsonObject();
            ingredient.addProperty("item", id);
            return ingredient;
        }

        @Override
        public String getName() {
            return "Arffornia Space Elevator recipe";
        }
    }
}
