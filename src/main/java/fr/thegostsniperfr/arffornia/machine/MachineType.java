package fr.thegostsniperfr.arffornia.machine;

import net.minecraft.resources.ResourceLocation;

/**
 * What makes one machine different from another.
 *
 * @param id             the {@code machine} field of the {@code arffornia:machine} recipes it runs
 * @param layout         its item slots
 * @param energyCapacity FE it can buffer
 * @param maxReceive     FE it accepts per tick
 * @param titleKey       lang key of its screen title
 * @param selection      whether it runs the first recipe its inputs match or only the one the player selected
 */
public record MachineType(ResourceLocation id, SlotLayout layout, int energyCapacity, int maxReceive, String titleKey,
                          RecipeSelection selection) {
    public enum RecipeSelection {
        AUTO, SELECTED
    }
}
