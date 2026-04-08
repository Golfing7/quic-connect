package me.ramidzkh.qc.mixin.server;

import com.google.common.collect.Maps;
import io.netty.channel.ChannelFutureListener;
import me.ramidzkh.qc.shared.ConnectionSpoofer;
import me.ramidzkh.qc.util.Predicates;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.BiPredicate;

@Mixin(Connection.class)
public abstract class ServerConnectionMixin implements ConnectionSpoofer {
    @Shadow
    private volatile @Nullable PacketListener packetListener;
    @Unique
    private static final Map<Class<? extends Packet<?>>, BiPredicate<Player, Packet<?>>> CLIENTBOUND_PACKETS = Maps.newHashMap();
    @Unique
    @SuppressWarnings("unchecked")
    private static <T extends Packet<?>> void registerClientboundPacket(Class<T> packet, BiPredicate<Player, T> predicate) {
        CLIENTBOUND_PACKETS.put(packet, (BiPredicate<Player, Packet<?>>) predicate);
    }

    static {
        // Hurt animations are not important.
        registerClientboundPacket(ClientboundHurtAnimationPacket.class, Predicates.alwaysTrue());
        // Particles are not important.
        registerClientboundPacket(ClientboundLevelParticlesPacket.class, Predicates.alwaysTrue());

        // As of 1.21.11, these packets serve no purpose!
        registerClientboundPacket(ClientboundPlayerCombatEndPacket.class, Predicates.alwaysTrue());
        registerClientboundPacket(ClientboundPlayerCombatEnterPacket.class, Predicates.alwaysTrue());

        // Sound packets are very unnecessary
        registerClientboundPacket(ClientboundSoundPacket.class, Predicates.alwaysTrue());
        registerClientboundPacket(ClientboundSoundEntityPacket.class, Predicates.alwaysTrue());
        registerClientboundPacket(ClientboundSoundPacket.class, Predicates.alwaysTrue());
        registerClientboundPacket(ClientboundStopSoundPacket.class, Predicates.alwaysTrue());

        // Some movement/entity data packets are unnecessary.
        registerClientboundPacket(ClientboundMoveEntityPacket.class, (pl, packet) -> {
            return pl != packet.getEntity(pl.level());
        });
        // Minecart movement doesn't need to be tracked.
        registerClientboundPacket(ClientboundMoveMinecartPacket.class, Predicates.alwaysTrue());
        registerClientboundPacket(ClientboundAnimatePacket.class, (pl, packet) -> {
            // All animations are superfluous, except for waking up.
            return packet.getAction() != ClientboundAnimatePacket.WAKE_UP;
        });
        // Setting entity motion isn't necessary if you aren't the target.
        registerClientboundPacket(ClientboundSetEntityMotionPacket.class, (pl, packet) -> {
            return pl.getId() != packet.getId();
        });
    }

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, boolean bl, CallbackInfo ci) {
        if (CLIENTBOUND_PACKETS.containsKey(packet.getClass())) {
            var predicate = CLIENTBOUND_PACKETS.get(packet.getClass());
            if (packetListener instanceof ServerGamePacketListenerImpl gamePacketListener && predicate.test(gamePacketListener.getPlayer(), packet)) {
                quic_connect$sendDatagramPacket(packet);
                ci.cancel();
            }
        }
    }
}
