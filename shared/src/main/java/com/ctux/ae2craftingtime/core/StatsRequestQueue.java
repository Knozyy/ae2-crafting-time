package com.ctux.ae2craftingtime.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded FIFO batches, with a reserved background share so sorting cannot starve. */
public final class StatsRequestQueue {
    public static final int MAX_PENDING = 4096;
    private final LinkedHashSet<ProfileKey> visible = new LinkedHashSet<>();
    private final LinkedHashSet<ProfileKey> background = new LinkedHashSet<>();
    private final Map<ProfileKey, Long> sent = new HashMap<>();
    private long nextBatch = Long.MIN_VALUE;
    private Object context;
    private long cpuContext;

    public boolean context(Object context, long cpuContext) {
        if (!Objects.equals(this.context, context) || this.cpuContext != cpuContext) {
            clear();
            this.context = context;
            this.cpuContext = cpuContext;
            return true;
        }
        return false;
    }

    public void request(ProfileKey key, boolean priority, long now) {
        var last = sent.get(key);
        if (last != null && now - last < 1000) return;
        if (visible.contains(key)) return;
        if (background.contains(key)) {
            if (priority) { background.remove(key); visible.add(key); }
            return;
        }
        if (visible.size() + background.size() >= MAX_PENDING) {
            if (!priority || background.isEmpty()) return;
            // A full-plan scan must not exclude newly visible rows.
            var oldest = background.iterator();
            oldest.next();
            oldest.remove();
        }
        (priority ? visible : background).add(key);
    }

    public List<ProfileKey> drain(long now) {
        if (now < nextBatch) return List.of();
        var batch = new ArrayList<ProfileKey>();
        take(visible, batch, 192);
        take(background, batch, PacketLimits.MAX_KEYS);
        take(visible, batch, PacketLimits.MAX_KEYS);
        if (!batch.isEmpty()) {
            sent.entrySet().removeIf(entry -> now - entry.getValue() >= 1000);
            for (var key : batch) sent.put(key, now);
            nextBatch = now + 500;
        }
        return List.copyOf(batch);
    }

    private static void take(LinkedHashSet<ProfileKey> source, List<ProfileKey> batch, int limit) {
        var iterator = source.iterator();
        while (batch.size() < limit && iterator.hasNext()) {
            batch.add(iterator.next());
            iterator.remove();
        }
    }

    public void clear() {
        visible.clear();
        background.clear();
        sent.clear();
        context = null;
        // Screen/CPU changes must not bypass the connection's send interval.
    }
}
