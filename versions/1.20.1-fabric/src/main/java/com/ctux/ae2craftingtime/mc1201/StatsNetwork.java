package com.ctux.ae2craftingtime.mc1201;

import com.ctux.ae2craftingtime.mc1201.net.ProviderHighlightS2C;
import com.ctux.ae2craftingtime.mc1201.net.CpuTtcRequestC2S;
import com.ctux.ae2craftingtime.mc1201.net.CpuTtcSnapshotS2C;
import com.ctux.ae2craftingtime.mc1201.net.ProviderLocateC2S;
import com.ctux.ae2craftingtime.mc1201.net.StatsChatC2S;
import com.ctux.ae2craftingtime.mc1201.net.StatsRequestC2S;
import com.ctux.ae2craftingtime.mc1201.net.StatsSnapshotS2C;
import com.ctux.ae2craftingtime.mc1201.net.PlanRecurrenceS2C;
import com.ctux.ae2craftingtime.mc1201.net.PlanStoredVariantsS2C;
import com.ctux.ae2craftingtime.mc1201.net.WarningPreferenceC2S;
import com.ctux.ae2craftingtime.mc1201.net.ServerOptionsSnapshotS2C;
import com.ctux.ae2craftingtime.mc1201.net.ServerOptionsUpdateC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class StatsNetwork {
    private static final ResourceLocation REQUEST_ID = new ResourceLocation(Ae2CraftingTime.MOD_ID,
            "stats_request_v2");
    private static final ResourceLocation SNAPSHOT_ID = new ResourceLocation(Ae2CraftingTime.MOD_ID,
            "stats_snapshot_v10");
    private static final ResourceLocation CHAT_ID = new ResourceLocation(Ae2CraftingTime.MOD_ID, "stats_chat_v2");
    private static final ResourceLocation HIGHLIGHT_ID = new ResourceLocation(Ae2CraftingTime.MOD_ID,
            "provider_highlight_v5");
    private static final ResourceLocation LOCATE_ID = new ResourceLocation(Ae2CraftingTime.MOD_ID,
            "provider_locate_v1");
    private static final ResourceLocation CPU_TTC_REQUEST_ID = new ResourceLocation(Ae2CraftingTime.MOD_ID,
            "cpu_ttc_request_v1");
    private static final ResourceLocation CPU_TTC_SNAPSHOT_ID = new ResourceLocation(Ae2CraftingTime.MOD_ID,
            "cpu_ttc_snapshot_v1");
    private static final ResourceLocation PLAN_RECURRENCE_ID = new ResourceLocation(Ae2CraftingTime.MOD_ID, "plan_recurrence_v1");
    private static final ResourceLocation PLAN_STORED_VARIANTS_ID = new ResourceLocation(Ae2CraftingTime.MOD_ID, "plan_stored_variants_v1");
    private static final ResourceLocation WARNING_PREFERENCE_ID = new ResourceLocation(Ae2CraftingTime.MOD_ID, "warning_preference_v1");
    private static final ResourceLocation SERVER_OPTIONS_SNAPSHOT_ID = new ResourceLocation(Ae2CraftingTime.MOD_ID, "server_options_snapshot_v1");
    private static final ResourceLocation SERVER_OPTIONS_UPDATE_ID = new ResourceLocation(Ae2CraftingTime.MOD_ID, "server_options_update_v1");

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST_ID,
                (server, player, handler, buffer, responseSender) -> {
                    var packet = StatsRequestC2S.decode(buffer);
                    server.execute(() -> packet.handle(player));
                });
        ServerPlayNetworking.registerGlobalReceiver(CHAT_ID,
                (server, player, handler, buffer, responseSender) -> {
                    var packet = StatsChatC2S.decode(buffer);
                    server.execute(() -> packet.handle(player));
                });
        ServerPlayNetworking.registerGlobalReceiver(LOCATE_ID,
                (server, player, handler, buffer, responseSender) -> {
                    var packet = ProviderLocateC2S.decode(buffer);
                    server.execute(() -> packet.handle(player));
                });
        ServerPlayNetworking.registerGlobalReceiver(CPU_TTC_REQUEST_ID,
                (server, player, handler, buffer, responseSender) -> {
                    var packet = CpuTtcRequestC2S.decode(buffer);
                    server.execute(() -> packet.handle(player));
                });
        ServerPlayNetworking.registerGlobalReceiver(WARNING_PREFERENCE_ID,
                (server, player, handler, buffer, responseSender) -> {
                    var packet = WarningPreferenceC2S.decode(buffer);
                    server.execute(() -> packet.handle(player));
                });
        ServerPlayNetworking.registerGlobalReceiver(SERVER_OPTIONS_UPDATE_ID,
                (server, player, handler, buffer, responseSender) -> {
                    var packet = ServerOptionsUpdateC2S.decode(buffer);
                    server.execute(() -> packet.handle(player));
                });
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(SNAPSHOT_ID,
                (client, handler, buffer, responseSender) -> {
                    var packet = StatsSnapshotS2C.decode(buffer);
                    client.execute(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(handler.getConnection(), packet::handle));
                });
        ClientPlayNetworking.registerGlobalReceiver(HIGHLIGHT_ID,
                (client, handler, buffer, responseSender) -> {
                    var packet = ProviderHighlightS2C.decode(buffer);
                    client.execute(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(handler.getConnection(), packet::handle));
                });
        ClientPlayNetworking.registerGlobalReceiver(CPU_TTC_SNAPSHOT_ID,
                (client, handler, buffer, responseSender) -> {
                    var packet = CpuTtcSnapshotS2C.decode(buffer);
                    client.execute(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(handler.getConnection(), packet::handle));
                });
        ClientPlayNetworking.registerGlobalReceiver(PLAN_RECURRENCE_ID, (client, handler, buffer, sender) -> { var packet=PlanRecurrenceS2C.decode(buffer); client.execute(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(handler.getConnection(), packet::handle)); });
        ClientPlayNetworking.registerGlobalReceiver(PLAN_STORED_VARIANTS_ID, (client, handler, buffer, sender) -> { var packet=PlanStoredVariantsS2C.decode(buffer); client.execute(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(handler.getConnection(), packet::handle)); });
        ClientPlayNetworking.registerGlobalReceiver(SERVER_OPTIONS_SNAPSHOT_ID,
                (client, handler, buffer, sender) -> {
                    var packet = ServerOptionsSnapshotS2C.decode(buffer);
                    client.execute(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(handler.getConnection(), packet::handle));
                });
    }

    public static void sendToServer(StatsRequestC2S packet) {
        if (canSendChannel(REQUEST_ID)) ClientPlayNetworking.send(REQUEST_ID, encode(packet));
    }

    public static void sendToServer(StatsChatC2S packet) {
        if (canSendChannel(CHAT_ID)) ClientPlayNetworking.send(CHAT_ID, encode(packet));
    }

    public static void sendToServer(ProviderLocateC2S packet) {
        if (canSendChannel(LOCATE_ID)) ClientPlayNetworking.send(LOCATE_ID, encode(packet));
    }

    public static void sendToServer(WarningPreferenceC2S packet) {
        if (canSendChannel(WARNING_PREFERENCE_ID)) ClientPlayNetworking.send(WARNING_PREFERENCE_ID, encode(packet));
    }
    public static void sendToServer(ServerOptionsUpdateC2S packet) {
        if (canSendChannel(SERVER_OPTIONS_UPDATE_ID)) ClientPlayNetworking.send(SERVER_OPTIONS_UPDATE_ID, encode(packet));
    }

    public static boolean canSendCpuTtc() {
        return canSendChannel(CPU_TTC_REQUEST_ID);
    }

    public static boolean canSend() { return canSendChannel(REQUEST_ID); }

    private static boolean canSendChannel(ResourceLocation id) {
        // Fabric can briefly retain the previous server's advertised channels
        // while switching servers. The guarded options snapshot proves that
        // this play connection is talking to Crafting Time.
        return canSendChannel(() -> ClientPlayNetworking.canSend(id));
    }

    static boolean canSendChannel(java.util.function.BooleanSupplier advertised) {
        return ClientServerOptions.snapshot() != null && advertised.getAsBoolean();
    }

    public static void sendToServer(CpuTtcRequestC2S packet) {
        if (canSendCpuTtc()) ClientPlayNetworking.send(CPU_TTC_REQUEST_ID, encode(packet));
    }

    public static void sendTo(ServerPlayer player, StatsSnapshotS2C packet) {
        if (ServerPlayNetworking.canSend(player, SNAPSHOT_ID)) ServerPlayNetworking.send(player, SNAPSHOT_ID, encode(packet));
    }

    public static void sendTo(ServerPlayer player, ProviderHighlightS2C packet) {
        if (ServerPlayNetworking.canSend(player, HIGHLIGHT_ID)) ServerPlayNetworking.send(player, HIGHLIGHT_ID, encode(packet));
    }

    public static void sendTo(ServerPlayer player, CpuTtcSnapshotS2C packet) {
        if (ServerPlayNetworking.canSend(player, CPU_TTC_SNAPSHOT_ID)) ServerPlayNetworking.send(player, CPU_TTC_SNAPSHOT_ID, encode(packet));
    }
    public static void sendTo(ServerPlayer player, ServerOptionsSnapshotS2C packet) {
        if (ServerPlayNetworking.canSend(player, SERVER_OPTIONS_SNAPSHOT_ID))
            ServerPlayNetworking.send(player, SERVER_OPTIONS_SNAPSHOT_ID, encode(packet));
    }
    public static void sendTo(ServerPlayer player, PlanRecurrenceS2C packet) { if(ServerPlayNetworking.canSend(player, PLAN_RECURRENCE_ID)) ServerPlayNetworking.send(player, PLAN_RECURRENCE_ID, encode(packet)); }
    public static void sendTo(ServerPlayer player, PlanStoredVariantsS2C packet) { if(ServerPlayNetworking.canSend(player, PLAN_STORED_VARIANTS_ID)) ServerPlayNetworking.send(player, PLAN_STORED_VARIANTS_ID, encode(packet)); }
    private static FriendlyByteBuf encode(PlanStoredVariantsS2C packet) { var buffer=PacketByteBufs.create(); PlanStoredVariantsS2C.encode(packet,buffer); return buffer; }
    private static FriendlyByteBuf encode(PlanRecurrenceS2C packet) { var buffer=PacketByteBufs.create(); PlanRecurrenceS2C.encode(packet,buffer); return buffer; }

    private static FriendlyByteBuf encode(StatsRequestC2S packet) {
        var buffer = PacketByteBufs.create();
        StatsRequestC2S.encode(packet, buffer);
        return buffer;
    }

    private static FriendlyByteBuf encode(StatsSnapshotS2C packet) {
        var buffer = PacketByteBufs.create();
        StatsSnapshotS2C.encode(packet, buffer);
        return buffer;
    }

    private static FriendlyByteBuf encode(StatsChatC2S packet) {
        var buffer = PacketByteBufs.create();
        StatsChatC2S.encode(packet, buffer);
        return buffer;
    }

    private static FriendlyByteBuf encode(ProviderHighlightS2C packet) {
        var buffer = PacketByteBufs.create();
        ProviderHighlightS2C.encode(packet, buffer);
        return buffer;
    }

    private static FriendlyByteBuf encode(ProviderLocateC2S packet) {
        var buffer = PacketByteBufs.create();
        ProviderLocateC2S.encode(packet, buffer);
        return buffer;
    }

    private static FriendlyByteBuf encode(WarningPreferenceC2S packet) {
        var buffer = PacketByteBufs.create();
        WarningPreferenceC2S.encode(packet, buffer);
        return buffer;
    }

    private static FriendlyByteBuf encode(ServerOptionsSnapshotS2C packet) {
        var buffer = PacketByteBufs.create();
        ServerOptionsSnapshotS2C.encode(packet, buffer);
        return buffer;
    }

    private static FriendlyByteBuf encode(ServerOptionsUpdateC2S packet) {
        var buffer = PacketByteBufs.create();
        ServerOptionsUpdateC2S.encode(packet, buffer);
        return buffer;
    }

    private static FriendlyByteBuf encode(CpuTtcRequestC2S packet) {
        var buffer = PacketByteBufs.create();
        CpuTtcRequestC2S.encode(packet, buffer);
        return buffer;
    }

    private static FriendlyByteBuf encode(CpuTtcSnapshotS2C packet) {
        var buffer = PacketByteBufs.create();
        CpuTtcSnapshotS2C.encode(packet, buffer);
        return buffer;
    }

    public static boolean canSend(ServerPlayer player) { return ServerPlayNetworking.canSend(player, SNAPSHOT_ID); }

    private StatsNetwork() {
    }
}
