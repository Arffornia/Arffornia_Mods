package fr.thegostsniperfr.arffornia.command.management;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Handles the '/arffornia progression remove <player> <milestone_id>' command.
 * This command removes a completed milestone from a player's active progression.
 */
public class RemoveMilestoneCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("remove")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("milestone_id", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
                                    int milestoneId = IntegerArgumentType.getInteger(context, "milestone_id");
                                    CommandSourceStack source = context.getSource();

                                    source.sendSystemMessage(Component.literal("Requesting to remove milestone " + milestoneId + " from " + targetPlayer.getName().getString() + "..."));

                                    Arffornia.ARFFORNA_API_SERVICE.removeMilestone(targetPlayer.getUUID(), milestoneId)
                                            .thenAccept(success -> {
                                                if (success) {
                                                    source.sendSystemMessage(Component.literal("§aSuccessfully removed milestone."));
                                                    // TODO: Send a network packet to the targetPlayer to notify their client of the change.
                                                } else {
                                                    source.sendSystemMessage(Component.literal("§cFailed to remove milestone. Check server logs for details."));
                                                }
                                            });

                                    return 1;
                                })
                        )
                );
    }
}