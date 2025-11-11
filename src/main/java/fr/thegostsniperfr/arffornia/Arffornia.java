package fr.thegostsniperfr.arffornia;

import com.mojang.logging.LogUtils;
import fr.thegostsniperfr.arffornia.api.service.ArfforniaApiService;
import fr.thegostsniperfr.arffornia.block.ModBlocks;
import fr.thegostsniperfr.arffornia.block.entity.ModBlockEntities;
import fr.thegostsniperfr.arffornia.command.ArfforniaCommand;
import fr.thegostsniperfr.arffornia.compat.ftbteams.FTBTeamsEventHandler;
import fr.thegostsniperfr.arffornia.config.ApiConfig;
import fr.thegostsniperfr.arffornia.creative.ModCreativeTabs;
import fr.thegostsniperfr.arffornia.item.ModItems;
import fr.thegostsniperfr.arffornia.network.ClientboundUpdateTargetNamePacket;
import fr.thegostsniperfr.arffornia.recipe.CustomRecipeManager;
import fr.thegostsniperfr.arffornia.recipe.RecipeBanManager;
import fr.thegostsniperfr.arffornia.screen.ModMenuTypes;
import fr.thegostsniperfr.arffornia.shop.RewardHandler;
import fr.thegostsniperfr.arffornia.shop.internal.DatabaseManager;
import fr.thegostsniperfr.arffornia.util.Permissions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

@Mod(Arffornia.MODID)
public class Arffornia {
    public static final String MODID = "arffornia";
    public static final Logger LOGGER = LogUtils.getLogger();

    private DatabaseManager databaseManager;
    private RewardHandler rewardHandler;
    private static final AtomicBoolean hasAttemptedMigration = new AtomicBoolean(false);

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public Arffornia(IEventBus modEventBus, ModContainer modContainer) {
        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (Arffornia) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        // Register event handlers to the FORGE event bus
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(Permissions.class);
        NeoForge.EVENT_BUS.register(RecipeBanManager.class);
        NeoForge.EVENT_BUS.register(CustomRecipeManager.class);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "arffornia-common.toml");

        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
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
        ArfforniaCommand.register(event.getServer().getCommands().getDispatcher(), this.rewardHandler);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (ApiConfig.MIGRATE_ON_STARTUP.get() && !hasAttemptedMigration.getAndSet(true)) {
            Arffornia.LOGGER.info("Run Arffornia custom recipies migration.");
            ArfforniaApiService.getInstance().runRecipeMigration(event.getServer(), RecipeBanManager.getOriginalRecipes());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (this.databaseManager != null) {
            this.databaseManager.close();
        }

        if (this.rewardHandler != null) {
            this.rewardHandler.shutdown();
        }
        ArfforniaApiService.getInstance().shutdown();
    }

    /**
     * Fired when a player joins the server.
     * 1. Ensure the player exists in the backend database.
     * 2. Once confirmed, proceed with caching their ID and checking for shop rewards.
     * 3. Fetch and send the current milestone target to the client.
     */
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (this.rewardHandler != null && event.getEntity() instanceof ServerPlayer player) {
            ArfforniaApiService.getInstance().ensurePlayerExists(player.getUUID(), player.getName().getString())
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

                            // Fetch and send the current milestone target to the player
                            updateAndSendPlayerTarget(player);

                        } else {
                            player.getServer().execute(() -> {
                                player.sendSystemMessage(Component.literal("§cWarning: Could not synchronize your progression data. Shop and progression features may be unavailable."));
                                LOGGER.error("Failed to ensure player {} exists in the database.", player.getName().getString());
                            });
                        }
                    });
        }
    }

    /**
     * Fetches the player's graph data, finds the name of their target milestone,
     * and sends it to the client.
     *
     * @param player The player to update.
     */
    private void updateAndSendPlayerTarget(ServerPlayer player) {
        String playerUuid = player.getUUID().toString().replace("-", "");

        ArfforniaApiService.getInstance().fetchPlayerGraphData(playerUuid).thenAccept(graphData -> {
            if (graphData != null && graphData.playerProgress() != null && graphData.playerProgress().currentTargetId() != null) {
                int targetId = graphData.playerProgress().currentTargetId();
                ArfforniaApiService.getInstance().fetchMilestoneDetails(targetId).thenAccept(details -> {
                    if (details != null) {
                        PacketDistributor.sendToPlayer(player, new ClientboundUpdateTargetNamePacket(details.name()));
                    } else {
                        PacketDistributor.sendToPlayer(player, new ClientboundUpdateTargetNamePacket("Unknown Target"));
                        LOGGER.warn("Could not fetch details for target milestone ID {} for player {}", targetId, player.getName().getString());
                    }
                });
            } else {
                PacketDistributor.sendToPlayer(player, new ClientboundUpdateTargetNamePacket("None"));
            }
        }).exceptionally(ex -> {
            LOGGER.error("Failed to fetch graph data on player join for {}: {}", player.getName().getString(), ex.getMessage());
            player.getServer().execute(() -> {
                PacketDistributor.sendToPlayer(player, new ClientboundUpdateTargetNamePacket("Error"));
            });
            return null;
        });
    }


    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (this.rewardHandler != null && event.getEntity() instanceof ServerPlayer) {
            this.rewardHandler.removePlayerFromCache((ServerPlayer) event.getEntity());
        }
    }
}