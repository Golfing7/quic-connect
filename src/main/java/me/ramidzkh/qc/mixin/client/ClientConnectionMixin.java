package me.ramidzkh.qc.mixin.client;

import com.google.common.collect.Maps;
import io.netty.channel.ChannelFutureListener;
import me.ramidzkh.qc.shared.ConnectionSpoofer;
import me.ramidzkh.qc.util.Predicates;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.BiPredicate;

@Mixin(Connection.class)
public abstract class ClientConnectionMixin implements ConnectionSpoofer {
    @Unique
    private static final Map<Class<? extends Packet<?>>, BiPredicate<Player, Packet<?>>> SERVERBOUND_PACKETS = Maps.newHashMap();

    @Unique
    @SuppressWarnings("unchecked")
    private static <T extends Packet<?>> void registerServerboundPacket(Class<T> packet, BiPredicate<Player, T> predicate) {
        SERVERBOUND_PACKETS.put(packet, (BiPredicate<Player, Packet<?>>) predicate);
    }

    static {
        // This is a very good example of a packet that could be placed into a datagram channel.
        // Every client will send this packet to the server when they are done ticking.
        registerServerboundPacket(ServerboundClientTickEndPacket.class, Predicates.alwaysTrue());
        // Players can control a boat to move it left or right. These packets are fine to miss!
        registerServerboundPacket(ServerboundPaddleBoatPacket.class, Predicates.alwaysTrue());
        // Player input does not need to be reliable.
        registerServerboundPacket(ServerboundPlayerInputPacket.class, Predicates.alwaysTrue());
        // A player swinging their arm (uselessly) does not need to be reliable.
        registerServerboundPacket(ServerboundSwingPacket.class, Predicates.alwaysTrue());
        // Command suggestion request packets are unimportant.
        registerServerboundPacket(ServerboundCommandSuggestionPacket.class, Predicates.alwaysTrue());
        // Pick block and entity packets can be re-sent by the player without too much fuss.
        registerServerboundPacket(ServerboundPickItemFromBlockPacket.class, Predicates.alwaysTrue());
        registerServerboundPacket(ServerboundPickItemFromEntityPacket.class, Predicates.alwaysTrue());
    }

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, boolean bl, CallbackInfo ci) {
        if (SERVERBOUND_PACKETS.containsKey(packet.getClass())) {
            var predicate = SERVERBOUND_PACKETS.get(packet.getClass());
            if (predicate.test(Minecraft.getInstance().player, packet)) {
                quic_connect$sendDatagramPacket(packet);
                ci.cancel();
            }
        }
    }
}
