package com.ctux.ae2craftingtime.mc1201;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

class CraftingCpuTitleInjectionTest {
    @Test
    void titleHookReplacesActiveTitleAndPreservesFallbackNamesAndWarnings() throws IOException {
        var mixin = readClass("com/ctux/ae2craftingtime/mc1201/mixin/CraftingCPUScreenMixin");
        var handler = mixin.methods.stream()
                .filter(method -> method.name.equals("ae2craftingtime$appendStatusTotalTtc"))
                .findFirst().orElseThrow();
        var instructions = Arrays.asList(handler.instructions.toArray());
        assertFalse(mixin.methods.stream().anyMatch(method -> method.name.equals("ae2craftingtime$drawTitleTtcBadge")));
        var calls = instructions.stream().filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).toList();

        assertFalse(instructions.stream().filter(TypeInsnNode.class::isInstance)
                .map(TypeInsnNode.class::cast)
                .anyMatch(instruction -> instruction.getOpcode() == Opcodes.INSTANCEOF
                        && instruction.desc.endsWith("/CraftingStatusScreen")));
        assertEquals(0, calls.stream().filter(call -> call.name.equals("hasMeasuredProgress")).count());
        assertEquals(1, calls.stream().filter(call -> call.name.equals("getGuiDisplayName")).count());
        assertEquals(1, calls.stream().filter(call -> call.name.equals("totalTtc")).count());
        assertEquals(1, calls.stream().filter(call -> call.name.equals("rowSymbolsInTextColor")).count());
        assertEquals(1, calls.stream().filter(call -> call.name.equals("isCantStoreItems")).count());
        assertEquals(1, calls.stream().filter(call -> call.name.equals("copy")).count());
        assertEquals(2, calls.stream().filter(call -> call.name.equals("append")).count());
        assertEquals(1, instructions.stream().filter(FieldInsnNode.class::isInstance)
                .map(FieldInsnNode.class::cast).filter(field -> field.name.equals("CantStoreItems")).count());
        assertEquals(1, instructions.stream().filter(FieldInsnNode.class::isInstance)
                .map(FieldInsnNode.class::cast).filter(field -> field.name.equals("RED")).count());
    }

    private static ClassNode readClass(String name) throws IOException {
        try (var input = CraftingCpuTitleInjectionTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }
}
