package fr.thegostsniperfr.arffornia.compat.ftbteams;

import dev.ftb.mods.ftbteams.api.event.PlayerJoinedPartyTeamEvent;
import dev.ftb.mods.ftbteams.api.event.PlayerLeftPartyTeamEvent;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import fr.thegostsniperfr.arffornia.Arffornia;

import java.util.UUID;

public class FTBTeamsEventHandler {

    public FTBTeamsEventHandler()
    {
        // Register event to FTB Teams
        TeamEvent.PLAYER_JOINED_PARTY.register(this::onPlayerJoinedTeam);
        TeamEvent.PLAYER_LEFT_PARTY.register(this::onPlayerLeftTeam);
    }

    public void onPlayerJoinedTeam(PlayerJoinedPartyTeamEvent event) {
        if (event.getPlayer().level().isClientSide()) return;

        UUID playerUuid = event.getPlayer().getUUID();
        UUID teamId = event.getTeam().getId();
        String teamName = event.getTeam().getName().getString();

        Arffornia.LOGGER.info("Architectury Event: Player {} joined team {} ({})", playerUuid, teamId, teamName);
        Arffornia.ARFFORNA_API_SERVICE.sendPlayerJoinedTeam(playerUuid, teamId, teamName);
    }

    public void onPlayerLeftTeam(PlayerLeftPartyTeamEvent event) {
        if (event.getPlayer().level().isClientSide()) return;

        UUID playerUuid = event.getPlayer().getUUID();
        Arffornia.LOGGER.info("Architectury Event: Player {} left a team", playerUuid);
        Arffornia.ARFFORNA_API_SERVICE.sendPlayerLeftTeam(playerUuid);
    }
}