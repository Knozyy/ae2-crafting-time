package com.ctux.ae2craftingtime.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.ctux.ae2craftingtime.core.IntegrationCatalog;
import com.ctux.ae2craftingtime.core.IntegrationContract;
import com.ctux.ae2craftingtime.core.IntegrationSelection;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

class IntegrationBoundaryTest {
    @Test
    void pluginKeepsDefaultMixinDiscoveryAndTargetBytecodeUnchanged() {
        var plugin = new IntegrationMixinPlugin();
        var node = new ClassNode();
        node.name = "example/Target";
        var targets = new HashSet<>(Set.of("example.Target"));
        plugin.onLoad("example.mixin");
        assertNull(plugin.getRefMapperConfig());
        assertNull(plugin.getMixins());
        plugin.acceptTargets(targets, Set.of("other.Target"));
        assertEquals(Set.of("example.Target"), targets);
        plugin.preApply("example.Target", node, "example.mixin.TargetMixin", null);
        plugin.postApply("example.Target", node, "example.mixin.TargetMixin", null);
        assertEquals("example/Target", node.name);
        assertTrue(node.fields.isEmpty());
        assertTrue(node.methods.isEmpty());
        assertTrue(node.interfaces.isEmpty());
    }

    @Test
    void cpuSelectionListRetainsTheSharedRenderAndInputSeams() throws Exception {
        var node = new ClassNode();
        try (var input = getClass().getResourceAsStream("/appeng/client/gui/widgets/CPUSelectionList.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        assertTrue(node.fields.stream().anyMatch(field -> field.name.equals("buttonBg")
                && field.desc.equals("Lappeng/client/gui/style/Blitter;")));
        assertTrue(node.fields.stream().anyMatch(field -> field.name.equals("bounds")
                && field.desc.equals("Lnet/minecraft/client/renderer/Rect2i;")));

        var draw = node.methods.stream().filter(method -> method.name.equals("drawBackgroundLayer"))
                .findFirst().orElseThrow();
        var subList = java.util.stream.StreamSupport.stream(draw.instructions.spliterator(), false)
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .filter(instruction -> instruction.owner.equals("java/util/List")
                        && instruction.name.equals("subList")
                        && instruction.desc.equals("(II)Ljava/util/List;"))
                .findFirst().orElseThrow();
        var store = nextInstruction(subList);
        assertInstanceOf(VarInsnNode.class, store);
        assertEquals(Opcodes.ASTORE, store.getOpcode());
        var listLocals = draw.localVariables.stream().filter(local -> local.desc.equals("Ljava/util/List;")).toList();
        assertEquals(1, listLocals.size(), "ModifyVariable ordinal 0 must identify the post-slice list");
        assertEquals(((VarInsnNode) store).var, listLocals.get(0).index);

        assertTrue(node.methods.stream().anyMatch(method -> method.name.equals("hitTestCpu")
                && method.desc.equals("(Lappeng/client/Point;)"
                        + "Lappeng/menu/me/crafting/CraftingStatusMenu$CraftingCpuListEntry;")));
    }

