package com.ctux.ae2craftingtime.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

class CraftingSuspensionInjectionTest {
    @Test
    void suspensionGuardRunsBeforeNeoEcoDispatchesAndCancelsAtPriority2000() throws IOException {
        var type = new ClassNode();
        try (var input = getClass().getResourceAsStream(
                "/com/ctux/ae2craftingtime/mc1201/mixin/CraftingSuspensionLogicMixin.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(type, 0);
        }
        var mixin = type.invisibleAnnotations.stream()
                .filter(annotation -> annotation.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
        assertTrue((int) value(mixin, "priority") > 2000,
                "NeoEco 20.4.2 dispatches inside its priority-2000 executeCrafting HEAD injector");
        var guard = type.methods.stream().filter(method -> method.name.equals("ae2craftingtime$pauseDispatch"))
                .findFirst().orElseThrow();
        var inject = guard.visibleAnnotations.stream()
                .filter(annotation -> annotation.desc.endsWith("/Inject;")).findFirst().orElseThrow();
        assertEquals(List.of("executeCrafting"), value(inject, "method"));
        assertEquals(true, value(inject, "cancellable"));
        var at = (AnnotationNode) ((List<?>) value(inject, "at")).get(0);
        assertEquals("HEAD", value(at, "value"));
    }

    private static Object value(AnnotationNode annotation, String key) {
        return annotation.values.get(annotation.values.indexOf(key) + 1);
    }
}
