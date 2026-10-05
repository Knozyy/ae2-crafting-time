package com.ctux.ae2craftingtime.mc1201;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DispatchMetadataReuseTest {
    @BeforeAll
    static void loadDefaultLoaderConfig() throws ReflectiveOperationException {
        Object spec;
        try {
            spec = Ae2CraftingTimeConfig.class.getField("SPEC").get(null);
        } catch (NoSuchFieldException fabricConfig) {
            return; // Fabric's config values already contain their defaults.
        }
        var configType = Class.forName("com.electronwill.nightconfig.core.CommentedConfig");
        var config = configType.getMethod("inMemory").invoke(null);
        spec.getClass().getMethod("correct", configType).invoke(spec, config);
        var accept = Arrays.stream(spec.getClass().getMethods()).filter(method ->
                method.getName().equals("acceptConfig") && method.getParameterCount() == 1
                        && !method.isBridge()).findFirst().orElseThrow();
        var parameter = accept.getParameterTypes()[0];
        Object loaded = config;
        if (!parameter.isInstance(config)) {
            var constructor = parameter.getPermittedSubclasses()[0].getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            loaded = constructor.newInstance(config, null, null);
        }
        accept.invoke(spec, loaded);
    }

    @Test
    void patternOutputsAreReadOnceAcrossPowerPresenceAndFinish() {
        var reads = new AtomicInteger();
        var pattern = (IPatternDetails) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IPatternDetails.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "getOutputs" -> {
                        reads.incrementAndGet();
                        yield method.getReturnType().isArray() ? new GenericStack[0] : List.of();
                    }
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var scope = new Object();
        var observer = new ProviderDispatchObserver("network", null, scope, pattern, 0);
        assertEquals("network", observer.networkId());
        observer.power(10, 10);
        observer.power(10, 0);
        assertFalse(observer.iterator(List.of()).hasNext());
        observer.finish();
        assertEquals(1, reads.get());
        ProviderStartTracker.clear(scope);
    }
}
