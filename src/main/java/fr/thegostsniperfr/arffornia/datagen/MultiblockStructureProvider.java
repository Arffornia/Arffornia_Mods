package fr.thegostsniperfr.arffornia.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Writes each multiblock from a readable layer pattern: the structure template the server matches (and GuideME can
 * import), its definition, and the placeholder model drawn once formed. Layers go bottom to top; in a layer, rows go
 * from the front (north) to the back and characters from west to east.
 */
public final class MultiblockStructureProvider implements DataProvider {
    private record Key(BlockState state, Map<String, String> textures) {
        static Key same(BlockState state, String texture) {
            return new Key(state, Map.of("all", texture));
        }

        String texture(Direction face) {
            return textures.getOrDefault(face.getSerializedName(), textures.getOrDefault(face.getAxis() == Direction.Axis.Y ? "end" : "side", textures.get("all")));
        }
    }

    private record Multiblock(String id, String trigger, String master, String particle, String[][] layers, Map<Character, Key> keys) {
    }

    /** Elements lie outside 0..16, where automatic UVs would sample the neighbouring sprites of the atlas. */
    private static final JsonArray FULL_FACE_UV = uv();

    private final PackOutput output;

    public MultiblockStructureProvider(PackOutput output) {
        this.output = output;
    }

    private static List<Multiblock> multiblocks() {
        return List.of(new Multiblock("smelter", "arffornia:smelter_controller", "arffornia:smelter", "minecraft:block/bricks",
                new String[][]{
                        {"CCC", "CCC", "CCC"},
                        {"BKB", "O#I", "BEB"},
                        {"BBB", "BBB", "BBB"},
                },
                Map.of(
                        'C', Key.same(ModBlocks.MACHINE_CASING.get().defaultBlockState(), "minecraft:block/smooth_stone"),
                        'B', Key.same(Blocks.BRICKS.defaultBlockState(), "minecraft:block/bricks"),
                        '#', Key.same(Blocks.MAGMA_BLOCK.defaultBlockState(), "minecraft:block/magma"),
                        'K', new Key(ModBlocks.SMELTER_CONTROLLER.get().defaultBlockState(), Map.of(
                                "north", "minecraft:block/blast_furnace_front_on", "side", "minecraft:block/blast_furnace_side",
                                "end", "minecraft:block/blast_furnace_top")),
                        'I', Key.same(ModBlocks.ITEM_INPUT_PORT.get().defaultBlockState(), "minecraft:block/lapis_block"),
                        'O', Key.same(ModBlocks.ITEM_OUTPUT_PORT.get().defaultBlockState(), "minecraft:block/orange_concrete"),
                        'E', Key.same(ModBlocks.ENERGY_INPUT_PORT.get().defaultBlockState(), "minecraft:block/redstone_block"))));
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        Path data = output.getOutputFolder(PackOutput.Target.DATA_PACK).resolve(Arffornia.MODID);
        Path assets = output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(Arffornia.MODID);
        for (Multiblock multiblock : multiblocks()) {
            writes.add(CompletableFuture.runAsync(() -> writeNbt(cache, data.resolve("structure/multiblock/" + multiblock.id() + ".nbt"), template(multiblock))));
            writes.add(DataProvider.saveStable(cache, definition(multiblock), data.resolve("multiblock/" + multiblock.id() + ".json")));
            writes.add(DataProvider.saveStable(cache, formedModel(multiblock), assets.resolve("models/block/" + multiblock.id() + "_formed.json")));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
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
        palette.forEach(key -> paletteTag.add(NbtUtils.writeBlockState(multiblock.keys().get(key).state())));
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

    /** One cube per block around the centre block, outer faces only: a stand-in until the real model is wired. */
    private static JsonObject formedModel(Multiblock multiblock) {
        String[][] layers = multiblock.layers();
        int centreX = layers[0][0].length() / 2;
        int centreY = layers.length / 2;
        int centreZ = layers[0].length / 2;
        JsonObject textures = new JsonObject();
        JsonArray elements = new JsonArray();
        for (int y = 0; y < layers.length; y++) {
            for (int z = 0; z < layers[y].length; z++) {
                for (int x = 0; x < layers[y][z].length(); x++) {
                    char key = layers[y][z].charAt(x);
                    if (key == ' ') {
                        continue;
                    }
                    JsonObject faces = new JsonObject();
                    for (Direction face : Direction.values()) {
                        if (filled(layers, x + face.getStepX(), y + face.getStepY(), z + face.getStepZ())) {
                            continue;
                        }
                        String texture = multiblock.keys().get(key).texture(face);
                        String variable = texture.substring(texture.lastIndexOf('/') + 1);
                        textures.addProperty(variable, texture);
                        JsonObject faceJson = new JsonObject();
                        faceJson.add("uv", FULL_FACE_UV);
                        faceJson.addProperty("texture", "#" + variable);
                        faces.add(face.getSerializedName(), faceJson);
                    }
                    if (faces.isEmpty()) {
                        continue;
                    }
                    JsonObject element = new JsonObject();
                    element.add("from", vector((x - centreX) * 16, (y - centreY) * 16, (z - centreZ) * 16));
                    element.add("to", vector((x - centreX + 1) * 16, (y - centreY + 1) * 16, (z - centreZ + 1) * 16));
                    element.add("faces", faces);
                    elements.add(element);
                }
            }
        }
        textures.addProperty("particle", multiblock.particle());
        JsonObject model = new JsonObject();
        model.add("textures", textures);
        model.add("elements", elements);
        return model;
    }

    private static boolean filled(String[][] layers, int x, int y, int z) {
        return y >= 0 && y < layers.length && z >= 0 && z < layers[y].length && x >= 0 && x < layers[y][z].length()
                && layers[y][z].charAt(x) != ' ';
    }

    private static JsonArray uv() {
        JsonArray array = new JsonArray();
        array.add(0);
        array.add(0);
        array.add(16);
        array.add(16);
        return array;
    }

    private static JsonArray vector(int x, int y, int z) {
        JsonArray array = new JsonArray();
        array.add(x);
        array.add(y);
        array.add(z);
        return array;
    }

    @Override
    public String getName() {
        return "Arffornia multiblocks";
    }
}
