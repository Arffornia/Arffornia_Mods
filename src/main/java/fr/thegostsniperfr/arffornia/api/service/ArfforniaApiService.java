package fr.thegostsniperfr.arffornia.api.service;

import com.google.gson.Gson;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Handles HTTP requests and JSON parsing.
 */
public class ArfforniaApiService {

    private static final String API_BASE_URL = "http://127.0.0.1:8000/api";

    private final HttpClient client = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    /**
     * Fetches the basic graph structure (nodes and links) asynchronously.
     * @return A CompletableFuture containing the parsed graph data.
     */
    public CompletableFuture<ArfforniaApiDtos.GraphData> fetchGraphData() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/stages"))
                .header("Accept", "application/json")
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(json -> gson.fromJson(json, ArfforniaApiDtos.GraphData.class));
    }

    /**
     * Fetches the detailed information for a single milestone asynchronously.
     * @param nodeId The ID of the node to fetch.
     * @return A CompletableFuture containing the parsed milestone details.
     */
    public CompletableFuture<ArfforniaApiDtos.MilestoneDetails> fetchMilestoneDetails(int nodeId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/milestone/get/" + nodeId))
                .header("Accept", "application/json")
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(json -> gson.fromJson(json, ArfforniaApiDtos.MilestoneDetails.class));
    }

    /**
     * Fetches the server's progression configuration, including the list of banned recipes.
     * This should be called once on server start or /reload.
     *
     * @return A CompletableFuture containing the parsed progression config.
     */
    public CompletableFuture<ArfforniaApiDtos.ProgressionConfig> fetchProgressionConfig() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/progression/config"))
                .header("Accept", "application/json")
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(json -> gson.fromJson(json, ArfforniaApiDtos.ProgressionConfig.class));
    }
}