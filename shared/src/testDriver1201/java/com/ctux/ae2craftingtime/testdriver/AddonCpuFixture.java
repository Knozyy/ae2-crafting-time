package com.ctux.ae2craftingtime.testdriver;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.menu.me.crafting.CraftConfirmMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

abstract class AddonCpuFixture<P> {
    private static final Map<String, String> FIXTURES = Map.ofEntries(
            Map.entry("advancedae-cpu", "com.ctux.ae2craftingtime.testdriver.AdvancedAeFixture"),
            Map.entry("extendedae-cpu", "com.ctux.ae2craftingtime.testdriver.ExtendedAeFixture"),
            Map.entry("extendedae-plus-cpu", "com.ctux.ae2craftingtime.testdriver.ExtendedAePlusFixture"),
            Map.entry("bmaddon-cpu", "com.ctux.ae2craftingtime.testdriver.BmAddonFixture"),
            Map.entry("crazyae2addons-cpu", "com.ctux.ae2craftingtime.testdriver.NativeCpuFixture"),
            Map.entry("appbot-cpu", "com.ctux.ae2craftingtime.testdriver.AppliedBotanicsFixture"),
            Map.entry("appbot-fork-cpu", "com.ctux.ae2craftingtime.testdriver.AppliedBotanicsFixture"),
            Map.entry("advancedperipherals-cpu", "com.ctux.ae2craftingtime.testdriver.AdvancedPeripheralsFixture"),
            Map.entry("ae2things-cpu", "com.ctux.ae2craftingtime.testdriver.Ae2ThingsFixture"),
            Map.entry("expandedae-cpu", "com.ctux.ae2craftingtime.testdriver.ExpandedAeFixture"),
            Map.entry("megacells-cpu", "com.ctux.ae2craftingtime.testdriver.MegaCellsFixture"),
            Map.entry("neoeco-cpu", "com.ctux.ae2craftingtime.testdriver.NeoEcoFixture"),
            Map.entry("neoeco-fastpath-cpu", "com.ctux.ae2craftingtime.testdriver.NeoEcoFastPathFixture"),
            Map.entry("lightningtech-cpu", "com.ctux.ae2craftingtime.testdriver.LightningTechFixture"),
            Map.entry("omnicells-cpu", "com.ctux.ae2craftingtime.testdriver.OmniCellsFixture"),
            Map.entry("projectcell-cpu", "com.ctux.ae2craftingtime.testdriver.ProjectCellFixture"),
            Map.entry("appliede-cpu", "com.ctux.ae2craftingtime.testdriver.AppliedEFixture"),
            Map.entry("appflux-cpu", "com.ctux.ae2craftingtime.testdriver.AppliedFluxFixture"),
            Map.entry("appmek-cpu", "com.ctux.ae2craftingtime.testdriver.AppliedMekanisticsFixture"),
            Map.entry("modern-ae2-additions-cpu", "com.ctux.ae2craftingtime.testdriver.ModernAe2AdditionsFixture"),
            Map.entry("omnisequence-cpu", "com.ctux.ae2craftingtime.testdriver.OmniSequenceFixture"));

    private CompletableFuture<P> placementFuture;
    private CompletableFuture<Boolean> setupFuture;

    static boolean supports(String scenario) {
        return ("craft-plan".equals(scenario) || "chance-output-status".equals(scenario) || StandardAe2Scenario.supports(scenario)) || NoSpaceScenario.SCENARIO.equals(scenario) || NoProviderScenario.SCENARIO.equals(scenario) || NoPowerScenario.SCENARIO.equals(scenario) || ProviderDispatchStatusScenario.supports(scenario) || CraftingTreeScenario.supports(scenario) || RequesterFixture.supports(scenario)
                || Ae2NetworkAnalyserFixture.SCENARIO.equals(scenario)
                || WirelessTerminalFixture.supports(scenario)
                || FIXTURES.containsKey(scenario);
    }

