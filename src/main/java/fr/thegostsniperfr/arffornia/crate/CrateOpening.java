package fr.thegostsniperfr.arffornia.crate;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.item.CrateKeyItem;
import fr.thegostsniperfr.arffornia.network.ClientboundCrateSequencePacket;
import fr.thegostsniperfr.arffornia.shop.InventoryRoom;
import fr.thegostsniperfr.arffornia.shop.LootStash;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server side of a crate: checks the key and its owner, rolls, checks that the whole roll fits, consumes one key, then
 * plays the sequence. The loot is kept in the {@link LootStash} (held) from the roll on, so a player who leaves, dies,
 * or a server that stops mid-sequence, loses nothing; it is given at the end, or on the next join or respawn.
 */
public final class CrateOpening {
    private static final int ROULETTE_LENGTH = 24;
    private static final int MAX_ROLL_ATTEMPTS = 5;

    private CrateOpening() {
    }

    static final class Running {
        final ServerPlayer opener;
        final UUID openerId;
        final CrateDefinition crate;
        final List<ItemStack> loot;
        final CrateRarity rarity;
        final long stashId;
        final CrateSequence.Show show;
        int tick;
        boolean revealed;

        Running(ServerPlayer opener, CrateDefinition crate, List<ItemStack> loot, CrateRarity rarity, long stashId, CrateSequence.Show show) {
            this.opener = opener;
            this.openerId = opener.getUUID();
            this.crate = crate;
            this.loot = loot;
            this.rarity = rarity;
            this.stashId = stashId;
            this.show = show;
        }
    }

