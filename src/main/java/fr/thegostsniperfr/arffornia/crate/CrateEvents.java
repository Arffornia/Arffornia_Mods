package fr.thegostsniperfr.arffornia.crate;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.network.ClientboundCrateDefinitionsPacket;
import fr.thegostsniperfr.arffornia.shop.LootStash;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Arffornia.MODID)
public final class CrateEvents {
    private CrateEvents() {
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CrateLoader());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ClientboundCrateDefinitionsPacket packet = new ClientboundCrateDefinitionsPacket(CrateDefinitions.server().stream().map(CrateDefinition::client).toList());
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, packet));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            giveStashedLoot(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            giveStashedLoot(player);
        }
    }

    /** Gives what waits in the {@link LootStash} and says how much room the rest needs. */
    public static void giveStashedLoot(ServerPlayer player) {
        LootStash stash = LootStash.get(player.server);
        if (!stash.has(player.getUUID())) {
            return;
        }
        int missing = stash.deliver(player);
        if (missing > 0) {
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.free_slots", missing,
                    Arffornia.CLAIM_REWARD_COMMAND.copy().withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.RED));
        }
    }
}
