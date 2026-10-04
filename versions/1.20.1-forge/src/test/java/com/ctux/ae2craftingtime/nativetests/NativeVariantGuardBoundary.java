package com.ctux.ae2craftingtime.nativetests;

import static com.ctux.ae2craftingtime.nativetests.NativeOptionsBoundaryMod.*;
import static org.junit.jupiter.api.Assertions.*;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import appeng.menu.me.crafting.CraftConfirmMenu;
import com.ctux.ae2craftingtime.mc1201.RecurrentPlanEntry;
import com.ctux.ae2craftingtime.mc1201.RecurrentPlanMenu;
import com.ctux.ae2craftingtime.testdriver.DriverOptions;
import com.ctux.ae2craftingtime.testdriver.UiObservationStore;
import com.ctux.ae2craftingtime.testdriver.UiSnapshot;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;

/** Inputs are recorded separately from actual native frames; native payload edits restore before rendering. */
final class NativeVariantGuardBoundary {
    private static final String LABEL = "text.ae2craftingtime.plan.stored_variant";
    private static final String EXPLANATION = LABEL + ".explanation";
    private static final String SUGGESTION = LABEL + ".suggestion";
    private static final String RECURRENT = "text.ae2craftingtime.plan.recurrent";
    private final boolean diagnosed;
    private final List<String> cases;
    private final ArrayList<Object> flows = new ArrayList<>();
    private final ArrayList<ArrayList<UiSnapshot>> inputs = new ArrayList<>();
    private final ArrayList<UiSnapshot> originals = new ArrayList<>();
    private final boolean[] done;
    private long started;
    private long lastFrame = -1;

    NativeVariantGuardBoundary(boolean diagnosed) {
        this.diagnosed = diagnosed;
        cases = diagnosed ? List.of("flag-absent", "label-absent", "explanation-absent", "suggestion-absent",
                "recurrence-absent", "recurrence-label-absent", "recurrence-hint-absent", "draw-absent",
                "draw-bounds-absent", "fresh-menu-pending", "fresh-summary-pending", "fresh-revision-pending",
                "lifecycle-label-pending", "lifecycle-flag-pending", "lifecycle-explanation-pending",
                "lifecycle-suggestion-pending") : List.of("summary-absent", "summary-changed", "revision-changed",
                "missing-changed", "unrelated-flag", "row-absent", "expected-flag", "label-absent",
                "network-summary-changed", "lifecycle-pending", "too-small", "resize");
        done = new boolean[cases.size()];
    }

    int caseCount() { return cases.size(); }

