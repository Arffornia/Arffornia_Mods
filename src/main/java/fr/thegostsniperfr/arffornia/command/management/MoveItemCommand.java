package fr.thegostsniperfr.arffornia.command.management;

import net.minecraft.ChatFormatting;
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
                                        context.getSource().sendFailure(Component.translatable("commands.arffornia.moveitem.no_item"));
                                        return 0;
                                    }

                                    if (fromId == toId) {
                                        context.getSource().sendFailure(Component.translatable("commands.arffornia.moveitem.same_milestone"));
                                        return 0;
                                    }

                                    String itemId = BuiltInRegistries.ITEM.getKey(heldItemStack.getItem()).toString();

                                    context.getSource().sendSystemMessage(Component.translatable("commands.arffornia.moveitem.moving", itemId, fromId, toId).withStyle(ChatFormatting.YELLOW));

                                    ArfforniaApiService.getInstance().moveMilestoneItem(fromId, toId, itemId)
                                            .thenAcceptAsync(success -> {
                                                if (success) {
                                                    context.getSource().sendSystemMessage(Component.translatable("commands.arffornia.moveitem.success").withStyle(ChatFormatting.GREEN));
                                                } else {
                                                    context.getSource().sendFailure(Component.translatable("commands.arffornia.moveitem.failed"));
                                                }
                                            }, player.getServer());

                                    return 1;
                                })
                        )
                );
    }
}