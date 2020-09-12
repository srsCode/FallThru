/*
 * Project      : FallThru
 * File         : S2CFallThruUpdatePacket.java
 * Last Modified: 20200908-10:50:27-0400
 *
 * Copyright (c) 2019-2020 srsCode, srs-bsns (forfrdm [at] gmail.com)
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
        FallThru.LOGGER.debug(NetworkHandler.MARKER_NETWORK, "Enqueuing config synchronization from the server");
        ctx.get().enqueueWork(() -> FallThru.BLOCK_CONFIG_MAP.syncFromRemote(packet.configBlocks));
        ctx.get().setPacketHandled(true);
    }
}
