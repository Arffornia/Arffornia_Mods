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
 */
public record MachineType(ResourceLocation id, SlotLayout layout, int energyCapacity, int maxReceive, String titleKey) {
}
