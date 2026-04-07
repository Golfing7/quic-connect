package me.ramidzkh.qc.mixin;

import io.netty.channel.*;
import io.netty.incubator.codec.quic.QuicStreamAddress;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import me.ramidzkh.qc.client.QuicConnection;
import me.ramidzkh.qc.client.QuicSocketAddress;
import me.ramidzkh.qc.client.ServerAddressProperties;
import me.ramidzkh.qc.shared.ConnectionSpoofer;
import me.ramidzkh.qc.shared.DatagramConnectionWrapper;
import net.minecraft.network.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@ChannelHandler.Sharable
@Mixin(Connection.class)
public abstract class ConnectionMixin implements ConnectionSpoofer {
    // TODO Make these maps and have certain packets be conditionally datagrams. I.e. packets that concern *other* entities may not be important.
    // Packets that can be conditionally datagrams:
    /*
    ClientboundMoveEntityPacket (If the entity being moved is not you!)
    ClientboundMoveMinecartPacket
     */
    @Unique
    private static final Set<Class<? extends Packet<?>>> CLIENTBOUND_DATAGRAM_PACKETS = Set.of(
            // Hurt animations are not important.
            ClientboundHurtAnimationPacket.class,
            // Particles are not important.
            ClientboundLevelParticlesPacket.class,

            // As of 1.21.11, these packets serve no purpose!
            ClientboundPlayerCombatEndPacket.class,
            ClientboundPlayerCombatEnterPacket.class,

            // Sound packets are very unnecessary
            ClientboundSoundEntityPacket.class,
            ClientboundSoundPacket.class,
            ClientboundStopSoundPacket.class
    );
    @Unique
    private static final Set<Class<? extends Packet<?>>> SERVERBOUND_DATAGRAM_PACKETS = Set.of(
            // This is a very good example of a packet that could be placed into a datagram channel.
            // Every client will send this packet to the server when they are done ticking.
            ServerboundClientTickEndPacket.class,
            // Players can control a boat to move it left or right. These packets are fine to miss!
            ServerboundPaddleBoatPacket.class,
            // Player input does not need to be reliable.
            ServerboundPlayerInputPacket.class,
            // A player swinging their arm (uselessly) does not need to be reliable.
            ServerboundSwingPacket.class

    );

    @Shadow
    private Channel channel;

    @Shadow
    private SocketAddress address;

    @Shadow
    private boolean encrypted;

    @Shadow
    @Final
    private static Logger LOGGER;

    @Shadow
    private static void syncAfterConfigurationChange(ChannelFuture channelFuture) {}

    @Shadow
    public abstract PacketFlow getReceiving();

    @Shadow
    protected abstract void channelRead0(ChannelHandlerContext par1, Object par2) throws Exception;

    @Shadow
    public abstract PacketFlow getSending();

    @Override
    public void quic_connect$sendDatagramPacket(Packet<?> packet) {
        this.channel.parent().writeAndFlush(packet, this.channel.parent().voidPromise());
    }

    @Override
    public void quic_connect$readPacket(ChannelHandlerContext ctx, Packet<?> packet) throws Exception {
        this.channelRead0(ctx, packet);
    }

    @Inject(method = "connect", at = @At("HEAD"), cancellable = true)
    private static void onConnect(InetSocketAddress address, EventLoopGroupHolder groupHolder, Connection connection,
                                  CallbackInfoReturnable<ChannelFuture> callbackInfoReturnable)
            throws ExecutionException, InterruptedException {
        if (address instanceof QuicSocketAddress quicAddress) {
            if (((ServerAddressProperties) (Object) quicAddress.getOrigin()).quic_connect$getUseQuic()) {
                callbackInfoReturnable.setReturnValue(QuicConnection.connect(address, groupHolder, connection));
            }
        }
    }

    @Redirect(method = "channelActive", at = @At(value = "FIELD", target = "Lnet/minecraft/network/Connection;address:Ljava/net/SocketAddress;", opcode = Opcodes.PUTFIELD))
    private void dropQuicAddresses(Connection instance, SocketAddress value) {
        if (!(value instanceof QuicStreamAddress)) {
            address = value;
        }
    }

