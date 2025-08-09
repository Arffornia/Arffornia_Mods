package fr.thegostsniperfr.arffornia.block.entity;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Arffornia.MODID);

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpaceElevatorBlockEntity>> SPACE_ELEVATOR_BE =
            BLOCK_ENTITIES.register("space_elevator", () ->
                    BlockEntityType.Builder.of(SpaceElevatorBlockEntity::new,
                            ModBlocks.SPACE_ELEVATOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrafterBlockEntity>> CRAFTER_BE =
            BLOCK_ENTITIES.register("crafter_block", () ->
                    BlockEntityType.Builder.of(CrafterBlockEntity::new,
                            ModBlocks.CRAFTER_BLOCK.get()).build(null));


}