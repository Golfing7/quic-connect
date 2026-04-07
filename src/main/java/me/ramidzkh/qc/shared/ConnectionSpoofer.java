package me.ramidzkh.qc.shared;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.protocol.Packet;

public interface ConnectionSpoofer {
    void quic_connect$sendDatagramPacket(Packet<?> packet);

    void quic_connect$readPacket(ChannelHandlerContext ctx, Packet<?> packet) throws Exception;
}
