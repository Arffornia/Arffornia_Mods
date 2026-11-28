package fr.thegostsniperfr.arffornia.gametest;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import fr.thegostsniperfr.arffornia.block.ModBlocks;

/** Datagen output for mod-owned recipes, loot tables and tags. */
@GameTestHolder(Arffornia.MODID)
@PrefixGameTestTemplate(false)
public final class ModDataGameTests {
    private static final String PLATFORM = "platform";

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Arffornia.MODID, path);
    }

    @GameTest(template = PLATFORM)
    public static void machineCasingRecipeIsLoaded(GameTestHelper helper) {
        var recipe = helper.getLevel().getServer().getRecipeManager().byKey(id("machine_casing"));

        helper.assertTrue(recipe.isPresent(), "machine_casing recipe should be generated");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void spaceElevatorRecipeIsSkippedWithoutSophisticatedStorage(GameTestHelper helper) {
        var recipe = helper.getLevel().getServer().getRecipeManager().byKey(id("space_elevator"));

        helper.assertTrue(recipe.isEmpty(), "space_elevator recipe must not load without sophisticatedstorage");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void machineBlocksNeedAnIronPickaxe(GameTestHelper helper) {
        helper.assertTrue(ModBlocks.SPACE_ELEVATOR.get().defaultBlockState().is(BlockTags.NEEDS_IRON_TOOL), "needs iron tool");
        helper.assertTrue(ModBlocks.MACHINE_CASING.get().defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE), "mineable with pickaxe");
        helper.assertFalse(Blocks.STONE.defaultBlockState().is(BlockTags.NEEDS_IRON_TOOL), "vanilla stone untouched");
        helper.succeed();
    }
}
