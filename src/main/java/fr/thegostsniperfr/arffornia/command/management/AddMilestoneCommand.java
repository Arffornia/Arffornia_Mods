package fr.thegostsniperfr.arffornia.command.management;

import net.minecraft.ChatFormatting;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.thegostsniperfr.arffornia.api.backend.ProgressionBackends;
import fr.thegostsniperfr.arffornia.progression.MilestoneResult;
import fr.thegostsniperfr.arffornia.progression.ProgressionEventSender;
import fr.thegostsniperfr.arffornia.progression.ProgressionModel;
import fr.thegostsniperfr.arffornia.progression.ProgressionModels;
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
                                    CommandSourceStack source = context.getSource();

                                    source.sendSystemMessage(Component.translatable("commands.arffornia.add.adding", milestoneId, target.getDisplayName()));

                                    MinecraftServer server = target.server;
                                    ProgressionBackends.get().fetchMilestoneDetails(milestoneId, null)
                                            .thenAcceptAsync(details -> {
                                                if (details == null || details.key() == null) {
                                                    source.sendSystemMessage(Component.translatable("commands.arffornia.add.not_found", milestoneId)
                                                            .withStyle(ChatFormatting.RED));
                                                    return;
                                                }
                                                long progressionId = Progressions.activeProgressionOf(target);
                                                MilestoneResult result = ProgressionEventSender.milestoneCompleted(server, progressionId, details.key(), target.getUUID());
                                                if (result.isCompleted()) {
                                                    Progressions.recordCompleted(server, progressionId, details.id());
                                                }
                                                ProgressionModel model = ProgressionModels.current(server);
                                                String language = source.getPlayer() != null ? source.getPlayer().clientInformation().language() : "en_us";
                                                source.sendSystemMessage(result.message(details.name(), key -> model.titleOf(key, language)));
                                            }, server);
                                    return 1;
                                })
                        )
                );
    }
}
