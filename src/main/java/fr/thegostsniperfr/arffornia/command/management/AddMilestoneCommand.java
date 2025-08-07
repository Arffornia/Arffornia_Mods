package fr.thegostsniperfr.arffornia.command.management;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class AddMilestoneCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("add")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("milestone_id", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                    int milestoneId = IntegerArgumentType.getInteger(context, "milestone_id");

                                    context.getSource().sendSystemMessage(Component.literal("Adding milestone " + milestoneId + " to " + target.getName().getString() + "..."));

                                    ArfforniaApiService.getInstance().addMilestone(target.getUUID(), milestoneId)
                                            .thenAccept(success -> {
                                                if (success) {
                                                    context.getSource().sendSystemMessage(Component.literal("§aSuccessfully added milestone."));
                                                    // TODO: Send packet to player to update GUI
                                                } else {
                                                    context.getSource().sendSystemMessage(Component.literal("§cFailed to add milestone. Check server logs."));
                                                }
                                            });
                                    return 1;
                                })
                        )
                );
    }
}