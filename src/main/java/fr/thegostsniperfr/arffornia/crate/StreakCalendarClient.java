package fr.thegostsniperfr.arffornia.crate;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.backend.ProgressionBackends;
import fr.thegostsniperfr.arffornia.api.service.ApiHttp;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Reads the streak calendar for a player. Any failure, a 404 included, is an empty result: the screen says so. */
public final class StreakCalendarClient {
    private StreakCalendarClient() {
    }

    public static CompletableFuture<Optional<StreakCalendar>> fetch(UUID player) {
        if (ProgressionBackends.isFake()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        ApiHttp http = ArfforniaApiService.getInstance().http();
        String path = "/players/" + player.toString().replace("-", "") + "/rewards/streak";
        return http.sendAuthenticated(http.authGet(path))
                .thenApply(response -> {
                    if (response == null || !ApiHttp.isSuccess(response.statusCode())) {
                        Arffornia.LOGGER.warn("Streak calendar for {} unavailable: status {}", player, response == null ? "no token" : response.statusCode());
                        return Optional.<StreakCalendar>empty();
                    }
                    try {
                        return Optional.ofNullable(StreakCalendar.parse(http.gson().fromJson(response.body(), JsonElement.class)));
                    } catch (JsonParseException e) {
                        return Optional.<StreakCalendar>empty();
                    }
                })
                .exceptionally(error -> {
                    Arffornia.LOGGER.warn("Streak calendar for {} unavailable: {}", player, error.getMessage());
                    return Optional.empty();
                });
    }
}
