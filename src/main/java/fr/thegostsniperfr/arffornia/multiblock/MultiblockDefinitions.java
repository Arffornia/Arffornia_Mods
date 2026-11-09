package fr.thegostsniperfr.arffornia.multiblock;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The structures the Wrench can form: loaded from data on the server, synced to clients for the hologram. Both sides
 * live in one JVM in single player, so each keeps its own copy.
 */
public final class MultiblockDefinitions {
    private static volatile Map<Block, MultiblockDefinition> server = Map.of();
    private static volatile Map<Block, MultiblockDefinition> client = Map.of();

    private MultiblockDefinitions() {
    }

    public static Optional<MultiblockDefinition> forTrigger(Level level, Block trigger) {
        return Optional.ofNullable((level.isClientSide() ? client : server).get(trigger));
    }

    public static Collection<MultiblockDefinition> server() {
        return server.values();
    }

    static void setServer(List<MultiblockDefinition> definitions) {
        server = byTrigger(definitions);
    }

    public static void setClient(List<MultiblockDefinition> definitions) {
        client = byTrigger(definitions);
    }

    private static Map<Block, MultiblockDefinition> byTrigger(List<MultiblockDefinition> definitions) {
        return definitions.stream().collect(Collectors.toUnmodifiableMap(MultiblockDefinition::trigger, Function.identity(), (first, second) -> first));
    }
}
