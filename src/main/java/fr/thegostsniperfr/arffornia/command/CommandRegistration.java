package fr.thegostsniperfr.arffornia.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.thegostsniperfr.arffornia.shop.RewardHandler;
import fr.thegostsniperfr.arffornia.util.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CommandRegistration {
    private final RewardHandler rewardHandler;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final int COOLDOWN_SECONDS = 3;

    public CommandRegistration(RewardHandler rewardHandler) {
        this.rewardHandler = rewardHandler;
    }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("claim_reward")
                // `/claim_reward`: for default players
                .executes(context -> claimForSelf(context.getSource()))

                // `/claim_reward targets`: for admin players
                .then(Commands.argument("targets", EntityArgument.players())
                        .requires(source -> {
                            ServerPlayer player = source.getPlayer();
                            if (player != null)
                            {
                                return PermissionAPI.getPermission(player, Permissions.CLAIM_REWARD_OTHERS);
                            }
                            else {
                                // Cmd executed by another source than player (cmd block, console, ..)
                                return source.hasPermission(2);
                            }
                        })
                        .executes(context -> claimForOthers(
                                context.getSource(),
                                EntityArgument.getPlayers(context, "targets")
                        ))
                )
        );
    }

    private int claimForSelf(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        UUID playerUUID = player.getUUID();

        // Cooldown Check
        long currentTime = System.currentTimeMillis();
        long lastUseTime = cooldowns.getOrDefault(playerUUID, 0L);

        if (currentTime < lastUseTime) {
            long remaining = (lastUseTime - currentTime) / 1000;
            source.sendFailure(Component.literal("§cPlease wait " + remaining + " more seconds before using this command again."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§aChecking for pending rewards..."), false);
        rewardHandler.claimRewardsForPlayer(player);

        cooldowns.put(playerUUID, currentTime + (COOLDOWN_SECONDS * 1000));
        return 1;
    }

    private int claimForOthers(CommandSourceStack source, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            rewardHandler.claimRewardsForPlayer(player);
            source.sendSuccess(() -> Component.literal("§aTriggered reward claim for " + player.getName().getString()), true);
        }

        return targets.size();
    }
}