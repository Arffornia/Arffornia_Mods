package fr.thegostsniperfr.arffornia.block.lootbox;

import com.mojang.serialization.MapCodec;
import fr.thegostsniperfr.arffornia.lootbox.LootBoxData;
import fr.thegostsniperfr.arffornia.lootbox.LootBoxManager;
import fr.thegostsniperfr.arffornia.block.entity.LootBoxBlockEntity;
import fr.thegostsniperfr.arffornia.screen.PreviewMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

public class LootBoxBlock extends BaseEntityBlock {
    public static final MapCodec<LootBoxBlock> CODEC = simpleCodec(LootBoxBlock::new);

    public LootBoxBlock(Properties properties) { super(properties); }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LootBoxBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;

        if (level.getBlockEntity(pos) instanceof LootBoxBlockEntity be) {
            LootBoxData boxData = LootBoxManager.getBox(be.getBoxId());
            if (boxData == null) {
                serverPlayer.sendSystemMessage(Component.literal("§cThis box is not configured properly."));
                return InteractionResult.FAIL;
            }

            // View Box Stats (Shift Right Click)
            if (serverPlayer.isShiftKeyDown()) {
                List<ItemStack> previewItems = new ArrayList<>();
                for (LootBoxData.Reward reward : boxData.rewards) {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(reward.item));
                    ItemStack stack = new ItemStack(item, reward.amount);

                    net.minecraft.world.item.component.ItemLore lore = new net.minecraft.world.item.component.ItemLore(
                            List.of(Component.literal("§eChance: " + reward.chance + "%"))
                    );
                    stack.set(net.minecraft.core.component.DataComponents.LORE, lore);

                    previewItems.add(stack);
                }

                serverPlayer.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new PreviewMenu(id, inv, previewItems, boxData.name),
                        Component.literal(boxData.name)
                ), (RegistryFriendlyByteBuf buf) -> {
                    buf.writeUtf(boxData.name);
                    buf.writeInt(previewItems.size());
                    for(ItemStack s : previewItems) {
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s);
                    }
                });
                return InteractionResult.SUCCESS;
            }

            // Open Mode (Right Click)
            ItemStack handStack = serverPlayer.getMainHandItem();
            Item requiredKey = BuiltInRegistries.ITEM.get(ResourceLocation.parse(boxData.key_item));

            if (handStack.is(requiredKey)) {
                handStack.shrink(1);

                LootBoxData.Reward reward = LootBoxManager.getRandomReward(be.getBoxId());
                if(reward != null) {
                    Item rewardItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(reward.item));
                    ItemStack rewardStack = new ItemStack(rewardItem, reward.amount);

                    if (!serverPlayer.getInventory().add(rewardStack)) {
                        serverPlayer.drop(rewardStack, false);
                    }

                    level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.0F);
                    serverPlayer.sendSystemMessage(Component.literal("§aYou opened the " + boxData.name + " §aand got §e" + reward.amount + "x " + rewardItem.getName(rewardStack).getString() + "§a!"));
                }
            } else {
                serverPlayer.sendSystemMessage(Component.literal("§cYou need a §e" + requiredKey.getName(new ItemStack(requiredKey)).getString() + " §cto open this box!"));
            }
        }
        return InteractionResult.SUCCESS;
    }
}