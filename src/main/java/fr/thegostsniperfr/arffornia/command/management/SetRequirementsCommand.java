package fr.thegostsniperfr.arffornia.command.management;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SetRequirementsCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("setrequirements")
                .then(Commands.argument("milestone_id", IntegerArgumentType.integer(1))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            int milestoneId = IntegerArgumentType.getInteger(context, "milestone_id");

                            // Aggregate all items from the player's inventory into a map.
                            Map<Item, Integer> aggregatedItems = new HashMap<>();
                            for (int i = 0; i < 36; i++) { // Only main inventory
                                ItemStack stack = player.getInventory().getItem(i);
                                if (!stack.isEmpty()) {
                                    aggregatedItems.merge(stack.getItem(), stack.getCount(), Integer::sum);
                                }
                            }

                            // Check if the number of unique items exceeds the limit.
                            if (aggregatedItems.size() > 3) {
                                context.getSource().sendFailure(Component.literal("§cYou can only set requirements for a maximum of 3 different item types at a time. Your inventory contains " + aggregatedItems.size() + "."));
                                return 0;
                            }

                            if (aggregatedItems.isEmpty()) {
                                context.getSource().sendFailure(Component.literal("§cYour inventory is empty. No requirements were set."));
                                return 0;
                            }

                            List<Map<String, Object>> requirementsPayload = new ArrayList<>();
                            int totalItems = 0;

                            for (Map.Entry<Item, Integer> entry : aggregatedItems.entrySet()) {
                                Item item = entry.getKey();
                                int totalAmount = entry.getValue();
                                String itemId = BuiltInRegistries.ITEM.getKey(item).toString();

                                Map<String, Object> itemData = new HashMap<>();
                                itemData.put("item_id", itemId);
                                itemData.put("display_name", new ItemStack(item).getHoverName().getString());
                                itemData.put("amount", totalAmount);
                                itemData.put("image_path", itemId.replace(":", "_"));

                                requirementsPayload.add(itemData);
                                totalItems += totalAmount;
                            }


                            context.getSource().sendSystemMessage(Component.literal("§eSending " + requirementsPayload.size() + " unique items (" + totalItems + " total) as new requirements for milestone " + milestoneId + "..."));

                            ArfforniaApiService.getInstance().setMilestoneRequirements(milestoneId, requirementsPayload)
                                    .thenAcceptAsync(success -> {
                                        if (success) {
                                            context.getSource().sendSystemMessage(Component.literal("§aSuccessfully updated requirements for milestone " + milestoneId + "."));
                                        } else {
                                            context.getSource().sendFailure(Component.literal("§cFailed to update requirements. Check server logs."));
                                        }
                                    }, player.getServer());

                            return 1;
                        })
                );
    }
}