    boolean tick(Minecraft minecraft, Object original, Object marker, Map<?, ?> ordinaryChecks, Path output) throws Exception {
        var source = UiObservationStore.latest();
        assertInstanceOf(CraftConfirmScreen.class, minecraft.screen);
        var menu = ((CraftConfirmScreen) minecraft.screen).getMenu();
        var summary = menu.getPlan();
        if (source == null || source.frame() == lastFrame || source.tooltip().isEmpty() || summary == null) return false;
        var row = source.rows().stream().filter(r -> r.outputId().equals("minecraft:iron_pickaxe")).findFirst().orElse(null);
        if (row == null || source.tooltip().stream().anyMatch(t -> t.key().equals(EXPLANATION)) != diagnosed
                || source.tooltip().stream().anyMatch(t -> t.key().equals(SUGGESTION)) != diagnosed) return false;
        var entry = summary.getEntries().stream().filter(e -> e.getWhat().getId().toString().equals("minecraft:iron_pickaxe"))
                .findFirst().orElseThrow();
        var nativeFlags = (RecurrentPlanEntry) entry;
        assertEquals(diagnosed, nativeFlags.ae2craftingtime$storedVariant());
        var other = (RecurrentPlanEntry) summary.getEntries().stream().filter(e -> e != entry).findFirst().orElseThrow();
        var type = original.getClass();
        if (flows.isEmpty()) {
            started = System.nanoTime();
            var constructor = type.getDeclaredConstructor(String.class, String.class, Path.class, boolean.class);
            constructor.setAccessible(true);
            for (var name : cases) {
                var flow = constructor.newInstance("stored-variant-plan", DriverOptions.load().world(), output, false);
                for (var fieldName : List.of("fixture", "phase")) set(type, fieldName, flow, field(type, fieldName, original));
                set(type, "variantScaleRequested", flow, true);
                set(type, "variantHover", flow, true);
                set(type, "variantStep", flow, diagnosed ? 1 : 0);
                set(type, "variantMenu", flow, menu);
                set(type, "variantSummary", flow, summary);
                set(type, "variantRevision", flow, ((RecurrentPlanMenu) menu).ae2craftingtime$summaryRevision());
                set(type, "variantMissing", flow, entry.getMissingAmount());
                set(type, "variantAmounts", flow, summary.getEntries().stream().map(e -> List.of(
                        e.getStoredAmount(), e.getCraftAmount(), e.getMissingAmount())).toList());
                set(type, "variantButtons", flow, minecraft.screen.children().stream().filter(AbstractWidget.class::isInstance)
                        .map(AbstractWidget.class::cast).map(w -> w.active).toList());
                if (name.equals("summary-changed") || name.equals("network-summary-changed")) set(type, "variantSummary", flow, null);
                if (name.equals("revision-changed")) set(type, "variantRevision", flow, ((RecurrentPlanMenu) menu).ae2craftingtime$summaryRevision() + 1);
                if (name.equals("missing-changed")) set(type, "variantMissing", flow, entry.getMissingAmount() + 1);
                if (name.equals("expected-flag") || !diagnosed && name.equals("label-absent")) set(type, "variantStep", flow, 1);
                if (name.equals("network-summary-changed")) set(type, "variantLifecycle", flow, 1);
                if (name.equals("lifecycle-pending")) set(type, "variantLifecycle", flow, 3);
                if (name.startsWith("fresh-") || name.startsWith("lifecycle-") && diagnosed) {
                    set(type, "variantLifecycle", flow, 2);
                    set(type, "variantSecondMenu", flow, menu.containerId + (name.equals("fresh-menu-pending") ? 1 : 0));
                    set(type, "variantSecondSummary", flow, name.equals("fresh-summary-pending") ? summary : null);
                    set(type, "variantSecondRevision", flow, ((RecurrentPlanMenu) menu).ae2craftingtime$summaryRevision());
                }
                flows.add(flow);
                inputs.add(new ArrayList<>());
            }
        }
        assertTrue(System.nanoTime() - started < 30_000_000_000L, "Variant guard checkpoint exceeded thirty seconds");
        lastFrame = source.frame();
        var method = type.getDeclaredMethod("tick", Minecraft.class, marker.getClass(), Map.class,
                java.util.function.Consumer.class, java.util.function.BiConsumer.class);
        method.setAccessible(true);
        var checksBefore = Map.copyOf(ordinaryChecks);
        var config = minecraft.gameDirectory.toPath().resolve("config/ae2craftingtime-client.toml");
        var saved = Files.readAllBytes(config);
        var screen = minecraft.screen;
        int originalScale = minecraft.options.guiScale().get();
        boolean originalVariant = nativeFlags.ae2craftingtime$storedVariant();
        boolean originalRecurrent = nativeFlags.ae2craftingtime$recurrent();
        boolean originalOther = other.ae2craftingtime$storedVariant();
        try {
            for (int i = 0; i < cases.size(); i++) {
                if (done[i]) continue;
                var name = cases.get(i);
                var flow = flows.get(i);
                var input = input(source, name);
                set(UiObservationStore.class, "latest", null, input);
                if (name.equals("summary-absent")) set(CraftConfirmMenu.class, "plan", menu, null);
                if (name.equals("unrelated-flag")) other.ae2craftingtime$storedVariant(true);
                if (name.equals("label-absent") && !diagnosed) nativeFlags.ae2craftingtime$storedVariant(true);
                if (name.equals("flag-absent") || name.equals("lifecycle-flag-pending")) nativeFlags.ae2craftingtime$storedVariant(false);
                if (name.equals("recurrence-absent")) nativeFlags.ae2craftingtime$recurrent(false);
                var checks = new LinkedHashMap<String, Boolean>();
                for (var key : List.of("fluid-clear", "ordinary-clear", "coexistence", "variant-layout", "native-replan", "network-switch")) checks.put(key, false);
                inputs.get(i).add(input);
                var expectedError = switch (name) {
                    case "summary-changed", "revision-changed", "missing-changed" -> "Stored-variant transition replaced the native plan";
                    case "unrelated-flag" -> "Unrelated row received a stored-variant diagnosis";
                    case "network-summary-changed" -> "Network switch replaced the retained native summary";
                    case "too-small" -> "Variant plan cannot fit native screen";
                    default -> "";
                };
                try {
                    assertEquals(false, method.invoke(flow, minecraft, marker, checks,
                            (java.util.function.Consumer<String>) capture -> fail("Invalid variant input captured success: " + capture),
                            (java.util.function.BiConsumer<Integer, Integer>) (x, y) -> fail("Invalid variant input moved the mouse")));
                    var stability = field(type, "frames", flow);
                    int count = (int) field(stability.getClass(), "count", stability);
                    int required = (int) field(stability.getClass(), "required", stability);
                    if (count == 1 && inputs.get(i).size() > 1) inputs.get(i).subList(0, inputs.get(i).size() - 1).clear();
                    if (name.equals("resize") && minecraft.options.guiScale().get() != originalScale) {
                        assertEquals(1, minecraft.options.guiScale().get());
                        done[i] = true;
                    } else if (count >= required) {
                        assertEquals("", expectedError, "Expected rejection did not occur: " + name);
                        done[i] = true;
                    }
                } catch (InvocationTargetException error) {
                    assertInstanceOf(IllegalStateException.class, error.getCause());
                    assertFalse(expectedError.isEmpty(), "Unexpected guard exception: " + name);
                    assertEquals(expectedError, error.getCause().getMessage());
                    done[i] = true;
                } finally {
                    set(CraftConfirmMenu.class, "plan", menu, summary);
                    nativeFlags.ae2craftingtime$storedVariant(originalVariant);
                    nativeFlags.ae2craftingtime$recurrent(originalRecurrent);
                    other.ae2craftingtime$storedVariant(originalOther);
                    set(UiObservationStore.class, "latest", null, source);
                    if (minecraft.options.guiScale().get() != originalScale) {
                        minecraft.options.guiScale().set(originalScale);
                        minecraft.resizeDisplay();
                    }
                }
                assertFalse(checks.get("variant-layout") || checks.get("native-replan") || checks.get("network-switch"));
                assertNull(field(type, "operation", flow));
                assertEquals("PLAN_SORT", field(type, "phase", flow).toString());
                assertEquals(-1, field(type, "variantPendingStep", flow));
                assertSame(screen, minecraft.screen);
                assertSame(menu, minecraft.player.containerMenu);
                assertSame(summary, menu.getPlan());
                assertEquals(checksBefore, ordinaryChecks);
                assertArrayEquals(saved, Files.readAllBytes(config));
                if (done[i]) assertTrue(inputs.get(i).size() >= 8, "Guard did not consume eight native source frames: " + name);
            }
        } finally {
            set(UiObservationStore.class, "latest", null, source);
        }
        originals.add(source);
        for (boolean value : done) if (!value) return false;
        var prefix = diagnosed ? "variant-diagnosed-guards" : "variant-clear-guards";
        Files.writeString(output.resolve(prefix + "-inputs.json"), new com.google.gson.Gson().toJson(Map.of(
                "scope", "native frames and separate invalid observation DTOs; native payload edits restored before rendering",
                "cases", cases, "originals", originals, "inputs", inputs, "savedBytesUnchanged", true,
                "nativeReferencesRestored", true, "ordinaryChecksUnchanged", true)));
        try (var image = net.minecraft.client.Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            image.writeToFile(output.resolve(prefix + ".png"));
        }
        return true;
    }

