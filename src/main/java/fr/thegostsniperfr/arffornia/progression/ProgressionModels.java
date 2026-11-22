package fr.thegostsniperfr.arffornia.progression;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.backend.ProgressionBackends;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The last published model the game read, saved with the world so availability still works when the server starts
 * during a website outage. Read from {@code GET /progression/model} with the cached ETag: at start, and when a state
 * answer names another snapshot version. Server thread only.
 */
public final class ProgressionModels extends SavedData {
    private static final String NAME = Arffornia.MODID + "_progression_model";
    private static final long MIN_FETCH_INTERVAL_MS = 60_000;

    private static boolean fetching;
    private static long lastFetchStartedAt;

    private ProgressionModel model = ProgressionModel.NONE;

    public static ProgressionModels get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ProgressionModels::new, ProgressionModels::load, null), NAME);
    }

    private static ProgressionModels load(CompoundTag tag, HolderLookup.Provider registries) {
        ProgressionModels models = new ProgressionModels();
        ProgressionModel.CODEC.parse(NbtOps.INSTANCE, tag.get("model"))
                .resultOrPartial(error -> Arffornia.LOGGER.error("Could not read the saved progression model: {}", error))
                .ifPresent(model -> models.model = model);
        return models;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("model", ProgressionModel.CODEC.encodeStart(NbtOps.INSTANCE, model).getOrThrow());
        return tag;
    }

    public static ProgressionModel current(MinecraftServer server) {
        return get(server).model;
    }

    /** Re-reads the model; a 304 keeps the cached one. At most one read at a time, and not more than once a minute unless forced. */
    public static void refresh(MinecraftServer server, boolean force) {
        long now = System.currentTimeMillis();
        if (fetching || (!force && now - lastFetchStartedAt < MIN_FETCH_INTERVAL_MS)) {
            return;
        }
        fetching = true;
        lastFetchStartedAt = now;
        String etag = current(server).etag();
        ProgressionBackends.get().fetchProgressionModel(etag.isEmpty() ? null : etag)
                .thenAcceptAsync(reply -> {
                    fetching = false;
                    if (reply.status() == 200 && reply.document() != null) {
                        apply(server, ProgressionModel.of(reply.document(), reply.etag()));
                    } else if (reply.status() != 304) {
                        Arffornia.LOGGER.warn("Could not read the progression model (status {}); keeping version {}.", reply.status(), current(server).version());
                    }
                }, server)
                .exceptionally(error -> {
                    server.execute(() -> fetching = false);
                    return null;
                });
    }

    /** Reads the model again when the website answered with another snapshot version. */
    public static void ensureVersion(MinecraftServer server, int snapshotVersion) {
        if (snapshotVersion > 0 && snapshotVersion != current(server).version()) {
            refresh(server, false);
        }
    }

    /** Replaces the model; milestones may have become available, so online players' inventories are checked again. */
    public static void apply(MinecraftServer server, ProgressionModel model) {
        ProgressionModels models = get(server);
        if (model.equals(models.model)) {
            return;
        }
        boolean newVersion = model.version() != models.model.version();
        models.model = model;
        models.setDirty();
        Arffornia.LOGGER.info("Progression model version {} loaded ({} milestones).", model.version(), model.milestones().size());
        if (newVersion) {
            // The website judged refused milestones with the old rules.
            ProgressionEventSender.forgetRefusals();
        }
        server.getPlayerList().getPlayers().forEach(MilestoneAdvancements::recheckInventoryLater);
    }

    static void resetFetchState() {
        fetching = false;
        lastFetchStartedAt = 0;
    }
}
