package com.ctux.ae2craftingtime.integration.jei;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.Test;

class GuideMeJeiBoundaryTest {
    @Test void fallbackEndsAfterTheLastGuideMeVersionWithoutItsOwnJeiPlugin() throws Exception {
        var singleton = ModList.class.getDeclaredField("INSTANCE");
        singleton.setAccessible(true);
        var previous = singleton.get(null);
        var constructor = ModList.class.getDeclaredConstructor(List.class, List.class);
        constructor.setAccessible(true);
        var crashField = net.neoforged.fml.CrashReportCallables.class.getDeclaredField("crashCallables");
        crashField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var crashCallables = (List<net.neoforged.fml.ISystemReportExtender>) crashField.get(null);
        var previousCallables = List.copyOf(crashCallables);
        var index = ModList.class.getDeclaredField("indexedMods");
        index.setAccessible(true);
        var needsFallback = GuideMeJeiPlugin.class.getDeclaredMethod("needsLocalFallback");
        needsFallback.setAccessible(true);
        try {
            var mods = constructor.newInstance(List.of(), List.of());
            singleton.set(null, mods);
            index.set(mods, Map.of());
            assertEquals(false, needsFallback.invoke(null));
            var plugin = new GuideMeJeiPlugin();
            assertEquals("ae2craftingtime:guideme", plugin.getPluginUid().toString());
            plugin.registerItemSubtypes(rejectCalls(ISubtypeRegistration.class));
            plugin.registerExtraIngredients(rejectCalls(IExtraIngredientRegistration.class));
            for (var version : List.of("0", "26.1.12-beta", "99999")) {
                var info = (IModInfo) Proxy.newProxyInstance(IModInfo.class.getClassLoader(),
                        new Class<?>[]{IModInfo.class}, (proxy, method, arguments) -> switch (method.getName()) {
                            case "getModId" -> "guideme";
                            case "getVersion" -> new DefaultArtifactVersion(version);
                            default -> throw new AssertionError("Unexpected metadata access: " + method);
                        });
                var container = new ModContainer(info) {
                    @Override public net.neoforged.bus.api.IEventBus getEventBus() { return null; }
                };
                index.set(mods, Map.of("guideme", container));
                assertEquals(!version.equals("99999"), needsFallback.invoke(null), version);
            }
            plugin.registerItemSubtypes(rejectCalls(ISubtypeRegistration.class));
            plugin.registerExtraIngredients(rejectCalls(IExtraIngredientRegistration.class));
        } finally {
            singleton.set(null, previous);
            crashCallables.clear();
            crashCallables.addAll(previousCallables);
        }
    }

    private static <T> T rejectCalls(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, arguments) -> { throw new AssertionError("Unexpected local registration: " + method); }));
    }
}

