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
    private int originalScale;

    public NativeOptionsBoundaryMod() {
        if (System.getProperty("ae2craftingtime.test.nativeVariantOutput") != null) {
            new NativeVariantBoundaryRunner();
            return;
        }
        if (System.getProperty("ae2craftingtime.test.nativeCraftOutput") != null) {
            new NativeCraftBoundaryRunner();
            return;
        }
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
                originalScale = minecraft.options.guiScale().get();
                set(ClientOptionsRuntime.class, "path", null, output.resolve("client.toml"));
                started = System.nanoTime();
                scenarioType = Class.forName("com.ctux.ae2craftingtime.testdriver.StandardAe2Scenario");
                var constructor = scenarioType.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
                constructor.setAccessible(true);
                scenario = constructor.newInstance("badge-background", "native-options-boundary", output, false);
                org.lwjgl.glfw.GLFW.glfwMaximizeWindow(minecraft.getWindow().getWindow());
            }
            assertTrue(System.nanoTime() - started < 60_000_000_000L, "Native options checks exceeded 60 seconds");
            var runtime = Class.forName("com.ctux.ae2craftingtime.testdriver.TestDriverRuntime");
            set(runtime, "renderedFrames", null, (long) field(runtime, "renderedFrames", null) + 1);
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
                    // Search only on actual completed render callbacks as the driver does.
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
                    minecraft.setScreen(new TitleScreen());
                    stage++;
                }
                case 5 -> {
                    set(scenarioType, "amountResumeChecksRestored", scenario, true);
                    set(scenarioType, "amountResumeSaving", scenario, true);
                    ClientOptionsRuntime.apply(new ClientConfig());
                    reject("statusRelaunchTick", new Class<?>[]{Minecraft.class, Map.class, java.util.function.Consumer.class},
                            "Compact amounts did not restore after relaunch", minecraft, Map.of(),
                            (java.util.function.Consumer<String>) name -> fail("Invalid restoration must not capture success"));
                    var config = new ClientConfig();
                    config.features().setEnabled(OptionFeature.COMPACT_STATUS_AMOUNTS, true);
                    ClientOptionsRuntime.apply(config);
                    var captures = new ArrayList<String>();
                    assertEquals(true, call("statusRelaunchTick",
                            new Class<?>[]{Minecraft.class, Map.class, java.util.function.Consumer.class},
                            minecraft, Map.of(), (java.util.function.Consumer<String>) captures::add));
                    assertEquals(java.util.List.of("status-relaunch-restored.png"), captures);
                    passed.add("Rejected lost compact-amount restoration; accepted restored value with exact success checkpoint");
                    config.features().setEnabled(OptionFeature.BADGE_BACKGROUND, true);
                    ClientOptionsRuntime.apply(config);
                    minecraft.setScreen(new OptionsScreen(null));
                    call("clickOptionButton", new Class<?>[]{Minecraft.class, String.class}, minecraft,
                            net.minecraft.client.resources.language.I18n.get("config.ae2craftingtime.group.appearance"));
                    stage++;
                }
                case 6 -> {
                    set(scenarioType, "badgeResumeStep", scenario, 7);
                    set(scenarioType, "badgeResetCheckPhase", scenario, 0);
                    reject("badgeRelaunchTick", new Class<?>[]{Minecraft.class, Map.class, java.util.function.Consumer.class},
                            "Reset did not restore badge Off", minecraft, Map.of(),
                            (java.util.function.Consumer<String>) name -> fail("Invalid reset must not capture success"));
                    passed.add("Rejected native reset retaining badge background On");
                    var config = new ClientConfig();
                    config.features().setEnabled(OptionFeature.TEXT_SHADOW, true);
                    ClientOptionsRuntime.apply(config);
                    minecraft.setScreen(new OptionsScreen(null));
                    call("clickOptionButton", new Class<?>[]{Minecraft.class, String.class}, minecraft,
                            net.minecraft.client.resources.language.I18n.get("config.ae2craftingtime.group.appearance"));
                    stage++;
                }
                case 7 -> {
                    set(scenarioType, "badgeResetCheckPhase", scenario, 1);
                    reject("badgeRelaunchTick", new Class<?>[]{Minecraft.class, Map.class, java.util.function.Consumer.class},
                            "Reset did not restore text shadow Off", minecraft, Map.of(),
                            (java.util.function.Consumer<String>) name -> fail("Invalid shadow reset must not capture success"));
                    passed.add("Rejected native reset retaining text shadow On");
                    set(scenarioType, "badgeResumeStep", scenario, -1);
                    reject("badgeRelaunchTick", new Class<?>[]{Minecraft.class, Map.class, java.util.function.Consumer.class},
                            "Unexpected badge relaunch step -1", minecraft, Map.of(),
                            (java.util.function.Consumer<String>) name -> fail("Unknown stage must not capture success"));
                    passed.add("Rejected invalid native relaunch stage without changing saved options");
                    capture(minecraft, "native-options-rejected-reset.png");
                    stage++;
                }
                case 8 -> {
                    for (int fault = 0; fault < 3; fault++) {
                        var config = new ClientConfig();
                        config.features().setEnabled(OptionFeature.BADGE_BACKGROUND, fault < 2);
                        config.features().setEnabled(OptionFeature.TEXT_SHADOW, fault == 1);
                        openGroup(minecraft, config, "appearance");
                        set(scenarioType, "badgeResumeStep", scenario, 6);
                        set(scenarioType, "badgeResetBadgeEdited", scenario, fault == 1);
                        set(scenarioType, "badgeShadowEdited", scenario, fault == 2);
                        rejectBadge(minecraft, switch (fault) {
                            case 0 -> "Unexpected badge value before section reset";
                            case 1 -> "Text shadow did not start Off";
                            default -> "Text shadow edit did not apply before reset";
                        });
                    }
                    passed.add("Rejected unexpected badge state and both failed shadow edits before reset");
                    var config = new ClientConfig();
                    config.setPlanSort(0);
                    openGroup(minecraft, config, "controls");
                    set(scenarioType, "badgeResumeStep", scenario, 21);
                    stage++;
                }
                case 9 -> {
                    if (!awaitBadgeRejection(minecraft, "Controls sort differs at step 21")) return;
                    passed.add("Rejected wrong plan sort displayed by the native Controls screen");
                    var config = new ClientConfig();
                    config.setStatusSort(0);
                    openGroup(minecraft, config, "controls");
                    set(scenarioType, "badgeResumeStep", scenario, 23);
                    stage++;
                }
                case 10 -> {
                    if (!awaitBadgeRejection(minecraft, "Controls sort differs at step 23")) return;
                    passed.add("Rejected wrong status sort displayed by the native Controls screen");
                    openGroup(minecraft, new ClientConfig(), "displays");
                    set(scenarioType, "badgeResumeStep", scenario, 7);
                    set(scenarioType, "badgeResetCheckPhase", scenario, 3);
                    stage++;
                }
                case 11 -> {
                    if (!awaitBadgeRejection(minecraft, "Reset changed another group or Reset All failed")) return;
                    passed.add("Rejected section reset losing another group's edited colors");
                    var config = new ClientConfig();
                    config.features().setEnabled(OptionFeature.TTC_COLORS, true);
                    openGroup(minecraft, config, "displays");
                    set(scenarioType, "badgeResumeStep", scenario, 11);
                    set(scenarioType, "badgeResetCheckPhase", scenario, 3);
                    stage++;
                }
                case 12 -> {
                    if (!awaitBadgeRejection(minecraft, "Reset changed another group or Reset All failed")) return;
                    passed.add("Rejected Reset All retaining edited colors");
                    capture(minecraft, "native-options-rejected-colors.png");
                    stage++;
                }
                case 13 -> {
                    var config = new ClientConfig();
                    config.setColor(ClientConfig.Color.BADGE, 0x245A7D);
                    config.setBadgeOpacity(96);
                    var physicalPath = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
                    set(ClientOptionsRuntime.class, "path", null, physicalPath);
                    try { ClientOptionsRuntime.apply(config); }
                    finally { set(ClientOptionsRuntime.class, "path", null, output.resolve("client.toml")); }
                    ClientOptionsRuntime.apply(config);
                    var continuationType = Class.forName(scenarioType.getName() + "$BadgeContinuation");
                    var recordConstructor = continuationType.getDeclaredConstructor(int.class, String.class, String.class,
                            String.class, java.util.List.class, java.util.List.class);
                    recordConstructor.setAccessible(true);
                    call("writeBadgeContinuation", new Class<?>[]{continuationType}, recordConstructor.newInstance(
                            1, "native-options-boundary", "local",
                            call("configHash", new Class<?>[]{Minecraft.class}, minecraft), java.util.List.of(), java.util.List.of()));
                    assertNull(System.getProperty("ae2craftingtime.test.badgeRelaunch"));
                    assertNull(System.getProperty("ae2craftingtime.test.continuation"));
                    try {
                        System.setProperty("ae2craftingtime.test.badgeRelaunch", "true");
                        System.setProperty("ae2craftingtime.test.continuation", output.resolve("badge-background-continuation.json").toString());
                        var constructor = scenarioType.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
                        constructor.setAccessible(true);
                        scenario = constructor.newInstance("badge-background", "native-options-boundary", output, false);
                    } finally {
                        System.clearProperty("ae2craftingtime.test.badgeRelaunch");
                        System.clearProperty("ae2craftingtime.test.continuation");
                    }
                    minecraft.setScreen(new OptionsScreen(new TitleScreen()));
                    call("clickOptionButton", new Class<?>[]{Minecraft.class, String.class}, minecraft,
                            net.minecraft.client.resources.language.I18n.get("gui.cancel"));
                    assertInstanceOf(TitleScreen.class, minecraft.screen);
                    set(scenarioType, "badgeResumeStep", scenario, 4);
                    var saved = Files.readAllBytes(physicalPath);
                    try {
                        Files.writeString(physicalPath, "\n# native test: unexpected save after Cancel\n", java.nio.file.StandardOpenOption.APPEND);
                        rejectBadge(minecraft, "Cancel changed saved badge options");
                    } finally { Files.write(physicalPath, saved); }
                    assertEquals(false, call("badgeRelaunchTick",
                            new Class<?>[]{Minecraft.class, Map.class, java.util.function.Consumer.class},
                            minecraft, Map.of(), (java.util.function.Consumer<String>) name -> fail("Cancel recovery captured success")));
                    assertEquals(5, field(scenarioType, "badgeResumeStep", scenario));
                    passed.add("Rejected changed physical options after native Cancel; accepted restored file hash");
                    openGroup(minecraft, new ClientConfig(), "warnings");
                    set(scenarioType, "amountResumeChecksRestored", scenario, true);
                    set(scenarioType, "amountResumeOpened", scenario, true);
                    set(scenarioType, "amountOptionRenderedAfter", scenario, 0L);
                    stage++;
                }
                case 14 -> {
                    var before = Files.readAllBytes(output.resolve("client.toml"));
                    try {
                        assertEquals(false, call("statusRelaunchTick",
                                new Class<?>[]{Minecraft.class, Map.class, java.util.function.Consumer.class},
                                minecraft, Map.of(), (java.util.function.Consumer<String>) name -> fail("Wrong group captured success")));
                    } catch (IllegalStateException error) {
                        assertEquals("Option button missing: >", error.getMessage());
                        passed.add("Rejected compact restoration after exhausting the wrong native settings group's pages");
                        minecraft.setScreen(new OptionsScreen(new TitleScreen()));
                        set(scenarioType, "amountOptionRenderedAfter", scenario, 0L);
                        stage++;
                    } finally { assertArrayEquals(before, Files.readAllBytes(output.resolve("client.toml"))); }
                }
                case 15 -> {
                    var captures = new ArrayList<String>();
                    var complete = (boolean) call("statusRelaunchTick",
                            new Class<?>[]{Minecraft.class, Map.class, java.util.function.Consumer.class},
                            minecraft, Map.of(), (java.util.function.Consumer<String>) captures::add);
                    for (var name : captures) capture(minecraft, name);
                    if (!complete) return;
                    assertTrue(ClientOptionsRuntime.current().features().enabled(OptionFeature.COMPACT_STATUS_AMOUNTS));
                    assertFalse(minecraft.screen instanceof OptionsScreen);
                    passed.add("Recovered compact restoration in Displays through native controls and saved Done");
                    minecraft.setScreen(new OptionsScreen(new TitleScreen()));
                    var stageType = Class.forName(scenarioType.getName() + "$Stage");
                    var optionsStage = java.util.Arrays.stream(stageType.getEnumConstants())
                            .filter(value -> value.toString().equals("STATUS_OPTIONS")).findFirst().orElseThrow();
                    set(scenarioType, "phase", scenario, optionsStage);
                    set(scenarioType, "amountOptionSaving", scenario, true);
                    set(scenarioType, "amountOptionSavingTicks", scenario, 0);
                    set(scenarioType, "amountOptionRenderedAfter", scenario, 0L);
                    stage++;
                }
                case 16 -> {
                    var before = Files.readAllBytes(output.resolve("client.toml"));
                    int callbacks = (int) field(scenarioType, "amountOptionSavingTicks", scenario);
                    var arguments = new Class<?>[]{Minecraft.class,
                            Class.forName("com.ctux.ae2craftingtime.testdriver.FixtureMarker"), Map.class,
                            java.util.function.Consumer.class, java.util.function.BiConsumer.class};
                    try {
                        var consumer = (java.util.function.Consumer<String>) name -> fail("Stuck save captured success");
                        var mouse = (java.util.function.BiConsumer<Integer, Integer>) (x, y) -> fail("Stuck save moved mouse");
                        if (callbacks < 100) {
                            // This Options-only guard does not consume a world marker or native player.
                            assertEquals(false, call("tick", arguments, minecraft, null, Map.of(), consumer, mouse));
                            assertEquals(callbacks + 1, field(scenarioType, "amountOptionSavingTicks", scenario));
                            return;
                        }
                        var controls = minecraft.screen.children().stream()
                                .filter(net.minecraft.client.gui.components.Button.class::isInstance)
                                .map(net.minecraft.client.gui.components.Button.class::cast)
                                .map(button -> button.getMessage().getString() + " active=" + button.active).toList();
                        var error = assertThrows(IllegalStateException.class,
                                () -> call("tick", arguments, minecraft, null, Map.of(), consumer, mouse));
                        assertEquals("Amount option save did not close screen: case=0 controls=" + controls, error.getMessage());
                        passed.add("Stuck native save remained pending for 100 completed callbacks and failed on callback 101");
                        capture(minecraft, "native-options-stuck-save.png");
                        stage++;
                    } finally { assertArrayEquals(before, Files.readAllBytes(output.resolve("client.toml"))); }
                }
                case 17 -> {
                    var config = new ClientConfig();
                    config.features().setEnabled(OptionFeature.COMPACT_STATUS_AMOUNTS, true);
                    openGroup(minecraft, config, "warnings");
                    var stageType = Class.forName(scenarioType.getName() + "$Stage");
                    set(scenarioType, "phase", scenario, java.util.Arrays.stream(stageType.getEnumConstants())
                            .filter(value -> value.toString().equals("STATUS_PERSIST")).findFirst().orElseThrow());
                    set(scenarioType, "amountPersistSaving", scenario, false);
                    seekAttempts = 0;
                    stage++;
                }
                case 18 -> {
                    var before = Files.readAllBytes(output.resolve("client.toml"));
                    try {
                        assertEquals(false, persistenceTick(minecraft));
                        assertTrue(++seekAttempts < 16, "Persistence navigation did not exhaust native pages");
                        assertFalse((boolean) field(scenarioType, "amountPersistSaving", scenario));
                        return;
                    } catch (IllegalStateException error) {
                        assertEquals("Option button missing: >", error.getMessage());
                        assertFalse((boolean) field(scenarioType, "amountPersistSaving", scenario));
                        assertFalse((boolean) field(scenarioType, "amountContinuationWritten", scenario));
                        capture(minecraft, "native-options-persistence-wrong-group.png");
                        passed.add("Persistence exhausted the wrong native group without saving or writing continuation");
                        stage++;
                    } finally {
                        assertArrayEquals(before, Files.readAllBytes(output.resolve("client.toml")));
                    }
                }
                case 19 -> {
                    var config = new ClientConfig();
                    config.features().setEnabled(OptionFeature.COMPACT_STATUS_AMOUNTS, true);
                    openGroup(minecraft, config, "displays");
                    stage++;
                }
                case 20 -> {
                    var before = Files.readAllBytes(output.resolve("client.toml"));
                    assertEquals(false, persistenceTick(minecraft));
                    assertInstanceOf(OptionsScreen.class, minecraft.screen);
                    assertFalse((boolean) field(scenarioType, "amountPersistSaving", scenario));
                    assertArrayEquals(before, Files.readAllBytes(output.resolve("client.toml")), "Toggle saved before Done");
                    stage++;
                }
                case 21 -> {
                    capture(minecraft, "native-options-persistence-off.png");
                    assertEquals(false, persistenceTick(minecraft));
                    assertFalse(minecraft.screen instanceof OptionsScreen);
                    assertTrue((boolean) field(scenarioType, "amountPersistSaving", scenario));
                    assertFalse(ClientOptionsRuntime.current().features().enabled(OptionFeature.COMPACT_STATUS_AMOUNTS));
                    assertTrue(Files.readString(output.resolve("client.toml")).contains("compactStatusAmounts = false"));
                    assertFalse((boolean) field(scenarioType, "amountContinuationWritten", scenario));
                    assertFalse(Files.exists(output.resolve("status-amounts-continuation.json")));
                    passed.add("Persistence recovered through native Displays toggle and Done, saving compact amounts Off");
                    stage++;
                }
                case 22 -> {
                    minecraft.setScreen(new TitleScreen());
                    stage++;
                }
                case 23 -> {
                    NativeObservationBoundary.verify(minecraft, passed);
                    var before = Files.readAllBytes(output.resolve("client.toml"));
                    var screen = minecraft.screen;
                    var checks = Map.of("not-ready", false);
                    var capture = (java.util.function.Consumer<String>) name -> fail("Wrong screen captured success: " + name);
                    set(scenarioType, "badgeStep", scenario, 1);
                    set(scenarioType, "badgeRenderedAfter", scenario, 0L);
                    assertEquals(false, call("badgeTick",
                            new Class<?>[]{Minecraft.class, Map.class, java.util.function.Consumer.class},
                            minecraft, checks, capture));
                    assertEquals(1, field(scenarioType, "badgeStep", scenario));
                    set(scenarioType, "badgeScaleStep", scenario, 2);
                    assertEquals(false, call("badgeScaleTick",
                            new Class<?>[]{Minecraft.class, java.util.function.Consumer.class}, minecraft, capture));
                    assertEquals(2, field(scenarioType, "badgeScaleStep", scenario));
                    set(scenarioType, "amountResumeChecksRestored", scenario, true);
                    set(scenarioType, "amountResumeSaving", scenario, false);
                    set(scenarioType, "amountResumeOpened", scenario, true);
                    set(scenarioType, "amountOptionRenderedAfter", scenario, 0L);
                    assertEquals(false, call("statusRelaunchTick",
                            new Class<?>[]{Minecraft.class, Map.class, java.util.function.Consumer.class},
                            minecraft, checks, capture));
                    assertSame(screen, minecraft.screen);
                    assertEquals(Map.of("not-ready", false), checks);
                    assertArrayEquals(before, Files.readAllBytes(output.resolve("client.toml")));
                    capture(minecraft, "native-options-wrong-screen.png");
                    passed.add("Wrong native screen preserved pending badge, scale and status restoration");
                    minecraft.setScreen(new OptionsScreen(null));
                    stage++;
                }
                case 24 -> {
                    assertInstanceOf(OptionsScreen.class, minecraft.screen);
                    var before = Files.readAllBytes(output.resolve("client.toml"));
                    var screen = minecraft.screen;
                    long deadline = (long) field(runtime, "renderedFrames", null) + 3;
                    var checks = Map.of("not-ready", false);
                    var capture = (java.util.function.Consumer<String>) name -> fail("Pending redraw captured success: " + name);
                    set(scenarioType, "badgeRenderedAfter", scenario, deadline);
                    assertEquals(false, call("badgeEditTick", new Class<?>[]{Minecraft.class, int.class}, minecraft, 0));
                    set(scenarioType, "amountOptionRenderedAfter", scenario, deadline);
                    assertEquals(false, call("statusRelaunchTick",
                            new Class<?>[]{Minecraft.class, Map.class, java.util.function.Consumer.class},
                            minecraft, checks, capture));
                    set(scenarioType, "badgeResumeStep", scenario, 1);
                    set(scenarioType, "badgeResumeRenderedAfter", scenario, deadline);
                    assertEquals(false, call("badgeRelaunchTick",
                            new Class<?>[]{Minecraft.class, Map.class, java.util.function.Consumer.class},
                            minecraft, checks, capture));
                    assertEquals(1, field(scenarioType, "badgeResumeStep", scenario));
                    assertSame(screen, minecraft.screen);
                    assertArrayEquals(before, Files.readAllBytes(output.resolve("client.toml")));
                    passed.add("Pending native redraw blocked badge edits and both relaunch captures");
                    set(scenarioType, "badgeRenderedAfter", scenario, 0L);
                    set(scenarioType, "badgeSaving", scenario, true);
                    assertEquals(false, call("badgeEditTick", new Class<?>[]{Minecraft.class, int.class}, minecraft, 0));
                    assertTrue((boolean) field(scenarioType, "badgeSaving", scenario));
                    assertSame(screen, minecraft.screen);
                    assertArrayEquals(before, Files.readAllBytes(output.resolve("client.toml")));
                    assertEquals(Map.of("not-ready", false), checks);
                    capture(minecraft, "native-options-pending-redraw.png");
                    passed.add("Pending native save blocked repeated badge edits without changing saved bytes");
                    minecraft.setScreen(new TitleScreen());
                    stage++;
                }
                case 25 -> {
                    assertEquals(24, passed.size(), "Every native Options assertion group must execute");
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

    private Object persistenceTick(Minecraft minecraft) throws Exception {
        // The actual Options branch consumes neither a world marker nor a status snapshot.
        return call("tick", new Class<?>[]{Minecraft.class,
                        Class.forName("com.ctux.ae2craftingtime.testdriver.FixtureMarker"), Map.class,
                        java.util.function.Consumer.class, java.util.function.BiConsumer.class},
                minecraft, null, Map.of(),
                (java.util.function.Consumer<String>) name -> fail("Options navigation captured status success"),
                (java.util.function.BiConsumer<Integer, Integer>) (x, y) -> fail("Options navigation moved hover mouse"));
    }

    private void reject(String name, Class<?>[] types, String message, Object... arguments) throws Exception {
        var before = Files.readAllBytes(output.resolve("client.toml"));
        var error = assertThrows(IllegalStateException.class, () -> call(name, types, arguments));
        assertEquals(message, error.getMessage());
        assertArrayEquals(before, Files.readAllBytes(output.resolve("client.toml")), "Rejected action changed saved options");
    }

    private void openGroup(Minecraft minecraft, ClientConfig config, String group) throws Exception {
        ClientOptionsRuntime.apply(config);
        minecraft.setScreen(new OptionsScreen(null));
        call("clickOptionButton", new Class<?>[]{Minecraft.class, String.class}, minecraft,
                net.minecraft.client.resources.language.I18n.get("config.ae2craftingtime.group." + group));
        set(scenarioType, "badgeResumeRenderedAfter", scenario, 0L);
    }

    private void rejectBadge(Minecraft minecraft, String message) throws Exception {
        reject("badgeRelaunchTick", new Class<?>[]{Minecraft.class, Map.class, java.util.function.Consumer.class},
                message, minecraft, Map.of(),
                (java.util.function.Consumer<String>) name -> fail("Invalid options must not capture success"));
    }

    private boolean awaitBadgeRejection(Minecraft minecraft, String message) throws Exception {
        var before = Files.readAllBytes(output.resolve("client.toml"));
        try {
            assertEquals(false, call("badgeRelaunchTick",
                    new Class<?>[]{Minecraft.class, Map.class, java.util.function.Consumer.class},
                    minecraft, Map.of(), (java.util.function.Consumer<String>) name -> fail("Invalid options captured success")));
            return false;
        } catch (IllegalStateException error) {
            assertEquals(message, error.getMessage());
            return true;
        } finally {
            assertArrayEquals(before, Files.readAllBytes(output.resolve("client.toml")), "Rejected action changed saved options");
        }
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

    static Object field(Class<?> owner, String name, Object instance) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(instance);
    }

    static void set(Class<?> owner, String name, Object instance, Object value) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true); field.set(instance, value);
    }

    private void restore() throws Exception {
        if (originalConfig == null) return;
        ClientOptionsRuntime.apply(originalConfig);
        set(ClientOptionsRuntime.class, "path", null, originalPath);
        var minecraft = Minecraft.getInstance();
        minecraft.options.guiScale().set(originalScale);
        minecraft.resizeDisplay();
    }

    private static void capture(Minecraft minecraft, String name) throws Exception {
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            image.writeToFile(Path.of(System.getProperty("ae2craftingtime.test.nativeOptionsOutput")).resolve(name));
        }
    }

    static String runtimeHash() throws Exception {
        var scenarioType = Class.forName("com.ctux.ae2craftingtime.testdriver.StandardAe2Scenario");
        try (var stream = scenarioType.getResourceAsStream("StandardAe2Scenario.class")) {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(stream.readAllBytes()));
        }
    }
}
