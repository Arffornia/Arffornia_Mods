package fr.thegostsniperfr.arffornia.block.entity;

import fr.thegostsniperfr.arffornia.machine.MachineBlockEntity;
import fr.thegostsniperfr.arffornia.machine.MachineTypes;
import fr.thegostsniperfr.arffornia.machine.SideConfig;
import fr.thegostsniperfr.arffornia.machine.SideMode;
import fr.thegostsniperfr.arffornia.recipe.CrafterRecipes;
import fr.thegostsniperfr.arffornia.recipe.MachineRecipe;
import fr.thegostsniperfr.arffornia.screen.CrafterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The Crafter's machine: 9 inputs, 2 outputs, runs the one Crafter recipe its player selected. */
public class CrafterBlockEntity extends MachineBlockEntity {
    public static final int INPUT_SLOTS = MachineTypes.CRAFTER.layout().inputs();

    public CrafterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRAFTER_BE.get(), MachineTypes.CRAFTER, pos, state, defaultSides());
    }

    private static SideConfig defaultSides() {
        return new SideConfig(List.of(SideMode.INPUT, SideMode.OUTPUT, SideMode.BOTH, SideMode.NONE))
                .set(Direction.DOWN, SideMode.OUTPUT)
                .set(Direction.EAST, SideMode.OUTPUT);
    }

    public Optional<RecipeHolder<MachineRecipe>> getSelectedRecipe() {
        ResourceLocation selected = selectedRecipeId();
        if (selected == null || level == null) {
            return Optional.empty();
        }
        return CrafterRecipes.byId(level.getRecipeManager(), selected);
    }

    public boolean isRecipeSelected(ResourceLocation recipeId) {
        return recipeId.equals(selectedRecipeId());
    }

    @Override
    protected void writeMenuData(RegistryFriendlyByteBuf buf) {
        MinecraftServer server = Objects.requireNonNull(level).getServer();
        CrafterMenu.writeOpenData(buf, worldPosition, CrafterRecipes.unlockedStages(server, linkedProgressionId()));
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CrafterMenu(containerId, inventory, this);
    }

    /** The client screen shows the selected recipe, so the block entity syncs its saved state. */
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    /**
     * Crafters saved before the machine framework kept {@code selectedRecipe}, {@code progress} and {@code maxProgress}
     * at the top level; {@code inventory}, {@code energy} and {@code linkedProgressionId} were already the same.
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("processor")) {
            tag = tag.copy();
            CompoundTag processor = new CompoundTag();
            processor.putInt("progress", tag.getInt("progress"));
            processor.putInt("maxProgress", tag.getInt("maxProgress"));
            if (tag.contains("selectedRecipe")) {
                processor.putString("selected", tag.getString("selectedRecipe"));
                processor.putString("recipe", tag.getString("selectedRecipe"));
            }
            tag.put("processor", processor);
        }
        super.loadAdditional(tag, registries);
    }
}
