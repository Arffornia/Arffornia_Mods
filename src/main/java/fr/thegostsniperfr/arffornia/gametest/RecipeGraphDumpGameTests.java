package fr.thegostsniperfr.arffornia.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.graph.RecipeGraphDump;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@GameTestHolder(Arffornia.MODID)
@PrefixGameTestTemplate(false)
public final class RecipeGraphDumpGameTests {

    @GameTest(template = "platform", timeoutTicks = 200)
    public static void dumpWritesEveryRecipeWithItsTypeAndCodecJson(GameTestHelper helper) {
        try {
            Path dir = Files.createTempDirectory("arffornia-dump");
            RecipeGraphDump.Result result = RecipeGraphDump.write(helper.getLevel().getServer(), dir);

            List<String> lines = Files.readAllLines(dir.resolve("recipes.jsonl"));
            helper.assertTrue(lines.size() == result.recipes(), "one line per recipe");
            JsonObject torch = lines.stream()
                    .map(line -> JsonParser.parseString(line).getAsJsonObject())
                    .filter(row -> row.get("id").getAsString().equals("minecraft:torch"))
                    .findFirst().orElseThrow();
            helper.assertTrue(torch.get("type").getAsString().equals("minecraft:crafting"), "torch is a crafting recipe");
            helper.assertTrue(torch.getAsJsonObject("result").get("item").getAsString().equals("minecraft:torch"), "torch result");
            helper.assertTrue(torch.has("json"), "codec JSON present");
            helper.assertTrue(Files.readString(dir.resolve("tags.json")).contains("minecraft:planks"), "item tags written");
            helper.succeed();
        } catch (IOException e) {
            helper.fail("dump failed: " + e.getMessage());
        }
    }
}
