package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** Guard inputs use initialized native controls; invalid labels never reach a draw. */
final class NativeOptionsWidgetBoundary {
    static void verify(Minecraft minecraft, Class<?> type, Object scenario, Path output, boolean on) throws Exception {
        var badge = button(minecraft, "config.ae2craftingtime.badgeBackground");
        var shadow = button(minecraft, "config.ae2craftingtime.textShadow");
        assertEquals(on, badge.getMessage().getString().endsWith(I18n.get("options.on")));
        var cases = new ArrayList<String>();
        if (on) {
            for (var input : Map.of(2, "Native badge toggle was not Off after relaunch",
                    10, "Cancel did not discard Appearance reset", 14, "Cancel did not discard Reset all").entrySet()) {
                check(minecraft, type, scenario, output, input.getKey(), input.getValue());
                cases.add("rejected-on-step-" + input.getKey());
            }
            for (int step : new int[]{4, 8, 12, 16, 19, 29}) {
                check(minecraft, type, scenario, output, step, null);
                cases.add("pending-native-options-step-" + step);
            }
        } else {
            for (int step : new int[]{3, 15}) {
                check(minecraft, type, scenario, output, step, null);
                cases.add("pending-off-step-" + step);
            }
            check(minecraft, type, scenario, output, 18, "Saved badge option was not On after Done");
            cases.add("rejected-off-after-done");
            var original = badge.getMessage();
            try {
                badge.setMessage(Component.literal("native test: badge control unavailable"));
                check(minecraft, type, scenario, output, 2, null);
                cases.add("missing-badge-control-pending");
            } finally { badge.setMessage(original); }
            assertSame(original, badge.getMessage());
            set(type, "badgeResetBadgeEdited", scenario, false);
            original = shadow.getMessage();
            try {
                shadow.setMessage(Component.literal("native test: shadow control unavailable"));
                check(minecraft, type, scenario, output, 6, "Text shadow option is missing");
                cases.add("missing-shadow-control-rejected");
            } finally { shadow.setMessage(original); }
            assertSame(original, shadow.getMessage());
        }
        Files.writeString(output.resolve(on ? "native-widget-on-inputs.json" : "native-widget-off-inputs.json"),
                new com.google.gson.Gson().toJson(Map.of("cases", cases,
                    "scope", "isolated driver expectations on actual native Options controls; no process relaunch claim",
                    "screen", minecraft.screen.getClass().getName(),
                    "nativeRenderCallback", field(com.ctux.ae2craftingtime.testdriver.TestDriverRuntime.class,
                        "renderedFrames", null),
                    "labels", minecraft.screen.children().stream().filter(Button.class::isInstance)
                        .map(Button.class::cast).map(b -> b.getMessage().getString()).toList(),
                    "savedConfigUnchanged", true, "nativeLabelsRestoredBeforeDraw", true)));
    }

    static void verifyColors(Minecraft minecraft, Class<?> type, Object scenario, Path output, boolean on) throws Exception {
        var colors = button(minecraft, "config.ae2craftingtime.ttcColors");
        assertEquals(on, colors.getMessage().getString().endsWith(I18n.get("options.on")));
        set(type, "badgeOutsideEdited", scenario, !on);
        check(minecraft, type, scenario, output, 5, on ? "TTC colors did not start Off" : "Other-group edit did not apply");
        Files.writeString(output.resolve(on ? "native-colors-on-input.json" : "native-colors-off-input.json"),
                new com.google.gson.Gson().toJson(Map.of("actualLabel", colors.getMessage().getString(),
                    "independentEditExpected", !on, "savedConfigUnchanged", true,
                    "scope", "actual native display control rejects incorrect independent edit expectation; no process relaunch claim")));
    }

    static void verifyCompact(Minecraft minecraft, Class<?> type, Object scenario, Path output, boolean on) throws Exception {
        var toggle = button(minecraft, "config.ae2craftingtime.compactStatusAmounts");
        assertEquals(on, toggle.getMessage().getString().endsWith(I18n.get("options.on")));
        set(type, "amountResumeChecksRestored", scenario, true);
        set(type, "amountResumeSaving", scenario, false);
        set(type, "amountResumeOpened", scenario, true);
        set(type, "amountResumeOffCaptured", scenario, !on);
        set(type, "amountResumeOnCaptured", scenario, false);
        set(type, "amountOptionRenderedAfter", scenario, 0L);
        var method = type.getDeclaredMethod("statusRelaunchTick", Minecraft.class, Map.class, java.util.function.Consumer.class);
        method.setAccessible(true);
        var screen = minecraft.screen;
        var saved = Files.readAllBytes(output.resolve("client.toml"));
        var checks = new LinkedHashMap<String, Boolean>();
        Throwable failure = null;
        try {
            assertEquals(false, method.invoke(scenario, minecraft, checks,
                    (java.util.function.Consumer<String>) name -> fail("Invalid compact state captured success")));
        } catch (InvocationTargetException error) { failure = error.getCause(); }
        if (on) assertEquals("Relaunched Options screen did not show compact amounts off",
                assertInstanceOf(IllegalStateException.class, failure).getMessage());
        else assertNull(failure);
        assertSame(screen, minecraft.screen);
        assertTrue(checks.isEmpty());
        assertEquals(!on, field(type, "amountResumeOffCaptured", scenario));
        assertEquals(false, field(type, "amountResumeOnCaptured", scenario));
        assertEquals(false, field(type, "amountResumeSaving", scenario));
        assertArrayEquals(saved, Files.readAllBytes(output.resolve("client.toml")));
        Files.writeString(output.resolve(on ? "native-compact-on-input.json" : "native-compact-off-input.json"),
                new com.google.gson.Gson().toJson(Map.of("actualLabel", toggle.getMessage().getString(),
                    "independentOffCheckpointAlreadyObserved", !on, "savedConfigUnchanged", true,
                    "scope", "actual native compact control rejects wrong initial On or waits on unchanged Off; no process relaunch claim")));
    }

    private static Button button(Minecraft minecraft, String key) {
        return minecraft.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().startsWith(I18n.get(key) + ": "))
                .findFirst().orElseThrow();
    }

    private static void check(Minecraft minecraft, Class<?> type, Object scenario, Path output,
            int step, String expectedError) throws Exception {
        set(type, "badgeResumeStep", scenario, step);
        set(type, "badgeResumeRenderedAfter", scenario, 0L);
        var screen = minecraft.screen;
        var saved = Files.readAllBytes(output.resolve("client.toml"));
        var checks = new LinkedHashMap<String, Boolean>();
        var method = type.getDeclaredMethod("badgeRelaunchTick", Minecraft.class, Map.class,
                java.util.function.Consumer.class);
        method.setAccessible(true);
        Throwable failure = null;
        try {
            assertEquals(false, method.invoke(scenario, minecraft, checks,
                    (java.util.function.Consumer<String>) name -> fail("Invalid control captured success")));
        } catch (InvocationTargetException error) { failure = error.getCause(); }
        if (expectedError == null) assertNull(failure);
        else assertEquals(expectedError, assertInstanceOf(IllegalStateException.class, failure).getMessage());
        assertEquals(step, field(type, "badgeResumeStep", scenario));
        assertTrue(checks.isEmpty());
        assertSame(screen, minecraft.screen);
        assertArrayEquals(saved, Files.readAllBytes(output.resolve("client.toml")));
    }
}
