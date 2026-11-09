package fr.thegostsniperfr.arffornia.block;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.block.crafterblock.CrafterBlock;
import fr.thegostsniperfr.arffornia.block.crafterblock.CrafterPartBlock;
import fr.thegostsniperfr.arffornia.block.spaceelevator.SpaceElevator;
import fr.thegostsniperfr.arffornia.block.spaceelevator.SpaceElevatorPartBlock;
import fr.thegostsniperfr.arffornia.item.ModItems;
import fr.thegostsniperfr.arffornia.machine.smelter.SmelterBlock;
import fr.thegostsniperfr.arffornia.multiblock.ControllerBlock;
import fr.thegostsniperfr.arffornia.multiblock.MultiblockPartBlock;
import fr.thegostsniperfr.arffornia.multiblock.PortBlock;
import fr.thegostsniperfr.arffornia.multiblock.PortKind;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
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

    public static final DeferredBlock<MultiblockPartBlock> MULTIBLOCK_PART = BLOCKS.register("multiblock_part", () -> new MultiblockPartBlock());

    public static final DeferredBlock<Block> MACHINE_CASING = registerBlock("machine_casing",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
    public static final DeferredBlock<ControllerBlock> SMELTER_CONTROLLER = registerBlock("smelter_controller",
            () -> new ControllerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.5F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<PortBlock> ITEM_INPUT_PORT = registerBlock("item_input_port", () -> port(PortKind.ITEM_INPUT));
    public static final DeferredBlock<PortBlock> ITEM_OUTPUT_PORT = registerBlock("item_output_port", () -> port(PortKind.ITEM_OUTPUT));
    public static final DeferredBlock<PortBlock> ENERGY_INPUT_PORT = registerBlock("energy_input_port", () -> port(PortKind.ENERGY_INPUT));
    public static final DeferredBlock<SmelterBlock> SMELTER = BLOCKS.register("smelter", () -> new SmelterBlock());

    private static PortBlock port(PortKind kind) {
        return new PortBlock(kind, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());
    }


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