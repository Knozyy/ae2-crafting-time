package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.core.ProfileKey;
import com.ctux.ae2craftingtime.core.RequestCooldown;
import com.ctux.ae2craftingtime.mc1201.net.StatsRequestC2S;

import java.util.List;

public final class ClientStatsRequests {
    private static final RequestCooldown COOLDOWN = new RequestCooldown(1000);

    public static void request(ProfileKey key) {
        if (!COOLDOWN.markIfAllowed(key, System.currentTimeMillis())) {
            return;
        }

        // ponytail: one-key requests are simple; batch visible nodes if packet spam shows up.
        StatsNetwork.sendToServer(new StatsRequestC2S(List.of(key.outputId())));
    }

    public static void clear() {
        COOLDOWN.clear();
    }

    private ClientStatsRequests() {
    }
}
