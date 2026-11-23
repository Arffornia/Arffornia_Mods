package fr.thegostsniperfr.arffornia.api.service;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

final class TeamSyncBody {
    private TeamSyncBody() {
    }

    // The backend requires team_uuid to be present, so a missing team must be sent as an explicit null.
    static String toJson(UUID playerUuid, @Nullable UUID teamUuid, @Nullable String teamName, long observedAtMillis) {
        JsonObject body = new JsonObject();
        body.addProperty("player_uuid", playerUuid.toString().replace("-", ""));
        body.addProperty("team_uuid", teamUuid != null ? teamUuid.toString() : null);
        body.addProperty("team_name", teamName);
        body.addProperty("observed_at", observedAtMillis);
        return new GsonBuilder().serializeNulls().create().toJson(body);
    }
}
