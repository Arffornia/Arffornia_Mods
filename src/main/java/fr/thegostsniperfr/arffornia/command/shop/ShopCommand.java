package fr.thegostsniperfr.arffornia.command.shop;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.thegostsniperfr.arffornia.shop.RewardHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Commands related to the online shop.
 */
public class ShopCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register(RewardHandler rewardHandler) {
        return Commands.literal("shop")
                .then(ClaimRewardCommand.register(rewardHandler));
    }
}