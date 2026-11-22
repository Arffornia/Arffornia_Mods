package fr.thegostsniperfr.arffornia.command.management;

import net.minecraft.ChatFormatting;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.thegostsniperfr.arffornia.api.backend.ProgressionBackends;
import fr.thegostsniperfr.arffornia.progression.ProgressionEventSender;
import fr.thegostsniperfr.arffornia.progression.Progressions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class AddMilestoneCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("add")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("milestone_id", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                    int milestoneId = IntegerArgumentType.getInteger(context, "milestone_id");

                                    context.getSource().sendSystemMessage(Component.translatable("commands.arffornia.add.adding", milestoneId, target.getDisplayName()));

                                    MinecraftServer server = target.server;
                                    ProgressionBackends.get().fetchMilestoneDetails(milestoneId, null)
                                            .thenAcceptAsync(details -> {
                                                boolean done = details != null && details.key() != null
                                                        && ProgressionEventSender.milestoneCompleted(server, Progressions.activeProgressionOf(target), details.key(), target.getUUID());
                                                context.getSource().sendSystemMessage(done
                                                        ? Component.translatable("commands.arffornia.add.success").withStyle(ChatFormatting.GREEN)
                                                        : Component.translatable("commands.arffornia.add.failed").withStyle(ChatFormatting.RED));
                                            }, server);
                                    return 1;
                                })
                        )
                );
    }
}