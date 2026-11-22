package fr.thegostsniperfr.arffornia.gametest;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.crate.CrateKey;
import fr.thegostsniperfr.arffornia.item.CrateKeyItem;
import fr.thegostsniperfr.arffornia.item.ModItems;
import fr.thegostsniperfr.arffornia.shop.InventoryRoom;
import fr.thegostsniperfr.arffornia.shop.LootStash;
import fr.thegostsniperfr.arffornia.shop.PendingReward;
import fr.thegostsniperfr.arffornia.shop.RewardDelivery;
import fr.thegostsniperfr.arffornia.shop.RewardEntry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import com.mojang.authlib.GameProfile;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(Arffornia.MODID)
@PrefixGameTestTemplate(false)
public final class RewardDeliveryGameTests {

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "rewardee"));
        player.getInventory().clearContent();
        player.setPos(helper.absoluteVec(new Vec3(1, 2, 1)));
        return player;
    }

    private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().countItem(item);
    }

    @GameTest(template = "platform")
    public static void itemRewardsAreSplitIntoStacks(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        RewardDelivery delivery = new RewardDelivery(helper.getLevel().getServer());
        RewardEntry diamonds = new RewardEntry.Item("minecraft:diamond", 130);

        delivery.deliver(player, new PendingReward(1, "shop", List.of(diamonds)));

        helper.assertTrue(InventoryRoom.slotsFor(delivery.prepare(player, new PendingReward(1, "shop", List.of(diamonds))).stacks()) == 3,
                "130 diamonds need 3 slots");
        helper.assertTrue(count(player, Items.DIAMOND) == 130, "130 diamonds delivered, got " + count(player, Items.DIAMOND));
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void streakLuckyBoxRollsTheShippedLootTable(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        RewardDelivery delivery = new RewardDelivery(helper.getLevel().getServer());
        RewardEntry box = new RewardEntry.LuckyBox("arffornia:streak", 3);

        helper.assertTrue(delivery.whyUndeliverable(box) == null, "streak box is deliverable");
        delivery.deliver(player, new PendingReward(2, "streak", List.of(box)));

        int stacks = 0;
        for (int i = 0; i < 36; i++) {
            if (!player.getInventory().getItem(i).isEmpty()) {
                stacks++;
            }
        }
        helper.assertTrue(stacks >= 1, "three rolls put loot in the inventory");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void unknownItemsBoxesAndTypesAreUndeliverable(GameTestHelper helper) {
        RewardDelivery delivery = new RewardDelivery(helper.getLevel().getServer());

        helper.assertTrue(delivery.whyUndeliverable(new RewardEntry.Item("othermod:gone", 1)) != null, "unknown item");
        helper.assertTrue(delivery.whyUndeliverable(new RewardEntry.LuckyBox("arffornia:missing", 1)) != null, "unknown box");
        helper.assertTrue(delivery.whyUndeliverable(new RewardEntry.Unsupported("pet", "unknown type")) != null, "unknown type");
        helper.assertTrue(delivery.whyUndeliverable(new RewardEntry.Permission("cosmetic.hat", true)) != null, "no LuckPerms in tests");
        helper.assertTrue(delivery.whyUndeliverable(new RewardEntry.Key("arffornia:missing", 1)) != null, "unknown crate");
        helper.assertTrue(delivery.whyUndeliverable(new RewardEntry.Key("arffornia:streak", 1)) == null, "shipped streak crate");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void keyRewardsGiveKeysBoundToThePlayerInStacksOf64(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        RewardDelivery delivery = new RewardDelivery(helper.getLevel().getServer());

        delivery.deliver(player, new PendingReward(3, "streak", List.of(new RewardEntry.Key("arffornia:streak", 40), new RewardEntry.Key("arffornia:streak", 30))));

        helper.assertTrue(player.getInventory().countItem(ModItems.CRATE_KEY.get()) == 70, "70 keys");
        ItemStack first = player.getInventory().getItem(0);
        CrateKey key = CrateKeyItem.keyOf(first);
        helper.assertTrue(first.getCount() == 64, "keys of one crate and owner stack to 64, got " + first.getCount());
        helper.assertTrue(key != null && key.isOwnedBy(player.getUUID()), "bound to the receiver");
        helper.succeed();
    }

    @GameTest(template = "platform")
    public static void whatNoLongerFitsAtDeliveryIsStashedNotDropped(GameTestHelper helper) {
        ServerPlayer player = player(helper);
        for (int i = 0; i < 36; i++) {
            player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        RewardDelivery delivery = new RewardDelivery(helper.getLevel().getServer());

        delivery.deliver(player, new PendingReward(4, "shop", List.of(new RewardEntry.Item("minecraft:diamond", 5))));

        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 0, "no room, nothing given");
        helper.assertTrue(LootStash.get(helper.getLevel().getServer()).waiting(player.getUUID()).stream().mapToInt(ItemStack::getCount).sum() == 5,
                "5 diamonds stashed");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(8)).isEmpty(), "nothing dropped");
        player.getInventory().setItem(0, ItemStack.EMPTY);
        helper.assertTrue(LootStash.get(helper.getLevel().getServer()).deliver(player) == 0, "stash emptied once there is room");
        helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 5, "stashed diamonds given");
        helper.succeed();
    }
}
