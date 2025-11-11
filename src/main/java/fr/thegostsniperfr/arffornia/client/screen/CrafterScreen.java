package fr.thegostsniperfr.arffornia.client.screen;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.compat.jei.ArfforniaJeiPlugin;
import fr.thegostsniperfr.arffornia.network.ServerboundSelectRecipePacket;
import fr.thegostsniperfr.arffornia.screen.CrafterMenu;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CrafterScreen extends AbstractContainerScreen<CrafterMenu> {
    private static final ResourceLocation GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "textures/gui/crafter_gui.png");
    private static final ResourceLocation ENERGY_BAR_TEXTURE = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "textures/gui/energy_bar.png");
    private static final ResourceLocation PROGRESS_ARROW_TEXTURE = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "textures/gui/progress_arrow.png");
    private static final ResourceLocation RECIPE_BG = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "textures/gui/recipe_background.png");
    private static final ResourceLocation RECIPE_BG_SELECTED = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "textures/gui/recipe_background_selected.png");
    private static final Pattern MOD_ID_PATTERN = Pattern.compile("@(\\w*)");

    private static final int RECIPE_ROWS = 7;
    private static final int RECIPE_COLUMNS = 4;
    private final int recipesPerPage = RECIPE_ROWS * RECIPE_COLUMNS;

    private EditBox searchBox;
    private Button nextPageButton;
    private Button prevPageButton;
    private List<ArfforniaApiDtos.CustomRecipe> filteredRecipes = new ArrayList<>();
    private int currentPage = 0;
    private String lastSearch = "";

    private float smoothedEnergy;
    private boolean firstTick = true;

    public CrafterScreen(CrafterMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
        this.imageWidth = 80 + 176;
        this.imageHeight = 166;
        this.smoothedEnergy = 0;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        this.titleLabelX = 80 + (176 / 2);
        this.inventoryLabelX = 80 + 8;
        this.inventoryLabelY = this.imageHeight - 94;

        this.searchBox = new EditBox(this.font, x + 8, y + 148, 70, 12, Component.translatable("gui.arffornia.search_hint"));
        this.searchBox.setMaxLength(50);
        this.searchBox.setBordered(false);
        this.searchBox.setVisible(true);
        this.searchBox.setTextColor(0xFFFFFF);
        this.searchBox.setHint(Component.literal("Search...").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
        this.addWidget(this.searchBox);
        this.setInitialFocus(this.searchBox);

        this.prevPageButton = addRenderableWidget(Button.builder(Component.literal("<"), (btn) -> {
            if (currentPage > 0) {
                currentPage--;
                updatePageButtons();
            }
        }).bounds(x + 8, y + 4, 18, 12).build());

        this.nextPageButton = addRenderableWidget(Button.builder(Component.literal(">"), (btn) -> {
            if ((currentPage + 1) * recipesPerPage < filteredRecipes.size()) {
                currentPage++;
                updatePageButtons();
            }
        }).bounds(x + 60, y + 4, 18, 12).build());

        updateFilteredRecipes();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (this.searchBox != null && !this.searchBox.getValue().equals(lastSearch)) {
            lastSearch = this.searchBox.getValue();
            updateFilteredRecipes();
        }

        float targetEnergy = this.menu.getEnergy();

        if (this.firstTick) {
            this.smoothedEnergy = targetEnergy;
            this.firstTick = false;
        } else {
            this.smoothedEnergy += (targetEnergy - this.smoothedEnergy) * 0.4F;
        }
    }

    @Override
    public void render(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        super.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        if (this.searchBox != null) {
            this.searchBox.render(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        }
        this.renderTooltip(pGuiGraphics, pMouseX, pMouseY);
    }

    private static String formatEnergy(int energy) {
        if (energy >= 1_000) {
            return String.format(Locale.US, "%.2f kFE", energy / 1_000.0);
        }

        return energy + " FE";
    }

    public int getScaledEnergy() {
        int capacity = this.menu.getCapacity();
        int energyBarSize = 60;
        if (capacity == 0) return 0;
        return Math.min(energyBarSize, (int) ((this.smoothedEnergy * energyBarSize) / capacity));
    }

    @Override
    protected void renderBg(GuiGraphics pGuiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        pGuiGraphics.fill(x + 7, y + 147, x + 78, y + 160, 0xFF373737);
        pGuiGraphics.blit(GUI_TEXTURE, x + 80, y, 0, 0, 176, 166, 176, 166);

        int progress = this.menu.getScaledProgress();
        if (progress > 0) {
            pGuiGraphics.blit(PROGRESS_ARROW_TEXTURE, x + 80 + 76, y + 35, 0, 0, progress, 16, 24, 16);
        }

        int energy = getScaledEnergy();
        if (energy > 0) {
            pGuiGraphics.blit(ENERGY_BAR_TEXTURE, x + 80 + 158, y + 13 + (60 - energy), 0, 60 - energy, 12, energy, 12, 60);
        }

        renderRecipeList(pGuiGraphics, pMouseX, pMouseY);
    }

    private void renderRecipeList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        int startIndex = currentPage * recipesPerPage;

        for (int i = 0; i < recipesPerPage; i++) {
            int recipeIndex = startIndex + i;
            if (recipeIndex >= filteredRecipes.size()) {
                break;
            }

            int row = i % RECIPE_ROWS;
            int col = i / RECIPE_ROWS;
            int recipeX = x + 7 + (col * 18);
            int recipeY = y + 17 + (row * 18);

            ArfforniaApiDtos.CustomRecipe recipe = filteredRecipes.get(recipeIndex);
            ResourceLocation background = this.menu.blockEntity.isRecipeSelected(recipe) ? RECIPE_BG_SELECTED : RECIPE_BG;
            guiGraphics.blit(background, recipeX, recipeY, 0, 0, 18, 18, 18, 18);

            ItemStack resultStack = ItemStack.EMPTY;
            if (!recipe.result().isEmpty()) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(recipe.result().get(0).item()));
                if (item != Items.AIR) resultStack = new ItemStack(item);
            }

            guiGraphics.renderItem(resultStack, recipeX + 1, recipeY + 1);

            if (isMouseOver(mouseX, mouseY, recipeX, recipeY, 18, 18)) {
                guiGraphics.renderTooltip(this.font, formatRecipeName(recipe.type()), mouseX, mouseY);
            }
        }

        if (isMouseOver(mouseX, mouseY, leftPos + 80 + 158, topPos + 13, 12, 60)) {
            String energyText = formatEnergy(this.menu.getEnergy());
            String capacityText = formatEnergy(this.menu.getCapacity());
            guiGraphics.renderTooltip(this.font, Component.literal(energyText + " / " + capacityText), mouseX, mouseY);
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) filteredRecipes.size() / recipesPerPage));
        String pageText = (currentPage + 1) + "/" + totalPages;
        guiGraphics.drawCenteredString(this.font, pageText, x + 43, y + 5, 0x404040);
    }

    private void updateFilteredRecipes() {
        String rawSearchText = searchBox.getValue().toLowerCase(Locale.ROOT);
        currentPage = 0;

        if (rawSearchText.isEmpty()) {
            filteredRecipes = new ArrayList<>(this.menu.availableRecipes);
            updatePageButtons();
            return;
        }

        Matcher modMatcher = MOD_ID_PATTERN.matcher(rawSearchText);
        String modFilter = null;
        if (modMatcher.find()) {
            modFilter = modMatcher.group(1);
            rawSearchText = modMatcher.replaceAll("").trim();
        }

        final String finalSearchText = rawSearchText.replace("_", " ");
        final String finalModFilter = modFilter;

        this.filteredRecipes = this.menu.availableRecipes.stream().filter(recipe -> {
            boolean modMatches = true;
            if (finalModFilter != null && !finalModFilter.isEmpty()) {
                modMatches = recipe.result().stream()
                        .anyMatch(result -> ResourceLocation.parse(result.item()).getNamespace().startsWith(finalModFilter));
            }

            boolean nameMatches = true;
            if (!finalSearchText.isEmpty()) {
                nameMatches = recipe.result().stream().anyMatch(result -> {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(result.item()));
                    if (item == Items.AIR) return false;

                    String displayName = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
                    String registryName = result.item().substring(result.item().indexOf(':') + 1).replace("_", " ");

                    return displayName.contains(finalSearchText) || registryName.contains(finalSearchText);
                });
            }
            return modMatches && nameMatches;
        }).collect(Collectors.toList());

        updatePageButtons();
    }

    private void updatePageButtons() {
        this.prevPageButton.active = currentPage > 0;
        this.nextPageButton.active = (currentPage + 1) * recipesPerPage < filteredRecipes.size();
    }

    private MutableComponent formatRecipeName(String type) {
        String[] parts = type.split(":");
        if (parts.length == 2) {
            String name = parts[1].replace("_recipe", "").replace("_", " ");
            return Component.literal(StringUtils.capitalize(name));
        }
        return Component.literal(type);
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        int x = this.leftPos;
        int y = this.topPos;
        int startIndex = currentPage * recipesPerPage;

        for (int i = 0; i < recipesPerPage; i++) {
            int recipeIndex = startIndex + i;
            if (recipeIndex >= filteredRecipes.size()) break;

            int row = i % RECIPE_ROWS;
            int col = i / RECIPE_ROWS;
            int recipeX = x + 7 + (col * 18);
            int recipeY = y + 17 + (row * 18);

            if (isMouseOver(pMouseX, pMouseY, recipeX, recipeY, 18, 18)) {
                ArfforniaApiDtos.CustomRecipe clickedRecipe = filteredRecipes.get(recipeIndex);

                if (pButton == 0) { // Left Click
                    Integer unlockId = clickedRecipe.milestoneUnlockId();

                    if (this.menu.blockEntity.isRecipeSelected(clickedRecipe)) {
                        unlockId = null;
                    }

                    PacketDistributor.sendToServer(new ServerboundSelectRecipePacket(this.menu.blockEntity.getBlockPos(), unlockId));
                    this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                } else if (pButton == 2) { // Middle Click
                    if (!clickedRecipe.result().isEmpty() && ArfforniaJeiPlugin.RUNTIME != null) {
                        Item resultItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(clickedRecipe.result().get(0).item()));
                        ItemStack resultStack = new ItemStack(resultItem);
                        if (!resultStack.isEmpty()) {
                            ArfforniaJeiPlugin.RUNTIME.getRecipesGui().show(
                                    ArfforniaJeiPlugin.RUNTIME.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, resultStack)
                            );
                            return true;
                        }
                    }
                }
            }
        }

        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    protected void renderLabels(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY) {
        pGuiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);

        ArfforniaApiDtos.CustomRecipe selectedRecipe = this.menu.blockEntity.getSelectedRecipe();
        Component title = (selectedRecipe != null) ? formatRecipeName(selectedRecipe.type()).withStyle(ChatFormatting.GOLD) : this.title;

        pGuiGraphics.drawCenteredString(this.font, title, this.titleLabelX, this.titleLabelY, 4210752);
    }

    private boolean isMouseOver(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        if (pKeyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.player.closeContainer();
            return true;
        }

        if (this.searchBox.isFocused()) {
            return this.searchBox.keyPressed(pKeyCode, pScanCode, pModifiers);
        }

        return super.keyPressed(pKeyCode, pScanCode, pModifiers);
    }
}