/*
 * Project      : FallThru
 * File         : FallThru.java
 * Last Modified: 20190914-03:05:53-0400
 *
 * Copyright (c) 2019 srsCode, srs-bsns (forfrdm [at] gmail.com)
 *
 * The MIT License (MIT)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package srscode.fallthru;

import java.util.Collection;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.Logging;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.config.ModConfig.ConfigReloading;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.fml.network.simple.SimpleChannel;

import srscode.fallthru.BlockConfigMap.BlockConfig;

@Mod(FallThru.MOD_ID)
public class FallThru
{
    static final String MOD_ID           = "fallthru";
    static final Marker MARKER_LIFECYCLE = MarkerManager.getMarker("LIFECYCLE").addParents(Logging.LOADING);
    static final Marker MARKER_CONFIG    = MarkerManager.getMarker("CONFIG");
    static final Marker MARKER_NETWORK   = MarkerManager.getMarker("NETWORK");
    static final Marker MARKER_BLOCKCFG  = MarkerManager.getMarker("BLOCK CONFIG");
    static final Logger LOGGER           = LogManager.getLogger(MOD_ID);

    static final ResourceLocation NET_CHANNEL_NAME = new ResourceLocation(MOD_ID, "config_update");
    static final String           NET_VERSION      = "ftcu-1";

    static final BlockConfigMap  BLOCK_CONFIG_MAP = new BlockConfigMap();
    static final ForgeConfigSpec COMMON_CONFIG_SPEC;
    static final CommonConfig    COMMON_CONFIG;

    static
    {
        final Pair<CommonConfig, ForgeConfigSpec> common = new Builder().configure(CommonConfig::new);
        COMMON_CONFIG_SPEC = common.getRight();
        COMMON_CONFIG = common.getLeft();
    }

    private static FallThru instance;

    private final SimpleChannel channel;

    public FallThru()
    {
        LOGGER.debug(MARKER_LIFECYCLE, "Creating an instance of FallThru!");
        instance = this;
        LOGGER.debug(MARKER_NETWORK, "Creating an instance of NetworkHandler with channel: {}, version: {}", NET_CHANNEL_NAME, NET_VERSION);
        this.channel = NetworkRegistry.ChannelBuilder.named(NET_CHANNEL_NAME).networkProtocolVersion(() -> NET_VERSION)
            .clientAcceptedVersions(NET_VERSION::equals).serverAcceptedVersions(NET_VERSION::equals).simpleChannel();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_CONFIG_SPEC, MOD_ID + ".toml");
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerPackets);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::blockConfigMapInit);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigUpdate);
        MinecraftForge.EVENT_BUS.register(this); // FMLServerStartingEvent, PlayerLoggedInEvent
    }

    /**
     * An Event handler to register the {@link S2CFallThruUpdatePacket} packet for server -> client config sync.
     *
     * @param event The {@link FMLCommonSetupEvent}.
     */
    private void registerPackets(final FMLCommonSetupEvent event)
    {
        LOGGER.debug(MARKER_NETWORK, "Registering the S2CFallThruUpdatePacket on channel: {}", NET_CHANNEL_NAME);
        this.channel
            .messageBuilder(S2CFallThruUpdatePacket.class, 0)
            .decoder(S2CFallThruUpdatePacket::decode)
            .encoder(S2CFallThruUpdatePacket::encode)
            .consumer(S2CFallThruUpdatePacket::handle)
            .add();
    }

    /**
     * An Event handler to initialize the {@link BlockConfigMap} storage at a time when everything should be finished registering.
     *
     * @param event The {@link FMLLoadCompleteEvent}.
     */
    private void blockConfigMapInit(final FMLLoadCompleteEvent event)
    {
        BLOCK_CONFIG_MAP.init();
    }

    /**
     * This Event handler syncs the {@link BlockConfigMap} with {@link CommonConfig#passableBlocks}.
     * This fires when the config file has been changed on disk and only updates on the client if
     * the client is <b>not</b> connected to a remote server (the client should <b>not</b> override
     * it's current BlockConfigMap that was sent to it from the remote server), or if the integrated
     * server <b>is</b> running, since Tags won't be populated from datapacks atleast until the server
     * is starting, at which time the syncing will occur.
     * This will always cause syncing on a dedicated server that will propogate to clients.
     *
     * @param event The {@link ModConfig.ConfigReloading} event
     */
    // TODO: This event was not firing consistently on config file change.
    public void onConfigUpdate(final ConfigReloading event)
    {
        if (event.getConfig().getModId().equals(MOD_ID)) {
            if (FMLEnvironment.dist == Dist.CLIENT && (Minecraft.getInstance().getIntegratedServer() == null || Minecraft.getInstance().getConnection() != null)) {
                LOGGER.debug(MARKER_CONFIG, "The config file has changed but the integrated server is not running. Nothing to do.");
            } else {
                LOGGER.debug(MARKER_CONFIG, "The config file has changed and the server is running. Resyncing config.");
                syncLocal();
            }
        }
    }

    /**
     * This Event handler syncs the {@link BlockConfigMap} with {@link CommonConfig#passableBlocks}.
     * {@link FMLServerStartingEvent} is the earliest lifecycle event that this can be done, as the
     * game's Tags (from datapacks) will have not being populated yet. Yay for data-driven structures!
     *
     * @param event The FMLServerStartingEvent.
     */
    @SubscribeEvent
    public void onServerStarting(final FMLServerStartingEvent event)
    {
        syncLocal();
    }

    /**
     * A Network event handler for syncing the client with a remote server upon connection.
     */
    @SubscribeEvent
    public void onPlayerLogin(final PlayerLoggedInEvent event)
    {
        final PlayerEntity player = event.getPlayer();
        final MinecraftServer server = player.getServer();
        if (server != null && server.isDedicatedServer()) {
            server.execute(() -> updatePlayer((ServerPlayerEntity) player));
        }
    }

    /**
     * When this method is called, it will resync the {@link BlockConfigMap} with {@link CommonConfig#passableBlocks}
     * If this is a dedicated server, dispatch a {@link S2CFallThruUpdatePacket}.
     */
    private void syncLocal()
    {
        LOGGER.debug(MARKER_CONFIG, "Syncing local config");
        BLOCK_CONFIG_MAP.clear();
        BLOCK_CONFIG_MAP.addAll(BLOCK_CONFIG_MAP.parseConfig(COMMON_CONFIG.getPassableBlocks()));

        // if this is a dedicated server, dispatch a S2CFallThruUpdatePacket.
        DistExecutor.runWhenOn(Dist.DEDICATED_SERVER, () -> this::updateAll);
    }

    /**
     * Syncs the {@link BlockConfigMap} from a {@link Collection} of {@link BlockConfig}s passed
     * from {@link S2CFallThruUpdatePacket#handle}.
     *
     * @param blockConfigs A Collection of BlockConfigs.
     */
    void syncFromRemote(final CompoundNBT blockConfigs)
    {
        LOGGER.debug(MARKER_CONFIG, "Syncing from remote config");
        BLOCK_CONFIG_MAP.clear();
        BLOCK_CONFIG_MAP.fromNBT(blockConfigs);
    }

    /**
     * A helper for syncing all clients from the server. Useful for when the configuration changes on the server and
     * all clients need to be updated.
     */
    private void updateAll()
    {
        LOGGER.debug(MARKER_NETWORK, "Sending S2CFallThruUpdatePacket to all clients on channel {}", NET_CHANNEL_NAME);
        this.channel.send(PacketDistributor.ALL.noArg(), new S2CFallThruUpdatePacket());
    }

    /**
     * A helper for syncing a single client from a remote server. Useful for when a player connects to the server.
     */
    private void updatePlayer(final ServerPlayerEntity player)
    {
        LOGGER.debug(MARKER_NETWORK, "Sending config update packet to: {}, on channel: {}", player.getName().getString(), NET_CHANNEL_NAME);
        this.channel.send(PacketDistributor.PLAYER.with(() -> player), new S2CFallThruUpdatePacket());
    }

    /**
     * @return The FallThru instance.
     */
    static FallThru getInstance()
    {
        return instance;
    }
}
