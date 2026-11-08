package fr.thegostsniperfr.arffornia.graph;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;

/** {@code /arffornia dump recipes [dir]}: writes the recipe graph of the running server (admins only). */
public final class DumpCommand {

    private static final String DEFAULT_DIR = "arffornia-dump";

    private DumpCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("dump")
                .requires(source -> source.hasPermission(4))
                .then(Commands.literal("recipes")
                        .executes(context -> run(context.getSource(), DEFAULT_DIR))
                        .then(Commands.argument("dir", StringArgumentType.string())
                                .executes(context -> run(context.getSource(), StringArgumentType.getString(context, "dir")))));
    }

    private static int run(CommandSourceStack source, String dir) {
        Path target = source.getServer().getServerDirectory().resolve(dir).normalize();
        if (!target.startsWith(source.getServer().getServerDirectory().normalize())) {
            source.sendFailure(Component.literal("The dump folder must stay inside the server folder."));
            return 0;
        }
        try {
            RecipeGraphDump.Result result = RecipeGraphDump.write(source.getServer(), target);
            source.sendSuccess(() -> Component.literal(result.recipes() + " recipes, " + result.items() + " items, "
                    + result.tags() + " tags written to " + target), true);
            return result.recipes();
        } catch (IOException e) {
            source.sendFailure(Component.literal("Dump failed: " + e.getMessage()));
            return 0;
        }
    }
}
