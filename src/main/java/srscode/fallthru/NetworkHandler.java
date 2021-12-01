/*
 * Project      : FallThru
 * File         : NetworkHandler.java
 *
 * Copyright (c) 2019-2021 srsCode, srs-bsns (forfrdm [at] gmail.com)
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

import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod.EventBusSubscriber(modid = FallThru.MOD_ID)
public enum NetworkHandler
{
    INSTANCE;

    static final Marker           MARKER_NETWORK   = MarkerManager.getMarker("NETWORK");
    static final ResourceLocation NET_CHANNEL_NAME = new ResourceLocation(FallThru.MOD_ID, "config_update");
    static final String           NET_VERSION      = "ftcu-1";
    static final SimpleChannel    CHANNEL;

    static
    {
        FallThru.LOGGER.debug(MARKER_NETWORK, "Creating an instance of NetworkHandler with channel: {}, version: {}", NET_CHANNEL_NAME, NET_VERSION);
        CHANNEL = NetworkRegistry.newSimpleChannel(NET_CHANNEL_NAME, () -> NET_VERSION, NET_VERSION::equals, NET_VERSION::equals);
    }

    /**
     * An Event handler to register the {@link S2CFallThruUpdatePacket} packet for server -> client config sync.
     *
     * @param event The {@link FMLCommonSetupEvent}.
     */
    void registerPackets(@SuppressWarnings("unused") final FMLCommonSetupEvent event)
    {
        FallThru.LOGGER.debug(MARKER_NETWORK, "Registering the S2CFallThruUpdatePacket on channel: {}", NET_CHANNEL_NAME);
        CHANNEL.messageBuilder(S2CFallThruUpdatePacket.class, 0)
            .decoder(S2CFallThruUpdatePacket::decode)
            .encoder(S2CFallThruUpdatePacket::encode)
            .consumer(S2CFallThruUpdatePacket::handle)
            .add();
    }

    /**
     * A helper for syncing a single client from a remote server. Useful for when a player connects to the server.
     */
    void updatePlayer(final ServerPlayer player)
    {
        FallThru.LOGGER.debug(MARKER_NETWORK, "Sending config update packet to: {}, on channel: {}", player.getName().getString(), NET_CHANNEL_NAME);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CFallThruUpdatePacket());
    }

    /**
     * A helper for syncing all clients from the server. Useful for when the configuration changes on the server and
     * all clients need to be updated.
     */
    void updateAll()
    {
        FallThru.LOGGER.debug(MARKER_NETWORK, "Sending S2CFallThruUpdatePacket to all clients on channel {}", NET_CHANNEL_NAME);
        CHANNEL.send(PacketDistributor.ALL.noArg(), new S2CFallThruUpdatePacket());
    }

    /**
     * This Event handler syncs the {@link BlockConfigMap} with {@link CommonConfig#passableBlocks}.
     * {@link ServerStartingEvent} is the earliest lifecycle event that this can be done, as the
     * game's Tags (from datapacks) will have not being populated yet. Yay for data-driven structures!
     *
     * @param event The FMLServerStartingEvent.
     */
    @SubscribeEvent
    public static void onServerStarting(final ServerStartingEvent event)
    {
        FallThru.BLOCK_CONFIG_MAP.syncLocal();
    }

    /**
     * A Network event handler for syncing the client with a remote server upon connection.
     */
    @SubscribeEvent
    public static void onPlayerLogin(final PlayerEvent.PlayerLoggedInEvent event)
    {
        final var player = event.getPlayer();
        final var server = player.getServer();
        if (server != null && server.isDedicatedServer()) {
            server.execute(() -> INSTANCE.updatePlayer((ServerPlayer) player));
        }
    }
}
