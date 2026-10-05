package com.ctux.ae2craftingtime.mc1201;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DispatchMetadataReuseTest {
    @Test
    void patternOutputsAreReadOnceAcrossPowerPresenceAndFinish() {
        var reads = new AtomicInteger();
        var pattern = (IPatternDetails) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { IPatternDetails.class }, (proxy, method, args) -> switch (method.getName()) {
                    case "getOutputs" -> { reads.incrementAndGet(); yield new GenericStack[0]; }
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
