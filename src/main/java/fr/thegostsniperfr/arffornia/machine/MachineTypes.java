package fr.thegostsniperfr.arffornia.machine;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.resources.ResourceLocation;

public final class MachineTypes {
    public static final MachineType SMELTER = new MachineType(ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "smelter"),
            new SlotLayout(2, 2), 20_000, 1_000, "block.arffornia.smelter");

    private MachineTypes() {
    }
}
