package fr.thegostsniperfr.arffornia.creative;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Arffornia.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ARFFORNIA_TAB = CREATIVE_MODE_TABS.register("arffornia_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.WRENCH.get()))
                    .title(Component.translatable("creativetab.arffornia_tab"))
                    .displayItems((displayParameters, output) -> {
                        output.accept(ModItems.WRENCH.get());
                        output.accept(ModBlocks.CRAFTER_BLOCK.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}