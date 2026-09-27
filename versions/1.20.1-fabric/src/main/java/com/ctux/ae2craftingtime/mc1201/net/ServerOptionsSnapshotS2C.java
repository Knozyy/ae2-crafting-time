package com.ctux.ae2craftingtime.mc1201.net;

import com.ctux.ae2craftingtime.mc1201.ClientServerOptions;
import com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime;
import net.minecraft.network.FriendlyByteBuf;

public record ServerOptionsSnapshotS2C(byte[] bytes) {
    public static void encode(ServerOptionsSnapshotS2C packet, FriendlyByteBuf buffer) {
        buffer.writeByteArray(packet.bytes);
    }

    public static ServerOptionsSnapshotS2C decode(FriendlyByteBuf buffer) {
        return new ServerOptionsSnapshotS2C(buffer.readByteArray(64));
    }

    public void handle() {
        boolean firstSnapshot = ClientServerOptions.snapshot() == null;
        ClientServerOptions.receive(bytes);
        if (firstSnapshot && ClientServerOptions.snapshot() != null) {
            ClientOptionsRuntime.syncWarningPreference();
        }
    }
}
