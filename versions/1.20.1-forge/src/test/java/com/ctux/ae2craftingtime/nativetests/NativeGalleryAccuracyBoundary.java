package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.testdriver.DriverOptions;
import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;

/** Only the independent expectation is wrong; the actual completed job remains unchanged. */
final class NativeGalleryAccuracyBoundary {
    private Object probe;
    private final ArrayList<Long> frames = new ArrayList<>();
    private long started;

    boolean tick(Minecraft minecraft, Object original, Object marker, Map<?, ?> ordinaryChecks, Path output) throws Exception {
        var source = UiObservationStore.latest();
        if (source == null || frames.contains(source.frame())) return false;
        var type = original.getClass();
        boolean partial = (boolean) field(type, "partialJob", original);
        if (probe == null) {
            started = System.nanoTime();
            var constructor = type.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
            constructor.setAccessible(true);
            probe = constructor.newInstance("craft-lifecycle", DriverOptions.load().world(), output, false);
            for (var name : List.of("fixture", "phase")) set(type, name, probe, field(type, name, original));
            set(type, "partialJob", probe, !partial);
        }
        assertTrue(System.nanoTime() - started < 30_000_000_000L, "Gallery accuracy checkpoint exceeded thirty seconds");
        var before = Map.copyOf(ordinaryChecks);
        var screen = minecraft.screen;
        var menu = minecraft.player.containerMenu;
        var config = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
        var saved = Files.exists(config) ? Files.readAllBytes(config) : null;
        frames.add(source.frame());
        var method = type.getDeclaredMethod("tick", Minecraft.class, marker.getClass(), Map.class,
                java.util.function.Consumer.class, java.util.function.BiConsumer.class);
        method.setAccessible(true);
        var checks = new LinkedHashMap<String, Boolean>();
        boolean done = false;
        try {
            assertEquals(false, method.invoke(probe, minecraft, marker, checks,
                    (java.util.function.Consumer<String>) name -> fail("Wrong accuracy expectation captured success"),
                    (java.util.function.BiConsumer<Integer, Integer>) (x, y) -> fail("Wrong accuracy expectation moved mouse")));
        } catch (InvocationTargetException error) {
            var serverFailure = assertInstanceOf(CompletionException.class, error.getCause());
            var cause = assertInstanceOf(IllegalStateException.class, serverFailure.getCause());
            assertTrue(cause.getMessage().startsWith("Unexpected real job coverage: "));
            assertTrue(frames.size() >= 8);
            assertNotNull(field(type, "operation", probe), "Retain the actual failed future on the discarded probe");
            Files.writeString(output.resolve(partial ? "gallery-partial-accuracy-inputs.json" : "gallery-full-accuracy-inputs.json"),
                    new com.google.gson.Gson().toJson(Map.of("frames", frames, "actualPartialJob", partial,
                        "independentExpectedPartialJob", !partial, "originalServerError", cause.getMessage(),
                        "scope", "actual retained completed-job sample rejects an incorrect independent expectation; no profile mutation",
                        "ordinaryChecksUnchanged", true, "savedConfigUnchanged", true)));
            done = true;
        }
        assertTrue(checks.isEmpty());
        assertSame(source, UiObservationStore.latest());
        assertSame(screen, minecraft.screen);
        assertSame(menu, minecraft.player.containerMenu);
        assertEquals(before, ordinaryChecks);
        assertEquals("GALLERY_DETAILS", field(type, "phase", probe).toString());
        if (saved == null) assertFalse(Files.exists(config));
        else assertArrayEquals(saved, Files.readAllBytes(config));
        return done;
    }
}
