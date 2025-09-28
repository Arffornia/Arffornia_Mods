package fr.thegostsniperfr.arffornia.command.management;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import java.util.stream.Collectors;

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

                            String itemId = BuiltInRegistries.ITEM.getKey(heldItemStack.getItem()).toString();
                            String displayName = generateDisplayName(itemId);
                            String imagePath = generateImagePath(itemId);

                            List<String> recipesToBan = player.getServer().getRecipeManager().getRecipes().stream()
                                    .filter(recipe -> {
                                        ItemStack resultItem = recipe.value().getResultItem(player.level().registryAccess());
                                        return resultItem != null && !resultItem.isEmpty() && ItemStack.isSameItem(resultItem, heldItemStack);
                                    })
                                    .map(RecipeHolder::id)
                                    .map(ResourceLocation::toString)
                                    .collect(Collectors.toList());

                            if (!recipesToBan.contains(itemId)) {
                                recipesToBan.add(itemId);
                            }

                            context.getSource().sendSystemMessage(Component.literal("§eAdding unlock for '" + displayName + "' to milestone " + milestoneId + "..."));
                            context.getSource().sendSystemMessage(Component.literal("§7Found " + recipesToBan.size() + " associated recipes to ban."));

                            ArfforniaApiService.getInstance().addUnlockToMilestone(milestoneId, itemId, displayName, imagePath, recipesToBan)
                                    .thenAcceptAsync(success -> {
                                        if (success) {
                                            context.getSource().sendSystemMessage(Component.literal("§aSuccessfully added item unlock for '" + displayName + "' to milestone " + milestoneId));
                                        } else {
                                            context.getSource().sendFailure(Component.literal("§cFailed to add item unlock. Check server logs for details."));
                                        }
                                    }, player.getServer());

                            return 1;
                        })
                );
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