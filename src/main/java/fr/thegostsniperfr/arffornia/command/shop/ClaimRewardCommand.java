package fr.thegostsniperfr.arffornia.command.shop;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
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

public class ClaimRewardCommand {
    private static final Map<UUID, Long> COOLDOWNS = new ConcurrentHashMap<>();
    private static final int COOLDOWN_SECONDS = 3;

    public static LiteralArgumentBuilder<CommandSourceStack> register(RewardHandler rewardHandler) {
        return Commands.literal("claim_reward")
                .executes(context -> claimForSelf(context, rewardHandler))
                .then(Commands.argument("targets", EntityArgument.players())
                        .requires(source -> source.getPlayer() != null && PermissionAPI.getPermission(source.getPlayer(), Permissions.CLAIM_REWARD_OTHERS))
                        .executes(context -> claimForOthers(context, rewardHandler))
                );
    }

    private static int claimForSelf(CommandContext<CommandSourceStack> context, RewardHandler rewardHandler) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (isOnCooldown(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("§cPlease wait before using this command again."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("§aChecking for pending rewards..."), false);
        rewardHandler.claimRewardsForPlayer(player);
        setCooldown(player.getUUID());
        return 1;
    }

    private static int claimForOthers(CommandContext<CommandSourceStack> context, RewardHandler rewardHandler) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        for (ServerPlayer player : targets) {
            rewardHandler.claimRewardsForPlayer(player);
            context.getSource().sendSuccess(() -> Component.literal("§aTriggered reward claim for " + player.getName().getString()), true);
        }
        return targets.size();
    }

    private static boolean isOnCooldown(UUID uuid) {
        return COOLDOWNS.getOrDefault(uuid, 0L) > System.currentTimeMillis();
    }

    private static void setCooldown(UUID uuid) {
        COOLDOWNS.put(uuid, System.currentTimeMillis() + (COOLDOWN_SECONDS * 1000));
    }
}