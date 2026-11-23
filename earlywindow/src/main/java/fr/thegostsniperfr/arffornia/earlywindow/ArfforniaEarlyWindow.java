package fr.thegostsniperfr.arffornia.earlywindow;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.neoforged.fml.earlydisplay.ColourScheme;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.earlydisplay.RenderElement;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arffornia's early loading window, selected by {@code earlyWindowProvider = "arffornia"} in
 * config/fml.toml. FML 4 (NeoForge 1.21.1) has no theming API and NeoForge's loading overlay only
 * accepts a {@link DisplayWindow}, so this keeps FML's window, handoff and overlay and swaps what
 * is painted. The private fields used below are pinned to FML 4.0.x; a NeoForge bump must re-run
 * {@code :earlywindow:visualTest}.
 */
public class ArfforniaEarlyWindow extends DisplayWindow {
    public static final String NAME = "arffornia";
    private static final Logger LOGGER = LoggerFactory.getLogger("ArfforniaEarlyWindow");

    private EarlyScene scene;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Runnable initialize(String[] arguments) {
        Path gameDir = FMLPaths.GAMEDIR.get();
        EarlyStrings strings = EarlyStrings.forGameDir(gameDir);
        String motd = MotdCache.read(gameDir).orElseGet(() -> strings.get("gui.arffornia.title.tagline"));
        scene = new EarlyScene(strings, motd, argument(arguments, "--fml.mcVersion", "1.21.1"));
        Runnable tick = super.initialize(arguments);
        // initRender was queued on the single render thread just before; a task queued now runs
        // after it and before the first 50 ms paint.
        ScheduledExecutorService renderThread = read("renderScheduler");
        renderThread.schedule(this::takeOver, 2, TimeUnit.MILLISECONDS);
        return tick;
    }

    /**
     * DisplayWindow's version calls {@code getClass().getModule().addReads(...)} from FML's module,
     * which the JVM refuses once getClass() is this subclass in another module. Same work, from here.
     */
    @Override
    public void updateModuleReads(ModuleLayer layer) {
        Module neoforge = layer.findModule("neoforge").orElseThrow();
        getClass().getModule().addReads(neoforge);
        Class<?> overlay = Class.forName(neoforge, "net.neoforged.neoforge.client.loading.NeoForgeLoadingOverlay");
        Method newInstance = Arrays.stream(overlay.getMethods())
                .filter(m -> Modifier.isStatic(m.getModifiers()) && m.getName().equals("newInstance"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("NeoForgeLoadingOverlay.newInstance not found"));
        write("loadingOverlay", newInstance);
    }

    private void takeOver() {
        write("colourScheme", ColourScheme.BLACK);
        RenderElement.DisplayContext ctx = context();
        // Letterboxing and the overlay's edge fill use the scheme's background: black, not FML red.
        write("context", new RenderElement.DisplayContext(ctx.width(), ctx.height(), ctx.scale(), ctx.elementShader(), ColourScheme.BLACK, ctx.performance()));
        write("elements", new SceneElements(this, scene));
        LOGGER.info("Arffornia early window active");
    }

    static String argument(String[] arguments, String name, String fallback) {
        for (int i = 0; i < arguments.length - 1; i++) {
            if (arguments[i].equals(name)) {
                return arguments[i + 1];
            }
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private <T> T read(String name) {
        try {
            return (T) field(name).get(this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FML DisplayWindow." + name + " changed: update the Arffornia early window", e);
        }
    }

    private void write(String name, Object value) {
        try {
            field(name).set(this, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FML DisplayWindow." + name + " changed: update the Arffornia early window", e);
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field f = DisplayWindow.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    /**
     * DisplayWindow paints with {@code elements.removeIf(e -> !e.render(...))}; this list paints the
     * scene there instead, and ignores the Mojang logo NeoForge adds for the overlay phase.
     */
    static final class SceneElements extends ArrayList<RenderElement> {
        private final transient DisplayWindow window;
        private final transient EarlyScene scene;

        SceneElements(DisplayWindow window, EarlyScene scene) {
            this.window = window;
            this.scene = scene;
        }

        @Override
        public boolean removeIf(Predicate<? super RenderElement> filter) {
            scene.paint(window.context());
            return false;
        }

        @Override
        public boolean add(RenderElement element) {
            return false;
        }

        @Override
        public void add(int index, RenderElement element) {}

        @Override
        public boolean addAll(java.util.Collection<? extends RenderElement> c) {
            return false;
        }
    }

    static List<String> requiredFields() {
        return List.of("renderScheduler", "colourScheme", "context", "elements", "loadingOverlay");
    }
}
