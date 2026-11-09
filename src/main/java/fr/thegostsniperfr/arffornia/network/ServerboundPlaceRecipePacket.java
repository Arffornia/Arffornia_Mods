package fr.thegostsniperfr.arffornia.network;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.block.entity.CrafterBlockEntity;
import fr.thegostsniperfr.arffornia.recipe.CrafterRecipes;
import fr.thegostsniperfr.arffornia.recipe.MachineRecipe;
import fr.thegostsniperfr.arffornia.screen.CrafterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JEI "+" on a Crafter recipe: moves the ingredients from the player's inventory into the grid and selects it. */
public record ServerboundPlaceRecipePacket(BlockPos blockPos, ResourceLocation recipeId) implements CustomPacketPayload {

    public static final Type<ServerboundPlaceRecipePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "place_recipe")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPlaceRecipePacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ServerboundPlaceRecipePacket::blockPos,
            ResourceLocation.STREAM_CODEC, ServerboundPlaceRecipePacket::recipeId,
            ServerboundPlaceRecipePacket::new
    );

    public static void handle(final ServerboundPlaceRecipePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                processOnServer(player, packet);
            }
        });
    }

    /** Must be called on the server thread. @return true if the ingredients were placed and the recipe selected. */
    public static boolean processOnServer(ServerPlayer player, ServerboundPlaceRecipePacket packet) {
        if (!(player.containerMenu instanceof CrafterMenu menu)
                || !ServerPayloadChecks.isInteractingWith(player, menu, menu.blockEntity, packet.blockPos())) {
            return false;
        }
        Optional<RecipeHolder<MachineRecipe>> recipe = CrafterRecipes.byId(player.server.getRecipeManager(), packet.recipeId());
        if (recipe.isEmpty() || recipe.get().value().ingredients().size() > CrafterBlockEntity.INPUT_SLOTS) {
            return false;
        }
        CrafterBlockEntity be = menu.blockEntity;
        if (!CrafterRecipes.isUnlocked(player.server, be.getLinkedProgressionId(), recipe.get().value())) {
            Arffornia.LOGGER.warn("Rejected recipe placement {} from {}: stage not unlocked.", packet.recipeId(), player.getName().getString());
            return false;
        }
        if (!placeRecipe(player, be, recipe.get().value())) {
            return false;
        }
        be.setSelectedRecipe(packet.recipeId());
        menu.broadcastChanges();
        return true;
    }

    /**
     * Fills the grid from the player's inventory plus what the grid already holds. Nothing moves unless every
     * ingredient can be served; leftovers from the old grid go back to the player.
     */
    private static boolean placeRecipe(ServerPlayer player, CrafterBlockEntity be, MachineRecipe recipe) {
        Inventory inventory = player.getInventory();
        List<ItemStack> grid = new ArrayList<>();
        for (int slot = 0; slot < CrafterBlockEntity.INPUT_SLOTS; slot++) {
            grid.add(be.itemHandler.getStackInSlot(slot).copy());
        }

        List<ItemStack> simulated = new ArrayList<>();
        inventory.items.forEach(stack -> simulated.add(stack.copy()));
        grid.forEach(stack -> simulated.add(stack.copy()));
        for (SizedIngredient ingredient : recipe.ingredients()) {
            if (take(simulated, ingredient).isEmpty()) {
                return false;
            }
        }

        for (int slot = 0; slot < CrafterBlockEntity.INPUT_SLOTS; slot++) {
            be.itemHandler.setStackInSlot(slot, ItemStack.EMPTY);
        }
        List<ItemStack> pool = new ArrayList<>(inventory.items);
        pool.addAll(grid);
        List<ItemStack> placed = recipe.ingredients().stream().map(ingredient -> take(pool, ingredient)).toList();
        for (int slot = 0; slot < placed.size(); slot++) {
            be.itemHandler.setStackInSlot(slot, placed.get(slot));
        }
        grid.stream().filter(stack -> !stack.isEmpty()).forEach(stack -> ItemHandlerHelper.giveItemToPlayer(player, stack));
        inventory.setChanged();
        return true;
    }

    /**
     * Removes {@code ingredient.count()} items of one kind matching the ingredient from {@code stacks}, or nothing
     * when no single kind is present in that amount (the result must fit in one Crafter slot).
     */
    private static ItemStack take(List<ItemStack> stacks, SizedIngredient ingredient) {
        for (ItemStack candidate : stacks) {
            if (candidate.isEmpty() || !ingredient.ingredient().test(candidate) || ingredient.count() > candidate.getMaxStackSize()) {
                continue;
            }
            int available = stacks.stream().filter(s -> ItemStack.isSameItemSameComponents(s, candidate)).mapToInt(ItemStack::getCount).sum();
            if (available < ingredient.count()) {
                continue;
            }
            ItemStack taken = candidate.copyWithCount(ingredient.count());
            int remaining = ingredient.count();
            for (ItemStack stack : stacks) {
                if (remaining == 0) {
                    break;
                }
                if (ItemStack.isSameItemSameComponents(stack, taken)) {
                    int amount = Math.min(remaining, stack.getCount());
                    stack.shrink(amount);
                    remaining -= amount;
                }
            }
            return taken;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
