package me.ramidzkh.qc.util;

import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.player.Player;

import java.util.function.BiPredicate;

public class Predicates {
    public static <T extends Packet<?>> BiPredicate<Player, T> alwaysTrue() {
        return (pl, t) -> true;
    }
}
