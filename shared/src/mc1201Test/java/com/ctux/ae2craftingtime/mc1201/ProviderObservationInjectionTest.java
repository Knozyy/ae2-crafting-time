package com.ctux.ae2craftingtime.mc1201;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;

class ProviderObservationInjectionTest {
    private static final String ITERATOR = "Ljava/lang/Iterable;iterator()Ljava/util/Iterator;";
    private static final String MIXINS = "com/ctux/ae2craftingtime/mc1201/mixin/";

    @Test
    void dispatchMetadataIsPreparedOnceAndReused() throws IOException {
        var observer = readClass("com/ctux/ae2craftingtime/mc1201/ProviderDispatchObserver");
        assertEquals(1, calls(method(observer, "<init>")).stream()
                .filter(call -> call.name.equals("patternOutputs")).count());
        for (var name : List.of("power", "observePresence", "complete")) {
            var handler = method(observer, name);
            assertFalse(calls(handler).stream().anyMatch(call ->
                    call.name.equals("patternOutputs") || call.name.equals("getOutputs")));
            assertEquals(1, java.util.Arrays.stream(handler.instructions.toArray())
                    .filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast)
                    .filter(field -> field.name.equals("outputs")).count());
            assertEquals(1, calls(handler).stream().filter(call ->
                    call.owner.endsWith("ProfilerBridge") && call.desc.contains("Ljava/util/Map;")).count());
        }
        var candidates = method(readClass("com/ctux/ae2craftingtime/mc1201/ProviderStartTracker"), "noteCandidate");
        assertTrue(candidates.desc.contains("Ljava/util/Set;"));
        assertFalse(calls(candidates).stream().anyMatch(call -> call.name.equals("getOutputs")));
    }

    @Test
    void nativeHookObservesIterationAfterTheAddonReplaceableLookup() throws IOException {
        var execution = method(readClass("appeng/crafting/execution/CraftingCpuLogic"), "executeCrafting");
        var calls = calls(execution);
        var lookups = calls.stream().filter(call -> call.name.equals("getProviders")).toList();
        var iterators = calls.stream().filter(call -> signature(call).equals(ITERATOR)).toList();
        assertEquals(1, lookups.size());
        assertEquals(1, iterators.size(), "Only the provider loop uses Iterable.iterator");
        assertEquals(calls.indexOf(lookups.get(0)) + 1, calls.indexOf(iterators.get(0)));
        assertRedirect(method(readClass(MIXINS + "CraftingCpuLogicMixin"), "ae2craftingtime$observeProviders"),
                ITERATOR);
    }

    @Test
    void bothCpuHooksDelegateIterationBusyAndPushObservationWithoutRepeatingCalls() throws IOException {
        for (var name : List.of("CraftingCpuLogicMixin", "AdvancedCraftingCpuLogicMixin")) {
            if (name.startsWith("Advanced") && getClass().getResource("/" + MIXINS + name + ".class") == null) {
                continue;
            }
            var type = readClass(MIXINS + name);
            assertDelegates(type, "ae2craftingtime$observeProviders", ITERATOR, "iterator");
            assertDelegates(type, "ae2craftingtime$observeProviderBusy",
                    "Lappeng/api/networking/crafting/ICraftingProvider;isBusy()Z", "busy");
            assertWraps(type, "ae2craftingtime$observeProviderPush",
                    "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z",
                    "push");
        }
        var observerPush = method(readClass("com/ctux/ae2craftingtime/mc1201/ProviderDispatchObserver"), "push");
        assertEquals(1, calls(observerPush).stream().filter(call -> call.owner.endsWith("/Operation")
                && call.name.equals("call")).count());
        assertFalse(calls(observerPush).stream().anyMatch(call -> call.name.equals("pushPattern")));
    }

    @Test
    void providerHooksMatchEveryPinnedPushPatternCallSite() throws IOException {
        var target = method(readClass("appeng/helpers/patternprovider/PatternProviderLogic"), "pushPattern");
        var targetCalls = calls(target).stream().map(ProviderObservationInjectionTest::signature).toList();
        var activity = "Lappeng/api/networking/IManagedGridNode;isActive()Z";
        assertEquals(1, targetCalls.stream().filter(activity::equals).count());
        var instructions = calls(target);
        var queueCheck = instructions.stream().filter(call -> call.name.equals("isEmpty")).findFirst().orElseThrow();
        var activityCheck = instructions.stream().filter(call -> signature(call).equals(activity)).findFirst().orElseThrow();
        var lockCheck = instructions.stream().filter(call -> call.name.equals("getCraftingLockedReason")).findFirst().orElseThrow();
        assertTrue(instructions.indexOf(queueCheck) < instructions.indexOf(activityCheck));
        assertTrue(instructions.indexOf(activityCheck) < instructions.indexOf(lockCheck));
        var queueJump = assertInstanceOf(org.objectweb.asm.tree.JumpInsnNode.class, queueCheck.getNext());
        assertTrue(target.instructions.indexOf(queueJump.label) > target.instructions.indexOf(activityCheck),
                "A nonempty send queue must skip the activity guard");
        var mixin = readClass(MIXINS + "PatternProviderLogicMixin");
        var handlers = mixin.methods.stream().filter(method -> method.name.startsWith("ae2craftingtime$observe"))
                .toList();
        assertEquals(7, handlers.size());
        for (var handler : handlers) {
            if (handler.name.equals("ae2craftingtime$observeBlocking")) {
                var expression = annotation(handler, "/ModifyExpressionValue;");
                assertEquals(List.of("pushPattern"), value(expression, "method"));
                var at = (AnnotationNode) ((List<?>) value(expression, "at")).get(0);
                assertTrue(targetCalls.contains((String) value(at, "target")));
                continue;
            }
            if (handler.name.equals("ae2craftingtime$observeActivity")) {
                var wrap = annotation(handler, "/WrapOperation;");
                assertEquals(List.of("pushPattern"), value(wrap, "method"));
                var at = (AnnotationNode) ((List<?>) value(wrap, "at")).get(0);
                assertTrue(targetCalls.contains((String) value(at, "target")));
                assertEquals(1, calls(handler).stream().filter(call -> call.owner.endsWith("/Operation")
                        && call.name.equals("call")).count());
                continue;
            }
            var redirect = annotation(handler, "/Redirect;");
            assertEquals(List.of("pushPattern"), value(redirect, "method"));
            var targetSignature = (String) value((AnnotationNode) value(redirect, "at"), "target");
            var selector = MemberInfo.parse(targetSignature, null).validate();
            assertEquals(1, calls(target).stream().filter(call ->
                    selector.matches(call.owner, call.name, call.desc).isExactMatch()).count(), targetSignature);
            assertEquals(1, calls(target).stream().filter(call -> selector.matches(call.owner, call.name,
                    call.desc.replace("Lnet/minecraft/core/Direction;", "Lnet/minecraft/class_2350;"))
                    .isExactMatch()).count(), "Fabric intermediary: " + targetSignature);
            assertFalse(redirect.values.contains("require"));
        }
        assertEquals(2, mixin.methods.stream().filter(method -> method.name.startsWith("ae2craftingtime$")
                && method.visibleAnnotations != null
                && method.visibleAnnotations.stream().anyMatch(a -> a.desc.endsWith("/Invoker;"))).count());
        var external = getClass().getResource("/" + MIXINS + "PatternProviderExternalPushMixin.class");
        if (external != null) {
            var handler = method(readClass(MIXINS + "PatternProviderExternalPushMixin"),
                    "ae2craftingtime$observeExternalPush");
            var redirect = annotation(handler, "/Redirect;");
            assertTrue(targetCalls.contains((String) value((AnnotationNode) value(redirect, "at"), "target")));
            assertFalse(redirect.values.contains("require"));
        }
    }

    private static void assertDelegates(ClassNode type, String methodName, String target, String observerCall) {
        var handler = method(type, methodName);
        assertRedirect(handler, target);
        var calls = calls(handler);
        assertEquals(1, calls.stream().filter(call -> call.owner.endsWith("ProviderDispatchObserver")
                && call.name.equals(observerCall)).count());
        assertFalse(calls.stream().anyMatch(call -> call.name.equals("getProviders") || call.name.equals("hasNext")));
    }

    private static void assertWraps(ClassNode type, String methodName, String target, String observerCall) {
        var handler = method(type, methodName);
        var wrap = annotation(handler, "/WrapOperation;");
        assertEquals(List.of("executeCrafting"), value(wrap, "method"));
        var at = assertInstanceOf(List.class, value(wrap, "at"));
        assertEquals(1, at.size());
        assertEquals(target, value(assertInstanceOf(AnnotationNode.class, at.get(0)), "target"));
        var calls = calls(handler);
        assertEquals(1, calls.stream().filter(call -> call.owner.endsWith("ProviderDispatchObserver")
                && call.name.equals(observerCall)).count());
        assertEquals(1, calls.stream().filter(call -> call.owner.endsWith("/Operation")
                && call.name.equals("call")).count());
    }

    private static void assertRedirect(MethodNode handler, String target) {
        var redirect = annotation(handler, "/Redirect;");
        assertEquals(List.of("executeCrafting"), value(redirect, "method"));
        assertEquals(target, value((AnnotationNode) value(redirect, "at"), "target"));
        assertFalse(redirect.values.contains("require"));
    }

    private static AnnotationNode annotation(MethodNode handler, String suffix) {
        var redirect = handler.visibleAnnotations.stream().filter(a -> a.desc.endsWith(suffix))
                .findFirst().orElseThrow();
        return redirect;
    }

    private static Object value(AnnotationNode annotation, String key) {
        return annotation.values.get(annotation.values.indexOf(key) + 1);
    }

    private static String signature(MethodInsnNode call) {
        return "L" + call.owner + ";" + call.name + call.desc;
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        return java.util.Arrays.stream(method.instructions.toArray()).filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast).toList();
    }

    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static ClassNode readClass(String name) throws IOException {
        try (var input = ProviderObservationInjectionTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }
}