    static AddonCpuFixture<?> create(String scenario) {
        var fixtureClass = FIXTURES.get(scenario);
        if (fixtureClass != null) {
            try {
                return (AddonCpuFixture<?>) Class.forName(fixtureClass).getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException | LinkageError error) {
                throw new IllegalStateException("cannot load test-driver fixture for " + scenario, error);
            }
        }
        if (("craft-plan".equals(scenario) || "chance-output-status".equals(scenario) || StandardAe2Scenario.supports(scenario)) || NoSpaceScenario.SCENARIO.equals(scenario) || NoProviderScenario.SCENARIO.equals(scenario) || NoPowerScenario.SCENARIO.equals(scenario) || ProviderDispatchStatusScenario.supports(scenario) || CraftingTreeScenario.supports(scenario) || RequesterFixture.supports(scenario)
                || Ae2NetworkAnalyserFixture.SCENARIO.equals(scenario)
                || WirelessTerminalFixture.supports(scenario)) {
            return null;
        }
        throw new IllegalArgumentException("unsupported test-driver scenario: " + scenario);
    }

    final boolean setup(Minecraft minecraft, FixtureMarker marker) {
        var server = minecraft.getSingleplayerServer();
        var playerId = minecraft.player.getUUID();
        if (placementFuture == null) {
            placementFuture = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(playerId);
                if (player == null) throw new IllegalStateException("fixture player is unavailable");
                player.teleportTo(marker.terminal().x() + 0.5, marker.terminal().y() - 1,
                        marker.terminal().z() - 2.5);
                return place(player, marker);
            });
        }
        if (!placementFuture.isDone()) {
            return false;
        }
        if (setupFuture == null) {
            setupFuture = server.submit(() -> finish(
                    server.getPlayerList().getPlayer(playerId), placementFuture.join()));
        }
        if (!setupFuture.isDone()) {
            return false;
        }
        if (!setupFuture.join()) {
            setupFuture = null;
            return false;
        }
        return true;
    }

    final ICraftingCPU cpu(ServerPlayer player, IGrid grid) {
        return cpu(player, placementFuture.join(), grid);
    }

    final String outputId(FixtureMarker marker) {
        return outputId(placementFuture.join(), marker);
    }

    protected String outputId(P placement, FixtureMarker marker) {
        return marker.outputId();
    }

    final void startCraft(ServerPlayer player, CraftConfirmMenu menu) {
        startCraft(player, placementFuture.join(), menu);
    }

    final void cleanup(ServerPlayer player) {
        if (placementFuture != null && placementFuture.isDone()) {
            cleanup(player, placementFuture.join());
        }
    }

    protected void cleanup(ServerPlayer player, P placement) {}

    protected void startCraft(ServerPlayer player, P placement, CraftConfirmMenu menu) {
        var selected = cpu(player, placement, com.ctux.ae2craftingtime.mc1201.StatsRequestContext.current(player).grid());
        if (selected == null) throw new IllegalStateException("Selected fixture CPU is no longer available");
        // Menu broadcasts can restore automatic selection between driver steps.
        ((com.ctux.ae2craftingtime.testdriver.mixin.CraftConfirmMenuAccessor) menu)
                .ae2craftingtime_test_driver$selectedCpu(selected);
        menu.startJob();
    }

    void configureAmount(appeng.client.gui.me.crafting.CraftAmountScreen screen) {}

    boolean dispatchReady(DispatchObservation.Snapshot snapshot) {
        return snapshot.finishes() > 0;
    }

    void verifyDispatch(DispatchObservation.Snapshot snapshot) {
        if (!snapshot.completedExactlyOnce()) throw new IllegalStateException("fixture dispatch/output mismatch: " + snapshot);
    }

    protected abstract P place(ServerPlayer player, FixtureMarker marker);

    protected abstract boolean finish(ServerPlayer player, P placement);

    protected abstract ICraftingCPU cpu(ServerPlayer player, P placement, IGrid grid);
}
