package fr.thegostsniperfr.arffornia.gametest;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.machine.MachineData;
import fr.thegostsniperfr.arffornia.machine.MachineEnergyStorage;
import fr.thegostsniperfr.arffornia.machine.SideConfig;
import fr.thegostsniperfr.arffornia.machine.SideMode;
import fr.thegostsniperfr.arffornia.machine.SlotLayout;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The machine framework's building blocks. They need Minecraft classes, which the JUnit classpath lacks, so they run
 * as GameTests.
 */
@GameTestHolder(Arffornia.MODID)
@PrefixGameTestTemplate(false)
public final class MachineFrameworkGameTests {
    private static final String PLATFORM = "platform";

    private MachineFrameworkGameTests() {
    }

    private static MachineEnergyStorage storage() {
        return new MachineEnergyStorage(1000, 100, () -> { });
    }

    @GameTest(template = PLATFORM)
    public static void slotLayoutPutsInputsBeforeOutputs(GameTestHelper helper) {
        SlotLayout layout = new SlotLayout(2, 3);

        helper.assertTrue(layout.isInput(1) && !layout.isInput(2) && layout.isOutput(2) && layout.isOutput(4) && !layout.isOutput(5),
                "Slots 0-1 are inputs, 2-4 outputs");
        helper.assertTrue(layout.outputSlot(1) == 3, "The second output is slot 3");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void energyIsClampedToTheCapacityWhenLoaded(GameTestHelper helper) {
        MachineEnergyStorage storage = storage();

        storage.setEnergy(5000);

        helper.assertTrue(storage.getEnergyStored() == 1000, "Energy should be clamped, got " + storage.getEnergyStored());
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void cablesCannotDrainAMachineAndConsumeIsAllOrNothing(GameTestHelper helper) {
        MachineEnergyStorage storage = storage();
        storage.setEnergy(10);

        int extracted = storage.extractEnergy(5, false);
        boolean consumed = storage.consume(11);

        helper.assertTrue(extracted == 0, "Cables must not drain a machine");
        helper.assertFalse(consumed, "Consuming more than stored must fail");
        helper.assertTrue(storage.getEnergyStored() == 10, "A failed consume must not draw energy");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void energyPerTickRoundsUp(GameTestHelper helper) {
        helper.assertTrue(MachineEnergyStorage.perTick(10, 20) == 1, "A cheap recipe still costs 1 FE per tick");
        helper.assertTrue(MachineEnergyStorage.perTick(0, 20) == 0, "A free recipe costs nothing");
        helper.assertTrue(MachineEnergyStorage.perTick(100, 3) == 34, "100 FE over 3 ticks rounds up to 34");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void energyBeyondAShortSurvivesTheMenuSync(GameTestHelper helper) {
        int energy = 1_234_567;

        int synced = MachineData.join((short) MachineData.low(energy), (short) MachineData.high(energy));

        helper.assertTrue(synced == energy, "Energy should survive the 16-bit sync, got " + synced);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void sideConfigCyclesOnlyThroughAllowedModes(GameTestHelper helper) {
        SideConfig config = new SideConfig(List.of(SideMode.INPUT, SideMode.NONE));

        SideMode first = config.cycle(Direction.EAST);
        SideMode second = config.cycle(Direction.EAST);

        helper.assertTrue(first == SideMode.NONE && second == SideMode.INPUT, "An input face toggles between input and off");
        helper.assertTrue(config.get(Direction.UP) == SideMode.INPUT, "Other faces keep their mode");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void sideConfigIgnoresSavedModesItDoesNotAllow(GameTestHelper helper) {
        CompoundTag saved = new CompoundTag();
        saved.putString("east", "none");
        saved.putString("west", "output");
        SideConfig config = new SideConfig(List.of(SideMode.INPUT, SideMode.NONE));

        config.load(saved);

        helper.assertTrue(config.get(Direction.EAST) == SideMode.NONE, "An allowed saved mode is restored");
        helper.assertTrue(config.get(Direction.WEST) == SideMode.INPUT, "A mode the port cannot have is ignored");
        helper.succeed();
    }
}
