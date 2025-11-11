package fr.thegostsniperfr.arffornia;

import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.client.Keybindings;
import fr.thegostsniperfr.arffornia.client.screen.CrafterScreen;
import fr.thegostsniperfr.arffornia.client.screen.SpaceElevatorScreen;
import fr.thegostsniperfr.arffornia.recipe.ClientRecipeCache;
import fr.thegostsniperfr.arffornia.screen.ModMenuTypes;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = Arffornia.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ArfforniaClient {

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        Arffornia.LOGGER.info("Client setup: Triggering ClientRecipeCache loading.");
        ClientRecipeCache.loadRecipes();

        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.CRAFTER_BLOCK.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.CRAFTER_PART_BLOCK.get(), RenderType.cutout());

            ItemBlockRenderTypes.setRenderLayer(ModBlocks.SPACE_ELEVATOR.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.SPACE_ELEVATOR_PART_BLOCK.get(), RenderType.cutout());
        });
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.SPACE_ELEVATOR_MENU.get(), SpaceElevatorScreen::new);
        event.register(ModMenuTypes.CRAFTER_MENU.get(), CrafterScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(Keybindings.OPEN_GRAPH_KEY);
    }
}