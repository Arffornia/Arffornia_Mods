package fr.thegostsniperfr.arffornia.gametest;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.backend.InMemoryProgressionBackend;
import fr.thegostsniperfr.arffornia.api.backend.ProgressionBackends;
import fr.thegostsniperfr.arffornia.progression.ItemCraftedTrigger;
import fr.thegostsniperfr.arffornia.progression.MilestoneAdvancements;
import fr.thegostsniperfr.arffornia.progression.ModAttachments;
import fr.thegostsniperfr.arffornia.progression.ModCriteriaTriggers;
import fr.thegostsniperfr.arffornia.progression.ProgressionEventOutbox;
import fr.thegostsniperfr.arffornia.progression.ProgressionEventSender;
import fr.thegostsniperfr.arffornia.progression.ProgressionSnapshot;
import fr.thegostsniperfr.arffornia.progression.Progressions;
import fr.thegostsniperfr.arffornia.progression.Stages;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Milestone detection through hidden advancements, team scope and the event sender (spec 017, work item 8).
 * The website datapack is not loaded here, so each test adds the milestone advancement it needs before its players
 * join (a player registers its advancement listeners when it is created). Each test uses its own key item, so a
 * player of another test cannot complete its milestone.
 */
@GameTestHolder(Arffornia.MODID)
@PrefixGameTestTemplate(false)
public final class ProgressionEventsGameTests {
    private static final String PLATFORM = "platform";
    private static final AtomicInteger IDS = new AtomicInteger(30_000);

    private ProgressionEventsGameTests() {
    }

    private static InMemoryProgressionBackend backend() {
        return ProgressionBackends.fake();
    }

    private static void addMilestoneAdvancement(MinecraftServer server, String key, Criterion<?> validation) {
        ResourceLocation id = MilestoneAdvancements.advancementId(key);
        AdvancementHolder holder = Advancement.Builder.advancement().addCriterion("validation", validation).build(id);
        ServerAdvancementManager manager = server.getAdvancements();
        try {
            Field field = ServerAdvancementManager.class.getDeclaredField("advancements");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<ResourceLocation, AdvancementHolder> current = (Map<ResourceLocation, AdvancementHolder>) field.get(manager);
            Map<ResourceLocation, AdvancementHolder> updated = new HashMap<>(current);
            updated.put(id, holder);
            field.set(manager, Map.copyOf(updated));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot add a test advancement", e);
        }
        manager.tree().addAll(List.of(holder));
    }

    private static void knownProgression(MinecraftServer server, long progressionId) {
        backend().createProgression(progressionId);
        Progressions.store(server, new ProgressionSnapshot(progressionId, Set.of(), null, false));
    }

