package fr.thegostsniperfr.arffornia.api.backend;

import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Production backend: delegates to the website API client.
 */
public final class HttpProgressionBackend implements ProgressionBackend {
    private final ArfforniaApiService api;

    public HttpProgressionBackend(ArfforniaApiService api) {
        this.api = api;
    }

    @Override
    public CompletableFuture<ArfforniaApiDtos.PlayerData> fetchPlayerData(String compactPlayerUuid) {
        return api.fetchPlayerData(compactPlayerUuid);
    }

    @Override
    public CompletableFuture<ArfforniaApiDtos.ProgressionData> fetchProgressionData(long progressionId) {
        return api.fetchProgressionData(progressionId);
    }

    @Override
    public CompletableFuture<ArfforniaApiDtos.MilestoneDetails> fetchMilestoneDetails(int milestoneId) {
        return api.fetchMilestoneDetails(milestoneId);
    }

    @Override
    public CompletableFuture<Boolean> addMilestone(UUID playerUuid, int milestoneId) {
        return api.addMilestone(playerUuid, milestoneId);
    }

    @Override
    public CompletableFuture<Boolean> setTargetMilestone(UUID playerUuid, @Nullable Integer milestoneId) {
        return api.setTargetMilestone(playerUuid, milestoneId);
    }

    @Override
    public CompletableFuture<List<ArfforniaApiDtos.CustomRecipe>> fetchAllCustomRecipes() {
        return api.fetchAllCustomRecipes();
    }

    @Override
    public CompletableFuture<ArfforniaApiDtos.ProgressionConfig> fetchProgressionConfig() {
        return api.fetchProgressionConfig();
    }
}
