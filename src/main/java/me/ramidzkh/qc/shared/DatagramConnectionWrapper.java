package me.ramidzkh.qc.shared;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;

/**
 * This acts as a message handler for datagram packets.
 * <p>
 * The goal of this class is to transparently forward packets into the connection.
 * </p>
 */
public class DatagramConnectionWrapper extends SimpleChannelInboundHandler<Packet<?>> {
    private final ConnectionSpoofer accessor;

    public DatagramConnectionWrapper(Connection connection) {
        this.accessor = (ConnectionSpoofer) connection;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet<?> msg) throws Exception {
        accessor.quic_connect$readPacket(ctx, msg);
    }
}
