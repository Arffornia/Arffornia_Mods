package fr.thegostsniperfr.arffornia.command.management;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.stream.Collectors;

/**
 * Handles the '/arffornia progression list <player>' command.
 * This command lists all completed milestones for a player's active progression.
 */
public class ListMilestonesCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("list")
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer targetPlayer = EntityArgument.getPlayer(context, "player");
                            CommandSourceStack source = context.getSource();

                            source.sendSystemMessage(Component.literal("Fetching completed milestones for " + targetPlayer.getName().getString() + "..."));

                            Arffornia.ARFFORNA_API_SERVICE.listMilestones(targetPlayer.getUUID())
                                    .thenAccept(completedIds -> {
                                        if (completedIds == null || completedIds.isEmpty()) {
                                            source.sendSystemMessage(Component.literal("§ePlayer " + targetPlayer.getName().getString() + " has no completed milestones."));
                                            return;
                                        }

                                        String idList = completedIds.stream()
                                                .map(String::valueOf)
                                                .collect(Collectors.joining(", "));

                                        source.sendSystemMessage(Component.literal("§aCompleted Milestones (" + completedIds.size() + "): §e" + idList));
                                    });

                            return 1;
                        })
                );
    }
}