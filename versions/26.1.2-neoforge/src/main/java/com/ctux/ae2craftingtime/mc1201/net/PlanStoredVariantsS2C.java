package com.ctux.ae2craftingtime.mc1201.net;

import com.ctux.ae2craftingtime.core.PlanRecurrenceChunk;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlanStoredVariantsS2C(long updateRevision, PlanRecurrenceChunk chunk) implements CustomPacketPayload {
    public static final Type<PlanStoredVariantsS2C> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("ae2craftingtime", "plan_stored_variants"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlanStoredVariantsS2C> STREAM_CODEC =
            StreamCodec.ofMember(PlanStoredVariantsS2C::encode, PlanStoredVariantsS2C::decode);
    @Override public Type<PlanStoredVariantsS2C> type() { return TYPE; }
    public static void encode(PlanStoredVariantsS2C packet, FriendlyByteBuf buffer) {
        PlanStoredVariantsCodec.write(buffer, packet.updateRevision, packet.chunk);
    }
    public static PlanStoredVariantsS2C decode(FriendlyByteBuf buffer) {
        var decoded = PlanStoredVariantsCodec.read(buffer);
        return new PlanStoredVariantsS2C(decoded.updateRevision(), decoded.chunk());
    }
    public static void handle(PlanStoredVariantsS2C packet, IPayloadContext context) {
        context.enqueueWork(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(context.connection(), () -> com.ctux.ae2craftingtime.mc1201.PlanStoredVariantsClient.receive(
                packet.chunk, packet.updateRevision)));
    }
}
