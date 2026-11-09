package fr.thegostsniperfr.arffornia.recipe.pack;

import fr.thegostsniperfr.arffornia.Arffornia;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The website-generated recipe datapack, kept on disk as the last valid download and loaded as a required pack on
 * top of every other one, so its overrides win over mod recipes and it cannot be disabled with {@code /datapack}.
 */
public final class RecipePack {
    public static final String ID = "arffornia/progression";
    private static final PackSelectionConfig SELECTION = new PackSelectionConfig(true, Pack.Position.TOP, true);

    private RecipePack() {
    }

    public static Path file() {
        return FMLPaths.GAMEDIR.get().resolve(Arffornia.MODID).resolve("recipe-pack.zip");
    }

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA || event.isTrusted()) {
            return;
        }
        event.addRepositorySource(onLoad -> {
            Path file = file();
            if (!Files.isRegularFile(file)) {
                return;
            }
            PackLocationInfo location = new PackLocationInfo(ID, Component.translatable("pack.arffornia.progression"), PackSource.SERVER, Optional.empty());
            Pack pack = Pack.readMetaAndCreate(location, new FilePackResources.FileResourcesSupplier(file), PackType.SERVER_DATA, SELECTION);
            if (pack == null) {
                Arffornia.LOGGER.error("Recipe datapack {} has no readable pack.mcmeta; it is not loaded.", file);
                return;
            }
            onLoad.accept(pack);
        });
    }
}
