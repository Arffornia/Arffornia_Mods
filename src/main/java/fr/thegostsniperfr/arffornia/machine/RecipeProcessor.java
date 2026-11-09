package fr.thegostsniperfr.arffornia.machine;

import fr.thegostsniperfr.arffornia.progression.Progressions;
import fr.thegostsniperfr.arffornia.progression.Stages;
import fr.thegostsniperfr.arffornia.recipe.MachineRecipe;
import fr.thegostsniperfr.arffornia.recipe.ModRecipes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Runs the {@code arffornia:machine} recipes of one machine: find a recipe the inputs satisfy, the machine's own
 * progression has unlocked and whose results fit the outputs, then draw energy tick by tick and commit. Inputs are
 * only taken at commit, so stopping or breaking the machine mid-craft never loses items.
 * <p>
 * The search runs again after the inventory changed, and every second while idle; the last recipe is tried first.
 */
public final class RecipeProcessor {
    /** An idle machine looks again this often, so a milestone completed meanwhile starts it without an inventory change. */
    private static final int RETRY_TICKS = 20;

    private final ResourceLocation machine;
    private final SlotLayout layout;
    @Nullable
    private RecipeHolder<MachineRecipe> current;
    @Nullable
    private ResourceLocation savedRecipeId;
    private boolean inventoryChanged = true;
    private int progress;
    private int maxProgress;
    private MachineStatus status = MachineStatus.IDLE;

    public RecipeProcessor(ResourceLocation machine, SlotLayout layout) {
        this.machine = machine;
        this.layout = layout;
    }

    public void onInventoryChanged() {
        inventoryChanged = true;
    }

    /** Stops without losing anything; returns true when the saved state changed. */
    public boolean halt(MachineStatus why) {
        boolean changed = progress != 0;
        progress = 0;
        status = why;
        return changed;
    }

    /** One server tick; returns true when the saved state changed. */
    public boolean tick(ServerLevel level, IItemHandlerModifiable items, MachineEnergyStorage energy, long progressionId) {
        MinecraftServer server = level.getServer();
        if (!Progressions.isServing(server, progressionId)) {
            return halt(MachineStatus.STOPPED);
        }
        if (inventoryChanged || (current == null && level.getGameTime() % RETRY_TICKS == 0)) {
            inventoryChanged = false;
            ResourceLocation previous = current != null ? current.id() : savedRecipeId;
            RecipeHolder<MachineRecipe> found = find(level, items, progressionId);
            if (found == null || !found.id().equals(previous)) {
                progress = 0;
            }
            current = found;
            if (found != null) {
                savedRecipeId = null;
            }
        }
        if (current == null) {
            return false;
        }
        MachineRecipe recipe = current.value();
        if (!Stages.isUnlocked(server, progressionId, recipe.stage())) {
            current = null;
            return halt(MachineStatus.LOCKED);
        }
        maxProgress = recipe.time();
        if (!energy.consume(MachineEnergyStorage.perTick(recipe.energy(), recipe.time()))) {
            status = MachineStatus.NO_ENERGY;
            return false;
        }
        status = MachineStatus.RUNNING;
        progress++;
        if (progress >= maxProgress) {
            progress = 0;
            commit(items, recipe);
        }
        return true;
    }

    @Nullable
    private RecipeHolder<MachineRecipe> find(ServerLevel level, IItemHandlerModifiable items, long progressionId) {
        List<ItemStack> inputs = inputs(items);
        if (inputs.stream().allMatch(ItemStack::isEmpty)) {
            status = MachineStatus.IDLE;
            return null;
        }
        List<RecipeHolder<MachineRecipe>> candidates = level.getRecipeManager().getAllRecipesFor(ModRecipes.MACHINE_TYPE.get()).stream()
                .filter(holder -> holder.value().machine().equals(machine))
                .sorted(Comparator.comparing((RecipeHolder<MachineRecipe> holder) -> !isPreferred(holder)).thenComparing(holder -> holder.id().toString()))
                .toList();
        MachineStatus best = MachineStatus.IDLE;
        for (RecipeHolder<MachineRecipe> holder : candidates) {
            MachineRecipe recipe = holder.value();
            if (recipe.allocate(inputs).isEmpty()) {
                continue;
            }
            if (!Stages.isUnlocked(level.getServer(), progressionId, recipe.stage())) {
                best = MachineStatus.LOCKED;
                continue;
            }
            if (!resultsFit(items, recipe)) {
                best = best == MachineStatus.LOCKED ? best : MachineStatus.OUTPUT_FULL;
                continue;
            }
            return holder;
        }
        status = best;
        return null;
    }

    private boolean isPreferred(RecipeHolder<MachineRecipe> holder) {
        ResourceLocation preferred = current != null ? current.id() : savedRecipeId;
        return holder.id().equals(preferred);
    }

    private List<ItemStack> inputs(IItemHandlerModifiable items) {
        List<ItemStack> stacks = new ArrayList<>(layout.inputs());
        for (int slot = 0; slot < layout.inputs(); slot++) {
            stacks.add(items.getStackInSlot(slot));
        }
        return stacks;
    }

    private boolean resultsFit(IItemHandlerModifiable items, MachineRecipe recipe) {
        if (recipe.results().size() > layout.outputs()) {
            return false;
        }
        for (int i = 0; i < recipe.results().size(); i++) {
            int slot = layout.outputSlot(i);
            ItemStack result = recipe.results().get(i);
            if (!items.insertItem(slot, result.copy(), true).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void commit(IItemHandlerModifiable items, MachineRecipe recipe) {
        Optional<int[]> allocation = recipe.allocate(inputs(items));
        if (allocation.isEmpty() || !resultsFit(items, recipe)) {
            current = null;
            return;
        }
        int[] taken = allocation.get();
        for (int slot = 0; slot < layout.inputs(); slot++) {
            if (taken[slot] > 0) {
                items.extractItem(slot, taken[slot], false);
            }
        }
        for (int i = 0; i < recipe.results().size(); i++) {
            items.insertItem(layout.outputSlot(i), recipe.results().get(i).copy(), false);
        }
    }

    public int progress() {
        return progress;
    }

    public int maxProgress() {
        return maxProgress;
    }

    public MachineStatus status() {
        return status;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("progress", progress);
        tag.putInt("maxProgress", maxProgress);
        ResourceLocation recipeId = current != null ? current.id() : savedRecipeId;
        if (recipeId != null) {
            tag.putString("recipe", recipeId.toString());
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        progress = Math.max(0, tag.getInt("progress"));
        maxProgress = Math.max(0, tag.getInt("maxProgress"));
        savedRecipeId = tag.contains("recipe") ? ResourceLocation.tryParse(tag.getString("recipe")) : null;
        current = null;
        inventoryChanged = true;
    }
}
