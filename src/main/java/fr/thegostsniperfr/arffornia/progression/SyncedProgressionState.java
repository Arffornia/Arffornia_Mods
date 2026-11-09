package fr.thegostsniperfr.arffornia.progression;

import java.util.Set;

/**
 * The local player's progression as last synced by the server. Client side only in practice, kept free of client
 * classes so the payload handler can live in common code.
 */
public final class SyncedProgressionState {
    private static final ProgressionSnapshot NONE = new ProgressionSnapshot(ModAttachments.NO_PROGRESSION, Set.of(), null, false);
    private static volatile ProgressionSnapshot current = NONE;

    private SyncedProgressionState() {
    }

    public static ProgressionSnapshot get() {
        return current;
    }

    public static void set(ProgressionSnapshot snapshot) {
        current = snapshot;
    }

    public static void clear() {
        current = NONE;
    }
}
