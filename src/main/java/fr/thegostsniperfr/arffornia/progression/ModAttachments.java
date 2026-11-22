package fr.thegostsniperfr.arffornia.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.thegostsniperfr.arffornia.Arffornia;
import fr.thegostsniperfr.arffornia.stats.DailyPlaytime;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.time.LocalDate;
import java.util.function.Supplier;

public final class ModAttachments {
    public static final long NO_PROGRESSION = -1L;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Arffornia.MODID);

    /** The player's active progression (solo or team) as last reported by the website. */
    public static final Supplier<AttachmentType<Long>> ACTIVE_PROGRESSION = ATTACHMENT_TYPES.register("active_progression",
            () -> AttachmentType.builder(() -> NO_PROGRESSION).serialize(Codec.LONG).copyOnDeath().build());

    private static final Codec<DailyPlaytime> PLAYTIME_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.xmap(LocalDate::parse, LocalDate::toString).fieldOf("day").forGetter(DailyPlaytime::day),
            Codec.LONG.fieldOf("seconds").forGetter(DailyPlaytime::seconds)
    ).apply(instance, DailyPlaytime::new));

    /** The player's playtime on the current UTC day, as last counted (spec 019 Q5). */
    public static final Supplier<AttachmentType<DailyPlaytime>> PLAYTIME = ATTACHMENT_TYPES.register("daily_playtime",
            () -> AttachmentType.builder(() -> DailyPlaytime.NONE).serialize(PLAYTIME_CODEC).copyOnDeath().build());

    /** Epoch millis up to which this session's playtime is counted; reset at each login, so the saved value is never read. */
    public static final Supplier<AttachmentType<Long>> PLAYTIME_COUNTED_UNTIL = ATTACHMENT_TYPES.register("playtime_counted_until",
            () -> AttachmentType.builder(() -> 0L).serialize(Codec.LONG).copyOnDeath().build());

    private ModAttachments() {
    }

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }
}
