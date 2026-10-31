package fr.thegostsniperfr.arffornia;

import fr.thegostsniperfr.arffornia.config.ApiConfig;
import fr.thegostsniperfr.arffornia.shop.ShopConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

// Root config spec: registers the shop and API sections into arffornia-common.toml.
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        ShopConfig.register(BUILDER);
        ApiConfig.register(BUILDER);
    }

    static final ModConfigSpec SPEC = BUILDER.build();
}
