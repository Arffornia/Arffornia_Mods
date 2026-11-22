package fr.thegostsniperfr.arffornia.crate;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads {@code data/<ns>/arffornia/crate/<path>.json}. A broken file is logged and skipped. */
public final class CrateLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();

    public CrateLoader() {
        super(GSON, "arffornia/crate");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        List<CrateDefinition> definitions = new ArrayList<>();
        files.forEach((id, json) -> {
            try {
                definitions.add(CrateDefinition.Json.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(IllegalArgumentException::new).withId(id));
            } catch (RuntimeException e) {
                Arffornia.LOGGER.error("Skipping crate {}: {}", id, e.getMessage());
            }
        });
        CrateDefinitions.setServer(definitions);
        Arffornia.LOGGER.info("Loaded {} crate definitions", definitions.size());
    }
}
