package com.ctux.ae2craftingtime.testdriver;

import appeng.client.gui.me.crafting.CraftingCPUScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Real provider dispatch and recipe metadata, with a controlled partial return. */
final class ChanceOutputScenario {
    static final String SCENARIO = "chance-output-status";
    static final List<String> CHECKS = DriverResult.CHANCE_OUTPUT_CHECKS;
    private static final String KEY = "text.ae2craftingtime.chance_output";
    private final ChanceOutputFixture fixture = new ChanceOutputFixture();
    private final StableFrames<Integer> frames = new StableFrames<>(3);
    private CompletableFuture<Boolean> operation;
    private int phase;
    private long phase3Started;

    boolean tick(Minecraft minecraft, FixtureMarker marker, Map<String, Boolean> checks,
            Consumer<String> screenshot, BiConsumer<Integer, Integer> moveMouse) {
        if (phase < 3) {
            if (serverStep(minecraft, player -> fixture.prepare(phase, player, marker))) phase++;
            return false;
        }
        var snapshot = UiObservationStore.latest();
        if (!(minecraft.screen instanceof CraftingCPUScreen<?> screen) || snapshot == null
                || !snapshot.screen().equals(screen.getClass().getName()) || !frames.observe(phase)) return false;
        var row = snapshot.rows().stream().filter(candidate -> candidate.outputId().equals("mekanism:sawdust"))
                .findFirst().orElse(null);
        if (phase == 3) {
            if (phase3Started == 0) phase3Started = System.nanoTime();
            var dispatched = serverStep(minecraft, fixture::dispatched);
            if (!dispatched || row == null || row.activeAmount() + row.pendingAmount() != ChanceOutputFixture.PROMISED
                    || !warning(snapshot)) {
                if (System.nanoTime() - phase3Started > 30_000_000_000L)
                    throw new IllegalStateException("Sawmill dispatch stalled: " + fixture.dispatchDiagnostic()
                            + " row=" + (row == null ? "absent" : row.activeAmount() + "/" + row.pendingAmount())
                            + " warning=" + warning(snapshot)
                            + " rowDescription=" + (row == null ? List.of() : row.description().stream()
                                    .map(text -> text.key() + ":" + text.rendered()).toList())
                            + " drawnText=" + snapshot.text().stream()
                                    .map(text -> text.key() + ":" + text.rendered()).toList());
                return false;
            }
            checks.put("real-job", true);
            checks.put("sawmill-recipe", true);
            checks.put("zero-return", true);
            screenshot.accept("chance-output-zero-return.png");
            phase++;
        } else if (phase == 4) {
            if (serverStep(minecraft, fixture::returnControlledOutput)) phase++;
        } else if (phase == 5) {
            if (row == null || row.activeAmount() + row.pendingAmount() != ChanceOutputFixture.REMAINING
                    || !warning(snapshot)) return false;
            var label = snapshot.text().stream().filter(text -> text.key().equals(KEY)).findFirst().orElseThrow();
            var badgeContainsLabel = snapshot.badges().stream().anyMatch(badge -> label.bounds().inside(badge));
            var badgeEnabled = com.ctux.ae2craftingtime.mc1201.ClientOptionsRuntime.current().badgeBackground();
            if (label.bold() || !Integer.valueOf(0xFF5555).equals(label.color())
                    || !label.bounds().inside(row.cell())
                    || badgeContainsLabel != badgeEnabled
                    || !LayoutValidator.validateBadges(snapshot).isEmpty())
                throw new IllegalStateException("Chance output badge is not normal red and contained");
            checks.put("partial-return", true);
            checks.put("chance-label", true);
            checks.put("layout", true);
            screenshot.accept("chance-output-40-outstanding.png");
            moveMouse.accept(row.cell().centerX(), row.cell().centerY());
            phase++;
        } else if (phase == 6) {
            if (snapshot.tooltip().stream().noneMatch(text -> text.key().equals(KEY + ".explanation")
                    && text.arguments().contains("50"))) return false;
            if (snapshot.tooltip().stream().noneMatch(text -> text.key().equals(KEY + ".outstanding")
                    && text.arguments().contains(Integer.toString(ChanceOutputFixture.REMAINING)))) return false;
            if (snapshot.tooltip().stream().noneMatch(text -> text.key().equals(KEY + ".suggestion"))) return false;
            checks.put("tooltip", true);
            screenshot.accept("chance-output-tooltip.png");
            if (serverStep(minecraft, player -> { fixture.cancel(player); return true; })) phase++;
        } else if (phase == 7 && !warning(snapshot)) {
            if (!checks.get("cancelled")) {
                checks.put("cancelled", true);
                screenshot.accept("chance-output-cancelled.png");
            }
            if (serverStep(minecraft, player -> { fixture.powerOneRealOperation(player); return true; })) phase++;
        } else if (phase == 8 && serverStep(minecraft, fixture::realRecipeProcessed)) {
            checks.put("real-random-recipe", true);
            return true;
        }
        return false;
    }

    private static boolean warning(UiSnapshot snapshot) {
        return snapshot.text().stream().anyMatch(text -> text.key().equals(KEY));
    }

    private boolean serverStep(Minecraft minecraft, Function<ServerPlayer, Boolean> action) {
        if (operation == null) {
            var server = minecraft.getSingleplayerServer();
            var playerId = minecraft.player.getUUID();
            operation = server.submit(() -> action.apply(server.getPlayerList().getPlayer(playerId)));
        }
        if (!operation.isDone()) return false;
        var done = operation.join();
        operation = null;
        return done;
    }
}
