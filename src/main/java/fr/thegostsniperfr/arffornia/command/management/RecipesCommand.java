package fr.thegostsniperfr.arffornia.command.management;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.recipe.pack.RecipePackUpdater;
import fr.thegostsniperfr.arffornia.util.Permissions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.server.permission.PermissionAPI;

/** {@code /arffornia recipes reload}: pulls the recipe datapack from the website and reloads it live. */
public final class RecipesCommand {

    private RecipesCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("recipes")
                .requires(source -> source.hasPermission(2) || (source.getPlayer() != null && PermissionAPI.getPermission(source.getPlayer(), Permissions.MANAGE_PROGRESSION)))
                .then(Commands.literal("reload").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    source.sendSuccess(() -> Component.translatable("commands.arffornia.recipes.reload.started").withStyle(ChatFormatting.YELLOW), true);
                    RecipePackUpdater.update(source.getServer())
                            .thenAcceptAsync(outcome -> report(source, outcome), source.getServer());
                    return 1;
                }));
    }

    static void report(CommandSourceStack source, RecipePackUpdater.Outcome outcome) {
        Arffornia.LOGGER.info("Recipe datapack update: {}", outcome);
        switch (outcome.status()) {
            case UPDATED -> source.sendSuccess(() -> Component.translatable("commands.arffornia.recipes.reload.updated",
                    outcome.recipeFiles(), outcome.reloadMillis(), outcome.longestTickGapMillis()).withStyle(ChatFormatting.GREEN), true);
            case UNCHANGED -> source.sendSuccess(() -> Component.translatable("commands.arffornia.recipes.reload.unchanged"), true);
            case BUSY -> source.sendFailure(Component.translatable("commands.arffornia.recipes.reload.busy"));
            case FAILED -> source.sendFailure(Component.translatable("commands.arffornia.recipes.reload.failed", outcome.detail()));
        }
    }
}
