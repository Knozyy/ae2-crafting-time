package com.ctux.ae2craftingtime.testdriver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ctux.ae2craftingtime.core.FeatureOptions;
import com.ctux.ae2craftingtime.core.OptionFeature;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class RecurrentPlanFixtureOptionsTest {
    @Test void planLabelIsAttributedToRenderedTextLikeStatusLabels() {
        var plan = com.ctux.ae2craftingtime.mc1201.TtcText.recurrent(1);
        var status = new UiSnapshot.ObservedText("text.ae2craftingtime.ttc", "TTC: 2s",
                java.util.List.of("2s"), null, 0xFFFFFF, false);
        var descriptions = java.util.Map.of("stone", java.util.List.of(status));
        var identity = new Object();
        java.util.List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        lines.add(net.minecraft.network.chat.Component.translatable("gui.ae2.ToCraft", 1));
        java.util.Map<Object, java.util.List<net.minecraft.network.chat.Component>> planDescriptions =
                java.util.Map.of(identity, lines);
        assertNull(UiObservationStore.semanticText(descriptions, planDescriptions, plan.getString()));
        lines.add(plan); // A later production RETURN injector mutates the same list.
        lines.add(com.ctux.ae2craftingtime.mc1201.TtcText.ttc("~2s"));
        var observed = UiObservationStore.planDescription(planDescriptions, identity);
        assertTrue(observed.stream().anyMatch(text -> text.key().equals("text.ae2craftingtime.plan.recurrent")));
        assertTrue(observed.stream().anyMatch(text -> text.key().equals("text.ae2craftingtime.ttc")));
        assertEquals(UiObservationStore.observed(java.util.List.of(plan), null).get(0),
                UiObservationStore.semanticText(descriptions, planDescriptions, plan.getString()));
        assertEquals(status, UiObservationStore.semanticText(descriptions, planDescriptions, "TTC: 2s"));
        assertNull(UiObservationStore.semanticText(descriptions, planDescriptions, lines.get(0).getString()));
        assertNull(UiObservationStore.semanticText(descriptions, planDescriptions, "unrelated"));
        assertEquals(java.util.List.of(), UiObservationStore.planDescription(planDescriptions, new Object()));
    }

    @Test void recurrentBadgeFollowsTheClientSwitchAndContainsTheLabel() {
        var text = new Rect(2, 2, 10, 8);
        var containing = new Rect(0, 0, 20, 12);
        var unrelated = new Rect(30, 0, 20, 12);
        assertTrue(StandardAe2Scenario.recurrentBadgeMatches(java.util.List.of(), text, false));
        assertFalse(StandardAe2Scenario.recurrentBadgeMatches(java.util.List.of(), text, true));
        assertTrue(StandardAe2Scenario.recurrentBadgeMatches(java.util.List.of(containing), text, true));
        assertFalse(StandardAe2Scenario.recurrentBadgeMatches(java.util.List.of(containing), text, false));
        assertTrue(StandardAe2Scenario.recurrentBadgeMatches(java.util.List.of(unrelated), text, false));
        assertFalse(StandardAe2Scenario.recurrentBadgeMatches(java.util.List.of(unrelated), text, true));
    }

    @Test void temporaryDetectionPreservesBothOriginalValuesAcrossRepeatedSetup() {
        for (boolean original : new boolean[] {false, true}) {
            var options = new FeatureOptions(OptionFeature.Owner.SERVER);
            options.setEnabled(OptionFeature.RECURRENT_DETECTION, original);
            options.setEnabled(OptionFeature.NOTIFY_ON_DELAYED, false);
            var originalDisabled = options.disabled();
            var fixture = new RecurrentPlanFixture(null);

            assertTrue(fixture.enableDetection(options));
            assertTrue(options.enabled(OptionFeature.RECURRENT_DETECTION));
            assertFalse(fixture.enableDetection(options));
            assertTrue(options.enabled(OptionFeature.RECURRENT_DETECTION));
            assertTrue(fixture.restoreDetection(options));
            assertEquals(original, options.enabled(OptionFeature.RECURRENT_DETECTION));
            assertEquals(originalDisabled, options.disabled());
            assertFalse(fixture.restoreDetection(options));
            fixture.close();
            fixture.close();
            options.setEnabled(OptionFeature.RECURRENT_DETECTION, !original);
            assertTrue(fixture.enableDetection(options));
            assertTrue(fixture.restoreDetection(options));
            assertEquals(!original, options.enabled(OptionFeature.RECURRENT_DETECTION));
        }

        new RecurrentPlanFixture(null).close();
    }

    @Test void adaptersRetainSetupAndCleanupDelegation() throws Exception {
        var fixture = bytecode(RecurrentPlanFixture.class);
        assertCalls(fixture, "prepare", fixture.name, "enableDetection");
        var optionsRuntime = "com/ctux/ae2craftingtime/mc1201/ServerOptionsRuntime";
        assertCalls(fixture, "prepare", optionsRuntime, "sendTo");
        assertCalls(fixture, "close", fixture.name, "restoreDetection");
        assertCalls(fixture, "close", optionsRuntime, "sendTo");
        assertCalls(fixture, "close", "appeng/api/networking/IManagedGridNode", "destroy");
        var dedicated = bytecode(DedicatedCpuScenario.class);
        assertCalls(dedicated, "stepRecurrentConnected", fixture.name, "close");
        assertCalls(dedicated, "finish", fixture.name, "close");
    }

    private static ClassNode bytecode(Class<?> type) throws Exception {
        var node = new ClassNode();
        try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    private static void assertCalls(ClassNode node, String method, String owner, String called) {
        var body = node.methods.stream().filter(candidate -> candidate.name.equals(method)).findFirst().orElseThrow();
        assertTrue(java.util.Arrays.stream(body.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .anyMatch(call -> call.owner.equals(owner) && call.name.equals(called)),
                method + " must delegate to " + owner + "." + called);
    }
}
