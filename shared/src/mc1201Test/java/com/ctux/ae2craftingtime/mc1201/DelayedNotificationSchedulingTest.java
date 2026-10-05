package com.ctux.ae2craftingtime.mc1201;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class DelayedNotificationSchedulingTest {
    @Test
    void cpuTickCollectsEachLiveProbeOnceAndReconciliationDoesNotProbeAgain() throws IOException {
        var notifications = read(DelayedNotificationServer.class);
        var tick = notifications.methods.stream().filter(m -> m.name.equals("tick")).findFirst().orElseThrow();
        var calls = Arrays.stream(tick.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).toList();
        for (var probe : List.of("delayedDiagnostics", "liveBlockReasons", "spaceKeys")) {
            assertEquals(1, calls.stream().filter(call -> call.name.equals(probe)).count(), probe);
        }
        var reconcile = notifications.methods.stream().filter(m -> m.name.equals("reconcile")
                && (m.access & org.objectweb.asm.Opcodes.ACC_PRIVATE) != 0).findFirst().orElseThrow();
        assertFalse(Arrays.stream(reconcile.instructions.toArray()).anyMatch(insn ->
                insn instanceof MethodInsnNode call && List.of("liveDelayedKeys", "liveBlockReasons",
                        "stuckKeys", "spaceKeys").contains(call.name)));
    }

    @Test
    void cpuUpdatesNeverFlushGlobalStateAndEveryLoaderRegistersTheFlush() throws IOException {
        var notifications = read(DelayedNotificationServer.class);
        for (var name : List.of("reconcile", "clearScope", "clearKey")) {
            var method = notifications.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
            assertFalse(Arrays.stream(method.instructions.toArray()).anyMatch(insn ->
                    insn instanceof MethodInsnNode call && call.name.equals("sync")));
        }
        var entrypoint = read(Ae2CraftingTime.class);
        assertTrue(entrypoint.methods.stream().flatMap(m -> Arrays.stream(m.instructions.toArray())).anyMatch(insn ->
                insn instanceof MethodInsnNode call && call.owner.endsWith("/DelayedNotificationServer")
                        && call.name.equals("flush")));
    }

    private static ClassNode read(Class<?> type) throws IOException {
        var node = new ClassNode();
        try (var bytes = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            new ClassReader(bytes).accept(node, 0);
        }
        return node;
    }
}
