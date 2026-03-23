package me.ramidzkh.qc.client;

public interface ServerAddressProperties {

    boolean quic_connect$getUseQuic();

    void quic_connect$setUseQuic(boolean quic);

    default void copy(ServerAddressProperties other) {
        quic_connect$setUseQuic(other.quic_connect$getUseQuic());
    }
}
