package fr.thegostsniperfr.arffornia.recipe;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.progression.Stages;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** The {@code arffornia:machine} recipes run by the Crafter, read from the synced {@link RecipeManager}. */
public final class CrafterRecipes {
    public static final ResourceLocation MACHINE = ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "crafter");

    private CrafterRecipes() {
    }

    public static List<RecipeHolder<MachineRecipe>> all(RecipeManager manager) {
        return manager.getAllRecipesFor(ModRecipes.MACHINE_TYPE.get()).stream()
                .filter(holder -> holder.value().machine().equals(MACHINE))
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .toList();
    }

    public static List<RecipeHolder<MachineRecipe>> unlocked(RecipeManager manager, Set<String> unlockedStages) {
        return all(manager).stream().filter(holder -> holder.value().isUnlockedBy(unlockedStages)).toList();
    }

    @SuppressWarnings("unchecked")
    public static Optional<RecipeHolder<MachineRecipe>> byId(RecipeManager manager, ResourceLocation id) {
        return manager.byKey(id)
                .filter(holder -> holder.value() instanceof MachineRecipe recipe && recipe.machine().equals(MACHINE))
                .map(holder -> (RecipeHolder<MachineRecipe>) holder);
    }

    /** Whether the progression a Crafter serves may run this recipe. Unknown and retired progressions fail closed. */
    public static boolean isUnlocked(MinecraftServer server, long progressionId, MachineRecipe recipe) {
        return Stages.isUnlocked(server, progressionId, recipe.stage());
    }

    public static Set<String> unlockedStages(MinecraftServer server, long progressionId) {
        return Stages.unlockedStages(server, progressionId);
    }

    public static String milestoneStage(int milestoneId) {
        return Stages.milestoneStage(milestoneId);
    }
}
