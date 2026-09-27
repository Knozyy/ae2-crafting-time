package com.ctux.ae2craftingtime.testdriver;

import com.ctux.ae2craftingtime.core.PlanRecurrenceChunk;
import com.ctux.ae2craftingtime.core.ServerConfig;
import com.ctux.ae2craftingtime.core.ServerOptionsWire;
import com.ctux.ae2craftingtime.core.StatsChatAction;
import com.ctux.ae2craftingtime.mc1201.StatsNetwork;
import com.ctux.ae2craftingtime.mc1201.net.*;
import java.util.BitSet;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/** Exercises every guarded outbound overload only in an unsupported-peer cell. */
public final class ConnectionProbe {
    private static final String OUTPUT = "minecraft:stone";

    public static void client() {
        if (!Boolean.getBoolean("ae2craftingtime.test.observeConnection")
                || StatsNetwork.canSend()) return;
        StatsNetwork.sendToServer(new StatsRequestC2S(List.of(OUTPUT)));
        StatsNetwork.sendToServer(new StatsChatC2S(OUTPUT, 0, StatsChatAction.SHOW));
        StatsNetwork.sendToServer(new ProviderLocateC2S(OUTPUT));
        StatsNetwork.sendToServer(new CpuTtcRequestC2S(new CpuTtcPacketCodec.Request(0, 0, 0, List.of(1))));
        StatsNetwork.sendToServer(new WarningPreferenceC2S(false));
        StatsNetwork.sendToServer(new ServerOptionsUpdateC2S(options()));
    }

    public static void server(ServerPlayer player) {
        if (!Boolean.getBoolean("ae2craftingtime.test.observeConnection")
                || !Boolean.getBoolean("ae2craftingtime.test.expectUnsupportedPeer")) return;
        StatsNetwork.sendTo(player, new StatsSnapshotS2C(List.of()));
        StatsNetwork.sendTo(player, new CpuTtcSnapshotS2C(new CpuTtcPacketCodec.Snapshot(0, 0, List.of())));
        StatsNetwork.sendTo(player, new ProviderHighlightS2C("minecraft:overworld", List.of(), OUTPUT, 0, false));
        StatsNetwork.sendTo(player, PlanRecurrenceS2C.of(1, 1, 1, 0, 1, new BitSet()));
        StatsNetwork.sendTo(player, new PlanStoredVariantsS2C(1, new PlanRecurrenceChunk(1, 1, 1, 0, 1, new byte[32])));
        StatsNetwork.sendTo(player, new ServerOptionsSnapshotS2C(options()));
    }

    private static byte[] options() {
        return ServerOptionsWire.encode(new ServerOptionsWire.Snapshot(0, false, new ServerConfig()));
    }

    private ConnectionProbe() { }
}
