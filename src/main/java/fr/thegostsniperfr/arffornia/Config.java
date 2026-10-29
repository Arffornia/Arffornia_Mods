package fr.thegostsniperfr.arffornia;

import fr.thegostsniperfr.arffornia.config.ApiConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

// Root COMMON config spec (arffornia-common.toml). It is also present on clients: never put secrets here.
// Server-only values live in config.ServerSecretsConfig (arffornia-server.toml).
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        ApiConfig.register(BUILDER);
    }

    static final ModConfigSpec SPEC = BUILDER.build();
}
