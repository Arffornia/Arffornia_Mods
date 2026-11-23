package fr.thegostsniperfr.arffornia.gametest;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.backend.InMemoryProgressionBackend;
import fr.thegostsniperfr.arffornia.api.backend.ProgressionBackends;
import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import fr.thegostsniperfr.arffornia.network.ServerPayloadChecks;
import fr.thegostsniperfr.arffornia.progression.ModAttachments;
import fr.thegostsniperfr.arffornia.progression.ProgressionModel;
import fr.thegostsniperfr.arffornia.progression.ProgressionModels;
import fr.thegostsniperfr.arffornia.progression.ProgressionSnapshot;
import fr.thegostsniperfr.arffornia.progression.Progressions;
import fr.thegostsniperfr.arffornia.progression.web.EmbedSessions;
import fr.thegostsniperfr.arffornia.progression.web.TargetMilestones;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Targeting from the in-game progression page (spec 018 D2 a): rule 3 is checked on the server before the website. */
@GameTestHolder(Arffornia.MODID)
@PrefixGameTestTemplate(false)
public final class TargetMilestoneGameTests {
    private static final String PLATFORM = "platform";
    private static final AtomicInteger IDS = new AtomicInteger(40_000);

    private TargetMilestoneGameTests() {
    }

    private static InMemoryProgressionBackend backend() {
        return ProgressionBackends.fake();
    }

    /** Stage 1: {@code first}, then {@code second} that requires it; stage 2: {@code later}. Stage 1 is open. */
    private record Tree(String first, String second, String later) {
    }

    private static Tree tree(MinecraftServer server, int id) {
        Tree tree = new Tree("gametest/target_first_" + id, "gametest/target_second_" + id, "gametest/target_later_" + id);
        backend().putModelMilestone(tree.first(), 1);
        backend().putModelMilestone(tree.second(), 1, tree.first());
        backend().putModelMilestone(tree.later(), 2);
        ProgressionModels.apply(server, ProgressionModel.of(backend().model(), "target-" + id));
        return tree;
    }

    private static ServerPlayer playerOn(GameTestHelper helper, long progressionId, String... done) {
        MinecraftServer server = helper.getLevel().getServer();
        backend().createProgression(progressionId);
        Progressions.store(server, new ProgressionSnapshot(progressionId, Set.of(), null, false, Set.of(done), 1));
        ServerPlayer player = joined(helper);
        player.setData(ModAttachments.ACTIVE_PROGRESSION, progressionId);
        return player;
    }

    /** Like {@code makeMockServerPlayerInLevel}, with NeoForge's mock connection so mod payloads sent on join are accepted. */
    private static ServerPlayer joined(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "target" + IDS.getAndIncrement()), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static void expect(GameTestHelper helper, ServerPlayer player, CompletableFuture<Boolean> outcome, boolean accepted, String expectedTarget, String why) {
        helper.succeedWhen(() -> {
            helper.assertTrue(outcome.isDone(), "The request should finish");
            helper.assertTrue(outcome.join() == accepted, why);
            String target = backend().targetKeyOf(player.getUUID());
            helper.assertTrue(Objects.equals(target, expectedTarget), "The website should hold target " + expectedTarget + ", got " + target);
            player.server.getPlayerList().remove(player);
        });
    }

    @GameTest(template = PLATFORM)
    public static void anAvailableMilestoneIsTargetedByKey(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        Tree tree = tree(helper.getLevel().getServer(), id);
        ServerPlayer player = playerOn(helper, id);

        expect(helper, player, TargetMilestones.request(player, tree.first()), true, tree.first(), "A stage 1 milestone without prerequisites is available");
    }

    @GameTest(template = PLATFORM)
    public static void aMilestoneWithAPrerequisiteNotDoneIsRefusedWithoutAskingTheWebsite(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        Tree tree = tree(helper.getLevel().getServer(), id);
        ServerPlayer player = playerOn(helper, id);

        expect(helper, player, TargetMilestones.request(player, tree.second()), false, null, "Its prerequisite is not done");
    }

    @GameTest(template = PLATFORM)
    public static void aMilestoneOfAClosedStageIsRefused(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        Tree tree = tree(helper.getLevel().getServer(), id);
        ServerPlayer player = playerOn(helper, id, tree.first(), tree.second());

        expect(helper, player, TargetMilestones.request(player, tree.later()), false, null, "Stage 2 is not open");
    }

