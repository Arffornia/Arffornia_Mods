package fr.thegostsniperfr.arffornia.client.screen.title;

import fr.thegostsniperfr.arffornia.api.dto.ArfforniaApiDtos;
import java.util.Optional;

/** The open stage's milestones against what the stage threshold asks for, for the Milestones tile's bar. */
record StageProgress(int stage, int done, int required) {
    static Optional<StageProgress> of(ArfforniaApiDtos.ProgressionState state) {
        if (state.stages() == null) return Optional.empty();
        return state.stages().stream()
                .filter(s -> s.number() == state.openStage())
                .findFirst()
                .map(s -> {
                    // The website opens a stage at done / total >= threshold, with a 1e-9 tolerance.
                    int required = (int) Math.ceil(s.threshold() * s.total() - 1e-9);
                    return new StageProgress(s.number(), Math.min(s.done(), required), required);
                });
    }

    float fraction() {
        return required == 0 ? 1 : done / (float) required;
    }
}
