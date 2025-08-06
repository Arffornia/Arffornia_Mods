package fr.thegostsniperfr.arffornia.block;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.block.crafterblock.CrafterBlock;
import fr.thegostsniperfr.arffornia.block.crafterblock.CrafterPartBlock;
import fr.thegostsniperfr.arffornia.block.spaceelevator.SpaceElevator;
import fr.thegostsniperfr.arffornia.block.spaceelevator.SpaceElevatorPartBlock;
import fr.thegostsniperfr.arffornia.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Arffornia.MODID);

    public static final DeferredBlock<Block> CRAFTER_BLOCK = registerBlock("crafter_block", CrafterBlock::new);
    public static final DeferredBlock<Block> CRAFTER_PART_BLOCK = BLOCKS.register("crafter_part_block", CrafterPartBlock::new);

    public static final DeferredBlock<Block> SPACE_ELEVATOR = registerBlock("space_elevator", SpaceElevator::new);
    public static final DeferredBlock<Block> SPACE_ELEVATOR_PART_BLOCK = BLOCKS.register("space_elevator_part_block", SpaceElevatorPartBlock::new);


    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}