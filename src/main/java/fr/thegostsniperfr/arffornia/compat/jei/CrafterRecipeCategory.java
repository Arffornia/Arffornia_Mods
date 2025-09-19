package fr.thegostsniperfr.arffornia.compat.jei;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CrafterRecipeCategory implements IRecipeCategory<ArfforniaApiDtos.CustomRecipe> {

    public static final RecipeType<ArfforniaApiDtos.CustomRecipe> CRAFTER_RECIPE_TYPE =
            RecipeType.create(Arffornia.MODID, "crafter", ArfforniaApiDtos.CustomRecipe.class);

    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "textures/gui/crafter_gui_jei.png");

    private final IDrawable background;
    private final IDrawable icon;

    public CrafterRecipeCategory(IGuiHelper helper) {
        this.background = helper.drawableBuilder(GUI_TEXTURE, 0, 0, 176, 86)
                .setTextureSize(176, 166)
                .build();

        this.icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModBlocks.CRAFTER_BLOCK.get()));
    }

    @Override
    public RecipeType<ArfforniaApiDtos.CustomRecipe> getRecipeType() {
        return CRAFTER_RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.arffornia.crafter_block");
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public IDrawable getBackground() {
        return this.background;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ArfforniaApiDtos.CustomRecipe recipe, IFocusGroup focuses) {
        // Inputs
        for (int i = 0; i < recipe.ingredients().size() && i < 3; i++) {
            ArfforniaApiDtos.RecipeIngredient ingredient = recipe.ingredients().get(i);
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(ingredient.item()));
            if (item != Items.AIR) {
                builder.addSlot(RecipeIngredientRole.INPUT, 34, 17 + (i * 18))
                        .addItemStack(new ItemStack(item, ingredient.count()));
            }
        }

        // Outputs
        int verticalCenter = 35;
        for (int i = 0; i < recipe.result().size() && i < 2; i++) {
            ArfforniaApiDtos.RecipeResult result = recipe.result().get(i);
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(result.item()));
            if (item != Items.AIR) {
                int yPos = (recipe.result().size() == 1) ? verticalCenter : (verticalCenter - 9 + (i * 18));
                builder.addSlot(RecipeIngredientRole.OUTPUT, 124, yPos)
                        .addItemStack(new ItemStack(item, result.count()));
            }
        }
    }

    @Override
    public void draw(ArfforniaApiDtos.CustomRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        List<Component> infoLines = new ArrayList<>();
        if (recipe.time() != null && recipe.time() > 0) {
            float timeInSeconds = recipe.time() / 20.0f;
            infoLines.add(Component.literal(String.format(Locale.US, "%.1f s", timeInSeconds)));
        }
        if (recipe.energy() != null && recipe.energy() > 0) {
            infoLines.add(Component.literal(String.format(Locale.US, "%,d FE", recipe.energy())));
        }

        if (!infoLines.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            int currentY = 55;

            for (Component line : infoLines) {
                int textWidth = font.width(line);
                int textX = (getBackground().getWidth() - textWidth) / 2;
                guiGraphics.drawString(font, line, textX, currentY, 0x404040, false);
                currentY += font.lineHeight;
            }
        }
    }
}