    @GameTest(template = PLATFORM)
    public static void aDoneMilestoneIsRefusedAndTheNextOneIsAccepted(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        Tree tree = tree(helper.getLevel().getServer(), id);
        ServerPlayer player = playerOn(helper, id, tree.first());

        helper.assertFalse(TargetMilestones.request(player, tree.first()).join(), "A done milestone cannot be targeted");
        expect(helper, player, TargetMilestones.request(player, tree.second()), true, tree.second(), "Its prerequisite is done");
    }

    @GameTest(template = PLATFORM)
    public static void aMilestoneTheModelDoesNotKnowIsRefused(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        tree(helper.getLevel().getServer(), id);
        ServerPlayer player = playerOn(helper, id);

        expect(helper, player, TargetMilestones.request(player, "gametest/unknown_" + id), false, null, "Unknown keys are never available");
    }

    @GameTest(template = PLATFORM)
    public static void onlySpec017KeysPassTheBoundary(GameTestHelper helper) {
        for (String key : List.of("mekanism/first", "arffornia", "a_1/b_2")) {
            helper.assertTrue(ServerPayloadChecks.isMilestoneKey(key), key + " is a milestone key");
        }
        for (String key : List.of("", "Mekanism/first", "../etc", "a/b/c", "a b", "first\n", "a".repeat(129))) {
            helper.assertFalse(ServerPayloadChecks.isMilestoneKey(key), "'" + key + "' is not a milestone key");
        }
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void aRetiredProgressionCannotTarget(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        Tree tree = tree(helper.getLevel().getServer(), id);
        ServerPlayer player = playerOn(helper, id);
        Progressions.store(helper.getLevel().getServer(), new ProgressionSnapshot(id, Set.of(), null, true, Set.of(), 1));

        expect(helper, player, TargetMilestones.request(player, tree.first()), false, null, "A retired team's progression is frozen");
    }

    @GameTest(template = PLATFORM)
    public static void clearingTheTargetIsAccepted(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        Tree tree = tree(helper.getLevel().getServer(), id);
        ServerPlayer player = playerOn(helper, id);
        TargetMilestones.request(player, tree.first());
        helper.assertTrue(tree.first().equals(backend().targetKeyOf(player.getUUID())), "The first target reached the website");

        expect(helper, player, TargetMilestones.request(player, null), true, null, "Clearing needs no availability");
    }

    @GameTest(template = PLATFORM)
    public static void aPlayerWithoutAProgressionCannotTarget(GameTestHelper helper) {
        int id = IDS.getAndIncrement();
        Tree tree = tree(helper.getLevel().getServer(), id);
        ServerPlayer player = joined(helper);

        expect(helper, player, TargetMilestones.request(player, tree.first()), false, null, "No active progression, nothing to target");
    }

    @GameTest(template = PLATFORM)
    public static void anEmbedTokenIsReusedUntilRenewedAndMintingIsThrottled(GameTestHelper helper) {
        UUID player = UUID.randomUUID();

        ArfforniaApiDtos.EmbedToken first = EmbedSessions.forPlayer(player, false, true).join();
        ArfforniaApiDtos.EmbedToken again = EmbedSessions.forPlayer(player, false, true).join();
        ArfforniaApiDtos.EmbedToken throttled = EmbedSessions.forPlayer(player, true, false).join();
        ArfforniaApiDtos.EmbedToken renewed = EmbedSessions.forPlayer(player, true, true).join();
        EmbedSessions.forget(player);
        ArfforniaApiDtos.EmbedToken afterLogout = EmbedSessions.forPlayer(player, false, false).join();

        helper.assertTrue(first != null && first.isComplete(), "A token and the page URL");
        helper.assertTrue(again == first && throttled == first, "A fresh token is served again without a website call");
        helper.assertTrue(renewed != null && !renewed.token().equals(first.token()), "Renew mints a new one");
        helper.assertTrue(backend().embedTokensMinted(player) == 2, "Two website calls, got " + backend().embedTokensMinted(player));
        helper.assertTrue(afterLogout == null, "Nothing is kept once the player left");
        helper.assertFalse(first.toString().contains(first.token()), "The token never shows in a log line");
        helper.succeed();
    }
}