    @Redirect(method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V", at = @At(value = "FIELD", target = "Lnet/minecraft/network/Connection;channel:Lio/netty/channel/Channel;", opcode = Opcodes.GETFIELD))
    private Channel getChannelForDisconnect(Connection self) {
        if (channel instanceof QuicStreamChannel quic) {
            return quic.parent();
        } else {
            return channel;
        }
    }

    @Inject(method = "setEncryptionKey", at = @At("HEAD"), cancellable = true)
    private void onSetEncryptionKey(CallbackInfo callbackInfo) {
        if (encrypted) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, boolean bl, CallbackInfo ci) {
        if (this.getSending() == PacketFlow.CLIENTBOUND) {
            if (CLIENTBOUND_DATAGRAM_PACKETS.contains(packet.getClass())) {
                quic_connect$sendDatagramPacket(packet);
                ci.cancel();
            }
        } else if (SERVERBOUND_DATAGRAM_PACKETS.contains(packet.getClass())) {
            quic_connect$sendDatagramPacket(packet);
            ci.cancel();
        }
    }

    @Inject(method = "setupCompression", at = @At("HEAD"))
    private void onSetupCompression(int threshold, boolean validate, CallbackInfo ci) {
        Channel parent = this.channel.parent();
        if (threshold >= 0) {
            if (parent.pipeline().get("decompress") instanceof CompressionDecoder compressionDecoder) {
                compressionDecoder.setThreshold(threshold, validate);
            } else {
                parent.pipeline().addAfter("splitter", "decompress", new CompressionDecoder(threshold, validate));
            }

            if (parent.pipeline().get("compress") instanceof CompressionEncoder compressionEncoder) {
                compressionEncoder.setThreshold(threshold);
            } else {
                parent.pipeline().addAfter("prepender", "compress", new CompressionEncoder(threshold));
            }
        } else {
            if (parent.pipeline().get("decompress") instanceof CompressionDecoder) {
                parent.pipeline().remove("decompress");
            }

            if (parent.pipeline().get("compress") instanceof CompressionEncoder) {
                parent.pipeline().remove("compress");
            }
        }
    }

    @Inject(method = "setupInboundProtocol", at = @At(value = "HEAD")/*@At(value = "INVOKE", target = "Lio/netty/channel/Channel;writeAndFlush(Ljava/lang/Object;)Lio/netty/channel/ChannelFuture;")*/)
    private <T extends PacketListener> void onSetupInboundProtocol(ProtocolInfo<T> protocolInfo, T packetListener, CallbackInfo callbackInfo) {
        if (protocolInfo.id() != ConnectionProtocol.PLAY)
            return;

        PacketDecoder<T> decoder = new PacketDecoder<>(protocolInfo);
        if (this.getReceiving() == PacketFlow.CLIENTBOUND) {
            this.channel.parent().pipeline().replace("inbound_config", "decoder", decoder);
            this.channel.parent().pipeline().addLast("packet_handler", new DatagramConnectionWrapper((Connection) (Object) this));
        } else {
            this.channel.parent().pipeline().replace("decoder", "decoder", decoder);
        }
    }

    @Inject(method = "setupOutboundProtocol", at = @At(value = "HEAD") /*@At(value = "INVOKE", target = "Lio/netty/channel/Channel;writeAndFlush(Ljava/lang/Object;)Lio/netty/channel/ChannelFuture;")*/)
    private <T extends PacketListener> void onSetupOutboundProtocol(ProtocolInfo<?> protocolInfo, CallbackInfo callbackInfo) {
        if (protocolInfo.id() != ConnectionProtocol.PLAY)
            return;

        PacketEncoder<?> encoder = new PacketEncoder<>(protocolInfo);
        if (this.getReceiving() == PacketFlow.CLIENTBOUND) {
            this.channel.parent().pipeline().replace("encoder", "encoder", encoder);
        } else {
            this.channel.parent().pipeline().replace("outbound_config", "encoder", encoder);
            this.channel.parent().pipeline().addLast("packet_handler", new DatagramConnectionWrapper((Connection) (Object) this));
        }

        LOGGER.info("{} INTO PROTOCOL {}", this.getReceiving(), protocolInfo.id());
        LOGGER.info("REWRITING OUTBOUND MAIN {}", this.channel.pipeline().names().toString());
        LOGGER.info("REWRITING OUTBOUND PARENT {}", this.channel.parent().pipeline().names().toString());
    }
}
