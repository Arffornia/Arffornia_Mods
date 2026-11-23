package fr.thegostsniperfr.arffornia.client.screen.title;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StageProgressTest {
    private static ArfforniaApiDtos.ProgressionState state(int openStage, List<ArfforniaApiDtos.StageState> stages) {
        return new ArfforniaApiDtos.ProgressionState(1, 1, false, openStage, List.of(), stages);
    }

    @Test
    void countsTheOpenStageAgainstItsThreshold() {
        var stages = List.of(new ArfforniaApiDtos.StageState(1, true, 10, 10, 0.8), new ArfforniaApiDtos.StageState(2, true, 3, 16, 0.5));

        assertEquals(Optional.of(new StageProgress(2, 3, 8)), StageProgress.of(state(2, stages)));
    }

    @Test
    void roundsTheRequiredCountUpLikeTheWebsite() {
        var stages = List.of(new ArfforniaApiDtos.StageState(1, true, 0, 7, 0.5));

        assertEquals(4, StageProgress.of(state(1, stages)).orElseThrow().required());
    }

    @Test
    void capsDoneAtTheRequiredCount() {
        var stages = List.of(new ArfforniaApiDtos.StageState(3, true, 9, 10, 0.6));

        assertEquals(1f, StageProgress.of(state(3, stages)).orElseThrow().fraction());
    }

    @Test
    void isFullForAStageWithNothingToDo() {
        var stages = List.of(new ArfforniaApiDtos.StageState(1, true, 0, 0, 1.0));

        assertEquals(1f, StageProgress.of(state(1, stages)).orElseThrow().fraction());
    }

    @Test
    void isEmptyWithoutStagesFromAnOlderWebsite() {
        assertEquals(Optional.empty(), StageProgress.of(state(1, null)));
    }
}
