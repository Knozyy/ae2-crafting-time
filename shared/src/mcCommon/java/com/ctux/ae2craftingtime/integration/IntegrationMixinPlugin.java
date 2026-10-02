package com.ctux.ae2craftingtime.integration;

import com.ctux.ae2craftingtime.core.IntegrationCatalog;
import com.ctux.ae2craftingtime.core.IntegrationContract;
import com.ctux.ae2craftingtime.core.IntegrationSelection;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.launch.MixinInitialisationError;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Lives outside Mixin's reserved package; shared by every config in this process. */
public final class IntegrationMixinPlugin implements IMixinConfigPlugin {
    private static final class Startup {
        private Startup() {}

        private static final IntegrationSelection SELECTION = new IntegrationSelection(IntegrationCatalog.CANDIDATES,
                IntegrationPlatform.TARGET, IntegrationPlatform.isClient(), IntegrationPlatform::version,
                candidate -> IntegrationContract.check(candidate.contract(), IntegrationMixinPlugin::bytecode),
                decision -> logDecision(decision, LoggerFactory.getLogger("ae2craftingtime/integrations")));
    }

    static void logDecision(IntegrationSelection.Decision decision, Logger logger) {
        var message = "dependency={} version={} adapter={} reason={} rejected={} (selection only)";
        if (decision.reason().equals("no_compatible_variant")) {
            logger.warn(message, decision.dependency(), decision.installedVersion(), decision.variant(),
                    decision.reason(), decision.rejected());
        } else {
            logger.info(message, decision.dependency(), decision.installedVersion(), decision.variant(),
                    decision.reason(), decision.rejected());
        }
    }

    public static Map<String, IntegrationSelection.Decision> snapshot() {
        return Startup.SELECTION.snapshot();
    }

    static IntegrationContract.ClassInfo bytecode(String internalName) {
        // ModLauncher rejects getClassNode(name, false); resource reads also avoid transformations.
        return read(internalName, MixinService.getService()::getResourceAsStream);
    }

    static IntegrationContract.ClassInfo read(String internalName, Function<String, InputStream> resources) {
        var input = resources.apply(internalName + ".class");
        if (input == null) return null;
        try (input) {
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return describe(node);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot inspect adapter target " + internalName, failure);
        }
    }

    static IntegrationContract.ClassInfo describe(ClassNode node) {
        var members = new HashMap<String, List<String>>();
        node.fields.forEach(field -> members.computeIfAbsent("field:" + field.name, key -> new ArrayList<>())
                .add(field.desc));
        node.methods.forEach(method -> members.computeIfAbsent("method:" + method.name, key -> new ArrayList<>())
                .add(method.desc));
        return new IntegrationContract.ClassInfo(node.superName, members);
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return select(Startup.SELECTION, mixinClassName);
    }

    static boolean select(IntegrationSelection selection, String mixinClassName) {
        try {
            return selection.shouldApply(mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1));
        } catch (RuntimeException failure) {
            // Optional configs log-and-skip Exceptions. Bootstrap faults must instead stop startup.
            throw new MixinInitialisationError("Cannot select integration for " + mixinClassName, failure);
        }
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
