package fr.thegostsniperfr.arffornia.shop;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Items a player won but has not received yet, saved with the world: what did not fit when a claimed reward was
 * delivered, and the loot of a crate while its sequence plays. They are given when the player joins, respawns, ends a
 * crate sequence or runs the claim command, as far as they fit; the rest waits. Nothing is ever dropped. A held entry
 * (a sequence still playing) is not given until it is released; holds are not saved, so a restart releases them.
 * Server thread only.
 */
public final class LootStash extends SavedData {
    private static final String NAME = "arffornia_loot_stash";

    private record Entry(long id, UUID player, List<ItemStack> stacks) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Set<Long> held = new HashSet<>();
    private long nextId = 1;

    public static LootStash get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(LootStash::new, LootStash::load, null), NAME);
    }

    /** @return the entry id, to {@link #release} it */
    public long add(UUID player, List<ItemStack> stacks, boolean hold) {
        long id = nextId++;
        entries.add(new Entry(id, player, new ArrayList<>(stacks.stream().filter(s -> !s.isEmpty()).map(ItemStack::copy).toList())));
        if (hold) {
            held.add(id);
        }
        setDirty();
        return id;
    }

    public void release(long id) {
        held.remove(id);
    }

    public boolean has(UUID player) {
        return entries.stream().anyMatch(e -> e.player().equals(player));
    }

    /** Stacks waiting for a player and not held. */
    public List<ItemStack> waiting(UUID player) {
        return entries.stream().filter(e -> e.player().equals(player) && !held.contains(e.id()))
                .flatMap(e -> e.stacks().stream()).map(ItemStack::copy).toList();
    }

    /**
     * Gives the player's released stacks that fit.
     *
     * @return the slots still missing for what stays, 0 when the stash is empty for them
     */
    public int deliver(ServerPlayer player) {
        boolean changed = false;
        int missing = 0;
        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (!entry.player().equals(player.getUUID()) || held.contains(entry.id())) {
                continue;
            }
            int before = count(entry.stacks());
            List<ItemStack> leftovers = InventoryRoom.give(player.getInventory(), entry.stacks());
            if (count(leftovers) != before) {
                changed = true;
                Arffornia.LOGGER.info("Gave stashed loot to {}: {}, still waiting: {}", player.getName().getString(), entry.stacks(), leftovers);
            }
            entry.stacks().clear();
            entry.stacks().addAll(leftovers);
            if (leftovers.isEmpty()) {
                it.remove();
            } else {
                missing += InventoryRoom.slotsFor(leftovers);
            }
        }
        if (changed) {
            setDirty();
        }
        return missing;
    }

    private static int count(List<ItemStack> stacks) {
        return stacks.stream().mapToInt(ItemStack::getCount).sum();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag e = new CompoundTag();
            e.putLong("id", entry.id());
            e.putUUID("player", entry.player());
            ListTag stacks = new ListTag();
            entry.stacks().forEach(stack -> stacks.add(stack.save(registries)));
            e.put("stacks", stacks);
            list.add(e);
        }
        tag.put("entries", list);
        tag.putLong("next_id", nextId);
        return tag;
    }

    private static LootStash load(CompoundTag tag, HolderLookup.Provider registries) {
        LootStash stash = new LootStash();
        stash.nextId = Math.max(1, tag.getLong("next_id"));
        for (Tag t : tag.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            List<ItemStack> stacks = new ArrayList<>();
            for (Tag s : e.getList("stacks", Tag.TAG_COMPOUND)) {
                ItemStack.parse(registries, s).ifPresentOrElse(stacks::add,
                        () -> Arffornia.LOGGER.error("Skipped an unreadable stashed stack for {}: {}", e.getUUID("player"), s));
            }
            stash.entries.add(new Entry(e.getLong("id"), e.getUUID("player"), stacks));
        }
        return stash;
    }
}
