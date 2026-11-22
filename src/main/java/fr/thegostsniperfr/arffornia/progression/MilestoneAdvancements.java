package fr.thegostsniperfr.arffornia.progression;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Detects milestones through hidden advancements (spec 017): the datapack built from a snapshot holds one advancement
 * per milestone, {@code arffornia:milestone/<key>}, without a display so players never see it, whose criteria are the
 * milestone's validation ({@code minecraft:inventory_changed} for obtain, {@code arffornia:item_crafted} for craft).
 * When a player earns one and the milestone is available (rule 3: its stage is open and what it requires is done),
 * it is done for the player's active progression. Otherwise the advancement is revoked at once, nothing is recorded or
 * sent, and an item the player keeps counts once the milestone becomes available. Detection runs on the server only.
 * <p>
 * A player's milestone advancements mirror their active progression: one the progression has not done is revoked, so it
 * can fire again after a team change or a moderator revoke. Advancements without a display never post
 * {@code AdvancementEarnEvent}, hence the progress event.
 */
@EventBusSubscriber(modid = Arffornia.MODID)
public final class MilestoneAdvancements {
    private static final String PATH_PREFIX = "milestone/";

    private MilestoneAdvancements() {
    }

    public static ResourceLocation advancementId(String milestoneKey) {
        return ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, PATH_PREFIX + milestoneKey);
    }

    static Optional<String> milestoneKeyOf(ResourceLocation advancement) {
        if (!advancement.getNamespace().equals(Arffornia.MODID) || !advancement.getPath().startsWith(PATH_PREFIX)) {
            return Optional.empty();
        }
        String key = advancement.getPath().substring(PATH_PREFIX.length());
        return Stages.MILESTONE_KEY.matcher(key).matches() ? Optional.of(key) : Optional.empty();
    }

    @SubscribeEvent
    public static void onAdvancementProgress(AdvancementEvent.AdvancementProgressEvent event) {
        if (event.getProgressType() != AdvancementEvent.AdvancementProgressEvent.ProgressType.GRANT
                || !event.getAdvancementProgress().isDone()
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        AdvancementHolder holder = event.getAdvancement();
        milestoneKeyOf(holder.id()).ifPresent(key -> {
            if (!ProgressionEventSender.milestoneCompleted(player, key)) {
                // Not counted (not available yet, no progression...): undo it so the validation can fire again later.
                revokeAll(player.getAdvancements(), holder);
            }
        });
    }

    /**
     * Fires the inventory trigger once per stack the player holds, on the next tick, so an item kept from before a
     * milestone became available completes it without another pickup. Craft milestones need a new craft.
     */
    public static void recheckInventoryLater(ServerPlayer player) {
        player.server.tell(new TickTask(player.server.getTickCount(), () -> {
            if (player.isRemoved()) {
                return;
            }
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!stack.isEmpty()) {
                    CriteriaTriggers.INVENTORY_CHANGED.trigger(player, inventory, stack);
                }
            }
        }));
    }

    private static void revokeAll(PlayerAdvancements advancements, AdvancementHolder holder) {
        List<String> criteria = new ArrayList<>();
        advancements.getOrStartProgress(holder).getCompletedCriteria().forEach(criteria::add);
        criteria.forEach(criterion -> advancements.revoke(holder, criterion));
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ModCriteriaTriggers.ITEM_CRAFTED.get().trigger(player, event.getCrafting());
        }
    }

    /** Revokes the milestone advancements {@code player} holds that their progression has not done. Server thread only. */
    static void reconcile(ServerPlayer player, ProgressionSnapshot snapshot) {
        if (!snapshot.isKeyedStateKnown() || snapshot.retired()) {
            return;
        }
        PlayerAdvancements advancements = player.getAdvancements();
        for (AdvancementHolder holder : player.server.getAdvancements().getAllAdvancements()) {
            Optional<String> key = milestoneKeyOf(holder.id());
            if (key.isEmpty() || snapshot.isCompleted(key.get())) {
                continue;
            }
            if (advancements.getOrStartProgress(holder).isDone()) {
                revokeAll(advancements, holder);
            }
        }
    }
}
