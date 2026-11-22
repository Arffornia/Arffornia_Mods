package fr.thegostsniperfr.arffornia.shop;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.crate.CrateDefinitions;
import fr.thegostsniperfr.arffornia.item.CrateKeyItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Maps each reward type to a fixed server-side action. Must run on the server thread. */
public final class RewardDelivery {
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
            case RewardEntry.Key key -> {
                ResourceLocation crate = ResourceLocation.tryParse(key.crate());
                yield crate != null && CrateDefinitions.server(crate) != null ? null : "unknown crate " + key.crate();
            }
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

    /** What a reward gives, worked out before it is claimed: item stacks (lucky boxes already rolled) and other actions. */
    public record Prepared(List<ItemStack> stacks, List<RewardEntry> actions) {
    }

    /** Rolls lucky boxes and builds the stacks of a deliverable reward. Rolling first lets the caller check the room. */
    public Prepared prepare(ServerPlayer player, PendingReward reward) {
        List<ItemStack> stacks = new ArrayList<>();
        List<RewardEntry> actions = new ArrayList<>();
        for (RewardEntry entry : reward.contents()) {
            switch (entry) {
                case RewardEntry.Item item -> resolveItem(item.item()).ifPresent(i -> stacks.addAll(split(new ItemStack(i), item.count())));
                case RewardEntry.LuckyBox box -> stacks.addAll(rollLuckyBox(player, box, reward.id()));
                case RewardEntry.Key key -> {
                    ResourceLocation crate = ResourceLocation.tryParse(key.crate());
                    if (crate != null) {
                        stacks.addAll(CrateKeyItem.stacks(crate, player, key.count()));
                    }
                }
                case RewardEntry.Permission permission -> actions.add(permission);
                case RewardEntry.Rank rank -> actions.add(rank);
                case RewardEntry.Unsupported unsupported -> actions.add(unsupported);
            }
        }
        return new Prepared(List.copyOf(stacks), List.copyOf(actions));
    }

    public void deliver(ServerPlayer player, PendingReward reward) {
        deliver(player, reward, prepare(player, reward));
    }

    /**
     * Gives a claimed reward. What does not fit any more (the inventory changed during the claim) goes to the
     * {@link LootStash}, never to the ground.
     */
    public void deliver(ServerPlayer player, PendingReward reward, Prepared prepared) {
        List<ItemStack> leftovers = InventoryRoom.give(player.getInventory(), prepared.stacks());
        if (!leftovers.isEmpty()) {
            LootStash.get(server).add(player.getUUID(), leftovers, false);
            Arffornia.LOGGER.warn("Reward {} for {}: {} did not fit and wait in the stash", reward.id(), player.getName().getString(), leftovers);
            player.sendSystemMessage(Component.translatable("chat.arffornia.rewards.stashed", InventoryRoom.slotsFor(leftovers),
                    Arffornia.CLAIM_REWARD_COMMAND.copy().withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.YELLOW));
        }
        for (RewardEntry action : prepared.actions()) {
            switch (action) {
                case RewardEntry.Permission permission -> LuckPermsRewards.setPermission(player.getUUID(), permission.node(), permission.value(), reward.id());
                case RewardEntry.Rank rank -> LuckPermsRewards.addGroup(player.getUUID(), rank.group(), reward.id());
                case RewardEntry.Unsupported unsupported ->
                        Arffornia.LOGGER.error("Reward {}: skipped unsupported entry {} after claim", reward.id(), unsupported.type());
                default -> Arffornia.LOGGER.error("Reward {}: unexpected action {}", reward.id(), action);
            }
        }
        Arffornia.LOGGER.info("Delivered reward {} ({}) to {}: {} -> {}", reward.id(), reward.source(), player.getName().getString(),
                reward.contents(), prepared.stacks());
    }

    private static List<ItemStack> split(ItemStack prototype, int count) {
        List<ItemStack> stacks = new ArrayList<>();
        int maxStack = prototype.getMaxStackSize();
        for (int remaining = count; remaining > 0; remaining -= maxStack) {
            stacks.add(prototype.copyWithCount(Math.min(maxStack, remaining)));
        }
        return stacks;
    }

    private List<ItemStack> rollLuckyBox(ServerPlayer player, RewardEntry.LuckyBox box, long rewardId) {
        LootTable table = lootTable(box.box());
        LootParams params = new LootParams.Builder(player.serverLevel())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withLuck(player.getLuck())
                .create(LootContextParamSets.GIFT);
        List<ItemStack> all = new ArrayList<>();
        for (int roll = 0; roll < box.count(); roll++) {
            List<ItemStack> loot = table.getRandomItems(params);
            Arffornia.LOGGER.info("Reward {}: lucky box {} roll {} for {} gave {}", rewardId, box.box(), roll + 1, player.getName().getString(), loot);
            all.addAll(loot);
        }
        return all;
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
