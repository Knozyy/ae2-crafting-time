package com.ctux.ae2craftingtime.mc1201.net;

import com.ctux.ae2craftingtime.core.PlanRecurrenceChunk;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record PlanStoredVariantsS2C(long updateRevision, PlanRecurrenceChunk chunk) {
    public static void encode(PlanStoredVariantsS2C packet, FriendlyByteBuf buffer) {
        PlanStoredVariantsCodec.write(buffer, packet.updateRevision, packet.chunk);
    }
    public static PlanStoredVariantsS2C decode(FriendlyByteBuf buffer) {
        var decoded = PlanStoredVariantsCodec.read(buffer);
        return new PlanStoredVariantsS2C(decoded.updateRevision(), decoded.chunk());
    }
    public static void handle(PlanStoredVariantsS2C packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(ctx.getNetworkManager(), () -> com.ctux.ae2craftingtime.mc1201.PlanStoredVariantsClient.receive(
                packet.chunk, packet.updateRevision)));
        ctx.setPacketHandled(true);
    }
}
