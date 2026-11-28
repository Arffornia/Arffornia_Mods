package fr.thegostsniperfr.arffornia;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

/** Test helper: lists the dotted keys of a config spec (Config.SPEC is package-private). */
public final class ConfigSpecAccess {
    private ConfigSpecAccess() {
    }

    public static List<String> commonKeys() {
        return keys(Config.SPEC);
    }

    public static List<String> keys(ModConfigSpec spec) {
        List<String> keys = new ArrayList<>();
        collect(spec.getSpec(), "", keys);
        return keys;
    }

    private static void collect(UnmodifiableConfig config, String prefix, List<String> out) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            String key = prefix + entry.getKey();
            if (entry.getValue() instanceof UnmodifiableConfig child) {
                collect(child, key + ".", out);
            } else {
                out.add(key);
            }
        }
    }
}
