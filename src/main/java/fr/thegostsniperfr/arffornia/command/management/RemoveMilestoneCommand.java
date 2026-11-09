package fr.thegostsniperfr.arffornia.command.management;

import net.minecraft.ChatFormatting;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import fr.thegostsniperfr.arffornia.progression.Progressions;
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

                                    source.sendSystemMessage(Component.translatable("commands.arffornia.remove.requesting", milestoneId, targetPlayer.getDisplayName()));

                                    ArfforniaApiService.getInstance().removeMilestone(targetPlayer.getUUID(), milestoneId)
                                            .thenAccept(success -> {
                                                targetPlayer.server.execute(() -> Progressions.refreshPlayer(targetPlayer));
                                                if (success) {
                                                    source.sendSystemMessage(Component.translatable("commands.arffornia.remove.success").withStyle(ChatFormatting.GREEN));
                                                } else {
                                                    source.sendSystemMessage(Component.translatable("commands.arffornia.remove.failed").withStyle(ChatFormatting.RED));
                                                }
                                            });

                                    return 1;
                                })
                        )
                );
    }
}