package fr.thegostsniperfr.arffornia.api.backend;

import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import org.jetbrains.annotations.Nullable;

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
    public CompletableFuture<ArfforniaApiDtos.MilestoneDetails> fetchMilestoneDetails(int milestoneId, String minecraftLanguage) {
        return api.fetchMilestoneDetails(milestoneId, minecraftLanguage);
    }

    @Override
    public CompletableFuture<Boolean> addMilestoneToProgression(long progressionId, int milestoneId) {
        return api.addMilestoneToProgression(progressionId, milestoneId);
    }

    @Override
    public CompletableFuture<Boolean> setTargetMilestone(UUID playerUuid, @Nullable Integer milestoneId) {
        return api.setTargetMilestone(playerUuid, milestoneId);
    }
}
