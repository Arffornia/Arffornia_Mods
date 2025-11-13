package fr.thegostsniperfr.arffornia.command.management;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
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
                            int milestoneId = IntegerArgumentType.getInteger(context, "milestone_id");
                            ItemStack heldItemStack = player.getMainHandItem();

                            if (heldItemStack.isEmpty()) {
                                context.getSource().sendFailure(Component.literal("§cYou must hold an item in your main hand."));
                                return 0;
                            }

                            RecipeManager recipeManager = player.getServer().getRecipeManager();
                            Optional<RecipeHolder<?>> recipeHolderOpt = findBestCraftingRecipeFor(recipeManager.getRecipes(), heldItemStack, player.level());

                            if (recipeHolderOpt.isEmpty()) {
                                context.getSource().sendFailure(Component.literal("§cNo compatible crafting-style recipe found for this item."));
                                return 0;
                            }

                            RecipeHolder<?> recipeHolder = recipeHolderOpt.get();
                            Map<String, Object> payload = convertRecipeToPayload(recipeHolder.value(), player.level().registryAccess());

                            if (payload == null) {
                                context.getSource().sendFailure(Component.literal("§cUnsupported recipe format: " + recipeHolder.value().getClass().getSimpleName()));
                                return 0;
                            }

                            String itemId = BuiltInRegistries.ITEM.getKey(heldItemStack.getItem()).toString();
                            String displayName = generateDisplayName(itemId);
                            String imagePath = generateImagePath(itemId);
                            List<String> recipesToBan = List.of(recipeHolder.id().toString());

                            context.getSource().sendSystemMessage(Component.literal("§eRegistering unlock for '" + displayName + "' to milestone " + milestoneId + "..."));
                            context.getSource().sendSystemMessage(Component.literal("§7Found recipe " + recipeHolder.id() + " to use and ban."));

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
                                    context.getSource().sendSystemMessage(Component.literal("§aSuccessfully registered item unlock and its recipe."));
                                } else {
                                    context.getSource().sendFailure(Component.literal("§cFailed to register item unlock. Check server logs."));
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

                    if (recipeResult == null || recipeResult.isEmpty()) {
//                        Arffornia.LOGGER.warn("Skipping recipe {} because its result is null or empty. This might be a misconfigured recipe from another mod.", recipe.id());
                        return false;
                    }

                    if (!ItemStack.isSameItem(recipeResult, result)) {
                        return false;
                    }
                    return recipe.value() instanceof ShapedRecipe || recipe.value() instanceof ShapelessRecipe;
                })
                .sorted((r1, r2) -> {
                    boolean r1IsShaped = r1.value() instanceof ShapedRecipe;
                    boolean r2IsShaped = r2.value() instanceof ShapelessRecipe;
                    if (r1IsShaped && !r2IsShaped) return -1;
                    if (!r1IsShaped && r2IsShaped) return 1;
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

        if (recipe instanceof ShapedRecipe shapedRecipe) {
            int recipeWidth = shapedRecipe.getWidth();
            int recipeHeight = shapedRecipe.getHeight();

            for (int y = 0; y < recipeHeight; y++) {
                for (int x = 0; x < recipeWidth; x++) {
                    Ingredient ingredient = ingredients.get(y * recipeWidth + x);
                    if (!ingredient.isEmpty()) {
                        ItemStack firstStack = ingredient.getItems()[0];
                        String ingredientId = BuiltInRegistries.ITEM.getKey(firstStack.getItem()).toString();
                        ingredientsPayload.set(y * 3 + x, Map.of("item", ingredientId, "count", 1));
                    }
                }
            }
        } else if (recipe instanceof ShapelessRecipe) {
            for (int i = 0; i < ingredients.size() && i < 9; i++) {
                Ingredient ingredient = ingredients.get(i);
                if (!ingredient.isEmpty()) {
                    ItemStack firstStack = ingredient.getItems()[0];
                    String ingredientId = BuiltInRegistries.ITEM.getKey(firstStack.getItem()).toString();
                    ingredientsPayload.set(i, Map.of("item", ingredientId, "count", 1));
                }
            }
        } else {
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