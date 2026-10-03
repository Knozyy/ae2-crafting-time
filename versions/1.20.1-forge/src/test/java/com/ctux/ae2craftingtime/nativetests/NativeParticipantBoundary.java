package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import net.minecraft.client.Minecraft;

/** Invalid rendezvous data is rejected against the actual connected native player. */
final class NativeParticipantBoundary {
    static void verify(Minecraft minecraft, Class<?> type, Object marker, Map<?, ?> checks, Path output) throws Exception {
        var previous = new HashMap<String, String>();
        for (var name : new String[]{"role", "control", "campaign"}) {
            var key = "ae2craftingtime.test." + name;
            previous.put(key, System.getProperty(key));
        }
        var control = output.resolve("invalid-participants");
        var screen = minecraft.screen;
        var player = minecraft.player;
        var otherPlayer = new java.util.UUID(0, 0);
        assertNotEquals(otherPlayer, player.getUUID());
        var menu = player.containerMenu;
        var before = Map.copyOf(checks);
        var config = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
        var saved = Files.readAllBytes(config);
        var constructor = type.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
        constructor.setAccessible(true);
        var tick = type.getDeclaredMethod("tick", Minecraft.class, marker.getClass(), Map.class,
                java.util.function.Consumer.class, java.util.function.BiConsumer.class);
        tick.setAccessible(true);
        int assertions = 0;
        try {
            System.setProperty("ae2craftingtime.test.control", control.toString());
            System.setProperty("ae2craftingtime.test.campaign", "native-participant-boundary");
            for (var role : new String[]{"alpha", "beta"}) {
                System.setProperty("ae2craftingtime.test.role", role);
                var directory = Files.createDirectories(control.resolve(role));
                for (int invalid = 0; invalid < 3; invalid++) {
                    var state = new Properties();
                    state.setProperty("ready", "true");
                    state.setProperty("epoch", "native-participant-boundary");
                    state.setProperty("player", invalid == 0 ? otherPlayer.toString() : player.getUUID().toString());
                    state.setProperty("turn", invalid == 2 ? "" : invalid == 1 ? (role.equals("alpha") ? "beta" : "alpha") : role);
                    try (var stream = Files.newOutputStream(directory.resolve("state.properties"))) { state.store(stream, null); }
                    var flow = constructor.newInstance("recurrent-plan", "native-participant-boundary", output, true);
                    assertEquals(false, tick.invoke(flow, minecraft, marker, checks,
                            (java.util.function.Consumer<String>) name -> fail("Wrong participant captured success: " + name),
                            (java.util.function.BiConsumer<Integer, Integer>) (x, y) -> fail("Wrong participant moved the mouse")));
                    assertEquals("PREPARE", field(type, "phase", flow).toString());
                    assertNull(field(type, "operation", flow));
                    assertFalse(Files.exists(directory.resolve("command.properties")));
                    assertEquals(before, checks);
                    assertSame(screen, minecraft.screen);
                    assertSame(player, minecraft.player);
                    assertSame(menu, player.containerMenu);
                    assertArrayEquals(saved, Files.readAllBytes(config));
                    assertions++;
                }
            }
            assertEquals(6, assertions);
            Files.writeString(output.resolve("participant-boundary.json"),
                    "{\"cases\":6,\"nativePlayer\":\"" + player.getUUID()
                            + "\",\"scope\":\"invalid rendezvous inputs; not a two-client server scenario\"}");
        } finally {
            previous.forEach((key, value) -> {
                if (value == null) System.clearProperty(key); else System.setProperty(key, value);
            });
        }
    }
}
