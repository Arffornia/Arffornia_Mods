package fr.thegostsniperfr.arffornia.command.management;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.thegostsniperfr.arffornia.block.entity.LootBoxBlockEntity;
import fr.thegostsniperfr.arffornia.lootbox.LootBoxManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class SetBoxCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("setbox")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("box_id", StringArgumentType.string())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(LootBoxManager.getAllBoxIds(), builder))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            String boxId = StringArgumentType.getString(context, "box_id");

                            HitResult hit = player.pick(5.0D, 0.0F, false);
                            if (hit.getType() == HitResult.Type.BLOCK) {
                                BlockPos pos = ((BlockHitResult) hit).getBlockPos();
                                Level level = player.level();

                                if (level.getBlockEntity(pos) instanceof LootBoxBlockEntity be) {
                                    be.setBoxId(boxId);
                                    context.getSource().sendSuccess(() -> Component.literal("§aLootBox successfully set to ID: §e" + boxId), false);
                                    return 1;
                                }
                            }

                            context.getSource().sendFailure(Component.literal("§cYou must be looking at a LootBox block!"));
                            return 0;
                        })
                );
    }
}