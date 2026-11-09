package fr.thegostsniperfr.arffornia.progression;

import com.mojang.serialization.Codec;
import fr.thegostsniperfr.arffornia.Arffornia;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final long NO_PROGRESSION = -1L;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Arffornia.MODID);

    /** The player's active progression (solo or team) as last reported by the website. */
    public static final Supplier<AttachmentType<Long>> ACTIVE_PROGRESSION = ATTACHMENT_TYPES.register("active_progression",
            () -> AttachmentType.builder(() -> NO_PROGRESSION).serialize(Codec.LONG).copyOnDeath().build());

    private ModAttachments() {
    }

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }
}
