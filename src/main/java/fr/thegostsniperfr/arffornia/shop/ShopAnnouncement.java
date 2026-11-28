package fr.thegostsniperfr.arffornia.shop;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/** Tells every online player about a shop purchase, with the item named in that player's own language. */
public final class ShopAnnouncement {
    /** Sends the announcement of a claimed reward; swapped in GameTests, which have no online players to read. */
    @FunctionalInterface
    public interface Announcer {
        void announce(String buyerName, PendingReward reward);
    }

    private ShopAnnouncement() {
    }

    public static Announcer broadcastOn(MinecraftServer server) {
        return (buyerName, reward) -> {
            for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
                recipient.sendSystemMessage(messageFor(buyerName, reward.label(), recipient.clientInformation().language()));
            }
        };
    }

    public static Component messageFor(String buyerName, Map<String, String> label, String clientLanguage) {
        return Component.translatable("chat.arffornia.shop.purchase", buyerName, ShopLabel.resolve(label, clientLanguage))
                .withStyle(ChatFormatting.GOLD);
    }
}
