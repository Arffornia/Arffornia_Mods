package fr.thegostsniperfr.arffornia;

import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.block.entity.ModBlockEntities;
import fr.thegostsniperfr.arffornia.client.multiblock.MultiblockMachineRenderer;
import fr.thegostsniperfr.arffornia.client.Keybindings;
import fr.thegostsniperfr.arffornia.client.screen.CrafterScreen;
import fr.thegostsniperfr.arffornia.client.screen.MachineScreen;
import fr.thegostsniperfr.arffornia.client.screen.SpaceElevatorScreen;
import fr.thegostsniperfr.arffornia.screen.ModMenuTypes;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(modid = Arffornia.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ArfforniaClient {
    private static final ModelResourceLocation SMELTER_FORMED_MODEL =
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, "block/smelter_formed"));

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        final ModContainer modContainer = ModLoadingContext.get().getActiveContainer();

        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

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
        event.register(ModMenuTypes.MACHINE_MENU.get(), MachineScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(SMELTER_FORMED_MODEL);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.SMELTER_BE.get(), context -> new MultiblockMachineRenderer(context, SMELTER_FORMED_MODEL));
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(Keybindings.OPEN_GRAPH_KEY);
    }
}