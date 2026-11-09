package fr.thegostsniperfr.arffornia.multiblock;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads {@code data/<ns>/multiblock/<path>.json} and the structure template each one names, which lives at
 * {@code data/<ns>/structure/<path>.nbt} (saved with a structure block, or by datagen). Air and structure voids in the
 * template are not part of the structure. A broken file is logged and skipped.
 */
public final class MultiblockLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();

    private record Json(ResourceLocation structure, Block trigger, Block master, Optional<String> stage) {
        static final Codec<Json> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("structure").forGetter(Json::structure),
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("trigger").forGetter(Json::trigger),
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("master").forGetter(Json::master),
                ExtraCodecs.NON_EMPTY_STRING.optionalFieldOf("stage").forGetter(Json::stage)
        ).apply(instance, Json::new));
    }

    public MultiblockLoader() {
        super(GSON, "multiblock");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        List<MultiblockDefinition> definitions = new ArrayList<>();
        files.forEach((id, json) -> {
            try {
                definitions.add(read(id, json, resources));
            } catch (IOException | RuntimeException e) {
                Arffornia.LOGGER.error("Skipping multiblock {}: {}", id, e.getMessage());
            }
        });
        MultiblockDefinitions.setServer(definitions);
        Arffornia.LOGGER.info("Loaded {} multiblock definitions", definitions.size());
    }

    private static MultiblockDefinition read(ResourceLocation id, JsonElement json, ResourceManager resources) throws IOException {
        Json parsed = Json.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(IllegalArgumentException::new);
        if (!(parsed.master() instanceof MultiblockMasterBlock master)) {
            throw new IllegalArgumentException(BuiltInRegistries.BLOCK.getKey(parsed.master()) + " is not a multiblock master block");
        }
        ResourceLocation file = parsed.structure().withPath(path -> "structure/" + path + ".nbt");
        Resource resource = resources.getResource(file).orElseThrow(() -> new IllegalArgumentException("missing structure " + file));
        CompoundTag template;
        try (InputStream stream = resource.open()) {
            template = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
        }
        return new MultiblockDefinition(id, TemplateShape.of(blocks(template), parsed.trigger()), parsed.trigger(), master, parsed.stage());
    }

    static List<BlockPlacement> blocks(CompoundTag template) {
        ListTag palette = template.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> states = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i)));
        }
        List<BlockPlacement> blocks = new ArrayList<>();
        ListTag entries = template.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ListTag pos = entry.getList("pos", Tag.TAG_INT);
            BlockState state = states.get(entry.getInt("state"));
            if (state.isAir() || state.is(Blocks.STRUCTURE_VOID)) {
                continue;
            }
            if (state.hasBlockEntity()) {
                throw new IllegalArgumentException(BuiltInRegistries.BLOCK.getKey(state.getBlock()) + " has a block entity, which formation would lose");
            }
            blocks.add(new BlockPlacement(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)), state));
        }
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException("the structure has no blocks");
        }
        return blocks;
    }
}
