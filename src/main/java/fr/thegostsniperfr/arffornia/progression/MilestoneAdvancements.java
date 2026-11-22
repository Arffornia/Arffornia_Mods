package fr.thegostsniperfr.arffornia.progression;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
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
 * When a player earns one, the milestone is done for the player's active progression.
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
        milestoneKeyOf(event.getAdvancement().id()).ifPresent(key -> ProgressionEventSender.milestoneCompleted(player, key));
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
            AdvancementProgress progress = advancements.getOrStartProgress(holder);
            if (progress.isDone()) {
                List<String> criteria = new ArrayList<>();
                progress.getCompletedCriteria().forEach(criteria::add);
                criteria.forEach(criterion -> advancements.revoke(holder, criterion));
            }
        }
    }
}
