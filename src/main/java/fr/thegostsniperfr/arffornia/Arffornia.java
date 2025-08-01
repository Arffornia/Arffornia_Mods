package fr.thegostsniperfr.arffornia;

import com.mojang.logging.LogUtils;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.command.ArfforniaCommand;
import fr.thegostsniperfr.arffornia.compat.ftbteams.FTBTeamsEventHandler;
import fr.thegostsniperfr.arffornia.creative.ModCreativeTabs;
import fr.thegostsniperfr.arffornia.item.ModItems;
import fr.thegostsniperfr.arffornia.recipe.RecipeBanManager;
import fr.thegostsniperfr.arffornia.shop.RewardHandler;
import fr.thegostsniperfr.arffornia.shop.internal.DatabaseManager;
import fr.thegostsniperfr.arffornia.util.Permissions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Arffornia.MODID)
public class Arffornia {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "arffornia";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final ArfforniaApiService ARFFORNA_API_SERVICE = new ArfforniaApiService();


    private DatabaseManager databaseManager;
    private RewardHandler rewardHandler;

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Arffornia(IEventBus modEventBus, ModContainer modContainer) {
        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (Arffornia) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(this);

        NeoForge.EVENT_BUS.register(Permissions.class);

        NeoForge.EVENT_BUS.register(RecipeBanManager.class);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "arffornia-common.toml");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("ftbteams")) {
            LOGGER.info("FTB Teams found. Registering compatibility event handler.");
            new FTBTeamsEventHandler();
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        this.databaseManager = new DatabaseManager();
        this.rewardHandler = new RewardHandler(this.databaseManager, event.getServer());

        // Register all commands
        ArfforniaCommand.register(event.getServer().getCommands().getDispatcher(), this.rewardHandler);
    }

    /**
     * Cleanly shuts down resources when the server stops.
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (this.databaseManager != null) {
            LOGGER.info("Closing shop database connection pool.");
            this.databaseManager.close();
        }

        if (this.rewardHandler != null) {
            this.rewardHandler.shutdown();
        }
    }

    /**
     * Fired when a player joins the server.
     * 1. Ensure the player exists in the backend database.
     * 2. Once confirmed, proceed with caching their ID and checking for shop rewards.
     */
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (this.rewardHandler != null && event.getEntity() instanceof ServerPlayer player) {
            ARFFORNA_API_SERVICE.ensurePlayerExists(player.getUUID(), player.getName().getString())
                    .thenAccept(success -> {
                        if (success) {
                            this.rewardHandler.addPlayerToCache(player);
                            this.rewardHandler.hasPendingRewards(player).thenAccept(hasRewards -> {
                                if (hasRewards) {
                                    player.getServer().execute(() -> {
                                        player.sendSystemMessage(Component.literal("§aYou have pending rewards from the shop!"));
                                        player.sendSystemMessage(Component.literal("§eType §b/arffornia shop claim_reward §eto receive them."));
                                    });
                                }
                            });
                        } else {
                            player.getServer().execute(() -> {
                                player.sendSystemMessage(Component.literal("§cWarning: Could not synchronize your progression data. Shop and progression features may be unavailable."));
                                LOGGER.error("Failed to ensure player {} exists in the database.", player.getName().getString());
                            });
                        }
                    });
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (this.rewardHandler != null && event.getEntity() instanceof ServerPlayer) {
            this.rewardHandler.removePlayerFromCache((ServerPlayer) event.getEntity());
        }
    }
}
