package fr.thegostsniperfr.arffornia.compat.jei;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.client.screen.CrafterScreen;
import fr.thegostsniperfr.arffornia.recipe.CustomRecipeManager;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

@JeiPlugin
public class ArfforniaJeiPlugin implements IModPlugin {
    public static IJeiRuntime RUNTIME;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "jei_plugin");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        RUNTIME = jeiRuntime;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new CrafterRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<ArfforniaApiDtos.CustomRecipe> recipes = CustomRecipeManager.getAllRecipes().stream().toList();
        Arffornia.LOGGER.info("Registering recipes from CustomRecipeManager for JEI. Found: {} recipes.", recipes.size());
        if (!recipes.isEmpty()) {
            registration.addRecipes(CrafterRecipeCategory.CRAFTER_RECIPE_TYPE, recipes);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.CRAFTER_BLOCK.get()), CrafterRecipeCategory.CRAFTER_RECIPE_TYPE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(
                CrafterScreen.class,
                80 + 76,
                35,
                24,
                16,
                CrafterRecipeCategory.CRAFTER_RECIPE_TYPE
        );
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new CrafterRecipeTransferHandler(), CrafterRecipeCategory.CRAFTER_RECIPE_TYPE);
    }
}