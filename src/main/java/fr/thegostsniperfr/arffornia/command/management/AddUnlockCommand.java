package fr.thegostsniperfr.arffornia.command.management;

import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import fr.thegostsniperfr.arffornia.recipe.RecipeBanManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Command to create a new unlock for a milestone based on the item held by the player.
 * It automatically finds a compatible crafting recipe for the item and sends it to the web API.
 */
public class AddUnlockCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("addunlock")
                .then(Commands.argument("milestone_id", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            Level level = player.level();
                            int milestoneId = IntegerArgumentType.getInteger(context, "milestone_id");
                            ItemStack heldItemStack = player.getMainHandItem();

                            if (heldItemStack.isEmpty()) {
                                context.getSource().sendFailure(Component.translatable("commands.arffornia.addunlock.no_item"));
                                return 0;
                            }

                            Collection<RecipeHolder<?>> allRecipes = RecipeBanManager.getOriginalRecipes();

                            Optional<RecipeHolder<?>> recipeHolderOpt = findBestCraftingRecipeFor(allRecipes, heldItemStack, level);

                            if (recipeHolderOpt.isEmpty()) {
                                context.getSource().sendFailure(Component.translatable("commands.arffornia.addunlock.no_recipe"));
                                context.getSource().sendSystemMessage(getAvailableRecipeTypesDebug(allRecipes, heldItemStack, level));
                                return 0;
                            }

                            RecipeHolder<?> recipeHolder = recipeHolderOpt.get();

                            Map<String, Object> payload = convertRecipeToPayload(recipeHolder.value(), level.registryAccess());

                            if (payload == null) {
                                context.getSource().sendFailure(Component.translatable("commands.arffornia.addunlock.unsupported", recipeHolder.value().getClass().getSimpleName()));
                                return 0;
                            }

                            String itemId = BuiltInRegistries.ITEM.getKey(heldItemStack.getItem()).toString();
                            String displayName = generateDisplayName(itemId);
                            String imagePath = generateImagePath(itemId);
                            List<String> recipesToBan = List.of(recipeHolder.id().toString());

                            context.getSource().sendSystemMessage(Component.translatable("commands.arffornia.addunlock.registering", displayName, recipeHolder.value().getClass().getSimpleName()).withStyle(ChatFormatting.YELLOW));

                            @SuppressWarnings("unchecked")
                            CompletableFuture<Boolean> future = ArfforniaApiService.getInstance().addUnlockToMilestone(
                                    milestoneId,
                                    itemId,
                                    displayName,
                                    imagePath,
                                    recipesToBan,
                                    (List<Map<String, Object>>) payload.get("ingredients"),
                                    (List<Map<String, Object>>) payload.get("result")
                            );

                            future.thenAcceptAsync(success -> {
                                if (success) {
                                    context.getSource().sendSystemMessage(Component.translatable("commands.arffornia.addunlock.success").withStyle(ChatFormatting.GREEN));
                                } else {
                                    context.getSource().sendFailure(Component.translatable("commands.arffornia.addunlock.failed"));
                                }
                            }, player.getServer());

                            return 1;
                        })
                );
    }

    /**
     * Finds the best crafting-style recipe for a given result item from a provided collection of recipes.
     * It iterates through all recipes, prioritizing shaped recipes to preserve the grid layout.
     * @param allRecipes A collection of all recipes to search through.
     * @param result The ItemStack that the recipe should produce.
     * @param level The current level, used to get the RegistryAccess.
     * @return An Optional containing the best matching RecipeHolder, or empty if none is found.
     */
    public static Optional<RecipeHolder<?>> findBestCraftingRecipeFor(Collection<RecipeHolder<?>> allRecipes, ItemStack result, Level level) {
        RegistryAccess registryAccess = level.registryAccess();

        return allRecipes.stream()
                .filter(recipe -> !recipe.id().getNamespace().equals("arffornia"))
                .filter(recipe -> {
                    ItemStack recipeResult = recipe.value().getResultItem(registryAccess);
                    if (recipeResult == null || recipeResult.isEmpty()) return false;
                    if (!ItemStack.isSameItem(recipeResult, result)) return false;

                    if (recipe.value() instanceof ShapedRecipe || recipe.value() instanceof ShapelessRecipe) {
                        return true;
                    }

                    String typeId = recipe.value().getType().toString();
                    String className = recipe.value().getClass().getSimpleName();

                    if (typeId.contains("infusion") || className.contains("InfusionRecipe")) {
                        return true;
                    }

                    return false;
                })
                .sorted((r1, r2) -> {
                    boolean r1Shape = r1.value() instanceof ShapedRecipe;
                    boolean r2Shape = r2.value() instanceof ShapedRecipe;
                    if (r1Shape && !r2Shape) return -1;
                    if (!r1Shape && r2Shape) return 1;
                    return 0;
                })
                .findFirst();
    }

    /**
     * Converts a Recipe object into a payload map suitable for JSON serialization.
     * @param recipe The recipe to convert.
     * @param registryAccess The registry access from the current level.
     * @return A mutable map representing the recipe, or null if the recipe type is unsupported.
     */
    @Nullable
    public static Map<String, Object> convertRecipeToPayload(Recipe<?> recipe, RegistryAccess registryAccess) {
        List<Map<String, Object>> ingredientsPayload = new ArrayList<>(Collections.nCopies(9, null));
        ItemStack resultStack = recipe.getResultItem(registryAccess);
        NonNullList<Ingredient> ingredients = recipe.getIngredients();

        String className = recipe.getClass().getSimpleName();
        String typeId = recipe.getType().toString();

        try {
            if (recipe instanceof ShapedRecipe shapedRecipe) {
                int width = shapedRecipe.getWidth();
                int height = shapedRecipe.getHeight();
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int index = y * width + x;
                        if (index < ingredients.size()) {
                            ingredientsPayload.set(y * 3 + x, convertIngredientToMap(ingredients.get(index)));
                        }
                    }
                }
            }
            else if (recipe instanceof ShapelessRecipe) {
                for (int i = 0; i < ingredients.size() && i < 9; i++) {
                    ingredientsPayload.set(i, convertIngredientToMap(ingredients.get(i)));
                }
            }
            // Mystical Agriculture Pattern
            else if (typeId.contains("infusion") || className.contains("InfusionRecipe")) {
                if (!ingredients.isEmpty()) {
                    ingredientsPayload.set(4, convertIngredientToMap(ingredients.get(0)));

                    int[] surroundingSlots = {0, 1, 2, 3, 5, 6, 7, 8};

                    for (int i = 1; i < ingredients.size() && i <= 8; i++) {
                        int gridIndex = surroundingSlots[i - 1];
                        ingredientsPayload.set(gridIndex, convertIngredientToMap(ingredients.get(i)));
                    }
                }
            }
            else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        List<Map<String, Object>> resultPayload = List.of(
                Map.of("item", BuiltInRegistries.ITEM.getKey(resultStack.getItem()).toString(), "count", resultStack.getCount())
        );

        Map<String, Object> payload = new HashMap<>();
        payload.put("ingredients", ingredientsPayload);
        payload.put("result", resultPayload);
        return payload;
    }

    @Nullable
    private static Map<String, Object> convertIngredientToMap(Ingredient ingredient) {
        if (ingredient.isEmpty()) return null;
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) return null;

        ItemStack firstStack = items[0];
        String ingredientId = BuiltInRegistries.ITEM.getKey(firstStack.getItem()).toString();
        return Map.of("item", ingredientId, "count", 1);
    }

    public static Component getAvailableRecipeTypesDebug(Collection<RecipeHolder<?>> allRecipes, ItemStack targetItem, Level level) {
        RegistryAccess registryAccess = level.registryAccess();
        List<RecipeHolder<?>> matchingRecipes = allRecipes.stream()
                .filter(r -> {
                    ItemStack result = r.value().getResultItem(registryAccess);
                    return result != null && !result.isEmpty() && ItemStack.isSameItem(result, targetItem);
                })
                .toList();

        if (matchingRecipes.isEmpty()) {
            return Component.translatable("commands.arffornia.addunlock.debug.none").withStyle(ChatFormatting.RED);
        }

        MutableComponent debugMsg = Component.translatable("commands.arffornia.addunlock.debug.found", matchingRecipes.size())
                .withStyle(ChatFormatting.YELLOW);
        for (RecipeHolder<?> holder : matchingRecipes) {
            debugMsg.append("\n").append(Component.translatable("commands.arffornia.addunlock.debug.entry",
                    holder.id().toString(), String.valueOf(holder.value().getType())).withStyle(ChatFormatting.GRAY));
        }
        return debugMsg;
    }

    private static String generateDisplayName(String itemId) {
        String path = itemId.substring(itemId.indexOf(':') + 1);
        String[] words = path.split("_");
        StringBuilder displayName = new StringBuilder();
        for (String word : words) {
            displayName.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return displayName.toString().trim();
    }

    private static String generateImagePath(String itemId) {
        return itemId.replace(":", "_");
    }
}