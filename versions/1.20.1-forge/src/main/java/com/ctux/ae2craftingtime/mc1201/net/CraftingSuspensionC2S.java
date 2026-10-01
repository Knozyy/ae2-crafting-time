package com.ctux.ae2craftingtime.mc1201.net;

import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.menu.me.crafting.CraftingCPUMenu;
import com.ctux.ae2craftingtime.core.CraftingSuspension;
import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.mc1201.CraftingSuspensionAccess;
import com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime;
import com.ctux.ae2craftingtime.mc1201.StatsNetwork;
import com.ctux.ae2craftingtime.mc1201.StatsRequestContext;
import com.ctux.ae2craftingtime.mc1201.mixin.CraftingCPUMenuAccessor;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record CraftingSuspensionC2S(CraftingSuspension.Request request) {
    public static void encode(CraftingSuspensionC2S packet, FriendlyByteBuf buffer) {
        buffer.writeBytes(CraftingSuspension.encode(packet.request));
    }

    public static CraftingSuspensionC2S decode(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() != CraftingSuspension.LENGTH)
            throw new IllegalArgumentException("Invalid suspension request length");
        var bytes = new byte[CraftingSuspension.LENGTH];
        buffer.readBytes(bytes);
        return new CraftingSuspensionC2S(CraftingSuspension.decodeRequest(bytes));
    }

    public static void handle(CraftingSuspensionC2S packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null || !StatsNetwork.canSend(player)
                    || !ServerOptionsRuntime.enabled(OptionFeature.CRAFTING_SUSPENSION)
                    || !(player.containerMenu instanceof CraftingCPUMenu menu)
                    || menu.containerId != packet.request.containerId()
                    || StatsRequestContext.cpuContext(menu) != packet.request.cpuContext()
                    || !menu.stillValid(player)) return;
            var cpu = ((CraftingCPUMenuAccessor) menu).ae2craftingtime$getCpu();
            if (cpu == null || cpu.getClass() != CraftingCPUCluster.class
                    || cpu.craftingLogic.getClass() != CraftingCpuLogic.class) return;
            var logic = (CraftingSuspensionAccess) cpu.craftingLogic;
            if (packet.request.jobId().equals(logic.ae2craftingtime$jobId()))
                logic.ae2craftingtime$setSuspended(packet.request.desired());
        });
        context.setPacketHandled(true);
    }
}
