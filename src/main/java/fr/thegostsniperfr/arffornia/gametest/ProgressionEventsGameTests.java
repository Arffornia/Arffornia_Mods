package fr.thegostsniperfr.arffornia.gametest;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.backend.InMemoryProgressionBackend;
import fr.thegostsniperfr.arffornia.api.backend.ProgressionBackends;
import fr.thegostsniperfr.arffornia.progression.MilestoneResult;
import fr.thegostsniperfr.arffornia.progression.ModAttachments;
import fr.thegostsniperfr.arffornia.progression.ProgressionEventOutbox;
import fr.thegostsniperfr.arffornia.progression.ProgressionEventSender;
import fr.thegostsniperfr.arffornia.progression.ProgressionModel;
import fr.thegostsniperfr.arffornia.progression.ProgressionModels;
import fr.thegostsniperfr.arffornia.progression.ProgressionSnapshot;
import fr.thegostsniperfr.arffornia.progression.Progressions;
import fr.thegostsniperfr.arffornia.progression.Stages;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Completing a milestone through the event sender (spec 017): the typed result for each refusal, team scope and the
 * outbox. A milestone is completed only by a Space Elevator delivery or the admin command, both of which call
 * {@link ProgressionEventSender#milestoneCompleted}.
 */
@GameTestHolder(Arffornia.MODID)
@PrefixGameTestTemplate(false)
public final class ProgressionEventsGameTests {
    private static final String PLATFORM = "platform";
    private static final AtomicInteger IDS = new AtomicInteger(30_000);
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private ProgressionEventsGameTests() {
    }

    private static InMemoryProgressionBackend backend() {
        return ProgressionBackends.fake();
    }

    private static void publish(MinecraftServer server, String key, int stage, String... requires) {
        backend().putModelMilestone(key, stage, requires);
        ProgressionModels.apply(server, ProgressionModel.of(backend().model(), "test-" + key));
    }

    private static void knownProgression(MinecraftServer server, long progressionId) {
        backend().createProgression(progressionId);
        Progressions.store(server, new ProgressionSnapshot(progressionId, Set.of(), null, false));
    }

    private static MilestoneResult complete(MinecraftServer server, long progressionId, String key) {
        return ProgressionEventSender.milestoneCompleted(server, progressionId, key, ACTOR);
    }

    private static boolean doneInGame(MinecraftServer server, long progressionId, String key) {
        return Progressions.find(server, progressionId).map(snapshot -> snapshot.isCompleted(key)).orElse(false);
    }

    @GameTest(template = PLATFORM)
    public static void anAvailableMilestoneIsCompletedForThatProgressionOnlyAndSent(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int team = IDS.getAndIncrement();
        int otherTeam = IDS.getAndIncrement();
        String key = "gametest/available_" + team;
        publish(server, key, 1);
        knownProgression(server, team);
        knownProgression(server, otherTeam);

        MilestoneResult result = complete(server, team, key);

        helper.assertTrue(result == MilestoneResult.COMPLETED, "An available milestone is completed, got " + result);
        helper.assertTrue(doneInGame(server, team, key), "The milestone applies at once, before the website answers");
        helper.assertTrue(Stages.isUnlocked(server, team, Optional.of(Stages.milestoneStage(key))), "What the milestone key gates unlocks at once");
        helper.assertFalse(doneInGame(server, otherTeam, key), "Another progression must not get the milestone");
        helper.succeedWhen(() -> {
            helper.assertTrue(backend().isCompleted(team, key), "The website should receive the milestone");
            helper.assertTrue(backend().receivedEvents(team).getFirst().actor().equals(ACTOR.toString().replace("-", "")),
                    "The event names the player who did it");
        });
    }

    @GameTest(template = PLATFORM)
    public static void completingAMilestoneTwiceSaysAlreadyDoneAndSendsOneEvent(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int team = IDS.getAndIncrement();
        String key = "gametest/twice_" + team;
        publish(server, key, 1);
        knownProgression(server, team);
        complete(server, team, key);

        MilestoneResult second = complete(server, team, key);

        helper.assertTrue(second == MilestoneResult.ALREADY_DONE, "A done milestone is reported as already done, got " + second);
        helper.succeedWhen(() -> helper.assertTrue(backend().receivedEvents(team).size() == 1,
                "Only one event is sent, got " + backend().receivedEvents(team).size()));
    }

    @GameTest(template = PLATFORM)
    public static void aMilestoneOfAClosedStageIsRefusedWithItsStage(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int team = IDS.getAndIncrement();
        String key = "gametest/stage_two_" + team;
        publish(server, key, 2);
        knownProgression(server, team);
        Progressions.store(server, Progressions.find(server, team).orElseThrow().withOpenStage(1));

        MilestoneResult atStageOne = complete(server, team, key);
        Progressions.store(server, Progressions.find(server, team).orElseThrow().withOpenStage(2));
        MilestoneResult atStageTwo = complete(server, team, key);

        helper.assertTrue(atStageOne.equals(new MilestoneResult.StageClosed(2)), "Stage 2 is closed at stage 1, got " + atStageOne);
        helper.assertTrue(atStageTwo == MilestoneResult.COMPLETED, "The milestone counts once stage 2 opens, got " + atStageTwo);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void aMilestoneWithAMissingPrerequisiteNamesItAndRecordsNothing(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int team = IDS.getAndIncrement();
        String prerequisite = "gametest/pre_" + team;
        String key = "gametest/after_pre_" + team;
        publish(server, prerequisite, 1);
        publish(server, key, 1, prerequisite);
        knownProgression(server, team);

        MilestoneResult early = complete(server, team, key);
        boolean queuedEarly = ProgressionEventOutbox.get(server).pendingKeys(team).contains(key);
        complete(server, team, prerequisite);
        MilestoneResult afterPrerequisite = complete(server, team, key);

        helper.assertTrue(early.equals(new MilestoneResult.MissingPrerequisites(List.of(prerequisite))), "The missing prerequisite is named, got " + early);
        helper.assertFalse(queuedEarly, "Nothing is queued for an unavailable milestone");
        helper.assertTrue(afterPrerequisite == MilestoneResult.COMPLETED, "The milestone counts once its prerequisite is done, got " + afterPrerequisite);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void aMilestoneTheModelDoesNotKnowIsUnknown(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int team = IDS.getAndIncrement();
        publish(server, "gametest/known_" + team, 1);
        knownProgression(server, team);

        MilestoneResult result = complete(server, team, "gametest/not_published_" + team);

        helper.assertTrue(result == MilestoneResult.UNKNOWN_MILESTONE, "An unpublished milestone is unknown, got " + result);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void aPlayerWithoutProgressionCannotCompleteAMilestone(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        String key = "gametest/nobody_" + IDS.getAndIncrement();
        publish(server, key, 1);

        MilestoneResult result = complete(server, ModAttachments.NO_PROGRESSION, key);

        helper.assertTrue(result == MilestoneResult.NO_PROGRESSION, "No progression, got " + result);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void aRetiredProgressionCannotCompleteAMilestone(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int team = IDS.getAndIncrement();
        String key = "gametest/retired_" + team;
        publish(server, key, 1);
        backend().createProgression(team);
        Progressions.store(server, new ProgressionSnapshot(team, Set.of(), null, true));

        MilestoneResult result = complete(server, team, key);

        helper.assertTrue(result == MilestoneResult.RETIRED, "A retired progression, got " + result);
        helper.succeed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void milestonesCompletedWhileTheWebsiteIsDownAreSentOnceItIsBack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int team = IDS.getAndIncrement();
        String key = "gametest/offline_" + team;
        publish(server, key, 1);
        knownProgression(server, team);
        backend().setUnreachable(team, true);

        complete(server, team, key);

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
                .thenExecute(() -> helper.assertTrue(doneInGame(server, team, key), "The website's answer keeps the milestone done"))
                .thenSucceed();
    }

    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void aMilestoneTheWebsiteRefusesIsDroppedAndReportedAsRefused(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int team = IDS.getAndIncrement();
        String key = "gametest/refused_" + team;
        publish(server, key, 1);
        backend().refuseMilestone(key);
        knownProgression(server, team);

        complete(server, team, key);

        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(backend().receivedEvents(team).size() == 1, "The milestone is sent once");
                    helper.assertTrue(ProgressionEventOutbox.get(server).pendingKeys(team).isEmpty(), "A refused event leaves the outbox");
                    helper.assertFalse(doneInGame(server, team, key), "The website's state wins over a refused milestone");
                })
                .thenExecute(() -> {
                    MilestoneResult again = complete(server, team, key);
                    helper.assertTrue(again == MilestoneResult.REFUSED, "A refused milestone is reported as refused, got " + again);
                    backend().allowMilestone(key);
                    Progressions.recordCompletedKey(server, team, "gametest/refused_unlocker_" + team);
                    MilestoneResult afterMoreAvailable = complete(server, team, key);
                    helper.assertTrue(afterMoreAvailable == MilestoneResult.COMPLETED,
                            "Once more milestones are available it may be tried again, got " + afterMoreAvailable);
                })
                .thenWaitUntil(() -> helper.assertTrue(backend().isCompleted(team, key), "The website accepts it"))
                .thenSucceed();
    }
}
