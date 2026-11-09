package fr.thegostsniperfr.arffornia.shop;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;

import java.util.UUID;

/** Touches LuckPerms classes; only reached when the luckperms mod is loaded. */
final class LuckPermsRewards {
    private LuckPermsRewards() {
    }

    static boolean groupExists(String group) {
        return LuckPermsProvider.get().getGroupManager().getGroup(group) != null;
    }

    static void setPermission(UUID player, String node, boolean value, long rewardId) {
        LuckPerms luckPerms = LuckPermsProvider.get();
        luckPerms.getUserManager()
                .modifyUser(player, user -> user.data().add(Node.builder(node).value(value).build()))
                .exceptionally(ex -> {
                    Arffornia.LOGGER.error("Reward {}: could not set permission {} for {}", rewardId, node, player, ex);
                    return null;
                });
    }

    static void addGroup(UUID player, String group, long rewardId) {
        LuckPerms luckPerms = LuckPermsProvider.get();
        luckPerms.getUserManager()
                .modifyUser(player, user -> user.data().add(InheritanceNode.builder(group).build()))
                .exceptionally(ex -> {
                    Arffornia.LOGGER.error("Reward {}: could not add group {} to {}", rewardId, group, player, ex);
                    return null;
                });
    }
}
