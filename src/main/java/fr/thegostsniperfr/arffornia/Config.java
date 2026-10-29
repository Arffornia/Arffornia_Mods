package fr.thegostsniperfr.arffornia;

import fr.thegostsniperfr.arffornia.config.ApiConfig;
import fr.thegostsniperfr.arffornia.shop.ShopConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

// Root COMMON config spec (arffornia-common.toml). It is also present on clients: never put secrets here.
// Server-only values live in config.ServerSecretsConfig (arffornia-server.toml).
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        ShopConfig.register(BUILDER);
        ApiConfig.register(BUILDER);
    }

    static final ModConfigSpec SPEC = BUILDER.build();
}
