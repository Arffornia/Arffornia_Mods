package fr.thegostsniperfr.arffornia.recipe;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.progression.Progressions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
        return Progressions.find(server, progressionId)
                .filter(snapshot -> !snapshot.retired())
                .map(snapshot -> recipe.stage().isEmpty() || milestoneOfStage(recipe.stage().get())
                        .map(snapshot::isCompleted).orElse(false))
                .orElse(false);
    }

    static Optional<Integer> milestoneOfStage(String stage) {
        String prefix = Arffornia.MODID + ":milestone/";
        if (!stage.startsWith(prefix)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(stage.substring(prefix.length())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static Set<String> unlockedStages(MinecraftServer server, long progressionId) {
        return Progressions.find(server, progressionId)
                .filter(snapshot -> !snapshot.retired())
                .map(snapshot -> stagesOfMilestones(snapshot.completedMilestones()))
                .orElse(Set.of());
    }

    /**
     * Stage keys unlocked by completed website milestones. Milestones have no stable keys yet (spec 017), so the
     * website datapack names a milestone's stage {@code arffornia:milestone/<id>} until they do.
     */
    public static Set<String> stagesOfMilestones(Collection<Integer> completedMilestoneIds) {
        return completedMilestoneIds.stream().map(CrafterRecipes::milestoneStage).collect(Collectors.toUnmodifiableSet());
    }

    public static String milestoneStage(int milestoneId) {
        return Arffornia.MODID + ":milestone/" + milestoneId;
    }
}
