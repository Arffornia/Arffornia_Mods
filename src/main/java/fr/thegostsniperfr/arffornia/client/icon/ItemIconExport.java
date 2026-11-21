package fr.thegostsniperfr.arffornia.client.icon;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Headless-ish export of every item's inventory render, named by registry id, for the website icon
 * store (graph viewer, shop, wiki). Start the client with {@code -Darffornia.exportItemIcons=<dir>}:
 * once the title screen is up it renders each item at every size of
 * {@code -Darffornia.exportItemIcons.sizes} (default 36,128), writes {@code manifest.json} and quits.
 * Uses the normal GUI item renderer, so block items come out as the 3D inventory model. Renders that
 * need a level or a player (about 0.3 % of the pack) fail at the title screen and are listed under
 * {@code failed} in the manifest; a world doubles the memory needed, which a workstation may not have.
 */
@EventBusSubscriber(modid = Arffornia.MODID, value = Dist.CLIENT)
public final class ItemIconExport {

    public static final String STARTUP_PROPERTY = "arffornia.exportItemIcons";
    public static final String SIZES_PROPERTY = "arffornia.exportItemIcons.sizes";
    private static final int ITEMS_PER_TICK = 40;

    private static final long HALT_AFTER_MS = 60_000;

    private static Run run;
    private static boolean finished;

    private ItemIconExport() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (finished) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (run == null) {
            String dir = System.getProperty(STARTUP_PROPERTY);
            if (dir == null) {
                finished = true;
                return;
            }
            if (mc.getOverlay() != null || !(mc.screen instanceof TitleScreen)) {
                return;
            }
            run = new Run(Path.of(dir), IconFiles.parseSizes(System.getProperty(SIZES_PROPERTY, "36,128")));
            Arffornia.LOGGER.info("Item icon export: {} items to {}", BuiltInRegistries.ITEM.size(), dir);
        }
        try {
            if (run.step(mc)) {
                finished = true;
                run.finish();
                quit(mc);
            }
        } catch (IOException e) {
            Arffornia.LOGGER.error("Item icon export failed", e);
            finished = true;
            run.close();
            quit(mc);
        }
    }

    /**
     * Stops the client. Some mods keep non-daemon threads alive after shutdown, which would leave a
     * scripted export hanging, so the JVM is halted if it is still up a minute later.
     */
    private static void quit(Minecraft mc) {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(HALT_AFTER_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Arffornia.LOGGER.warn("Item icon export: client still running after shutdown, halting the JVM");
            Runtime.getRuntime().halt(0);
        }, "arffornia-icon-export-halt");
        watchdog.setDaemon(true);
        watchdog.start();
        mc.stop();
    }

    private static final class Run {
        private final Path dir;
        private final List<Integer> sizes;
        private final Map<Integer, TextureTarget> targets = new HashMap<>();
        private final Iterator<Item> items = BuiltInRegistries.ITEM.iterator();
        private final Map<String, String> hashes = new HashMap<>();
        private final List<String> failed = new ArrayList<>();
        private final long startedAt = System.nanoTime();

        Run(Path dir, List<Integer> sizes) {
            this.dir = dir;
            this.sizes = sizes;
            for (int size : sizes) {
                TextureTarget target = new TextureTarget(size, size, true, Minecraft.ON_OSX);
                target.setClearColor(0f, 0f, 0f, 0f);
                targets.put(size, target);
            }
        }

        /** Renders the next batch; true when every item is done. */
        boolean step(Minecraft mc) throws IOException {
            for (int n = 0; n < ITEMS_PER_TICK && items.hasNext(); n++) {
                Item item = items.next();
                if (item == Items.AIR) {
                    continue;
                }
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                exportOne(mc, id, new ItemStack(item));
            }
            mc.getMainRenderTarget().bindWrite(true);
            return !items.hasNext();
        }

        private void exportOne(Minecraft mc, ResourceLocation id, ItemStack stack) throws IOException {
            String largestHash = null;
            for (int size : sizes) {
                Optional<String> relative = IconFiles.relativePath(id.getNamespace(), id.getPath(), size);
                if (relative.isEmpty()) {
                    failed.add(id.toString());
                    return;
                }
                byte[] png;
                try {
                    png = render(mc, targets.get(size), size, stack);
                } catch (RuntimeException | LinkageError e) {
                    Arffornia.LOGGER.warn("Item icon export: {} failed to render: {}", id, e.toString());
                    failed.add(id.toString());
                    return;
                }
                Path file = dir.resolve(relative.get());
                Files.createDirectories(file.getParent());
                Files.write(file, png);
                largestHash = IconFiles.shortHash(png);
            }
            hashes.put(id.toString(), largestHash);
        }

        private static byte[] render(Minecraft mc, TextureTarget target, int size, ItemStack stack) throws IOException {
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            Matrix4f projection = RenderSystem.getProjectionMatrix();
            VertexSorting sorting = RenderSystem.getVertexSorting();
            Matrix4fStack modelView = RenderSystem.getModelViewStack();
            modelView.pushMatrix();
            try {
                // A 16x16 GUI slot mapped onto the whole target, same depth range as the GUI.
                RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, 16f, 16f, 0f, 1000f, 21000f),
                        VertexSorting.ORTHOGRAPHIC_Z);
                modelView.identity();
                modelView.translate(0f, 0f, -11000f);
                RenderSystem.applyModelViewMatrix();
                GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
                graphics.renderItem(stack, 0, 0);
                graphics.flush();
            } finally {
                mc.renderBuffers().bufferSource().endBatch();
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(projection, sorting);
            }
            try (NativeImage image = new NativeImage(size, size, false)) {
                RenderSystem.bindTexture(target.getColorTextureId());
                image.downloadTexture(0, false);
                image.flipY();
                return image.asByteArray();
            }
        }

        void finish() throws IOException {
            IconFiles.writeManifest(dir, sizes, hashes, failed, Instant.now());
            Arffornia.LOGGER.info("Item icon export: {} items, {} failed, {} s, manifest at {}",
                    hashes.size(), failed.size(), (System.nanoTime() - startedAt) / 1_000_000_000L,
                    dir.resolve("manifest.json"));
            close();
        }

        void close() {
            targets.values().forEach(TextureTarget::destroyBuffers);
        }
    }
}
