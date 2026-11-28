package fr.thegostsniperfr.arffornia.machine;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.machine.MachineType.RecipeSelection;
import fr.thegostsniperfr.arffornia.recipe.CrafterRecipes;
import net.minecraft.resources.ResourceLocation;

public final class MachineTypes {
    public static final MachineType SMELTER = new MachineType(ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "smelter"),
            new SlotLayout(2, 2), 20_000, 1_000, "block.arffornia.smelter", RecipeSelection.AUTO);
    public static final MachineType CRAFTER = new MachineType(CrafterRecipes.MACHINE,
            new SlotLayout(9, 2), 12_000, 1_000, "block.arffornia.crafter_block", RecipeSelection.SELECTED);

    private MachineTypes() {
    }
}
