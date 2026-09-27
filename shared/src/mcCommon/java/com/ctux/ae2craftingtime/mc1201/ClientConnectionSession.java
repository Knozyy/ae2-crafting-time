package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.ConnectionEpoch;

/** Client state belongs to one negotiated play connection. */
public final class ClientConnectionSession {
    private static final ConnectionEpoch EPOCH = new ConnectionEpoch();

    public static long capture() { return EPOCH.current(); }
    public static boolean current(long epoch) { return EPOCH.isCurrent(epoch); }

    public static Runnable guard(net.minecraft.network.Connection connection, Runnable work) {
        return EPOCH.guard(() -> connection.getPacketListener()
                == net.minecraft.client.Minecraft.getInstance().getConnection(), work);
    }

    public static void reset() {
        EPOCH.advance();
        open();
    }

    public static void open() {
        // New-connection packets can already be queued before the login event.
        // Only disconnect advances the epoch; the receiving listener also gates work.
        ClientServerOptions.clear();
        ClientStats.clear();
        ClientStatsRequests.clear();
        CpuTtcClient.clear();
        ProviderHighlightClient.onSessionEnd();
    }

    private ClientConnectionSession() { }
}
