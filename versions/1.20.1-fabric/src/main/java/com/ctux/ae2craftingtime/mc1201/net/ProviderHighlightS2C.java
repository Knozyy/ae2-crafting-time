package com.ctux.ae2craftingtime.mc1201.net;

import appeng.api.stacks.AEKey;

import com.ctux.ae2craftingtime.mc1201.ProviderHighlightClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

public record ProviderHighlightS2C(String networkId, String dimensionId, List<BlockPos> positions, String outputId,
        int durationSeconds, boolean plateOnly, AEKey displayKey, boolean chatLocate) {
    public ProviderHighlightS2C(String dimensionId, List<BlockPos> positions, String outputId, int durationSeconds,
            boolean plateOnly) {
        this("", dimensionId, positions, outputId, durationSeconds, plateOnly, null, false);
    }

    public ProviderHighlightS2C(String networkId, String dimensionId, List<BlockPos> positions, String outputId,
            int durationSeconds, boolean plateOnly) {
        this(networkId, dimensionId, positions, outputId, durationSeconds, plateOnly, null, false);
    }

    public ProviderHighlightS2C(String networkId, String dimensionId, List<BlockPos> positions, String outputId,
            int durationSeconds, boolean plateOnly, AEKey displayKey) {
        this(networkId, dimensionId, positions, outputId, durationSeconds, plateOnly, displayKey, false);
    }

    public static void encode(ProviderHighlightS2C packet, FriendlyByteBuf buffer) {
        ProviderHighlightCodec.write(buffer, new ProviderHighlightCodec.Highlight(packet.networkId,
                packet.dimensionId, packet.positions, packet.outputId, packet.durationSeconds, packet.plateOnly,
                packet.displayKey, packet.chatLocate));
    }

    public static ProviderHighlightS2C decode(FriendlyByteBuf buffer) {
        var highlight = ProviderHighlightCodec.read(buffer);
        return new ProviderHighlightS2C(highlight.networkId(), highlight.dimensionId(), highlight.positions(),
                highlight.outputId(), highlight.durationSeconds(), highlight.plateOnly(), highlight.displayKey(),
                highlight.chatLocate());
    }

    public void handle() {
        if (durationSeconds <= 0 || positions == null || positions.isEmpty()) {
            if (dimensionId != null && !dimensionId.isBlank())
                ProviderHighlightClient.clearFor(networkId, dimensionId, outputId);
            else ProviderHighlightClient.clearFor(networkId, outputId);
        } else if (plateOnly) {
            ProviderHighlightClient.showPlate(networkId, dimensionId, positions, outputId, displayKey);
        } else {
            ProviderHighlightClient.show(networkId, dimensionId, positions, durationSeconds, outputId, chatLocate);
        }
    }
}
