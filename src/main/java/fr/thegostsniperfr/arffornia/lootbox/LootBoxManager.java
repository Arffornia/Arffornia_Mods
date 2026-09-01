package fr.thegostsniperfr.arffornia.lootbox;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import fr.thegostsniperfr.arffornia.Arffornia;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class LootBoxManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FMLPaths.CONFIGDIR.get().resolve("arffornia/lootboxes.json").toFile();

    private static final String GITHUB_CONFIG_URL = "https://raw.githubusercontent.com/Arffornia/Arffornia_Provisioning/refs/heads/main/config/server/lootboxes.json";

    private static Map<String, LootBoxData> lootBoxes = new HashMap<>();
    private static final Random RANDOM = new Random();

    public static void load() {
        if (!CONFIG_FILE.getParentFile().exists()) {
            CONFIG_FILE.getParentFile().mkdirs();
        }

        try {
            Arffornia.LOGGER.info("Fetching latest lootboxes configuration from GitHub...");
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_CONFIG_URL))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                    writer.write(response.body());
                }
                Arffornia.LOGGER.info("Successfully updated lootboxes.json from GitHub.");
            } else {
                Arffornia.LOGGER.warn("Failed to fetch lootboxes from GitHub. Status: {}. Using local cache.", response.statusCode());
            }
        } catch (Exception e) {
            Arffornia.LOGGER.error("Network error while fetching lootboxes from GitHub. Using local cache.", e);
        }

        if (!CONFIG_FILE.exists()) {
            Arffornia.LOGGER.info("No local config found, generating default fallback.");
            generateDefaultConfig();
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            Type type = new TypeToken<Map<String, LootBoxData>>(){}.getType();
            lootBoxes = GSON.fromJson(reader, type);
            Arffornia.LOGGER.info("Loaded {} lootboxes into memory.", lootBoxes.size());
        } catch (Exception e) {
            Arffornia.LOGGER.error("Failed to parse lootboxes.json", e);
        }
    }

    public static LootBoxData getBox(String id) {
        return lootBoxes.get(id);
    }

    public static LootBoxData.Reward getRandomReward(String boxId) {
        LootBoxData box = getBox(boxId);
        if (box == null || box.rewards.isEmpty()) return null;

        double totalWeight = box.rewards.stream().mapToDouble(r -> r.chance).sum();
        double randomValue = RANDOM.nextDouble() * totalWeight;

        for (LootBoxData.Reward reward : box.rewards) {
            randomValue -= reward.chance;
            if (randomValue <= 0) {
                return reward;
            }
        }
        return box.rewards.get(0); // Fallback
    }

    public static java.util.Set<String> getAllBoxIds() {
        return lootBoxes.keySet();
    }

    private static void generateDefaultConfig() {
        LootBoxData epicBox = new LootBoxData();
        epicBox.name = "§d★ Epic Box ★";
        epicBox.hologram = List.of("§d★ Epic Box ★", "§7Sneak+Right Click to preview");
        epicBox.key_item = "arffornia:epic_key";

        LootBoxData.Reward e1 = new LootBoxData.Reward();
        e1.item = "minecraft:diamond"; e1.amount = 5; e1.chance = 10.0;
        LootBoxData.Reward e2 = new LootBoxData.Reward();
        e2.item = "minecraft:iron_ingot"; e2.amount = 32; e2.chance = 90.0;
        epicBox.rewards = List.of(e1, e2);

        lootBoxes.put("epic_box", epicBox);

        LootBoxData dailyBox = new LootBoxData();
        dailyBox.name = "§a★ Daily Box ★";
        dailyBox.hologram = List.of("§a★ Daily Box ★", "§7Sneak+Right Click to preview");
        dailyBox.key_item = "arffornia:streak_key";

        LootBoxData.Reward d1 = new LootBoxData.Reward();
        d1.item = "minecraft:iron_ingot"; d1.amount = 2; d1.chance = 40.0;
        LootBoxData.Reward d2 = new LootBoxData.Reward();
        d2.item = "minecraft:gold_ingot"; d2.amount = 2; d2.chance = 30.0;
        LootBoxData.Reward d3 = new LootBoxData.Reward();
        d3.item = "minecraft:diamond"; d3.amount = 1; d3.chance = 30.0;

        dailyBox.rewards = List.of(d1, d2, d3);
        lootBoxes.put("daily_box", dailyBox);

        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(lootBoxes, writer);
        } catch (Exception e) {
            Arffornia.LOGGER.error("Failed to generate default lootboxes.json", e);
        }
    }
}