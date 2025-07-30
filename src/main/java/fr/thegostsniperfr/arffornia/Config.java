package fr.thegostsniperfr.arffornia;

import fr.thegostsniperfr.arffornia.config.ApiConfig;
import fr.thegostsniperfr.arffornia.shop.ShopConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        ShopConfig.register(BUILDER);
        ApiConfig.register(BUILDER);
    }

    static final ModConfigSpec SPEC = BUILDER.build();
}
