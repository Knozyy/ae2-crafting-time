package com.ctux.ae2craftingtime.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Indexed immutable histories; serializers receive a stable list, never the mutable map. */
public final class SavedSampleHistory {
    private final Map<ProfileKey, PersistedOutputSamples> outputs = new LinkedHashMap<>();

    public synchronized void replace(List<PersistedOutputSamples> samples) {
        outputs.clear();
        update(samples);
    }

    public synchronized void update(List<PersistedOutputSamples> changes) {
        for (var output : changes) {
            if (output.samples().isEmpty()) outputs.remove(output.key());
            else outputs.put(output.key(), output);
        }
    }

    public synchronized List<PersistedOutputSamples> snapshot() {
        return List.copyOf(outputs.values());
    }
}
