package fr.thegostsniperfr.arffornia.item;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Arffornia.MODID);

    public static final DeferredItem<Item> WRENCH = ITEMS.register("wrench", WrenchItem::new);
    public static final DeferredItem<CrateKeyItem> CRATE_KEY = ITEMS.register("crate_key", CrateKeyItem::new);

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}