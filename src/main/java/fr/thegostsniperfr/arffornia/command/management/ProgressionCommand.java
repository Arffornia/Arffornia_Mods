package fr.thegostsniperfr.arffornia.command.management;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.thegostsniperfr.arffornia.util.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.server.permission.PermissionAPI;

public class ProgressionCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("progression")
                .requires(source -> source.hasPermission(2) || (source.getPlayer() != null && PermissionAPI.getPermission(source.getPlayer(), Permissions.MANAGE_PROGRESSION)))
                .then(AddMilestoneCommand.register())
                .then(RemoveMilestoneCommand.register())
                .then(ListMilestonesCommand.register())
                .then(AddUnlockCommand.register())
                .then(SetRequirementsCommand.register());
    }
}