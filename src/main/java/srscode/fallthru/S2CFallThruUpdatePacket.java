package srscode.fallthru;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;

import net.minecraftforge.fml.network.NetworkEvent;

/**
 * The packet used for syncing the server config with the client config.
 */
final class S2CFallThruUpdatePacket
{
    /**
     * The main NBT name for the list of BlockConfigs in the serialized BlockConfigMap CompoundNBT object.
     */
    static final String NBT_CONFIG_TAG = "blocklist";

    /**
     * The serialized {@link BlockConfigMap} to be sent to a client.
     */
    private final CompoundNBT configBlocks;

    S2CFallThruUpdatePacket()
    {
        this.configBlocks = FallThru.BLOCK_CONFIG_MAP.toNBT();
    }

    private S2CFallThruUpdatePacket(final CompoundNBT nbt)
    {
        this.configBlocks = nbt;
    }

    /**
     * A S2CFallThruUpdatePacket decoder.
     *
     * @param  input A {@link PacketBuffer} containing a serialized {@link BlockConfigMap}.
     * @return       A new populated S2CFallThruUpdatePacket.
     */
    static S2CFallThruUpdatePacket decode(final PacketBuffer input)
    {
        return new S2CFallThruUpdatePacket(Objects.requireNonNull(input.readCompoundTag(), "PacketBuffer is null. This should be impossible."));
    }

    /**
     * A S2CFallThruUpdatePacket encoder for sending a serialized {@link BlockConfigMap}.
     *
     * @param output The {@link PacketBuffer} to write the serialized BlockConfigMap to.
     */
    final void encode(final PacketBuffer output)
    {
        output.writeCompoundTag(configBlocks);
    }

    /**
     * A handler for processing a S2CFallThruUpdatePacket on the client.
     *
     * @param packet The received packet to be handled.
     * @param ctx    The {@link NetworkEvent.Context} handling the communication.
     */
    static void handle(final S2CFallThruUpdatePacket packet, final Supplier<NetworkEvent.Context> ctx)
    {
        FallThru.LOGGER.debug(FallThru.MARKER_NETWORK, "Enqueuing config synchronization from the server");
        ctx.get().enqueueWork(() -> FallThru.getInstance().syncFromRemote(packet.configBlocks));
        ctx.get().setPacketHandled(true);
    }
}
