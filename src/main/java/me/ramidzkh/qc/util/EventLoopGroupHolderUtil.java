package me.ramidzkh.qc.util;

import io.netty.channel.Channel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;

public class EventLoopGroupHolderUtil {
    public static Class<? extends Channel> datagramChannel(boolean bl) {
        if (bl) {
            if (KQueue.isAvailable()) {
                return KQueueDatagramChannel.class;
            }

            if (Epoll.isAvailable()) {
                return EpollDatagramChannel.class;
            }
        }
        return NioDatagramChannel.class;
    }
}
