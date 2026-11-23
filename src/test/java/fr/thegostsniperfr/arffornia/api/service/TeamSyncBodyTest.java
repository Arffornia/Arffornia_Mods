package fr.thegostsniperfr.arffornia.api.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamSyncBodyTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void sendsExplicitNullTeamUuid_whenPlayerHasNoTeam() {
        String json = TeamSyncBody.toJson(PLAYER, null, null, 42L);

        JsonObject body = JsonParser.parseString(json).getAsJsonObject();
        assertTrue(body.has("team_uuid"));
        assertTrue(body.get("team_uuid").isJsonNull());
    }

    @Test
    void sendsTeamUuidAndName_whenPlayerHasTeam() {
        UUID team = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

        String json = TeamSyncBody.toJson(PLAYER, team, "Builders", 42L);

        JsonObject body = JsonParser.parseString(json).getAsJsonObject();
        assertEquals(team.toString(), body.get("team_uuid").getAsString());
        assertEquals("Builders", body.get("team_name").getAsString());
    }
}
