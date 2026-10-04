package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.testdriver.DriverOptions;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Calls the release guard against the actual idle fixture; no fake jobs or futures. */
final class NativePrematureReleaseBoundary {
    private CompletableFuture<Map<String, Boolean>> state;
    private Map<String, Boolean> before;
    private Object probe;
    private boolean rejected;

    boolean tick(Minecraft minecraft, Object original, Object marker, Map<?, ?> ordinaryChecks, Path output) throws Exception {
        var type = original.getClass();
        var fixture = field(type, "fixture", original);
        var terminal = (BlockPos) field(fixture.getClass(), "terminal", fixture);
        if (state == null) {
            state = nativeState(minecraft, fixture, terminal);
            return false;
        }
        if (!state.isDone()) return false;
        if (before == null) {
            before = state.join();
            assertTrue(before.get("formed") && before.get("active"));
            assertFalse(before.get("busy") || before.get("finalOutputReady"));
            var constructor = type.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
            constructor.setAccessible(true);
            probe = constructor.newInstance("stored-variant-plan", DriverOptions.load().world(), output, false);
            set(type, "fixture", probe, fixture);
            set(type, "leaf", probe, "delayed-status");
            var stages = Class.forName(type.getName() + "$Stage");
            set(type, "phase", probe, java.util.Arrays.stream(stages.getEnumConstants())
                    .filter(stage -> stage.toString().equals("WORLD_RELEASE")).findFirst().orElseThrow());
        }
        var screen = minecraft.screen;
        var menu = minecraft.player.containerMenu;
        var checksBefore = Map.copyOf(ordinaryChecks);
        var config = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
        var saved = Files.exists(config) ? Files.readAllBytes(config) : null;
        var hold = field(fixture.getClass(), "holdFinalOutput", fixture);
        if (!rejected) {
            var method = type.getDeclaredMethod("tick", Minecraft.class, marker.getClass(), Map.class,
                    java.util.function.Consumer.class, java.util.function.BiConsumer.class);
            method.setAccessible(true);
            var checks = new LinkedHashMap<String, Boolean>();
            try {
                assertEquals(false, method.invoke(probe, minecraft, marker, checks,
                        (java.util.function.Consumer<String>) name -> fail("Premature release captured success"),
                        (java.util.function.BiConsumer<Integer, Integer>) (x, y) -> fail("Premature release moved the mouse")));
            } catch (InvocationTargetException error) {
                var completion = assertInstanceOf(CompletionException.class, error.getCause());
                var cause = assertInstanceOf(IllegalStateException.class, completion.getCause());
                assertEquals("Final delayed output was not held in an active craft", cause.getMessage());
                rejected = true;
                state = nativeState(minecraft, fixture, terminal);
            }
            assertTrue(checks.isEmpty());
        }
        assertEquals("WORLD_RELEASE", field(type, "phase", probe).toString());
        assertEquals(hold, field(fixture.getClass(), "holdFinalOutput", fixture));
        assertSame(screen, minecraft.screen);
        assertSame(menu, minecraft.player.containerMenu);
        assertEquals(checksBefore, ordinaryChecks);
        if (saved == null) assertFalse(Files.exists(config));
        else assertArrayEquals(saved, Files.readAllBytes(config));
        if (!rejected || !state.isDone()) return false;
        assertEquals(before, state.join());
        Files.writeString(output.resolve("premature-final-output-release.json"), new com.google.gson.Gson().toJson(Map.of(
                "before", before, "after", state.join(), "phase", "WORLD_RELEASE", "error", "Final delayed output was not held in an active craft",
                "scope", "real prepared idle CPU has no held final output; actual server-submitted guard rejects without mutation")));
        return true;
    }

    private CompletableFuture<Map<String, Boolean>> nativeState(Minecraft minecraft, Object fixture, BlockPos terminal) {
        var id = minecraft.player.getUUID();
        return minecraft.getSingleplayerServer().submit(() -> {
            try {
                var player = minecraft.getSingleplayerServer().getPlayerList().getPlayer(id);
                var cpu = (appeng.blockentity.crafting.CraftingBlockEntity) player.serverLevel().getBlockEntity(terminal.west(2));
                var ready = fixture.getClass().getDeclaredMethod("finalOutputReady", net.minecraft.server.level.ServerPlayer.class);
                ready.setAccessible(true);
                return Map.of("formed", cpu.isFormed(), "active", cpu.isActive(), "busy", cpu.getCluster().isBusy(),
                        "finalOutputReady", (boolean) ready.invoke(fixture, player));
            } catch (Exception error) { throw new IllegalStateException("Read actual release boundary state", error); }
        });
    }
}
