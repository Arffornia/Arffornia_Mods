package fr.thegostsniperfr.arffornia.api.backend;

import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;

/**
 * Holds the active {@link ProgressionBackend}.
 * <p>
 * Defaults to the website API. Set the system property {@code arffornia.backend=fake} (as the
 * {@code gameTestServer} run does) to use an {@link InMemoryProgressionBackend} instead.
 */
public final class ProgressionBackends {
    public static final String BACKEND_PROPERTY = "arffornia.backend";

    private static volatile ProgressionBackend instance;

    private ProgressionBackends() {
    }

    public static ProgressionBackend get() {
        ProgressionBackend current = instance;
        if (current == null) {
            synchronized (ProgressionBackends.class) {
                if (instance == null) {
                    instance = create();
                }
                current = instance;
            }
        }
        return current;
    }

    /** True when the in-memory backend is active. */
    public static boolean isFake() {
        return get() instanceof InMemoryProgressionBackend;
    }

    /**
     * The in-memory backend, for tests.
     *
     * @throws IllegalStateException when the production backend is active.
     */
    public static InMemoryProgressionBackend fake() {
        if (get() instanceof InMemoryProgressionBackend fake) {
            return fake;
        }
        throw new IllegalStateException("The in-memory progression backend is not active (set -D" + BACKEND_PROPERTY + "=fake).");
    }

    private static ProgressionBackend create() {
        if ("fake".equalsIgnoreCase(System.getProperty(BACKEND_PROPERTY))) {
            Arffornia.LOGGER.warn("Using the IN-MEMORY progression backend ({}=fake). No data is sent to the website.", BACKEND_PROPERTY);
            return new InMemoryProgressionBackend();
        }
        return new HttpProgressionBackend(ArfforniaApiService.getInstance());
    }
}
