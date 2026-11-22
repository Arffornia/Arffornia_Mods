package fr.thegostsniperfr.arffornia.crate;

import fr.thegostsniperfr.arffornia.network.ClientboundCratePreviewPacket;

import java.util.function.Consumer;

/** The client opens the preview screen through this hook, set from the client entry point, so common code stays server-safe. */
public final class CrateClientHooks {
    private static volatile Consumer<ClientboundCratePreviewPacket> openPreview = packet -> {
    };

    private CrateClientHooks() {
    }

    public static void set(Consumer<ClientboundCratePreviewPacket> opener) {
        openPreview = opener;
    }

    public static void openPreview(ClientboundCratePreviewPacket packet) {
        openPreview.accept(packet);
    }
}
