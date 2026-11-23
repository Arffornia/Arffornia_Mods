package fr.thegostsniperfr.arffornia.progression.web;

import org.jetbrains.annotations.Nullable;

/** The in-game progression page's client side, set from the client entry point, so common payload code stays server-safe. */
public final class ProgressionWebHooks {
    public interface Client {
        /** An empty token means the server could not get one. */
        void onSession(String url, String token, long expiresAt);

        void onTargetResult(@Nullable String milestoneKey, boolean accepted);
    }

    private static final Client NONE = new Client() {
        @Override
        public void onSession(String url, String token, long expiresAt) {
        }

        @Override
        public void onTargetResult(@Nullable String milestoneKey, boolean accepted) {
        }
    };

    private static volatile Client client = NONE;

    private ProgressionWebHooks() {
    }

    public static void set(Client hooks) {
        client = hooks;
    }

    public static Client client() {
        return client;
    }
}
