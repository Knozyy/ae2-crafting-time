package com.ctux.ae2craftingtime.nativetests;

import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.core.ClientConfig;
import com.ctux.ae2craftingtime.core.OptionFeature;
import com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime;
import com.ctux.ae2craftingtime.mc1201.OptionsScreen;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

/** Optional native test artifact; never packaged in production or the ordinary driver. */
@Mod("ae2ct_native_options_boundary")
public final class NativeOptionsBoundaryMod {
    private final ArrayList<String> passed = new ArrayList<>();
    private Path output;
    private Path originalPath;
    private ClientConfig originalConfig;
    private int stage;
    private int seekAttempts;
    private long started;
    private boolean finished;
    private Object scenario;
    private Class<?> scenarioType;

    public NativeOptionsBoundaryMod() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        var minecraft = Minecraft.getInstance();
        if (started == 0 && (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null)) return;
        try {
            if (started == 0) {
                output = Path.of(System.getProperty("ae2craftingtime.test.nativeOptionsOutput"));
                Files.createDirectories(output);
                originalPath = (Path) field(ClientOptionsRuntime.class, "path", null);
                originalConfig = ClientOptionsRuntime.current().copy();
                set(ClientOptionsRuntime.class, "path", null, output.resolve("client.toml"));
                started = System.nanoTime();
                scenarioType = Class.forName("com.ctux.ae2craftingtime.testdriver.StandardAe2Scenario");
                var constructor = scenarioType.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
                constructor.setAccessible(true);
                scenario = constructor.newInstance("badge-background", "native-options-boundary", output, false);
                org.lwjgl.glfw.GLFW.glfwMaximizeWindow(minecraft.getWindow().getWindow());
            }
            assertTrue(System.nanoTime() - started < 60_000_000_000L, "Native options checks exceeded 60 seconds");
            switch (stage) {
                case 0 -> {
                    var config = new ClientConfig();
                    for (int fault = 0; fault < 3; fault++) {
                        ClientOptionsRuntime.apply(config);
                        set(scenarioType, "badgeEditOpen", scenario, true);
                        reject("badgeEditTick", new Class<?>[]{Minecraft.class, int.class},
                                "Badge option save did not apply", minecraft, 0);
                        config.features().setEnabled(OptionFeature.BADGE_BACKGROUND, true);
                        if (fault == 1) config.setColor(ClientConfig.Color.BADGE, 0x245A7D);
                    }
                    config.setBadgeOpacity(96);
                    ClientOptionsRuntime.apply(config);
                    assertEquals(true, call("badgeEditTick", new Class<?>[]{Minecraft.class, int.class}, minecraft, 0));
                    passed.add("Rejected wrong background, color and opacity; accepted restored save");
                    stage++;
                }
                case 1 -> {
                    set(scenarioType, "badgeResumeStep", scenario, 29);
                    for (boolean plan : new boolean[]{true, false}) {
                        var config = new ClientConfig();
                        if (plan) config.setPlanSort(0); else config.setStatusSort(0);
                        ClientOptionsRuntime.apply(config);
                        reject("badgeRelaunchTick", new Class<?>[]{Minecraft.class, Map.class, java.util.function.Consumer.class},
                                "Controls reset did not save both default sort modes", minecraft, Map.of(),
                                (java.util.function.Consumer<String>) name -> fail("Rejected reset must not capture success"));
                    }
                    ClientOptionsRuntime.apply(new ClientConfig());
                    assertEquals(true, call("badgeRelaunchTick",
                            new Class<?>[]{Minecraft.class, Map.class, java.util.function.Consumer.class},
                            minecraft, Map.of(), (java.util.function.Consumer<String>) name -> fail("Unexpected capture")));
                    passed.add("Rejected each wrong saved sort; accepted restored defaults");
                    minecraft.setScreen(new OptionsScreen(minecraft.screen));
                    stage++;
                }
                case 2 -> {
                    assertInstanceOf(OptionsScreen.class, minecraft.screen);
                    assertFalse(minecraft.screen.children().isEmpty(), "Actual options widgets must be initialized");
                    capture(minecraft, "native-options-before.png");
                    reject("clickOptionButton", new Class<?>[]{Minecraft.class, String.class},
                            "Option button missing: native-boundary-missing", minecraft, "native-boundary-missing");
                    passed.add("Missing native button rejected without modifying saved configuration");
                    stage++;
                }
                case 3 -> {
                    // Advance only on actual completed render callbacks as the driver does.
                    var runtime = Class.forName("com.ctux.ae2craftingtime.testdriver.TestDriverRuntime");
                    set(runtime, "renderedFrames", null, (long) field(runtime, "renderedFrames", null) + 1);
                    try {
                        assertNull(call("seekOptionButton", new Class<?>[]{Minecraft.class, String.class},
                                minecraft, "native-boundary-missing"));
                        assertTrue(++seekAttempts < 100, "Missing option search did not terminate");
                    } catch (IllegalStateException error) {
                        assertEquals("Option is missing from all pages: native-boundary-missing", error.getMessage());
                        passed.add("Exhausted real native option pages rejected the missing control");
                        stage++;
                    }
                }
                case 4 -> {
                    capture(minecraft, "native-options-after-search.png");
                    restore();
                    Files.writeString(output.resolve("result.json"), new com.google.gson.Gson().toJson(
                            Map.of("result", "PASS", "checks", passed, "runtimeClassSha256", runtimeHash())));
                    finished = true;
                    minecraft.stop();
                }
                default -> throw new AssertionError("Unknown native boundary stage " + stage);
            }
        } catch (Throwable error) {
            finished = true;
            try {
                restore();
                if (output != null) Files.writeString(output.resolve("failure.txt"), error.toString());
            } catch (Exception secondary) { error.addSuppressed(secondary); }
            minecraft.stop();
            throw new AssertionError("Native options boundary failed", error);
        }
    }

    private void reject(String name, Class<?>[] types, String message, Object... arguments) throws Exception {
        var before = Files.readAllBytes(output.resolve("client.toml"));
        var error = assertThrows(IllegalStateException.class, () -> call(name, types, arguments));
        assertEquals(message, error.getMessage());
        assertArrayEquals(before, Files.readAllBytes(output.resolve("client.toml")), "Rejected action changed saved options");
    }

    private Object call(String name, Class<?>[] types, Object... arguments) throws Exception {
        var method = scenarioType.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try { return method.invoke(scenario, arguments); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception cause) throw cause;
            if (error.getCause() instanceof Error cause) throw cause;
            throw error;
        }
    }

    private static Object field(Class<?> owner, String name, Object instance) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(instance);
    }

    private static void set(Class<?> owner, String name, Object instance, Object value) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); field.set(instance, value);
    }

    private void restore() throws Exception {
        if (originalConfig == null) return;
        ClientOptionsRuntime.apply(originalConfig);
        set(ClientOptionsRuntime.class, "path", null, originalPath);
    }

    private static void capture(Minecraft minecraft, String name) throws Exception {
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            image.writeToFile(Path.of(System.getProperty("ae2craftingtime.test.nativeOptionsOutput")).resolve(name));
        }
    }

    private static String runtimeHash() throws Exception {
        var scenarioType = Class.forName("com.ctux.ae2craftingtime.testdriver.StandardAe2Scenario");
        try (var stream = scenarioType.getResourceAsStream("StandardAe2Scenario.class")) {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(stream.readAllBytes()));
        }
    }
}
