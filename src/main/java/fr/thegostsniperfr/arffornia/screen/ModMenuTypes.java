package fr.thegostsniperfr.arffornia.screen;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Arffornia.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<SpaceElevatorMenu>> SPACE_ELEVATOR_MENU =
            MENUS.register("space_elevator_menu",
                    () -> IMenuTypeExtension.create(SpaceElevatorMenu::new));


    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}