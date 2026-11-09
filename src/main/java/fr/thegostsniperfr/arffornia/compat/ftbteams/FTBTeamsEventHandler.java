package fr.thegostsniperfr.arffornia.compat.ftbteams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.PlayerJoinedPartyTeamEvent;
import dev.ftb.mods.ftbteams.api.event.PlayerLeftPartyTeamEvent;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import fr.thegostsniperfr.arffornia.progression.Progressions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors each player's FTB party team to the backend. Every call states the full current team with the time it
 * was observed, so lost or reordered calls are repaired by the next one (team change or login).
 */
public class FTBTeamsEventHandler {

    public FTBTeamsEventHandler() {
        TeamEvent.PLAYER_JOINED_PARTY.register(this::onPlayerJoinedTeam);
        TeamEvent.PLAYER_LEFT_PARTY.register(this::onPlayerLeftTeam);
    }

    public void onPlayerJoinedTeam(PlayerJoinedPartyTeamEvent event) {
        if (event.getPlayer().level().isClientSide()) return;

        UUID playerUuid = event.getPlayer().getUUID();
        Team team = event.getTeam();
        Arffornia.LOGGER.info("Player {} joined party team {}", playerUuid, team.getId());
        refreshAfter(event.getPlayer(), ArfforniaApiService.getInstance().syncPlayerTeam(playerUuid, team.getId(), team.getName().getString(), System.currentTimeMillis()));
    }

    public void onPlayerLeftTeam(PlayerLeftPartyTeamEvent event) {
        ServerPlayer player = event.getPlayer();
        UUID playerUuid = event.getPlayerId();
        Arffornia.LOGGER.info("Player {} left a party team", playerUuid);
        CompletableFuture<Boolean> sync = ArfforniaApiService.getInstance().syncPlayerTeam(playerUuid, null, null, System.currentTimeMillis());
        if (player != null) {
            refreshAfter(player, sync);
            return;
        }
        // Kicked while offline: the left team may now be retired, and only the stored progressions say which it was.
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            sync.thenAcceptAsync(success -> Progressions.refreshAll(server), server);
        }
    }

    private static void refreshAfter(ServerPlayer player, CompletableFuture<Boolean> sync) {
        sync.thenAcceptAsync(success -> Progressions.refreshPlayer(player), player.server);
    }

    /** Sends the player's current party team. Call on the server thread so the read and the timestamp agree. */
    public static CompletableFuture<Boolean> syncCurrentTeam(UUID playerUuid) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) return CompletableFuture.completedFuture(false);

        Optional<Team> party = FTBTeamsAPI.api().getManager().getTeamForPlayerID(playerUuid).filter(Team::isPartyTeam);
        return ArfforniaApiService.getInstance().syncPlayerTeam(
                playerUuid,
                party.map(Team::getId).orElse(null),
                party.map(team -> team.getName().getString()).orElse(null),
                System.currentTimeMillis());
    }
}
