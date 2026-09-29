package com.ctux.ae2craftingtime.core;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** The eight current warning causes eligible for an automatic provider plate. */
public final class ProviderWarningKeys {
    public static Set<ProfileKey> combine(Set<ProfileKey> delayed,
            Map<ProfileKey, CraftingBlockReason> blocked, Set<ProfileKey> noSpace) {
        var keys = new LinkedHashSet<ProfileKey>();
        if (delayed != null) keys.addAll(delayed);
        if (blocked != null) keys.addAll(blocked.keySet());
        if (noSpace != null) keys.addAll(noSpace);
        keys.remove(null);
        return Set.copyOf(keys);
    }

    private ProviderWarningKeys() {
    }
}
