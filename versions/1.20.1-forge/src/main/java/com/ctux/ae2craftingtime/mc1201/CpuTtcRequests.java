package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.mc1201.net.CpuTtcPacketCodec;
import com.ctux.ae2craftingtime.mc1201.net.CpuTtcRequestC2S;

public final class CpuTtcRequests {
    public static boolean enabled() { return StatsNetwork.canSendCpuTtc(); }
    public static void send(CpuTtcPacketCodec.Request request) {
        StatsNetwork.sendToServer(new CpuTtcRequestC2S(request));
    }
    private CpuTtcRequests() { }
}
