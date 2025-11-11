package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import fr.thegostsniperfr.arffornia.recipe.CustomRecipeManager;
import fr.thegostsniperfr.arffornia.screen.CrafterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;

public record ServerboundPlaceRecipePacket(BlockPos blockPos, int milestoneUnlockId) implements CustomPacketPayload {

    public static final Type<ServerboundPlaceRecipePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "place_recipe")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPlaceRecipePacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeBlockPos(packet.blockPos());
                buf.writeVarInt(packet.milestoneUnlockId());
            },
            (buf) -> new ServerboundPlaceRecipePacket(buf.readBlockPos(), buf.readVarInt())
    );


    public static void handle(final ServerboundPlaceRecipePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof CrafterMenu crafterMenu)) {
                return;
            }

            if (!crafterMenu.blockEntity.getBlockPos().equals(packet.blockPos())) {
                return;
            }

            ArfforniaApiDtos.CustomRecipe recipe = CustomRecipeManager.getRecipeByMilestoneUnlockId(packet.milestoneUnlockId());
            if (recipe == null) {
                return; // Recipe doesn't exist on the server.
            }

            long progressionId = crafterMenu.blockEntity.getLinkedProgressionId();
            if (progressionId == -1) {
                return; // Crafter not linked.
            }

            int requiredMilestoneId = recipe.milestoneId();

            // Verify on the server-side that the player has unlocked the required milestone.
            ArfforniaApiService.getInstance().fetchProgressionData(progressionId).thenAcceptAsync(progressionData -> {
                if (progressionData == null || !progressionData.completedMilestones().contains(requiredMilestoneId)) {
                    Arffornia.LOGGER.warn("Player {} tried to use recipe from milestoneUnlockId {} without having completed the required milestoneId {}.", player.getName().getString(), packet.milestoneUnlockId, requiredMilestoneId);
                    return;
                }

                // Check if the player is still in the same menu
                if(!(player.containerMenu instanceof CrafterMenu currentMenu) || currentMenu != crafterMenu) {
                    return;
                }

                // If check passes, proceed with placing and selecting the recipe.
                if (placeRecipe(player, crafterMenu, recipe)) {
                    crafterMenu.blockEntity.setSelectedRecipe(packet.milestoneUnlockId());
                }

            }, player.getServer());
        });
    }

    private static boolean placeRecipe(ServerPlayer player, CrafterMenu menu, ArfforniaApiDtos.CustomRecipe recipe) {
        // Build a map of required ingredients
        Map<Item, Integer> requiredItems = new HashMap<>();
        recipe.ingredients().forEach(ing -> {
            if (ing != null) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(ing.item()));
                if(item != null) {
                    requiredItems.merge(item, ing.count(), Integer::sum);
                }
            }
        });

        // Check if the player has all the required items
        if (!hasEnoughItems(player, requiredItems)) {
            return false; // Player doesn't have the items, do nothing.
        }

        // Clear the crafting grid and return any existing items to the player
        for (int i = 0; i < 9; i++) {
            ItemStack stackInSlot = menu.blockEntity.itemHandler.getStackInSlot(i);
            if (!stackInSlot.isEmpty()) {
                ItemHandlerHelper.giveItemToPlayer(player, stackInSlot);
                menu.blockEntity.itemHandler.setStackInSlot(i, ItemStack.EMPTY);
            }
        }

        // Iterate through recipe ingredients, find them in player inventory, shrink the stack, and place in crafter
        for (int gridSlotIndex = 0; gridSlotIndex < 9; gridSlotIndex++) {
            if (gridSlotIndex >= recipe.ingredients().size() || recipe.ingredients().get(gridSlotIndex) == null) {
                continue;
            }

            ArfforniaApiDtos.RecipeIngredient ingredient = recipe.ingredients().get(gridSlotIndex);
            Item itemToFind = BuiltInRegistries.ITEM.get(ResourceLocation.parse(ingredient.item()));
            int countNeeded = ingredient.count();

            for (int playerInvSlot = 0; playerInvSlot < player.getInventory().getContainerSize(); playerInvSlot++) {
                if (countNeeded <= 0) break;

                ItemStack playerStack = player.getInventory().getItem(playerInvSlot);
                if (playerStack.is(itemToFind)) {
                    int canTake = Math.min(playerStack.getCount(), countNeeded);
                    playerStack.shrink(canTake);
                    countNeeded -= canTake;
                }
            }
            menu.blockEntity.itemHandler.setStackInSlot(gridSlotIndex, new ItemStack(itemToFind, ingredient.count()));
        }

        // Notify the client that the inventory has changed
        player.getInventory().setChanged();
        menu.broadcastChanges();
        return true;
    }

    private static boolean hasEnoughItems(ServerPlayer player, Map<Item, Integer> itemsToCheck) {
        if (itemsToCheck.isEmpty()) {
            return true;
        }

        Map<Item, Integer> remainingRequirements = new HashMap<>(itemsToCheck);

        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                Integer requiredCount = remainingRequirements.get(stack.getItem());
                if (requiredCount != null) {
                    int newCount = requiredCount - stack.getCount();
                    if (newCount <= 0) {
                        remainingRequirements.remove(stack.getItem());
                    } else {
                        remainingRequirements.put(stack.getItem(), newCount);
                    }
                }
            }
        }

        return remainingRequirements.isEmpty();
    }


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}