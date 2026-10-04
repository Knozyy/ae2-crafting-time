package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.testdriver.DriverOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Missing native control payloads restore before draw, never changing server settings. */
final class NativeSuspensionWidgetBoundary {
    static void verifyNoMenu(Minecraft minecraft, Object original, Path output) throws Exception {
        assertNull(minecraft.screen);
        assertSame(minecraft.player.inventoryMenu, minecraft.player.containerMenu);
        var type = original.getClass();
        for (var name : new String[]{"suspensionButton", "suspensionButtonStarting"}) {
            var method = type.getDeclaredMethod(name, Minecraft.class, String.class);
            method.setAccessible(true);
            assertNull(method.invoke(null, minecraft, "Suspend"));
        }
        var method = type.getDeclaredMethod("selectedSuspensionSnapshot", Minecraft.class);
        method.setAccessible(true);
        assertNull(method.invoke(null, minecraft));
        Files.writeString(output.resolve("native-no-suspension-menu.json"), new com.google.gson.Gson().toJson(Map.of(
                "screenAbsent", true, "menu", minecraft.player.containerMenu.getClass().getName(),
                "scope", "actual loaded world with native InventoryMenu; three original helper returns")));
    }

    static void verifyControls(Minecraft minecraft, Object original, Object marker, Map<?, ?> ordinaryChecks,
            Path output, boolean serverOptions, long renders) throws Exception {
        assertTrue(renders >= 8);
        var type = original.getClass();
        var constructor = type.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
        constructor.setAccessible(true);
        var probe = constructor.newInstance("crafting-suspension", DriverOptions.load().world(), output, false);
        set(type, "fixture", probe, field(type, "fixture", original));
        set(type, "phase", probe, field(type, "phase", original));
        var method = type.getDeclaredMethod("tickSuspension", Minecraft.class, marker.getClass(), Map.class,
                java.util.function.Consumer.class);
        method.setAccessible(true);
        var cases = new ArrayList<String>();
        var stages = serverOptions ? new int[]{12,16,41,44,17,31,13,18,42,45,32} : new int[]{2,2,6,28};
        var labels = serverOptions ? new String[]{"Allow crafting suspension: ","Allow crafting suspension: ",
                "Allow crafting suspension: ","Allow crafting suspension: ","Profiling and TTC: ","Profiling and TTC: ",
                "Done","Done","Done","Done","Done"} : new String[]{"Cancel","Suspend","Resume","Suspend"};
        var screen = minecraft.screen;
        var menu = minecraft.player.containerMenu;
        var serverConfig = com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.current();
        var checksBefore = Map.copyOf(ordinaryChecks);
        var config = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
        var saved = Files.exists(config) ? Files.readAllBytes(config) : null;
        for (int index = 0; index < stages.length; index++) {
            var label = labels[index];
            var widget = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                    .filter(b -> b.getMessage().getString().startsWith(label)).findFirst().orElse(null);
            if (!(label.equals("Resume") && !serverOptions)) assertNotNull(widget, "Actual control must exist before fault: " + label);
            else assertNull(widget, "Running native CPU must not offer Resume");
            int faults = serverOptions && !label.equals("Done") ? 2 : 1;
            for (int fault = 0; fault < faults; fault++) {
                var message = widget == null ? null : widget.getMessage();
                boolean active = widget != null && widget.active;
                set(type, "suspensionStage", probe, stages[index]);
                var checks = new LinkedHashMap<String, Boolean>();
                try {
                    if (widget != null) {
                        if (fault == 0) widget.setMessage(Component.literal("native test: unavailable control"));
                        else widget.active = false;
                    }
                    assertEquals(false, method.invoke(probe, minecraft, marker, checks,
                            (java.util.function.Consumer<String>) name -> fail("Missing control captured success")));
                    assertEquals(stages[index], field(type, "suspensionStage", probe));
                    assertNull(field(type, "operation", probe), "Control wait must not schedule server work");
                    assertTrue(checks.isEmpty());
                    cases.add("stage-"+stages[index]+"-"+label+"-"+(fault==0 ? "absent" : "inactive"));
                } finally {
                    if (widget != null) { widget.setMessage(message); widget.active = active; }
                }
                if (widget != null) { assertSame(message, widget.getMessage()); assertEquals(active, widget.active); }
            }
        }
        assertSame(screen, minecraft.screen);
        assertSame(menu, minecraft.player.containerMenu);
        assertSame(serverConfig, com.ctux.ae2craftingtime.mc1201.ServerOptionsRuntime.current());
        assertEquals(checksBefore, ordinaryChecks);
        if (saved == null) assertFalse(Files.exists(config));
        else assertArrayEquals(saved, Files.readAllBytes(config));
        assertEquals(serverOptions ? 17 : 4, cases.size());
        Files.writeString(output.resolve(serverOptions ? "native-server-control-inputs.json" : "native-cpu-control-inputs.json"),
                new com.google.gson.Gson().toJson(Map.of("cases", cases, "actualScreenRenders", renders,
                    "screen", screen.getClass().getName(), "menu", menu.getClass().getName(),
                    "scope", "independent driver expectations consume actual native control payloads restored before draw",
                    "ordinaryChecksUnchanged", true, "serverConfigUnchanged", true, "savedConfigUnchanged", true)));
    }
}
