package fr.thegostsniperfr.arffornia.client.jei;

import fr.thegostsniperfr.arffornia.recipe.MachineRecipe;
import fr.thegostsniperfr.arffornia.network.ServerboundPlaceRecipePacket;
import fr.thegostsniperfr.arffornia.screen.CrafterMenu;
import fr.thegostsniperfr.arffornia.screen.ModMenuTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class CrafterRecipeTransferHandler implements IRecipeTransferHandler<CrafterMenu, RecipeHolder<MachineRecipe>> {

    @Override
    public Class<? extends CrafterMenu> getContainerClass() {
        return CrafterMenu.class;
    }

    @Override
    public Optional<MenuType<CrafterMenu>> getMenuType() {
        return Optional.of(ModMenuTypes.CRAFTER_MENU.get());
    }

    @Override
    public RecipeType<RecipeHolder<MachineRecipe>> getRecipeType() {
        return CrafterRecipeCategory.CRAFTER_RECIPE_TYPE;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(@NotNull CrafterMenu container, @NotNull RecipeHolder<MachineRecipe> recipe, @NotNull IRecipeSlotsView recipeSlots, @NotNull Player player, boolean maxTransfer, boolean doTransfer) {
        if (!doTransfer) {
            return null;
        }

        if (player.level().isClientSide()) {
            PacketDistributor.sendToServer(new ServerboundPlaceRecipePacket(
                    container.blockEntity.getBlockPos(),
                    recipe.id()
            ));
        }

        return null;
    }
}