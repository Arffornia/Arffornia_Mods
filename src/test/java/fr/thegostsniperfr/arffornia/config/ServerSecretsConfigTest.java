package fr.thegostsniperfr.arffornia.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import fr.thegostsniperfr.arffornia.ConfigSpecAccess;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSecretsConfigTest {

    private static final Set<String> SECRET_KEYS = Set.of("clientId", "clientSecret", "username", "password");

    @AfterEach
    void unload() {
        ServerSecretsConfig.SPEC.acceptConfig(null);
    }

    @Test
    void registeredOnlyOnDedicatedServer() {
        assertTrue(ServerSecretsConfig.shouldRegister(Dist.DEDICATED_SERVER));
        assertFalse(ServerSecretsConfig.shouldRegister(Dist.CLIENT));
    }

    @Test
    void commonConfigHoldsNoSecrets() {
        List<String> keys = ConfigSpecAccess.commonKeys();

        assertEquals(List.of("api.baseUrl"), keys);
        for (String key : keys) {
            String leaf = key.substring(key.lastIndexOf('.') + 1);
            assertFalse(SECRET_KEYS.contains(leaf), "secret-looking key in COMMON config: " + key);
        }
    }

    @Test
    void serverConfigHoldsTheSecretsAndMigrationFlag() {
        List<String> keys = ConfigSpecAccess.keys(ServerSecretsConfig.SPEC);

        assertTrue(keys.containsAll(List.of(
                "api.clientId", "api.clientSecret", "api.migrateOnStartup",
                "database.host", "database.port", "database.database", "database.username", "database.password")), keys.toString());
    }

    @Test
    void notLoadedMeansEverythingDisabled() {
        assertFalse(ServerSecretsConfig.isLoaded());
        assertEquals("", ServerSecretsConfig.apiClientId());
        assertEquals("", ServerSecretsConfig.apiClientSecret());
        assertFalse(ServerSecretsConfig.migrateOnStartup());
        assertFalse(ServerSecretsConfig.hasServiceCredentials());
        assertFalse(ServerSecretsConfig.hasDatabaseSettings());
    }

    @Test
    void defaultsShipNoCredentials() {
        load(CommentedConfig.inMemory());

        assertTrue(ServerSecretsConfig.isLoaded());
        assertFalse(ServerSecretsConfig.hasServiceCredentials());
        assertFalse(ServerSecretsConfig.hasDatabaseSettings());
        assertFalse(ServerSecretsConfig.migrateOnStartup());
        assertEquals("", ServerSecretsConfig.dbPassword());
    }

    @Test
    void readsValuesWhenLoaded() {
        CommentedConfig config = CommentedConfig.inMemory();
        config.set("api.clientId", "11111111-2222-3333-4444-555555555555");
        config.set("api.clientSecret", "test-secret");
        config.set("api.migrateOnStartup", true);
        config.set("database.host", "db.local");
        config.set("database.port", 6543);
        config.set("database.database", "arffornia");
        config.set("database.username", "mod");
        config.set("database.password", "pw");
        load(config);

        assertTrue(ServerSecretsConfig.hasServiceCredentials());
        assertEquals("test-secret", ServerSecretsConfig.apiClientSecret());
        assertTrue(ServerSecretsConfig.migrateOnStartup());
        assertTrue(ServerSecretsConfig.hasDatabaseSettings());
        assertEquals("db.local", ServerSecretsConfig.dbHost());
        assertEquals(6543, ServerSecretsConfig.dbPort());
        assertEquals("mod", ServerSecretsConfig.dbUsername());
    }

    /**
     * Loads an in-memory config into the spec, as FML does at start-up. ILoadedConfig is sealed, so the FML record is
     * built reflectively. The config is corrected first, so the record's save() (which needs a file) is never called.
     */
    private static void load(CommentedConfig config) {
        ServerSecretsConfig.SPEC.correct(config);
        try {
            Class<?> type = Class.forName("net.neoforged.fml.config.LoadedConfig");
            Constructor<?> constructor = type.getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
            constructor.setAccessible(true);
            ServerSecretsConfig.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Cannot build an FML LoadedConfig for the test", e);
        }
    }
}