    /** A right-click on the crate; {@code hand} is the hand holding a key, or {@code null} for an empty hand. */
    public static void use(ServerPlayer player, CrateBlockEntity crateEntity, @Nullable InteractionHand hand) {
        CrateDefinition crate = crateEntity.crate() == null ? null : CrateDefinitions.server(crateEntity.crate());
        if (crate == null) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.crate.not_configured").withStyle(ChatFormatting.RED));
            return;
        }
        Running running = crateEntity.running;
        if (running != null) {
            if (running.opener == player && player.isShiftKeyDown()) {
                skip(crateEntity);
            } else if (running.opener != player) {
                player.sendSystemMessage(Component.translatable("chat.arffornia.crate.busy").withStyle(ChatFormatting.YELLOW));
            }
            return;
        }
        if (hand == null || player.isShiftKeyDown()) {
            CratePreview.open(player, crate);
            return;
        }
        open(player, crateEntity, crate, hand);
    }

    private static void open(ServerPlayer player, CrateBlockEntity crateEntity, CrateDefinition crate, InteractionHand hand) {
        ItemStack keyStack = player.getItemInHand(hand);
        var key = CrateKeyItem.keyOf(keyStack);
        if (key == null) {
            CratePreview.open(player, crate);
            return;
        }
        if (!key.crate().equals(crate.id())) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.crate.wrong_key", crate.name()).withStyle(ChatFormatting.RED));
            return;
        }
        if (!key.isOwnedBy(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.crate.not_your_key", key.ownerName()).withStyle(ChatFormatting.RED));
            return;
        }

        ServerLevel level = player.serverLevel();
        List<ItemStack> loot = roll(player, crateEntity, crate);
        if (loot.isEmpty()) {
            Arffornia.LOGGER.error("Crate {} at {} rolled nothing {} times; check its loot table {}", crate.id(), crateEntity.getBlockPos(),
                    MAX_ROLL_ATTEMPTS, crate.lootTable());
            player.sendSystemMessage(Component.translatable("chat.arffornia.crate.broken").withStyle(ChatFormatting.RED));
            return;
        }
        List<ItemStack> slots = InventoryRoom.snapshot(player.getInventory());
        if (hand == InteractionHand.MAIN_HAND) {
            slots.get(player.getInventory().selected).shrink(1);
        }
        int missing = InventoryRoom.insert(slots, loot);
        if (missing > 0) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.crate.no_room", missing).withStyle(ChatFormatting.RED));
            return;
        }

        keyStack.shrink(1);
        long stashId = LootStash.get(level.getServer()).add(player.getUUID(), loot, true);
        CrateRarity rarity = crate.rarityOf(loot);
        CrateSequence.Show show = new CrateSequence.Show(player.getId(), CrateKeyItem.stack(key.crate(), key.owner(), key.ownerName(), 1),
                roulette(level.getServer(), crate, loot.getFirst(), player.getRandom()), List.copyOf(loot), crate.rarity(), rarity);
        crateEntity.running = new Running(player, crate, loot, rarity, stashId, show);
        Arffornia.LOGGER.info("{} opened crate {} at {}: {} ({})", player.getName().getString(), crate.id(), crateEntity.getBlockPos(), loot,
                rarity.getSerializedName());
        broadcast(crateEntity, show, 0);
    }

    private static List<ItemStack> roll(ServerPlayer player, CrateBlockEntity crateEntity, CrateDefinition crate) {
        LootTable table = player.server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, crate.lootTable()));
        LootParams params = new LootParams.Builder(player.serverLevel())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.ORIGIN, crateEntity.getBlockPos().getCenter())
                .withLuck(player.getLuck())
                .create(LootContextParamSets.GIFT);
        for (int attempt = 0; attempt < MAX_ROLL_ATTEMPTS; attempt++) {
            List<ItemStack> loot = table.getRandomItems(params).stream().filter(stack -> !stack.isEmpty()).toList();
            if (!loot.isEmpty()) {
                return loot;
            }
        }
        return List.of();
    }

    /** Items drawn with the loot table's odds, ending on the result. */
    private static List<ItemStack> roulette(MinecraftServer server, CrateDefinition crate, ItemStack result, RandomSource random) {
        List<CrateOdds.Odds> odds = odds(server, crate.lootTable());
        List<ItemStack> reel = new ArrayList<>();
        double total = odds.stream().mapToDouble(CrateOdds.Odds::chance).sum();
        for (int i = 0; i < ROULETTE_LENGTH && total > 0; i++) {
            double pick = random.nextDouble() * total;
            for (CrateOdds.Odds o : odds) {
                pick -= o.chance();
                if (pick <= 0) {
                    BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(o.item()))
                            .ifPresent(item -> reel.add(new ItemStack(item, Math.max(1, Math.min(o.maxCount(), item.getDefaultMaxStackSize())))));
                    break;
                }
            }
        }
        reel.add(result.copy());
        return reel;
    }

    /** The odds of a loot table, read from its JSON in the server's data packs. */
    public static List<CrateOdds.Odds> odds(MinecraftServer server, ResourceLocation lootTable) {
        ResourceLocation file = lootTable.withPath(path -> "loot_table/" + path + ".json");
        Optional<Resource> resource = server.getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            Arffornia.LOGGER.error("Crate loot table {} not found", file);
            return List.of();
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            return CrateOdds.compute(json.getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            Arffornia.LOGGER.error("Cannot read crate loot table {}: {}", file, e.getMessage());
            return List.of();
        }
    }

    private static void skip(CrateBlockEntity crateEntity) {
        Running running = crateEntity.running;
        if (running == null || running.tick >= CrateSequence.REVEAL) {
            return;
        }
        running.tick = CrateSequence.REVEAL;
        broadcast(crateEntity, running.show, CrateSequence.REVEAL);
        reveal(crateEntity, running);
    }

    private static void broadcast(CrateBlockEntity crateEntity, CrateSequence.Show show, int tick) {
        if (crateEntity.getLevel() instanceof ServerLevel level) {
            PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(crateEntity.getBlockPos()),
                    new ClientboundCrateSequencePacket(crateEntity.getBlockPos(), show, tick));
        }
    }

    static void tick(CrateBlockEntity crateEntity) {
        Running running = crateEntity.running;
        if (running == null) {
            return;
        }
        if (!isHere(running.opener)) {
            finish(crateEntity);
            return;
        }
        running.tick++;
        if (running.tick == CrateSequence.REVEAL) {
            reveal(crateEntity, running);
        }
        if (running.tick >= CrateSequence.END) {
            finish(crateEntity);
        }
    }

    private static boolean isHere(ServerPlayer player) {
        return player.isAlive() && !player.hasDisconnected() && !player.isRemoved();
    }

    private static void reveal(CrateBlockEntity crateEntity, Running running) {
        if (running.revealed) {
            return;
        }
        running.revealed = true;
        ItemStack shown = running.show.shown();
        if (running.rarity.atLeast(CrateRarity.RARE)) {
            String key = running.rarity == CrateRarity.LEGENDARY ? "chat.arffornia.crate.announce.legendary" : "chat.arffornia.crate.announce.rare";
            Component line = Component.translatable(key, running.opener.getDisplayName(), stackName(shown), running.crate.name())
                    .withStyle(running.rarity.chatColour());
            running.opener.server.getPlayerList().broadcastSystemMessage(line, false);
        }
        if (running.rarity == CrateRarity.LEGENDARY) {
            running.opener.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 15));
            running.opener.connection.send(new ClientboundSetTitleTextPacket(
                    Component.translatable("title.arffornia.crate.legendary").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            running.opener.connection.send(new ClientboundSetSubtitleTextPacket(stackName(shown)));
        }
    }

    /** Ends the opening now: gives the loot to the opener if they are here, else it waits in the stash. */
    static void finish(CrateBlockEntity crateEntity) {
        Running running = crateEntity.running;
        if (running == null) {
            return;
        }
        crateEntity.running = null;
        MinecraftServer server = running.opener.server;
        LootStash stash = LootStash.get(server);
        stash.release(running.stashId);
        ServerPlayer target = isHere(running.opener) ? running.opener : server.getPlayerList().getPlayer(running.openerId);
        if (target == null || !isHere(target)) {
            Arffornia.LOGGER.info("Crate {} loot for {} waits in the stash: the player left or died", running.crate.id(), running.openerId);
            return;
        }
        int missing = stash.deliver(target);
        target.sendSystemMessage(Component.translatable("chat.arffornia.crate.won", running.crate.name(),
                ComponentUtils.formatList(running.loot, ComponentUtils.DEFAULT_SEPARATOR, CrateOpening::stackName)).withStyle(ChatFormatting.GREEN));
        if (missing > 0) {
            CrateEvents.giveStashedLoot(target);
        }
    }

    public static Component stackName(ItemStack stack) {
        return Component.translatable("chat.arffornia.crate.stack", stack.getCount(), stack.getDisplayName());
    }

    /** For tests: the crate's current opening tick, or -1. */
    public static int tickOf(CrateBlockEntity crateEntity) {
        return crateEntity.running == null ? -1 : crateEntity.running.tick;
    }

}
