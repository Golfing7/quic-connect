package me.ramidzkh.qc.shared;

import com.mojang.logging.LogUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.slf4j.Logger;

import java.util.concurrent.ThreadLocalRandom;

/**
 * This acts as a message handler for datagram packets.
 * <p>
 * The goal of this class is to transparently forward packets into the connection.
 * </p>
 */
public class DatagramConnectionWrapper extends SimpleChannelInboundHandler<Packet<?>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The rate at which packets are simulated to be dropped */
    private static final double DROP_RATE = 0.2;

    private final ConnectionSpoofer accessor;

    public DatagramConnectionWrapper(Connection connection) {
        this.accessor = (ConnectionSpoofer) connection;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet<?> msg) throws Exception {
        if (ThreadLocalRandom.current().nextDouble() > DROP_RATE) {
            accessor.quic_connect$readPacket(ctx, msg);
        } else {
            LOGGER.info("DROPPING PACKET {}", msg.getClass().getSimpleName());
        }
    }
}
