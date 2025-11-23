package fr.thegostsniperfr.arffornia.command.management;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class MoveItemCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("moveitem")
                .then(Commands.argument("from_milestone_id", IntegerArgumentType.integer(1))
                        .then(Commands.argument("to_milestone_id", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    int fromId = IntegerArgumentType.getInteger(context, "from_milestone_id");
                                    int toId = IntegerArgumentType.getInteger(context, "to_milestone_id");
                                    ItemStack heldItemStack = player.getMainHandItem();

                                    if (heldItemStack.isEmpty()) {
                                        context.getSource().sendFailure(Component.literal("§cYou must hold the item you want to move."));
                                        return 0;
                                    }

                                    if (fromId == toId) {
                                        context.getSource().sendFailure(Component.literal("§cSource and destination milestones cannot be the same."));
                                        return 0;
                                    }

                                    String itemId = BuiltInRegistries.ITEM.getKey(heldItemStack.getItem()).toString();

                                    context.getSource().sendSystemMessage(Component.literal("§eAttempting to move item '" + itemId + "' from milestone " + fromId + " to " + toId + "..."));

                                    ArfforniaApiService.getInstance().moveMilestoneItem(fromId, toId, itemId)
                                            .thenAcceptAsync(success -> {
                                                if (success) {
                                                    context.getSource().sendSystemMessage(Component.literal("§aSuccessfully moved item."));
                                                } else {
                                                    context.getSource().sendFailure(Component.literal("§cFailed to move item. Check API and server logs. The item might not exist on the source milestone."));
                                                }
                                            }, player.getServer());

                                    return 1;
                                })
                        )
                );
    }
}