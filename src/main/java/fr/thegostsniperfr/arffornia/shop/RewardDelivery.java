package fr.thegostsniperfr.arffornia.shop;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.GameMasterBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Maps each reward type to a fixed server-side action. Must run on the server thread. */
public final class RewardDelivery {
    private static final int MAIN_INVENTORY_SLOTS = 36;
    private static final Set<Item> OPERATOR_ITEMS = Set.of(Items.BARRIER, Items.LIGHT, Items.DEBUG_STICK, Items.STRUCTURE_VOID,
            Items.SPAWNER, Items.TRIAL_SPAWNER, Items.VAULT, Items.KNOWLEDGE_BOOK, Items.BEDROCK, Items.END_PORTAL_FRAME, Items.REINFORCED_DEEPSLATE);

    private final MinecraftServer server;

    public RewardDelivery(MinecraftServer server) {
        this.server = server;
    }

    /** @return why this server cannot deliver the entry, or {@code null} when it can. */
    @Nullable
    public String whyUndeliverable(RewardEntry entry) {
        return switch (entry) {
            case RewardEntry.Item item -> {
                Optional<Item> resolved = resolveItem(item.item());
                if (resolved.isEmpty()) yield "unknown item " + item.item();
                yield isOperatorItem(resolved.get()) ? "operator item " + item.item() + " cannot be a reward" : null;
            }
            case RewardEntry.LuckyBox box -> lootTable(box.box()) != LootTable.EMPTY ? null : "unknown lucky box " + box.box();
            case RewardEntry.Permission permission -> {
                if (!isLuckPermsLoaded()) yield "LuckPerms is not installed";
                yield ShopConfig.ALLOWED_PERMISSION_PREFIXES.get().stream().anyMatch(prefix -> RewardEntry.isNodeUnder(permission.node(), prefix))
                        ? null : "permission " + permission.node() + " is not in rewards.allowedPermissionPrefixes";
            }
            case RewardEntry.Rank rank -> {
                if (!isLuckPermsLoaded()) yield "LuckPerms is not installed";
                if (!ShopConfig.ALLOWED_RANK_GROUPS.get().contains(rank.group())) yield "group " + rank.group() + " is not in rewards.allowedRankGroups";
                yield LuckPermsRewards.groupExists(rank.group()) ? null : "LuckPerms group " + rank.group() + " does not exist";
            }
            case RewardEntry.Unsupported unsupported -> "unsupported entry " + unsupported.type() + ": " + unsupported.reason();
        };
    }

    /** Inventory slots an entry needs; a lucky box roll is counted as one stack. */
    public int slotsNeeded(RewardEntry entry) {
        return switch (entry) {
            case RewardEntry.Item item -> resolveItem(item.item())
                    .map(i -> (item.count() + new ItemStack(i).getMaxStackSize() - 1) / new ItemStack(i).getMaxStackSize())
                    .orElse(0);
            case RewardEntry.LuckyBox box -> box.count();
            default -> 0;
        };
    }

    public static int freeSlots(ServerPlayer player) {
        int free = 0;
        for (int i = 0; i < MAIN_INVENTORY_SLOTS; i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    public void deliver(ServerPlayer player, PendingReward reward) {
        for (RewardEntry entry : reward.contents()) {
            switch (entry) {
                case RewardEntry.Item item -> resolveItem(item.item()).ifPresent(i -> giveItems(player, i, item.count()));
                case RewardEntry.LuckyBox box -> rollLuckyBox(player, box, reward.id());
                case RewardEntry.Permission permission -> LuckPermsRewards.setPermission(player.getUUID(), permission.node(), permission.value(), reward.id());
                case RewardEntry.Rank rank -> LuckPermsRewards.addGroup(player.getUUID(), rank.group(), reward.id());
                case RewardEntry.Unsupported unsupported ->
                        Arffornia.LOGGER.error("Reward {}: skipped unsupported entry {} after claim", reward.id(), unsupported.type());
            }
        }
        Arffornia.LOGGER.info("Delivered reward {} ({}) to {}: {}", reward.id(), reward.source(), player.getName().getString(), reward.contents());
    }

    private void giveItems(ServerPlayer player, Item item, int count) {
        int maxStack = new ItemStack(item).getMaxStackSize();
        for (int remaining = count; remaining > 0; remaining -= maxStack) {
            give(player, new ItemStack(item, Math.min(maxStack, remaining)));
        }
    }

    private void rollLuckyBox(ServerPlayer player, RewardEntry.LuckyBox box, long rewardId) {
        LootTable table = lootTable(box.box());
        LootParams params = new LootParams.Builder(player.serverLevel())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withLuck(player.getLuck())
                .create(LootContextParamSets.GIFT);
        for (int roll = 0; roll < box.count(); roll++) {
            List<ItemStack> loot = table.getRandomItems(params);
            Arffornia.LOGGER.info("Reward {}: lucky box {} roll {} for {} gave {}", rewardId, box.box(), roll + 1, player.getName().getString(), loot);
            loot.forEach(stack -> give(player, stack));
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    private static boolean isOperatorItem(Item item) {
        return item instanceof GameMasterBlockItem || OPERATOR_ITEMS.contains(item);
    }

    private static Optional<Item> resolveItem(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.ITEM.getOptional(location).filter(item -> item != Items.AIR);
    }

    private LootTable lootTable(String boxId) {
        ResourceLocation box = ResourceLocation.tryParse(boxId);
        if (box == null) {
            return LootTable.EMPTY;
        }
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(box.getNamespace(), "lucky_box/" + box.getPath()));
        return server.reloadableRegistries().getLootTable(key);
    }

    private static boolean isLuckPermsLoaded() {
        return ModList.get().isLoaded("luckperms");
    }
}
