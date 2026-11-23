package fr.thegostsniperfr.arffornia.earlywindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.thegostsniperfr.arffornia.earlywindow.LoadingProgress.Bar;
import fr.thegostsniperfr.arffornia.earlywindow.LoadingProgress.Stage;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoadingProgressTest {
    @Test
    void startsAtTheLauncherShareBeforeFmlReportsAnything() {
        var progress = new LoadingProgress();

        var snap = progress.update(List.of(), 0);

        assertEquals(Stage.BOOT, snap.stage());
        assertEquals(0.04f, snap.fraction(), 1e-6);
    }

    @Test
    void showsModConstructionCountsWhileModsLoad() {
        var progress = new LoadingProgress();

        var snap = progress.update(List.of(new Bar("EARLY", 0, 0), new Bar("Mod Construction", 331, 132)), 2);

        assertEquals(Stage.MODS, snap.stage());
        assertEquals(132, snap.current());
        assertEquals(331, snap.total());
    }

    @Test
    void neverMovesBackwardsWhenAnEarlierBarReappears() {
        var progress = new LoadingProgress();
        progress.update(List.of(new Bar("Minecraft Progress", 1000, 400)), 10);

        var snap = progress.update(List.of(new Bar("Mod Construction", 331, 10)), 11);

        assertEquals(Stage.RESOURCES, snap.stage());
        assertEquals(0.82f, snap.fraction(), 1e-6);
    }

    @Test
    void reportsReadyWhenMinecraftProgressIsComplete() {
        var progress = new LoadingProgress();

        var snap = progress.update(List.of(new Bar("Minecraft Progress", 1000, 1000)), 14);

        assertEquals(Stage.READY, snap.stage());
        assertEquals(1f, snap.fraction(), 1e-6);
    }

    @Test
    void creepsForwardDuringDiscoveryWithoutPassingModLoading() {
        var progress = new LoadingProgress();

        float early = progress.update(List.of(new Bar("EARLY", 0, 0)), 1).fraction();
        float late = progress.update(List.of(new Bar("EARLY", 0, 0)), 60).fraction();

        assertTrue(late > early && late <= 0.15f, () -> early + " -> " + late);
    }

    @Test
    void treatsOtherCountedBarsAsContentRegistration() {
        var progress = new LoadingProgress();

        var snap = progress.update(List.of(new Bar("Common setup", 331, 100)), 8);

        assertEquals(Stage.REGISTRIES, snap.stage());
    }
}
