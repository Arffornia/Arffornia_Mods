package fr.thegostsniperfr.arffornia.client.jei;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.recipe.MachineRecipe;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class CrafterRecipeCategory implements IRecipeCategory<RecipeHolder<MachineRecipe>> {

    public static final RecipeType<RecipeHolder<MachineRecipe>> CRAFTER_RECIPE_TYPE =
            RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "crafter"));

    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "textures/gui/crafter_gui_jei.png");

    private final IDrawable background;
    private final IDrawable icon;

    public CrafterRecipeCategory(IGuiHelper helper) {
        this.background = helper.drawableBuilder(GUI_TEXTURE, 0, 0, 170, 80)
                .setTextureSize(170, 80)
                .build();

        this.icon = helper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModBlocks.CRAFTER_BLOCK.get()));
    }

    @Override
    public RecipeType<RecipeHolder<MachineRecipe>> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<MachineRecipe> holder, IFocusGroup focuses) {
        MachineRecipe recipe = holder.value();
        int inputGridStartX = 13;
        int inputGridStartY = 14;
        int outputSlotX = 121;
        int outputSlotY = 23;

        for (int i = 0; i < recipe.ingredients().size() && i < 9; i++) {
            SizedIngredient ingredient = recipe.ingredients().get(i);
            builder.addSlot(RecipeIngredientRole.INPUT, inputGridStartX + (i % 3) * 18, inputGridStartY + (i / 3) * 18)
                    .addItemStacks(Arrays.asList(ingredient.getItems()));
        }

        for (int i = 0; i < recipe.results().size() && i < 2; i++) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, outputSlotX, outputSlotY + i * 18)
                    .addItemStack(recipe.results().get(i));
        }
    }

    @Override
    public void draw(RecipeHolder<MachineRecipe> holder, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        MachineRecipe recipe = holder.value();
        List<Component> infoLines = new ArrayList<>();
        infoLines.add(Component.translatable("gui.arffornia.crafter.time", String.format(Locale.ROOT, "%.1f", recipe.time() / 20.0f)));
        if (recipe.energy() > 0) {
            infoLines.add(Component.translatable("gui.arffornia.energy.fe", String.format(Locale.US, "%,d", recipe.energy())));
        }

        Font font = Minecraft.getInstance().font;
        int currentY = 55;
        for (Component line : infoLines) {
            int textX = (getBackground().getWidth() - font.width(line)) / 2;
            guiGraphics.drawString(font, line, textX, currentY, 0x404040, false);
            currentY += font.lineHeight;
        }
    }
}
