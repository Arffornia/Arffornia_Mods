package fr.thegostsniperfr.arffornia.client.ui.band;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.thegostsniperfr.arffornia.Arffornia;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Spec 018 prototype harness: opens the native (LDLib2) or web (WebGUI) stage band and measures
 * open time and frame time. Driven by {@code /arfband native|web|bench} or, for headless runs, by
 * writing one of those words to {@code <game dir>/arffornia-band/cmd}.
 */
@EventBusSubscriber(modid = Arffornia.MODID, value = Dist.CLIENT)
public final class BandBench {
    private static final Path DIR = FMLPaths.GAMEDIR.get().resolve("arffornia-band");
    private static final Path CMD = DIR.resolve("cmd");

    private static StageBand band;
    private static long openRequestedAt;
    private static String openKind;
    private static boolean recording;
    private static long lastFrameAt;
    private static final List<Long> frameIntervals = new ArrayList<>();
    private static final List<Long> renderCosts = new ArrayList<>();
    private static final Queue<Step> steps = new ArrayDeque<>();
    private static final Map<String, Object> results = new LinkedHashMap<>();
    private static long stepUntil;
    private static int tickCounter;

    private BandBench() {
    }

    public static StageBand band() {
        if (band == null) {
            band = StageBand.load();
        }
        return band;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arfband")
                .then(Commands.argument("what", StringArgumentType.word()).executes(ctx -> {
                    String what = StringArgumentType.getString(ctx, "what");
                    Minecraft.getInstance().tell(() -> run(what));
                    return Command.SINGLE_SUCCESS;
                })));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (++tickCounter % 10 == 0 && Files.isRegularFile(CMD)) {
            try {
                String what = Files.readString(CMD).trim();
                Files.delete(CMD);
                run(what);
            } catch (IOException e) {
                Arffornia.LOGGER.warn("[band] cannot read {}", CMD, e);
            }
        }
        if (!steps.isEmpty() && System.nanoTime() >= stepUntil) {
            Step step = steps.poll();
            step.action.run();
            stepUntil = System.nanoTime() + step.holdMillis * 1_000_000L;
        }
    }

    @SubscribeEvent
    public static void onFramePost(RenderFrameEvent.Post event) {
        long now = System.nanoTime();
        if (recording && lastFrameAt != 0) {
            frameIntervals.add(now - lastFrameAt);
        }
        lastFrameAt = now;
    }

    public static void run(String what) {
        switch (what) {
            case "native", "web" -> open(what);
            case "close" -> Minecraft.getInstance().setScreen(null);
            case "bench" -> scheduleBench();
            default -> Arffornia.LOGGER.warn("[band] unknown command {}", what);
        }
    }

    public static boolean available(String kind) {
        return switch (kind) {
            case "native" -> ModList.get().isLoaded("ldlib2");
            case "web" -> ModList.get().isLoaded("webgui");
            default -> false;
        };
    }

    private static void open(String kind) {
        if (!available(kind)) {
            Arffornia.LOGGER.warn("[band] {} renderer unavailable: its library is not installed", kind);
            return;
        }
        openKind = kind;
        openRequestedAt = System.nanoTime();
        Screen screen = kind.equals("native") ? NativeBandScreens.create() : WebBandScreens.create();
        Minecraft.getInstance().setScreen(screen);
        @SuppressWarnings("unchecked")
        List<Double> builds = (List<Double>) results.computeIfAbsent("build_ms_" + kind, k -> new ArrayList<Double>());
        builds.add((System.nanoTime() - openRequestedAt) / 1e6);
    }

    /** Called by both screens at the end of each render pass with the time spent inside it. */
    public static void rendered(String kind, long renderNanos) {
        if (openRequestedAt != 0 && kind.equals(openKind)) {
            double ms = (System.nanoTime() - openRequestedAt) / 1e6;
            openRequestedAt = 0;
            Arffornia.LOGGER.info("[band] open {} first frame after {} ms", kind, String.format("%.1f", ms));
            @SuppressWarnings("unchecked")
            List<Double> opens = (List<Double>) results.computeIfAbsent("open_ms_" + kind, k -> new ArrayList<Double>());
            opens.add(ms);
        }
        if (recording) {
            renderCosts.add(renderNanos);
        }
    }

    private static void scheduleBench() {
        results.clear();
        results.put("window", Minecraft.getInstance().getWindow().getWidth() + "x" + Minecraft.getInstance().getWindow().getHeight());
        results.put("gui_scale", Minecraft.getInstance().getWindow().getGuiScale());
        results.put("in_world", Minecraft.getInstance().level != null);
        results.put("rss_start_kb", rssKb());
        steps.add(new Step(() -> Minecraft.getInstance().setScreen(null), 3000));
        steps.add(new Step(BandBench::startRecording, 10000));
        steps.add(new Step(() -> stopRecording("baseline"), 500));
        for (String kind : new String[]{"native", "web"}) {
            if (!available(kind)) {
                continue;
            }
            for (int i = 0; i < 5; i++) {
                steps.add(new Step(() -> open(kind), 1500));
                steps.add(new Step(() -> Minecraft.getInstance().setScreen(null), 1500));
            }
            steps.add(new Step(() -> open(kind), 2000));
            steps.add(new Step(BandBench::startRecording, 10000));
            steps.add(new Step(() -> stopRecording(kind), 0));
            steps.add(new Step(() -> Minecraft.getInstance().setScreen(null), 2000));
        }
        steps.add(new Step(BandBench::writeResults, 0));
        stepUntil = 0;
        Arffornia.LOGGER.info("[band] bench scheduled ({} steps)", steps.size());
    }

    private static void startRecording() {
        frameIntervals.clear();
        renderCosts.clear();
        lastFrameAt = 0;
        recording = true;
    }

    private static void stopRecording(String label) {
        recording = false;
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("frames", frameIntervals.size());
        stats.put("frame_ms", percentiles(frameIntervals));
        stats.put("screen_render_ms", percentiles(renderCosts));
        Runtime rt = Runtime.getRuntime();
        stats.put("heap_used_mb", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        stats.put("rss_kb", rssKb());
        results.put(label, stats);
        Arffornia.LOGGER.info("[band] {}: {}", label, stats);
    }

    private static Map<String, Double> percentiles(List<Long> nanos) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (nanos.isEmpty()) {
            return out;
        }
        long[] sorted = nanos.stream().mapToLong(Long::longValue).sorted().toArray();
        out.put("mean", Arrays.stream(sorted).average().orElse(0) / 1e6);
        for (int p : new int[]{50, 95, 99}) {
            out.put("p" + p, sorted[Math.min(sorted.length - 1, sorted.length * p / 100)] / 1e6);
        }
        out.put("max", sorted[sorted.length - 1] / 1e6);
        return out;
    }

    private static long rssKb() {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
                if (line.startsWith("VmRSS:")) {
                    return Long.parseLong(line.replaceAll("\\D", ""));
                }
            }
        } catch (IOException | RuntimeException e) {
            return -1;
        }
        return -1;
    }

    private static void writeResults() {
        try {
            Files.createDirectories(DIR);
            Path out = DIR.resolve("bench-" + System.currentTimeMillis() + ".json");
            Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(results));
            Arffornia.LOGGER.info("[band] bench written to {}", out);
        } catch (IOException e) {
            Arffornia.LOGGER.error("[band] cannot write bench results", e);
        }
    }

    private record Step(Runnable action, long holdMillis) {
    }
}
