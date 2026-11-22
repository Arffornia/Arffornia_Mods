package fr.thegostsniperfr.arffornia.progression;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCriteriaTriggers {
    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, Arffornia.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, ItemCraftedTrigger> ITEM_CRAFTED = TRIGGERS.register("item_crafted", ItemCraftedTrigger::new);

    private ModCriteriaTriggers() {
    }

    public static void register(IEventBus eventBus) {
        TRIGGERS.register(eventBus);
    }
}
