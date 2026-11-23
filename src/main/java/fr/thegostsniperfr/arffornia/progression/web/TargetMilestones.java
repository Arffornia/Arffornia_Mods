package fr.thegostsniperfr.arffornia.progression.web;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.backend.ProgressionBackends;
import fr.thegostsniperfr.arffornia.network.ServerPayloadChecks;
import fr.thegostsniperfr.arffornia.progression.ModAttachments;
import fr.thegostsniperfr.arffornia.progression.ProgressionModels;
import fr.thegostsniperfr.arffornia.progression.ProgressionSnapshot;
import fr.thegostsniperfr.arffornia.progression.Progressions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Targets a milestone by key for a player, from the in-game progression page. The server checks rule 3 on its copy
 * of the model and of the progression before asking the website, which checks it again.
 */
public final class TargetMilestones {
    private TargetMilestones() {
    }

    /** Call on the server thread. Completes with true once the website accepted and the progression was re-read. */
    public static CompletableFuture<Boolean> request(ServerPlayer player, @Nullable String milestoneKey) {
        MinecraftServer server = player.server;
        long progressionId = Progressions.activeProgressionOf(player);
        Optional<ProgressionSnapshot> progression = progressionId == ModAttachments.NO_PROGRESSION
                ? Optional.empty() : Progressions.find(server, progressionId);
        if (progression.isEmpty() || progression.get().retired()) {
            return CompletableFuture.completedFuture(false);
        }
        if (milestoneKey != null && !ServerPayloadChecks.canTarget(ProgressionModels.current(server), progression.get(), milestoneKey)) {
            Arffornia.LOGGER.info("Refused target {} for {}: not available", milestoneKey, player.getName().getString());
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> sent = milestoneKey == null
                ? ProgressionBackends.get().setTargetMilestone(player.getUUID(), null)
                : ProgressionBackends.get().setTargetMilestoneKey(player.getUUID(), milestoneKey);
        return sent.exceptionally(error -> false).thenComposeAsync(accepted -> accepted
                ? Progressions.refreshPlayer(player).handle((ignored, error) -> true)
                : CompletableFuture.completedFuture(false), server);
    }
}
