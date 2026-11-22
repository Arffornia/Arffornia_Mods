package fr.thegostsniperfr.arffornia.crate;

import fr.thegostsniperfr.arffornia.item.CrateKeyItem;
import fr.thegostsniperfr.arffornia.network.ClientboundCratePreviewPacket;
import fr.thegostsniperfr.arffornia.shop.RewardEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sends a crate's preview: its items with their odds, and the streak calendar for the streak crate. */
public final class CratePreview {
    private static final long COOLDOWN_TICKS = 20;
    private static final Map<UUID, Long> LAST_OPENED = new ConcurrentHashMap<>();

    private CratePreview() {
    }

    public static void open(ServerPlayer player, CrateDefinition crate) {
        long now = player.server.getTickCount();
        Long last = LAST_OPENED.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) {
            return;
        }
        LAST_OPENED.put(player.getUUID(), now);

        List<ClientboundCratePreviewPacket.Entry> entries = entries(player, crate);
        if (!crate.hasStreakCalendar()) {
            PacketDistributor.sendToPlayer(player, new ClientboundCratePreviewPacket(crate.id(), crate.name(), crate.rarity(), entries, false, Optional.empty()));
            return;
        }
        UUID uuid = player.getUUID();
        StreakCalendarClient.fetch(uuid).thenAcceptAsync(calendar -> {
            ServerPlayer online = player.server.getPlayerList().getPlayer(uuid);
            if (online != null) {
                PacketDistributor.sendToPlayer(online, new ClientboundCratePreviewPacket(crate.id(), crate.name(), crate.rarity(), entries, true,
                        calendar.map(c -> calendar(online, c))));
            }
        }, player.server);
    }

    public static List<ClientboundCratePreviewPacket.Entry> entries(ServerPlayer player, CrateDefinition crate) {
        List<ClientboundCratePreviewPacket.Entry> entries = new ArrayList<>();
        for (CrateOdds.Odds odds : CrateOpening.odds(player.server, crate.lootTable())) {
            ResourceLocation id = ResourceLocation.tryParse(odds.item());
            if (id == null) {
                continue;
            }
            BuiltInRegistries.ITEM.getOptional(id).filter(item -> item != Items.AIR).ifPresent(item -> {
                ItemStack stack = new ItemStack(item);
                entries.add(new ClientboundCratePreviewPacket.Entry(stack, (float) odds.chance(), odds.minCount(), odds.maxCount(), crate.rarityOf(stack)));
            });
        }
        return entries;
    }

    private static ClientboundCratePreviewPacket.Calendar calendar(ServerPlayer player, StreakCalendar calendar) {
        List<ClientboundCratePreviewPacket.Day> week = calendar.week().stream()
                .map(day -> new ClientboundCratePreviewPacket.Day(day.day(), icons(player, day.contents())))
                .toList();
        return new ClientboundCratePreviewPacket.Calendar(calendar.dayStreak(), calendar.bestStreak(), calendar.today(), week);
    }

    /** What a day gives, as icons: keys of the named crate, items, a chest for a lucky box. */
    private static List<ItemStack> icons(ServerPlayer player, List<RewardEntry> contents) {
        List<ItemStack> icons = new ArrayList<>();
        for (RewardEntry entry : contents) {
            switch (entry) {
                case RewardEntry.Key key -> {
                    ResourceLocation crate = ResourceLocation.tryParse(key.crate());
                    if (crate != null) {
                        icons.add(CrateKeyItem.stack(crate, player.getUUID(), player.getGameProfile().getName(), Math.min(64, key.count())));
                    }
                }
                case RewardEntry.Item item -> Optional.ofNullable(ResourceLocation.tryParse(item.item()))
                        .flatMap(BuiltInRegistries.ITEM::getOptional)
                        .filter(i -> i != Items.AIR)
                        .ifPresent(i -> icons.add(new ItemStack(i, Math.min(i.getDefaultMaxStackSize(), item.count()))));
                case RewardEntry.LuckyBox box -> icons.add(new ItemStack(Items.CHEST, box.count()));
                default -> {
                }
            }
        }
        return icons;
    }
}
