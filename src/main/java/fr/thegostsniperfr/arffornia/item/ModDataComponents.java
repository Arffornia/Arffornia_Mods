package fr.thegostsniperfr.arffornia.item;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.crate.CrateKey;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Arffornia.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CrateKey>> CRATE_KEY =
            COMPONENTS.registerComponentType("crate_key", builder -> builder.persistent(CrateKey.CODEC).networkSynchronized(CrateKey.STREAM_CODEC));

    private ModDataComponents() {
    }

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }
}