    /**
     * A real (not fake) player, since fake players never earn advancements. Like {@code makeMockServerPlayerInLevel},
     * with the mock connection NeoForge provides for tests, so mod payloads sent on join are accepted.
     */
    private static ServerPlayer playerOn(GameTestHelper helper, long progressionId) {
        MinecraftServer server = helper.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "milestone" + progressionId), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setData(ModAttachments.ACTIVE_PROGRESSION, progressionId);
        return player;
    }

    private static void obtain(ServerPlayer player, Item item) {
        player.getInventory().add(new ItemStack(item));
        player.inventoryMenu.broadcastChanges();
    }

    private static boolean doneInGame(MinecraftServer server, long progressionId, String key) {
        return Progressions.find(server, progressionId).map(snapshot -> snapshot.isCompleted(key)).orElse(false);
    }

    private static void leave(MinecraftServer server, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            server.getPlayerList().remove(player);
        }
    }

    @GameTest(template = PLATFORM)
    public static void obtainingAKeyItemCompletesTheMilestoneForThePlayersTeamOnly(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int team = IDS.getAndIncrement();
        int otherTeam = IDS.getAndIncrement();
        String key = "gametest/obtain_" + team;
        addMilestoneAdvancement(server, key, InventoryChangeTrigger.TriggerInstance.hasItems(Items.AMETHYST_SHARD));
        knownProgression(server, team);
        knownProgression(server, otherTeam);
        ServerPlayer finder = playerOn(helper, team);
        ServerPlayer teammate = playerOn(helper, team);
        ServerPlayer outsider = playerOn(helper, otherTeam);

        obtain(finder, Items.AMETHYST_SHARD);

        helper.assertTrue(doneInGame(server, team, key), "The milestone applies to the team at once, before the website answers");
        helper.assertTrue(Stages.isUnlocked(server, team, Optional.of(Stages.milestoneStage(key))), "What the milestone key gates unlocks at once");
        helper.succeedWhen(() -> {
            helper.assertTrue(backend().isCompleted(team, key), "The website should receive the milestone");
            obtain(teammate, Items.AMETHYST_SHARD);
            helper.assertTrue(backend().receivedEvents(team).size() == 1, "A teammate meeting a done milestone sends nothing, got "
                    + backend().receivedEvents(team).size() + " events");
            helper.assertFalse(doneInGame(server, otherTeam, key), "Another team must not get the milestone");
            helper.assertFalse(backend().isCompleted(otherTeam, key), "Nothing is sent for another team");
            helper.assertTrue(backend().receivedEvents(team).getFirst().actor().equals(finder.getUUID().toString().replace("-", "")),
                    "The event names the player who did it");
            leave(server, finder, teammate, outsider);
        });
    }

    @GameTest(template = PLATFORM)
    public static void craftingAKeyItemCompletesACraftMilestoneButObtainingItDoesNot(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int team = IDS.getAndIncrement();
        String key = "gametest/craft_" + team;
        addMilestoneAdvancement(server, key, ModCriteriaTriggers.ITEM_CRAFTED.get().createCriterion(
                new ItemCraftedTrigger.TriggerInstance(Optional.empty(), List.of(ItemPredicate.Builder.item().of(Items.LECTERN).build()))));
        knownProgression(server, team);
        ServerPlayer crafter = playerOn(helper, team);

        obtain(crafter, Items.LECTERN);
        boolean doneByObtaining = doneInGame(server, team, key);
        NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(crafter, new ItemStack(Items.LECTERN), new SimpleContainer(9)));

        helper.assertFalse(doneByObtaining, "Picking up the item must not complete a craft milestone");
        helper.assertTrue(doneInGame(server, team, key), "Crafting the item completes the milestone");
        helper.succeedWhen(() -> {
            helper.assertTrue(backend().isCompleted(team, key), "The website should receive the craft milestone");
            leave(server, crafter);
        });
    }

    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void milestonesCompletedWhileTheWebsiteIsDownAreSentOnceItIsBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int team = IDS.getAndIncrement();
        String key = "gametest/offline_" + team;
        addMilestoneAdvancement(server, key, InventoryChangeTrigger.TriggerInstance.hasItems(Items.ECHO_SHARD));
        knownProgression(server, team);
        backend().setUnreachable(team, true);
        ServerPlayer player = playerOn(helper, team);

        obtain(player, Items.ECHO_SHARD);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(doneInGame(server, team, key), "An outage must not hold back the milestone in game");
                    helper.assertFalse(backend().isCompleted(team, key), "The website is down");
                    helper.assertTrue(ProgressionEventOutbox.get(server).pendingKeys(team).contains(key), "The event waits in the outbox");
                    backend().setUnreachable(team, false);
                    ProgressionEventSender.send(server, team);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(backend().isCompleted(team, key), "The waiting event is sent once the website is back");
                    helper.assertTrue(ProgressionEventOutbox.get(server).pendingKeys(team).isEmpty(), "An acknowledged event leaves the outbox");
                })
                .thenExecute(() -> {
                    helper.assertTrue(doneInGame(server, team, key), "The website's answer keeps the milestone done");
                    leave(server, player);
                })
                .thenSucceed();
    }

    @GameTest(template = PLATFORM)
    public static void aMilestoneTheProgressionHasNotDoneIsRevokedSoItCanFireAgain(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int oldTeam = IDS.getAndIncrement();
        int newTeam = IDS.getAndIncrement();
        String key = "gametest/team_change_" + oldTeam;
        addMilestoneAdvancement(server, key, InventoryChangeTrigger.TriggerInstance.hasItems(Items.PRISMARINE_CRYSTALS));
        knownProgression(server, oldTeam);
        knownProgression(server, newTeam);
        ServerPlayer player = playerOn(helper, oldTeam);
        obtain(player, Items.PRISMARINE_CRYSTALS);

        player.setData(ModAttachments.ACTIVE_PROGRESSION, (long) newTeam);
        Progressions.store(server, Progressions.find(server, newTeam).orElseThrow().withOpenStage(1));
        obtain(player, Items.PRISMARINE_CRYSTALS);

        helper.assertTrue(doneInGame(server, oldTeam, key), "The old team keeps the milestone");
        helper.assertTrue(doneInGame(server, newTeam, key), "Meeting the validation again after a team change completes it for the new team");
        leave(server, player);
        helper.succeed();
    }
}
