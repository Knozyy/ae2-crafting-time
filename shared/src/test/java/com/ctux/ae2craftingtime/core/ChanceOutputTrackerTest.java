package com.ctux.ae2craftingtime.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChanceOutputTrackerTest {
    @Test
    void rejectsUnknownInputsAndInvalidProbabilities() {
        var tracker = new ChanceOutputTracker();
        var cpu = new Object();
        var output = new ProfileKey("grid", "mekanism:sawdust");
        tracker.observe(cpu, Set.of(output), Map.of(output, 5000));
        assertTrue(tracker.chance(cpu, output).isEmpty());
        tracker.plan(cpu, java.util.Arrays.asList(null, Set.of(),
                new java.util.HashSet<>(java.util.Arrays.asList(null, output))));
        tracker.observe(cpu, Set.of(output), Map.of(output, 0));
        assertTrue(tracker.chance(cpu, output).isEmpty());
        tracker.plan(cpu, java.util.List.of(Set.of(output)));
        tracker.observe(cpu, Set.of(output), Map.of(output, 10000));
        assertTrue(tracker.chance(cpu, output).isEmpty());
        tracker.plan(cpu, java.util.List.of(Set.of(output)));
        tracker.observe(cpu, Set.of(output), Map.of(output, -1));
        assertTrue(tracker.chance(cpu, output).isEmpty());
        tracker.plan(cpu, java.util.List.of(Set.of(output)));
        tracker.observe(cpu, new java.util.HashSet<>(java.util.Arrays.asList(null, output)), Map.of(output, 1));
        assertEquals(1, tracker.chance(cpu, output).orElseThrow());
        tracker.plan(cpu, java.util.List.of(Set.of(new ProfileKey("grid", "minecraft:plank"))));
        tracker.observe(cpu, Set.of(output), Map.of(output, 5000));
        assertTrue(tracker.chance(cpu, output).isEmpty());
    }

    @Test
    void confirmsOnlyConsistentLiveEvidenceWithinOneCpu() {
        var tracker = new ChanceOutputTracker();
        var cpu = new Object();
        var otherCpu = new Object();
        var output = new ProfileKey("network", "mekanism:sawdust");
        var guaranteed = new ProfileKey("network", "minecraft:oak_planks");
        assertTrue(tracker.chance(cpu, output).isEmpty());
        tracker.plan(null, null);
        tracker.plan(cpu, null);
        tracker.plan(cpu, java.util.List.of(Set.of(output, guaranteed)));
        tracker.observe(null, Set.of(output), Map.of(output, 5000));
        tracker.observe(cpu, null, null);
        tracker.observe(cpu, Set.of(), null);
        assertTrue(tracker.chance(cpu, output).isEmpty());
        tracker.observe(cpu, Set.of(output, guaranteed), Map.of(output, 5000));
        assertEquals(5000, tracker.chance(cpu, output).orElseThrow());
        assertTrue(tracker.chance(cpu, guaranteed).isEmpty());
        assertTrue(tracker.chance(otherCpu, output).isEmpty());
        tracker.plan(otherCpu, java.util.List.of(Set.of(output), Set.of(output)));
        tracker.observe(otherCpu, Set.of(output), Map.of(output, 5000));
        assertTrue(tracker.chance(otherCpu, output).isEmpty());
        tracker.observe(cpu, Set.of(output), Map.of(output, 5000));
        assertEquals(5000, tracker.chance(cpu, output).orElseThrow());
        tracker.observe(cpu, Set.of(output), Map.of(output, 6000));
        assertTrue(tracker.chance(cpu, output).isEmpty());
        tracker.observe(cpu, Set.of(output), Map.of(output, 5000));
        assertTrue(tracker.chance(cpu, output).isEmpty());
        tracker.clear(cpu);
        tracker.plan(cpu, java.util.List.of(Set.of(output)));
        tracker.observe(cpu, Set.of(output), Map.of(output, 5000));
        assertEquals(5000, tracker.chance(cpu, output).orElseThrow());
        tracker.observe(cpu, Set.of(output), null);
        assertTrue(tracker.chance(cpu, output).isEmpty());
        tracker.clearAll();
        assertTrue(tracker.chance(cpu, output).isEmpty());
    }
}
