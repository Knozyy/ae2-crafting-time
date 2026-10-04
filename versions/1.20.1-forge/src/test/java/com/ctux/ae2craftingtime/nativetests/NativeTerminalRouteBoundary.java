package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.testdriver.DriverOptions;
import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;

/** The actual ME terminal stays intact; only the independent flow expects a wireless screen. */
final class NativeTerminalRouteBoundary {
    private final ArrayList<Long> frames = new ArrayList<>();
    private Object probe;

    boolean tick(Minecraft minecraft, Object original, Object marker, Map<?, ?> ordinaryChecks, Path output) throws Exception {
        var source = UiObservationStore.latest();
        assertInstanceOf(appeng.client.gui.me.common.MEStorageScreen.class, minecraft.screen);
        assertNotEquals("com.lhy.wcwt.client.WirelessComprehensiveWorkTerminalScreen", minecraft.screen.getClass().getName());
        if (source == null || source.gui() == null || frames.contains(source.frame())) return false;
        var type = original.getClass();
        if (probe == null) {
            var constructor = type.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
            constructor.setAccessible(true);
            // Preserve stored-variant observation; change only this independent route expectation.
            probe = constructor.newInstance("stored-variant-plan", DriverOptions.load().world(), output, false);
            set(type, "fixture", probe, field(type, "fixture", original));
            set(type, "phase", probe, field(type, "phase", original));
            set(type, "leaf", probe, "recurrent-plan");
            set(type, "recurrenceAddonRoute", probe, true);
        }
        var screen = minecraft.screen;
        var menu = minecraft.player.containerMenu;
        var before = Map.copyOf(ordinaryChecks);
        var config = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
        var saved = Files.exists(config) ? Files.readAllBytes(config) : null;
        var method = type.getDeclaredMethod("tick", Minecraft.class, marker.getClass(), Map.class,
                java.util.function.Consumer.class, java.util.function.BiConsumer.class);
        method.setAccessible(true);
        var checks = new LinkedHashMap<String, Boolean>();
        assertEquals(false, method.invoke(probe, minecraft, marker, checks,
                (java.util.function.Consumer<String>) name -> fail("Wrong terminal captured success"),
                (java.util.function.BiConsumer<Integer, Integer>) (x, y) -> fail("Wrong terminal moved the mouse")));
        assertEquals("TERMINAL", field(type, "phase", probe).toString());
        assertFalse((boolean) field(type, "recurrenceWirelessOpened", probe));
        assertNull(field(type, "operation", probe));
        assertTrue(checks.isEmpty());
        assertEquals(before, ordinaryChecks);
        assertSame(screen, minecraft.screen);
        assertSame(menu, minecraft.player.containerMenu);
        assertSame(source, UiObservationStore.latest());
        if (saved == null) assertFalse(Files.exists(config));
        else assertArrayEquals(saved, Files.readAllBytes(config));
        frames.add(source.frame());
        if (frames.size() < 8) return false;
        Files.writeString(output.resolve("wireless-route-wrong-terminal.json"), new com.google.gson.Gson().toJson(Map.of(
                "frames", frames, "screen", screen.getClass().getName(), "menu", menu.getClass().getName(),
                "scope", "independent driver expects wireless route; actual native ME terminal unchanged",
                "ordinaryChecksUnchanged", true, "savedConfigUnchanged", true, "captureSnapshot", source)));
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            image.writeToFile(output.resolve("wireless-route-wrong-terminal.png"));
        }
        return true;
    }
}
