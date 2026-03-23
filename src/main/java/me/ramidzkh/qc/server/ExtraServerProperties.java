package me.ramidzkh.qc.server;

public interface ExtraServerProperties {

    int quic_connect$getQuicPort();

    boolean quic_connect$isForceClientAuthentication();
}