    private UiSnapshot input(UiSnapshot source, String name) {
        var rows = source.rows().stream().filter(r -> !name.equals("row-absent") || !r.outputId().equals("minecraft:iron_pickaxe"))
                .map(r -> new UiSnapshot.Row(r.outputId(), r.craftAmount(), r.missingAmount(), r.cell(), r.description().stream()
                        .filter(t -> !(name.equals("label-absent") || name.equals("lifecycle-label-pending")) || !t.key().equals(LABEL))
                        .filter(t -> !name.equals("recurrence-label-absent") || !t.key().equals(RECURRENT)).toList(),
                        r.storedAmount(), r.activeAmount(), r.pendingAmount())).toList();
        var tooltip = source.tooltip().stream()
                .filter(t -> !(name.equals("explanation-absent") || name.equals("lifecycle-explanation-pending")) || !t.key().equals(EXPLANATION))
                .filter(t -> !(name.equals("suggestion-absent") || name.equals("lifecycle-suggestion-pending")) || !t.key().equals(SUGGESTION))
                .filter(t -> !name.equals("recurrence-hint-absent") || !t.key().equals(RECURRENT + "_hint")).toList();
        var text = source.text().stream().filter(t -> !name.equals("draw-absent") || !t.key().equals(LABEL))
                .map(t -> name.equals("draw-bounds-absent") && t.key().equals(LABEL)
                        ? new UiSnapshot.ObservedText(t.key(), t.rendered(), t.arguments(), null, t.color(), t.bold()) : t).toList();
        boolean tooSmall = name.equals("too-small") || name.equals("resize");
        return new UiSnapshot(source.screen(), source.menu(), source.gui(), tooSmall ? 1 : source.screenWidth(),
                tooSmall ? 1 : source.screenHeight(), name.equals("too-small") ? 1 : name.equals("resize") ? 2 : source.guiScale(),
                source.frame(), source.scroll(), rows, text, source.badges(), source.widgets(), source.itemCells(), tooltip,
                source.cpuCards(), source.rawCpuSerials());
    }
}
