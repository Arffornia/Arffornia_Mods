package fr.thegostsniperfr.arffornia.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Writes each multiblock from a readable layer pattern: the structure template the server matches (and GuideME can
 * import), its definition and its guide page. Layers go bottom to top; in a layer, rows go from the front (north) to
 * the back and characters from west to east. The formed model is a GeckoLib asset from Arffornia_Assets.
 */
public final class MultiblockStructureProvider implements DataProvider {
    private record Multiblock(String id, String trigger, String master, String[][] layers, Map<Character, BlockState> keys) {
    }

    private final PackOutput output;

    public MultiblockStructureProvider(PackOutput output) {
        this.output = output;
    }

    private static List<Multiblock> multiblocks() {
        return List.of(new Multiblock("smelter", "arffornia:smelter_controller", "arffornia:smelter",
                new String[][]{
                        {"CCC", "CCC", "CCC"},
                        {"BKB", "O#I", "BEB"},
                        {"BBB", "BBB", "BBB"},
                },
                Map.of(
                        'C', ModBlocks.MACHINE_CASING.get().defaultBlockState(),
                        'B', Blocks.BRICKS.defaultBlockState(),
                        '#', Blocks.MAGMA_BLOCK.defaultBlockState(),
                        'K', ModBlocks.SMELTER_CONTROLLER.get().defaultBlockState(),
                        'I', ModBlocks.ITEM_INPUT_PORT.get().defaultBlockState(),
                        'O', ModBlocks.ITEM_OUTPUT_PORT.get().defaultBlockState(),
                        'E', ModBlocks.ENERGY_INPUT_PORT.get().defaultBlockState())));
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        Path data = output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(Arffornia.MODID);
        Path assets = output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(Arffornia.MODID);
        for (Multiblock multiblock : multiblocks()) {
            writes.add(CompletableFuture.runAsync(() -> writeNbt(cache, data.resolve("structure/multiblock/" + multiblock.id() + ".nbt"), template(multiblock))));
            writes.add(DataProvider.saveStable(cache, definition(multiblock), data.resolve("multiblock/" + multiblock.id() + ".json")));
            for (GuideMarkdown.Language language : GuideMarkdown.Language.values()) {
                writes.add(CompletableFuture.runAsync(() -> writeGuidePage(cache, assets, multiblock, language)));
            }
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    private static void writeGuidePage(CachedOutput cache, Path assets, Multiblock multiblock, GuideMarkdown.Language language) {
        String langCode = language.name().toLowerCase() + (language == GuideMarkdown.Language.EN ? "_us" : "_fr");
        Map<Character, String> blockIds = new LinkedHashMap<>();
        multiblock.keys().forEach((key, value) -> blockIds.put(key, BuiltInRegistries.BLOCK.getKey(value.getBlock()).toString()));
        String title = langValue(langCode, "block." + Arffornia.MODID + "." + multiblock.id());
        String page = GuideMarkdown.multiblockPage(language, title, multiblock.trigger(), List.of(multiblock.layers()), blockIds);
        String folder = language == GuideMarkdown.Language.EN ? "" : "_" + langCode + "/";
        byte[] content = page.getBytes(StandardCharsets.UTF_8);
        try {
            cache.writeIfNeeded(assets.resolve("guides/" + Arffornia.MODID + "/guide/" + folder + "multiblocks/" + multiblock.id() + ".md"), content,
                    Hashing.sha1().hashBytes(content));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String langValue(String langCode, String key) {
        try (InputStream stream = MultiblockStructureProvider.class.getResourceAsStream("/assets/" + Arffornia.MODID + "/lang/" + langCode + ".json")) {
            return com.google.gson.JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().get(key).getAsString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeNbt(CachedOutput cache, Path path, CompoundTag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, bytes);
            byte[] content = bytes.toByteArray();
            cache.writeIfNeeded(path, content, Hashing.sha1().hashBytes(content));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CompoundTag template(Multiblock multiblock) {
        List<Character> palette = new ArrayList<>(multiblock.keys().keySet().stream().sorted().toList());
        ListTag paletteTag = new ListTag();
        palette.forEach(key -> paletteTag.add(NbtUtils.writeBlockState(multiblock.keys().get(key))));
        ListTag blocks = new ListTag();
        String[][] layers = multiblock.layers();
        for (int y = 0; y < layers.length; y++) {
            for (int z = 0; z < layers[y].length; z++) {
                for (int x = 0; x < layers[y][z].length(); x++) {
                    char key = layers[y][z].charAt(x);
                    if (key == ' ') {
                        continue;
                    }
                    CompoundTag block = new CompoundTag();
                    block.put("pos", ints(x, y, z));
                    block.putInt("state", palette.indexOf(key));
                    blocks.add(block);
                }
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        tag.put("size", ints(layers[0][0].length(), layers.length, layers[0].length));
        tag.put("palette", paletteTag);
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        return tag;
    }

    private static ListTag ints(int... values) {
        ListTag list = new ListTag();
        for (int value : values) {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }

    private static JsonObject definition(Multiblock multiblock) {
        JsonObject json = new JsonObject();
        json.addProperty("structure", Arffornia.MODID + ":multiblock/" + multiblock.id());
        json.addProperty("trigger", multiblock.trigger());
        json.addProperty("master", multiblock.master());
        return json;
    }

    @Override
    public String getName() {
        return "Arffornia multiblocks";
    }
}
