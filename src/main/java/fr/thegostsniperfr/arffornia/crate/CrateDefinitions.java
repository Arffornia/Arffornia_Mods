package fr.thegostsniperfr.arffornia.crate;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The loaded crates. The server set comes from data; the client set is what the server last synced. An integrated
 * server fills both with the same data, so {@link #find} may read either.
 */
public final class CrateDefinitions {
    private static volatile Map<ResourceLocation, CrateDefinition> server = Map.of();
    private static volatile Map<ResourceLocation, CrateDefinition.Client> client = Map.of();

    private CrateDefinitions() {
    }

    public static void setServer(Collection<CrateDefinition> definitions) {
        server = definitions.stream().collect(Collectors.toUnmodifiableMap(CrateDefinition::id, Function.identity()));
    }

    public static void setClient(List<CrateDefinition.Client> definitions) {
        client = definitions.stream().collect(Collectors.toUnmodifiableMap(CrateDefinition.Client::id, Function.identity()));
    }

    @Nullable
    public static CrateDefinition server(ResourceLocation id) {
        return server.get(id);
    }

    public static Collection<CrateDefinition> server() {
        return server.values();
    }

    /** Name, key model and rarity, from the server data when this side has it, else from the last sync. */
    @Nullable
    public static CrateDefinition.Client find(ResourceLocation id) {
        CrateDefinition definition = server.get(id);
        return definition != null ? definition.client() : client.get(id);
    }

    public static Collection<CrateDefinition.Client> clientView() {
        return client.values();
    }
}
