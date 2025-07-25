package fr.thegostsniperfr.arffornia.util;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

public class Permissions {

    public static final PermissionNode<Boolean> CLAIM_REWARD_OTHERS = new PermissionNode<>(
            ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "command.claim_reward.others"),
            PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player.hasPermissions(2)
    );

    @SubscribeEvent
    public static void onGatherPermissions(PermissionGatherEvent.Nodes event) {
        event.addNodes(CLAIM_REWARD_OTHERS);
    }
}