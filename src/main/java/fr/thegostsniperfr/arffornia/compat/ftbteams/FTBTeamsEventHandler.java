package fr.thegostsniperfr.arffornia.compat.ftbteams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.PlayerJoinedPartyTeamEvent;
import dev.ftb.mods.ftbteams.api.event.PlayerLeftPartyTeamEvent;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;

import java.util.Optional;
import java.util.UUID;

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
        ArfforniaApiService.getInstance().syncPlayerTeam(playerUuid, team.getId(), team.getName().getString(), System.currentTimeMillis());
    }

    public void onPlayerLeftTeam(PlayerLeftPartyTeamEvent event) {
        if (event.getPlayer().level().isClientSide()) return;

        UUID playerUuid = event.getPlayer().getUUID();
        Arffornia.LOGGER.info("Player {} left a party team", playerUuid);
        ArfforniaApiService.getInstance().syncPlayerTeam(playerUuid, null, null, System.currentTimeMillis());
    }

    /** Sends the player's current party team. Call on the server thread so the read and the timestamp agree. */
    public static void syncCurrentTeam(UUID playerUuid) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) return;

        Optional<Team> party = FTBTeamsAPI.api().getManager().getTeamForPlayerID(playerUuid).filter(Team::isPartyTeam);
        ArfforniaApiService.getInstance().syncPlayerTeam(
                playerUuid,
                party.map(Team::getId).orElse(null),
                party.map(team -> team.getName().getString()).orElse(null),
                System.currentTimeMillis());
    }
}