    @Test
    void asmReadsAnOverlappingContractWithoutDefiningAnyClasses() {
        var classes = new HashMap<String, IntegrationContract.ClassInfo>();
        var owner = "cn/dancingsnow/neoecoae/api/me/ECOCraftingCPULogic";
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PRIVATE, "unused", "I", null, null).visitEnd();
        writer.visitMethod(Opcodes.ACC_PRIVATE, "recordPushedPattern",
                "(L" + owner + "$PendingPatternAccounting;)V", null, null).visitEnd();
        writer.visitMethod(Opcodes.ACC_PRIVATE, "recordPushedPattern",
                "(Lcn/dancingsnow/neoecoae/api/me/ExecutingCraftingJob;"
                        + "Lcn/dancingsnow/neoecoae/impl/crafting/fastpath/ECOExtractedPatternExecution;JZ)V",
                null, null).visitEnd();
        writer.visitEnd();
        classes.put(owner, IntegrationMixinPlugin.read(owner, resource -> {
            assertEquals(owner + ".class", resource);
            return new java.io.ByteArrayInputStream(writer.toByteArray());
        }));
        var selector = new IntegrationSelection(IntegrationCatalog.CANDIDATES, "1.20.1-forge", true,
                id -> "overlap-fixture", c -> IntegrationContract.check(c.contract(), classes::get), d -> {});
        assertTrue(selector.shouldApply("ECOCraftingCpuLogicMixin"));
        assertTrue(selector.shouldApply("NeoEcoLongBatchDispatchMixin"));
        assertFalse(selector.shouldApply("NeoEcoPendingDispatchMixin"));
        assertEquals(List.of("I"), classes.get(owner).members().get("field:unused"));
        assertNull(IntegrationMixinPlugin.read("absent", resource -> null));
        assertThrows(RuntimeException.class, () -> IntegrationMixinPlugin.read("malformed",
                resource -> new java.io.ByteArrayInputStream(new byte[] { 1, 2, 3 })));
        var failure = new java.io.IOException("fixture read failure");
        var thrown = assertThrows(java.io.UncheckedIOException.class, () -> IntegrationMixinPlugin.read("unreadable",
                resource -> new java.io.InputStream() {
                    @Override public int read() throws java.io.IOException { throw failure; }
                }));
        assertSame(failure, thrown.getCause());
        assertEquals("batched-long", selector.snapshot().get("neoecoae").variant());
        assertTrue(IntegrationMixinPlugin.select(selector, "example.NeoEcoLongBatchDispatchMixin"));
        var broken = new IntegrationSelection(IntegrationCatalog.CANDIDATES, "1.20.1-forge", true,
                id -> { throw new IllegalStateException("unavailable metadata"); }, c -> null, d -> {});
        var fatal = assertThrows(org.spongepowered.asm.launch.MixinInitialisationError.class,
                () -> IntegrationMixinPlugin.select(broken, "example.NeoEcoLongBatchDispatchMixin"));
        assertEquals("unavailable metadata", fatal.getCause().getMessage());
        assertTrue(broken.snapshot().isEmpty());
    }

    @Test
    void everyConfigUsesTheSamePluginAndOwnsAllOptionalHooks() throws Exception {
        var files = IntegrationPlatform.TARGET.equals("1.20.1-forge")
                ? List.of("ae2craftingtime.mixins.json", "ae2craftingtime-advancedae.mixins.json")
                : List.of("ae2craftingtime.mixins.json");
        var all = new HashSet<String>();
        var clients = new HashSet<String>();
        for (var file : files) {
            try (var input = getClass().getResourceAsStream("/" + file)) {
                var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals(IntegrationMixinPlugin.class.getName(), json.get("plugin").getAsString());
                assertFalse(json.get("required").getAsBoolean());
                assertEquals(1, json.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
                json.getAsJsonArray("mixins").forEach(m -> assertTrue(all.add(m.getAsString())));
                if (json.has("client")) json.getAsJsonArray("client").forEach(m -> {
                    assertTrue(all.add(m.getAsString()));
                    clients.add(m.getAsString());
                });
            }
        }
        assertTrue(all.contains("CraftingStatusMenuAccessor"), "production status accessor must be packaged");
        assertTrue(clients.contains("CPUSelectionListMixin"), "CPU list renderer must be packaged");
        assertFalse(clients.contains("CrazyAe2CpuListRenderMixin"),
                "CPU list compatibility must stay in the proven core mixin config");
        assertFalse(clients.contains("CrazyAe2CpuListCompatibilityMixin"),
                "CPU list compatibility must not target addon-private handlers");
        for (var candidate : IntegrationCatalog.CANDIDATES) {
            if (candidate.targets().contains(IntegrationPlatform.TARGET)) {
                assertTrue(all.containsAll(candidate.mixins()), candidate.variant());
                for (var mixin : candidate.mixins()) assertEquals(candidate.clientOnly(), clients.contains(mixin));
            }
        }
        for (var mixin : all) {
            var node = new ClassNode();
            try (var input = getClass().getResourceAsStream("/com/ctux/ae2craftingtime/mc1201/mixin/" + mixin + ".class")) {
                assertNotNull(input, mixin);
                new ClassReader(input).accept(node, ClassReader.SKIP_CODE);
            }
            boolean pseudo = node.invisibleAnnotations != null && node.invisibleAnnotations.stream()
                    .anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Pseudo;"));
            if (pseudo) assertTrue(IntegrationCatalog.CANDIDATES.stream().anyMatch(c -> c.mixins().contains(mixin)), mixin);
            if (mixin.equals("CPUSelectionListOrderMixin")) {
                var slice = node.methods.stream()
                        .filter(method -> method.name.equals("ae2craftingtime$sliceTtcFrame"))
                        .findFirst().orElseThrow();
                var modify = slice.visibleAnnotations.stream()
                        .filter(a -> a.desc.endsWith("/ModifyVariable;"))
                        .findFirst().orElseThrow();
                assertEquals(List.of("drawBackgroundLayer"), annotationValue(modify, "method"));
                assertEquals(0, annotationValue(modify, "ordinal"));
                assertEquals(1, annotationValue(modify, "require"));
                var store = (org.objectweb.asm.tree.AnnotationNode) annotationValue(modify, "at");
                assertEquals("STORE", annotationValue(store, "value"));
                var hit = node.methods.stream()
                        .filter(method -> method.name.equals("ae2craftingtime$hitTtcFrame"))
                        .findFirst().orElseThrow();
                var wrap = hit.visibleAnnotations.stream()
                        .filter(a -> a.desc.endsWith("/WrapOperation;"))
                        .findFirst().orElseThrow();
                assertEquals(List.of("getTooltip", "onMouseUp"), annotationValue(wrap, "method"));
                assertEquals(2, annotationValue(wrap, "require"));
                var head = (org.objectweb.asm.tree.AnnotationNode)
                        ((List<?>) annotationValue(wrap, "at")).get(0);
                assertEquals("INVOKE", annotationValue(head, "value"));
                assertEquals("Lappeng/client/gui/widgets/CPUSelectionList;hitTestCpu(Lappeng/client/Point;)"
                        + "Lappeng/menu/me/crafting/CraftingStatusMenu$CraftingCpuListEntry;",
                        annotationValue(head, "target"));
            }
        }
        // Config construction must be safe before loader metadata exists, including both Forge configs.
        for (int i = 0; i < files.size(); i++) {
            var plugin = new IntegrationMixinPlugin();
            plugin.onLoad("com.ctux.ae2craftingtime.mc1201.mixin");
            assertNull(plugin.getRefMapperConfig());
            assertNull(plugin.getMixins());
            plugin.acceptTargets(Set.of(), Set.of());
        }
    }

    private static Object annotationValue(org.objectweb.asm.tree.AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static AbstractInsnNode nextInstruction(AbstractInsnNode instruction) {
        var next = instruction.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        return next;
    }
}
