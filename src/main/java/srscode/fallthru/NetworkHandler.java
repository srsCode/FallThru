/*
 * Project      : FallThru
 * File         : NetworkHandler.java
 *
 * Copyright (c) 2019-2023 srsCode, srs-bsns (forfrdm [at] gmail.com)
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

import java.util.Objects;
import java.util.stream.Stream;

import com.mojang.datafixers.util.Either;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.NetworkEvent;
import net.neoforged.neoforge.network.NetworkRegistry;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.simple.SimpleChannel;

import de.srsco.srslib.util.Util;
import srscode.fallthru.mixin.Accessors;


@SuppressWarnings("WeakerAccess")
@Mod.EventBusSubscriber(modid = FallThru.MOD_ID)
public enum NetworkHandler
{
    INSTANCE;

    private static final Marker           MARKER_NETWORK   = MarkerFactory.getMarker("NETWORK");
    private static final ResourceLocation NET_CHANNEL_NAME = new ResourceLocation(FallThru.modId(), "config_update");
    private static final String           NET_VERSION      = "ftcu-1";
    private static final SimpleChannel    CHANNEL;

    static
    {
        FallThru.logger().debug(MARKER_NETWORK, "Creating an instance of NetworkHandler with channel: {}, version: {}", NET_CHANNEL_NAME, NET_VERSION);
        CHANNEL = NetworkRegistry.newSimpleChannel(NET_CHANNEL_NAME, () -> NET_VERSION, NET_VERSION::equals, NET_VERSION::equals);
    }

    /**
     * An Event handler to register the {@link S2CFallThruUpdatePacket} packet for server -> client config sync.
     *
     * @param ignored The {@link FMLCommonSetupEvent}.
     */
    void registerPackets(final FMLCommonSetupEvent ignored)
    {
        FallThru.logger().debug(MARKER_NETWORK, "Registering packets on channel: {}", NET_CHANNEL_NAME);
        CHANNEL.messageBuilder(S2CFallThruUpdatePacket.class, 0)
            .decoder(buf -> new S2CFallThruUpdatePacket(Objects.requireNonNull(buf.readNbt(), "FriendlyByteBuf is null. This should be impossible.")))
            .encoder((packet, buf) -> buf.writeNbt(packet.configBlocks()))
            .consumerMainThread(FallThru.blockConfigs()::syncFromRemote)
            .add();

        CHANNEL.messageBuilder(S2CFallThruAddPacket.class, 1)
            .decoder(S2CFallThruAddPacket::read)
            .encoder(S2CFallThruAddPacket::write)
            .consumerMainThread(S2CFallThruAddPacket::handle)
            .add();

        CHANNEL.messageBuilder(S2CFallThruRemovePacket.class, 2)
            .decoder(S2CFallThruRemovePacket::read)
            .encoder(S2CFallThruRemovePacket::write)
            .consumerMainThread(S2CFallThruRemovePacket::handle)
            .add();
    }

    /**
     * A helper for syncing a single client from a remote server. Useful for when a player connects to the server.
     */
    void updatePlayer(final ServerPlayer player)
    {
        FallThru.logger().debug(MARKER_NETWORK, "Sending config update packet to: {}, on channel: {}", player.getName().getString(), NET_CHANNEL_NAME);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CFallThruUpdatePacket());
    }

    /**
     * A helper for syncing all clients from the server. Useful for when the configuration changes on the server and
     * all clients need to be updated.
     */
    void updateAll()
    {
        FallThru.logger().debug(MARKER_NETWORK, "Sending S2CFallThruUpdatePacket to all clients on channel {}", NET_CHANNEL_NAME);
        CHANNEL.send(PacketDistributor.ALL.noArg(), new S2CFallThruUpdatePacket());
    }

    /**
     * This Event handler syncs the {@link BlockConfigMap} with {@link CommonConfig#passableBlocks}.
     * {@link ServerStartingEvent} is the earliest lifecycle event that this can be done, as the
     * game's Tags (from datapacks) will have not being populated yet. Yay for data-driven structures!
     *
     * @param ignore The ServerStartingEvent.
     */
    @SubscribeEvent
    public static void onServerStarting(final ServerStartingEvent ignore)
    {
        FallThru.blockConfigs().syncLocal();
    }

    /**
     * A Network event handler for syncing the client with a remote server upon connection.
     */
    @SubscribeEvent
    public static void onPlayerLogin(final PlayerEvent.PlayerLoggedInEvent event)
    {
        final var player = event.getEntity();
        final var server = player.getServer();
        if (server != null && server.isDedicatedServer()) {
            server.execute(() -> INSTANCE.updatePlayer((ServerPlayer) player));
        }
    }

    /**
     * The packet used for syncing the server config with the client config.
     *
     * @param configBlocks The serialized {@link BlockConfigMap} to be sent to a client.
     */
    record S2CFallThruUpdatePacket(CompoundTag configBlocks)
    {
        S2CFallThruUpdatePacket()
        {
            this(FallThru.blockConfigs().toNBT());
        }
    }

    /**
     * A server packet used to sync a client config when a new block is added.
     *
     * @param either
     * @param speedMult
     * @param damageMult
     * @param allowdef
     */
    record S2CFallThruAddPacket(Either<ResourceKey<Block>, TagKey<Block>> either, double speedMult, double damageMult, boolean allowdef)
    {
        static void write(final S2CFallThruAddPacket packet, final FriendlyByteBuf buf)
        {
            buf.writeBoolean(packet.either().left().isPresent());
            buf.writeEither(
                packet.either(),
                (b, rk) -> {
                    b.writeResourceLocation(rk.registry());
                    b.writeResourceLocation(rk.location());
                },
                (b, tk) -> {
                    b.writeResourceLocation(tk.registry().registry());
                    b.writeResourceLocation(tk.location());
                });
            buf.writeDouble(packet.speedMult());
            buf.writeDouble(packet.damageMult());
            buf.writeBoolean(packet.allowdef());
        }

        static S2CFallThruAddPacket read(final FriendlyByteBuf buf)
        {
            return new S2CFallThruAddPacket(
                buf.readBoolean()
                    ? Either.left(ResourceKey.create(ResourceKey.createRegistryKey(buf.readResourceLocation()), buf.readResourceLocation()))
                    : Either.right(TagKey.create(ResourceKey.createRegistryKey(buf.readResourceLocation()), buf.readResourceLocation())),
                buf.readDouble(),
                buf.readDouble(),
                buf.readBoolean()
            );
        }

        Stream<BlockConfigMap.BlockConfig> getBlockConfigs()
        {
            final var registry = BuiltInRegistries.BLOCK;
            return either().map(
                rk -> registry
                    .getHolder(rk)
                    .map(Holder::value)
                    .stream(),
                tk -> registry
                    .getTag(tk)
                    .stream()
                    .flatMap(holders -> holders.unwrap().right().stream())
                    .flatMap(blocks -> blocks.stream().map(Holder::value))
            )
                .map(block -> new BlockConfigMap.BlockConfig(block, Util.getResLoc(block).orElseThrow(), speedMult(), damageMult(), allowdef(),
                ((Accessors.BlockBehaviourAccessor) block).getHasCollision(), block.defaultBlockState().canOcclude()));
        }

        static void handle(final S2CFallThruAddPacket packet, final NetworkEvent.Context ignored)
        {
            packet.getBlockConfigs().forEach(FallThru.blockConfigs()::add);
        }
    }

    /**
     * A server packet used to sync a client config when a block is to be removed.
     *
     //* @param registry A {@link ResourceLocation} of a registry for a {@link ResourceKey} or a {@link TagKey}.
     //* @param object   A ResourceLocation of a registry object.
     //* @param type     The {@link BlockConfigMap.BlockConfig.EntryType} of object that {@param object} represents (Either a Block or a Tag).
     */
    //static final record S2CFallThruRemovePacket(ResourceLocation registry, ResourceLocation object, BlockConfig.EntryType type)
    record S2CFallThruRemovePacket(Either<ResourceKey<Block>, TagKey<Block>> either)
    {
        static void write(final S2CFallThruRemovePacket packet, final FriendlyByteBuf buf)
        {
            buf.writeBoolean(packet.either().left().isPresent());
            buf.writeEither(
                packet.either(),
                (b, rk) -> {
                    b.writeResourceLocation(rk.registry());
                    b.writeResourceLocation(rk.location());
                },
                (b, tk) -> {
                    b.writeResourceLocation(tk.registry().registry());
                    b.writeResourceLocation(tk.location());
                });
        }

        static S2CFallThruRemovePacket read(final FriendlyByteBuf buf)
        {
            return new S2CFallThruRemovePacket(
                buf.readBoolean()
                    ? Either.left(ResourceKey.create(ResourceKey.createRegistryKey(buf.readResourceLocation()), buf.readResourceLocation()))
                    : Either.right(TagKey.create(ResourceKey.createRegistryKey(buf.readResourceLocation()), buf.readResourceLocation()))
            );
        }

        Stream<Block> getBlocks()
        {
            final var registry = BuiltInRegistries.BLOCK;
            return either.map(
                rk -> registry
                    .getHolder(rk)
                    .map(Holder::value)
                    .stream(),
                tk -> registry
                    .getTag(tk)
                    .stream()
                    .flatMap(holders -> holders.unwrap().right().stream())
                    .flatMap(blocks -> blocks.stream().map(Holder::value))
            );
        }

        static void handle(final S2CFallThruRemovePacket packet, final NetworkEvent.Context ignored)
        {
            packet.getBlocks().forEach(FallThru.blockConfigs()::remove);
        }
    }
}
