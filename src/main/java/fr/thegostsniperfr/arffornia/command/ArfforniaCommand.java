package fr.thegostsniperfr.arffornia.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.thegostsniperfr.arffornia.command.management.ProgressionCommand;
import fr.thegostsniperfr.arffornia.command.shop.ShopCommand;
import fr.thegostsniperfr.arffornia.shop.RewardHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Main command registry for the Arffornia mod.
 */
public class ArfforniaCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, RewardHandler rewardHandler) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("arffornia")
                .then(ProgressionCommand.register())
                .then(ShopCommand.register(rewardHandler));

        dispatcher.register(root);
    }
}