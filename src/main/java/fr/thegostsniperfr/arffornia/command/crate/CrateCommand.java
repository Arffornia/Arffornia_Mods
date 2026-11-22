package fr.thegostsniperfr.arffornia.command.crate;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.crate.CrateBlock;
import fr.thegostsniperfr.arffornia.crate.CrateBlockEntity;
import fr.thegostsniperfr.arffornia.crate.CrateDefinition;
import fr.thegostsniperfr.arffornia.crate.CrateDefinitions;
import fr.thegostsniperfr.arffornia.item.CrateKeyItem;
import fr.thegostsniperfr.arffornia.shop.InventoryRoom;
import fr.thegostsniperfr.arffornia.shop.LootStash;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;

/** {@code /arffornia crate place|set|give_key}: admin tools to stand crates at spawn and test them (permission level 2). */
public final class CrateCommand {
    private static final DynamicCommandExceptionType UNKNOWN_CRATE =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.arffornia.crate.unknown", String.valueOf(id)));
    private static final SuggestionProvider<CommandSourceStack> CRATES = (context, builder) ->
            SharedSuggestionProvider.suggestResource(CrateDefinitions.server().stream().map(CrateDefinition::id), builder);

    private CrateCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("crate")
                .requires(source -> source.hasPermission(CrateBlock.ADMIN_LEVEL))
                .then(Commands.literal("place")
                        .then(Commands.argument("crate", ResourceLocationArgument.id()).suggests(CRATES)
                                .executes(context -> place(context, null))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> place(context, BlockPosArgument.getLoadedBlockPos(context, "pos"))))))
                .then(Commands.literal("set")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("crate", ResourceLocationArgument.id()).suggests(CRATES)
                                        .executes(CrateCommand::set))))
                .then(Commands.literal("give_key")
                        .then(Commands.argument("crate", ResourceLocationArgument.id()).suggests(CRATES)
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(context -> giveKey(context, 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> giveKey(context, IntegerArgumentType.getInteger(context, "count")))))));
    }

    private static CrateDefinition crate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(context, "crate");
        CrateDefinition crate = CrateDefinitions.server(id);
        if (crate == null) {
            throw UNKNOWN_CRATE.create(id);
        }
        return crate;
    }

    /** Without a position, the crate goes on the block in front of the admin, facing them. */
    private static int place(CommandContext<CommandSourceStack> context, BlockPos at) throws CommandSyntaxException {
        CrateDefinition crate = crate(context);
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Direction facing = source.getEntity() != null ? source.getEntity().getDirection().getOpposite() : Direction.NORTH;
        BlockPos pos = at != null ? at : BlockPos.containing(source.getPosition()).relative(facing.getOpposite());
        level.setBlockAndUpdate(pos, ModBlocks.CRATE.get().defaultBlockState().setValue(CrateBlock.FACING, facing));
        if (!(level.getBlockEntity(pos) instanceof CrateBlockEntity entity)) {
            source.sendFailure(Component.translatable("commands.arffornia.crate.place_failed"));
            return 0;
        }
        entity.setCrate(crate.id());
        source.sendSuccess(() -> Component.translatable("commands.arffornia.crate.placed", crate.name(), pos.toShortString()).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CrateDefinition crate = crate(context);
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        if (!(context.getSource().getLevel().getBlockEntity(pos) instanceof CrateBlockEntity entity)) {
            context.getSource().sendFailure(Component.translatable("commands.arffornia.crate.not_a_crate", pos.toShortString()));
            return 0;
        }
        entity.setCrate(crate.id());
        context.getSource().sendSuccess(() -> Component.translatable("commands.arffornia.crate.placed", crate.name(), pos.toShortString()).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** Test keys bound to each target; what does not fit waits in the stash, nothing is dropped. */
    private static int giveKey(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        CrateDefinition crate = crate(context);
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        for (ServerPlayer target : targets) {
            List<ItemStack> keys = CrateKeyItem.stacks(crate.id(), target, count);
            List<ItemStack> leftovers = InventoryRoom.give(target.getInventory(), keys);
            if (!leftovers.isEmpty()) {
                LootStash.get(target.server).add(target.getUUID(), leftovers, false);
            }
            context.getSource().sendSuccess(() -> Component.translatable("commands.arffornia.crate.keys_given", count, crate.name(), target.getDisplayName())
                    .withStyle(ChatFormatting.GREEN), true);
        }
        return targets.size();
    }
}
