package com.ctux.ae2craftingtime.mc1201.net;

import appeng.menu.me.crafting.CraftingCPUMenu;
import com.ctux.ae2craftingtime.core.CraftingSuspension;
import com.ctux.ae2craftingtime.mc1201.CraftingSuspensionMenuState;
import com.ctux.ae2craftingtime.mc1201.StatsRequestContext;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record CraftingSuspensionS2C(CraftingSuspension.Snapshot snapshot) {
    public static void encode(CraftingSuspensionS2C packet, FriendlyByteBuf buffer) {
        buffer.writeBytes(CraftingSuspension.encode(packet.snapshot));
    }

    public static CraftingSuspensionS2C decode(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() != CraftingSuspension.LENGTH)
            throw new IllegalArgumentException("Invalid suspension snapshot length");
        var bytes = new byte[CraftingSuspension.LENGTH];
        buffer.readBytes(bytes);
        return new CraftingSuspensionS2C(CraftingSuspension.decodeSnapshot(bytes));
    }

    public static void handle(CraftingSuspensionS2C packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(
                context.getNetworkManager(), () -> {
                    var player = Minecraft.getInstance().player;
                    if (player != null && player.containerMenu instanceof CraftingCPUMenu menu
                            && menu.containerId == packet.snapshot.containerId()
                            && StatsRequestContext.cpuContext(menu) == packet.snapshot.cpuContext())
                        ((CraftingSuspensionMenuState) menu).ae2craftingtime$acceptSuspension(packet.snapshot);
                }));
        context.setPacketHandled(true);
    }
}
