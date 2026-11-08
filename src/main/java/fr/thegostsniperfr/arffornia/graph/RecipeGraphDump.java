package fr.thegostsniperfr.arffornia.graph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes every loaded recipe, item and item tag of the running server, so the progression graph
 * tool (spec 017, initiative docs#28) can build the item -> recipe -> item graph of the whole pack.
 *
 * <p>Output files: {@code recipes.jsonl}, {@code items.jsonl}, {@code tags.json}, {@code summary.json}.
 * Each recipe carries the generic view (ingredients and result as item ids) when the recipe class
 * exposes it, and its full codec JSON, because machine and ritual recipes often keep their inputs
 * outside {@link Recipe#getIngredients()}.
 */
public final class RecipeGraphDump {

    public static final String STARTUP_PROPERTY = "arffornia.dumpRecipeGraph";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private RecipeGraphDump() {
    }

    public record Result(int recipes, int items, int tags, int codecFailures, Path dir) {
    }

    public static Result write(MinecraftServer server, Path dir) throws IOException {
        Files.createDirectories(dir);
        HolderLookup.Provider registries = server.registryAccess();
        RegistryOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);

        Map<String, Integer> perType = new TreeMap<>();
        Map<String, Integer> perNamespace = new TreeMap<>();
        int recipes = 0;
        int codecFailures = 0;
        try (BufferedWriter out = Files.newBufferedWriter(dir.resolve("recipes.jsonl"), StandardCharsets.UTF_8)) {
            for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
                JsonObject row = describe(holder, registries, ops);
                if (!row.has("json")) {
                    codecFailures++;
                }
                perType.merge(row.get("type").getAsString(), 1, Integer::sum);
                perNamespace.merge(holder.id().getNamespace(), 1, Integer::sum);
                out.write(GSON.toJson(row));
                out.newLine();
                recipes++;
            }
        }

        int items = 0;
        try (BufferedWriter out = Files.newBufferedWriter(dir.resolve("items.jsonl"), StandardCharsets.UTF_8)) {
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                JsonObject row = new JsonObject();
                row.addProperty("id", id.toString());
                row.addProperty("mod", id.getNamespace());
                row.addProperty("name_key", item.getDescriptionId());
                out.write(GSON.toJson(row));
                out.newLine();
                items++;
            }
        }

        JsonObject tags = new JsonObject();
        BuiltInRegistries.ITEM.getTags().forEach(pair -> {
            TagKey<Item> key = pair.getFirst();
            JsonArray members = new JsonArray();
            for (Holder<Item> member : pair.getSecond()) {
                members.add(BuiltInRegistries.ITEM.getKey(member.value()).toString());
            }
            tags.add(key.location().toString(), members);
        });
        Files.writeString(dir.resolve("tags.json"), GSON.toJson(tags), StandardCharsets.UTF_8);

        JsonObject summary = new JsonObject();
        summary.addProperty("recipes", recipes);
        summary.addProperty("items", items);
        summary.addProperty("tags", tags.size());
        summary.addProperty("codec_failures", codecFailures);
        summary.add("recipes_per_type", GSON.toJsonTree(perType));
        summary.add("recipes_per_namespace", GSON.toJsonTree(perNamespace));
        Files.writeString(dir.resolve("summary.json"), new GsonBuilder().setPrettyPrinting().create().toJson(summary), StandardCharsets.UTF_8);

        Arffornia.LOGGER.info("Recipe graph dump: {} recipes ({} without codec JSON), {} items, {} tags in {}",
                recipes, codecFailures, items, tags.size(), dir.toAbsolutePath());
        return new Result(recipes, items, tags.size(), codecFailures, dir);
    }

    private static JsonObject describe(RecipeHolder<?> holder, HolderLookup.Provider registries, RegistryOps<JsonElement> ops) {
        Recipe<?> recipe = holder.value();
        JsonObject row = new JsonObject();
        row.addProperty("id", holder.id().toString());
        row.addProperty("type", String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())));
        row.addProperty("serializer", String.valueOf(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer())));

        try {
            JsonArray ingredients = new JsonArray();
            for (Ingredient ingredient : recipe.getIngredients()) {
                JsonArray options = new JsonArray();
                for (ItemStack stack : ingredient.getItems()) {
                    options.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                }
                ingredients.add(options);
            }
            row.add("ingredients", ingredients);
        } catch (RuntimeException e) {
            row.addProperty("ingredients_error", e.getClass().getSimpleName());
        }

        try {
            ItemStack result = recipe.getResultItem(registries);
            if (!result.isEmpty()) {
                JsonObject out = new JsonObject();
                out.addProperty("item", BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
                out.addProperty("count", result.getCount());
                row.add("result", out);
            }
        } catch (RuntimeException e) {
            row.addProperty("result_error", e.getClass().getSimpleName());
        }

        try {
            Recipe.CODEC.encodeStart(ops, recipe).result().ifPresent(json -> row.add("json", json));
        } catch (RuntimeException e) {
            row.addProperty("json_error", e.getClass().getSimpleName());
        }
        return row;
    }
}
