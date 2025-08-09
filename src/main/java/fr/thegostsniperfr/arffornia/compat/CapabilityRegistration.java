package fr.thegostsniperfr.arffornia.compat;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.block.entity.ModBlockEntities;
import fr.thegostsniperfr.arffornia.block.entity.SpaceElevatorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@EventBusSubscriber(modid = Arffornia.MODID, bus = EventBusSubscriber.Bus.MOD)
public class CapabilityRegistration {

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        // --- Space Elevator Capabilities  ---
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.SPACE_ELEVATOR_BE.get(),
                (blockEntity, side) -> blockEntity.getAutomationItemHandler()
        );

        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, be, side) -> {
                    if (side == Direction.UP) {
                        return null;
                    }

                    BlockPos mainBlockPos = pos.below();
                    if (level.getBlockEntity(mainBlockPos) instanceof SpaceElevatorBlockEntity mainBe) {
                        return mainBe.getAutomationItemHandler();
                    }

                    return null;
                },
                ModBlocks.SPACE_ELEVATOR_PART_BLOCK.get()
        );

        // --- CRAFTER CAPABILITIES ---
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.CRAFTER_BE.get(),
                (be, side) -> {
                    if (side == null) return be.itemHandler;
                    return switch (side) {
                        case DOWN -> be.automationOutputDownHandler;
                        case EAST -> be.automationOutputEastHandler;
                        default -> be.automationInputHandler;
                    };
                }
        );

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.CRAFTER_BE.get(),
                (be, side) -> be.energyStorage
        );
    }
}