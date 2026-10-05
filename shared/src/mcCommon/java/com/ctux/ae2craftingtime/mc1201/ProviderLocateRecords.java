package com.ctux.ae2craftingtime.mc1201;

import appeng.api.stacks.AEKey;
import com.ctux.ae2craftingtime.core.ProfileKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/**
 * Server-side only. Click-scoped locate records plus the per-output fallback
 * (job owner, network, dimension, output, provider positions, display name)
 * that survives a world reload. Live dispatch data always wins; the persisted
 * copy only fills gaps after a reload.
 */
public final class ProviderLocateRecords {
    public record LocateRecord(UUID id, UUID owner, String dimensionId, List<BlockPos> positions,
            String outputName, String outputId, long createdTick, String networkId) {
        public LocateRecord {
            networkId = networkId == null ? "" : networkId;
            dimensionId = dimensionId == null ? "" : dimensionId;
            positions = positions == null ? List.of() : List.copyOf(positions);
            outputId = outputId == null ? "" : outputId;
        }
        public LocateRecord(UUID id, UUID owner, String dimensionId, List<BlockPos> positions,
                String outputName, String outputId, long createdTick) {
            this(id, owner, dimensionId, positions, outputName, outputId, createdTick, "");
        }
    }

    public record ProviderStartInfo(UUID owner, String dimensionId, List<BlockPos> positions, String outputName,
            AEKey displayKey) {
        public ProviderStartInfo {
            dimensionId = dimensionId == null ? "" : dimensionId;
            positions = positions == null ? List.of() : List.copyOf(positions);
        }

        public ProviderStartInfo(UUID owner, List<BlockPos> positions, String outputName) {
            this(owner, "", positions, outputName, null);
        }

        public ProviderStartInfo(UUID owner, String dimensionId, List<BlockPos> positions, String outputName) {
            this(owner, dimensionId, positions, outputName, null);
        }
    }

    public record StoredStart(ProfileKey key, UUID owner, String dimensionId, List<BlockPos> positions,
            String outputName, AEKey displayKey) {
        public StoredStart {
            dimensionId = dimensionId == null ? "" : dimensionId;
            positions = positions == null ? List.of() : List.copyOf(positions);
        }

        public StoredStart(ProfileKey key, UUID owner, List<BlockPos> positions, String outputName) {
            this(key, owner, "", positions, outputName, null);
        }

        public StoredStart(ProfileKey key, UUID owner, String dimensionId, List<BlockPos> positions,
                String outputName) {
            this(key, owner, dimensionId, positions, outputName, null);
        }
    }

    private static boolean dirty = true;

    public static synchronized boolean takeDirty() {
        var changed = dirty;
        dirty = false;
        return changed;
    }

    private static final int MAX_RECORDS = 256;
    private static final int MAX_STARTS = 512;
    private record StartIdentity(ProfileKey key, UUID owner) {
    }
    private static final LinkedHashMap<UUID, LocateRecord> RECORDS = new LinkedHashMap<>();
    private static final LinkedHashMap<StartIdentity, ProviderStartInfo> STARTS = new LinkedHashMap<>();

    public static synchronized LocateRecord create(UUID owner, String dimensionId, List<BlockPos> positions,
            String outputName, String outputId, long tick) {
        dirty = true;
        return create(owner, dimensionId, positions, outputName, outputId, tick, "");
    }

    public static synchronized LocateRecord create(UUID owner, String dimensionId, List<BlockPos> positions,
            String outputName, String outputId, long tick, String networkId) {
        dirty = true;
        var record = new LocateRecord(UUID.randomUUID(), owner, dimensionId, positions == null ? List.of()
                : List.copyOf(positions), outputName, outputId == null ? "" : outputId, tick,
                networkId == null ? "" : networkId);
        RECORDS.put(record.id(), record);
        evictEldest(RECORDS, MAX_RECORDS);
        return record;
    }

    public static synchronized Optional<LocateRecord> ownedBy(UUID owner, UUID id) {
        if (owner == null || id == null) {
            return Optional.empty();
        }
        var record = RECORDS.get(id);
        return record != null && record.owner().equals(owner) ? Optional.of(record) : Optional.empty();
    }

    /**
     * Records who started an output and where its providers were last seen.
     * Empty positions or a missing owner never erase a previously stored
     * entry; only strictly newer information replaces it. Dimension is merged
     * the same way. The profile key and owner jointly identify a start, so
     * separate players with an identical output on one grid keep their own
     * targets through save and reload.
     */
    public static synchronized void noteStart(ProfileKey key, UUID owner, List<BlockPos> positions,
            String outputName) {
        dirty = true;
        noteStart(key, owner, "", positions, outputName);
    }

    public static synchronized void noteStart(ProfileKey key, UUID owner, String dimensionId,
            List<BlockPos> positions, String outputName) {
        dirty = true;
        noteStart(key, owner, dimensionId, positions, outputName, null);
    }

    public static synchronized void noteStart(ProfileKey key, UUID owner, String dimensionId,
            List<BlockPos> positions, String outputName, AEKey displayKey) {
        dirty = true;
        if (key == null || owner == null) {
            return;
        }
        var identity = new StartIdentity(key, owner);
        var previous = STARTS.get(identity);
        var mergedDimension = dimensionId != null && !dimensionId.isBlank() ? dimensionId
                : previous == null ? "" : previous.dimensionId();
        List<BlockPos> mergedPositions;
        if (positions != null && !positions.isEmpty()) {
            mergedPositions = List.copyOf(positions);
        } else if (previous == null) {
            mergedPositions = List.of();
        } else {
            mergedPositions = previous.positions();
        }
        var mergedName = outputName != null && !outputName.isBlank() ? outputName
                : previous == null ? key.outputId() : previous.outputName();
        STARTS.put(identity, new ProviderStartInfo(owner, mergedDimension, mergedPositions, mergedName,
                displayKey != null ? displayKey : previous == null ? null : previous.displayKey()));
        evictEldest(STARTS, MAX_STARTS);
    }

    public static synchronized Optional<ProviderStartInfo> startFor(ProfileKey key) {
        if (key == null) return Optional.empty();
        ProviderStartInfo found = null;
        for (var entry : STARTS.entrySet()) {
            if (!key.equals(entry.getKey().key())) continue;
            if (found != null) return Optional.empty();
            found = entry.getValue();
        }
        return Optional.ofNullable(found);
    }

    public static synchronized Optional<ProviderStartInfo> startFor(ProfileKey key, UUID owner) {
        return key == null || owner == null ? Optional.empty()
                : Optional.ofNullable(STARTS.get(new StartIdentity(key, owner)));
    }

    /**
     * Overwrites one output's fallback with freshly resolved notify-time data,
     * even when the fresh positions are empty: an empty resolution means "no
     * locatable target right now", not "keep showing an old box". The stored
     * dimension travels with the fallback so resync never has to re-derive it
     * from the network id alone.
     */
    public static synchronized void replaceStart(ProfileKey key, UUID owner, List<BlockPos> positions,
            String outputName) {
        dirty = true;
        if (key == null || owner == null) {
            return;
        }
        var identity = new StartIdentity(key, owner);
        var previous = STARTS.get(identity);
        var keptDimension = previous == null ? "" : previous.dimensionId();
        STARTS.put(identity, new ProviderStartInfo(owner,
                keptDimension,
                positions == null ? List.of() : List.copyOf(positions),
                outputName == null || outputName.isBlank() ? key.outputId() : outputName,
                previous == null ? null : previous.displayKey()));
        if (positions == null || positions.isEmpty()) removeRecordsForKeys(List.of(key), owner);
        evictEldest(STARTS, MAX_STARTS);
    }

    public static synchronized void replaceStart(ProfileKey key, UUID owner, String dimensionId,
            List<BlockPos> positions, String outputName) {
        dirty = true;
        replaceStart(key, owner, dimensionId, positions, outputName, null);
    }

    public static synchronized void replaceStart(ProfileKey key, UUID owner, String dimensionId,
            List<BlockPos> positions, String outputName, AEKey displayKey) {
        dirty = true;
        if (key == null || owner == null) {
            return;
        }
        STARTS.put(new StartIdentity(key, owner), new ProviderStartInfo(owner,
                dimensionId == null ? "" : dimensionId,
                positions == null ? List.of() : List.copyOf(positions),
                outputName == null || outputName.isBlank() ? key.outputId() : outputName, displayKey));
        if (positions == null || positions.isEmpty()) removeRecordsForKeys(List.of(key), owner);
        evictEldest(STARTS, MAX_STARTS);
    }

    /**
     * Snapshot for world save. Empty starts are included only while an owned
     * network-specific chat record needs its captured blocked-warning target.
     * Invalidated empty starts have already removed their click records.
     * Packet bounds still apply to positions
     * per entry, but active fallbacks are never silently dropped to fit a cap:
     * the cap only bounds persistence size.
     */
    public static synchronized List<StoredStart> snapshotStarts() {
        var clicks = new java.util.HashSet<StartIdentity>();
        for (var record : RECORDS.values()) {
            if (record != null && record.owner() != null && !record.outputId().isBlank())
                clicks.add(new StartIdentity(new ProfileKey(record.networkId(), record.outputId()), record.owner()));
        }
        var snapshot = new ArrayList<StoredStart>();
        for (var entry : STARTS.entrySet()) {
            var info = entry.getValue();
            if (info.owner() != null && info.positions() != null
                    && (!info.positions().isEmpty() || clicks.contains(entry.getKey()))) {
                snapshot.add(new StoredStart(entry.getKey().key(), info.owner(), info.dimensionId(), info.positions(),
                        info.outputName(), info.displayKey()));
            }
            if (snapshot.size() >= MAX_STARTS) {
                break;
            }
        }
        return List.copyOf(snapshot);
    }

    /**
     * Forgets finished, cancelled, or broken outputs so they never return
     * after a reload. Called on job finish/cancel for the scope's keys and on
     * login resync when server-side validation finds all positions broken.
     */
    public static synchronized void removeStarts(java.util.Collection<com.ctux.ae2craftingtime.core.ProfileKey> keys) {
        dirty = true;
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (var key : keys) {
            if (key != null) {
                STARTS.keySet().removeIf(identity -> key.equals(identity.key()));
            }
        }
    }

    public static synchronized void removeStarts(java.util.Collection<ProfileKey> keys, UUID owner) {
        dirty = true;
        if (keys == null || owner == null) return;
        for (var key : keys) STARTS.remove(new StartIdentity(key, owner));
    }

    /**
     * All owner-bound starts for an output. The locate command filters these
     * by the click record's verified network before resolving a target.
     */
    public static synchronized List<StoredStart> startsForOutput(UUID owner, String outputId) {
        var matches = new ArrayList<StoredStart>();
        if (owner == null || outputId == null || outputId.isBlank()) {
            return List.copyOf(matches);
        }
        for (var entry : STARTS.entrySet()) {
            var key = entry.getKey().key();
            var info = entry.getValue();
            if (key == null || info == null || !outputId.equals(key.outputId())
                    || !owner.equals(info.owner())) {
                continue;
            }
            matches.add(new StoredStart(key, info.owner(), info.dimensionId(), info.positions(),
                    info.outputName(), info.displayKey()));
        }
        return List.copyOf(matches);
    }

    /** Old click records have no network ID; require a shared saved target. */
    public static boolean matchesLegacyStart(LocateRecord record, StoredStart start) {
        return record != null && start != null && record.networkId().isBlank()
                && record.owner().equals(start.owner()) && record.outputId().equals(start.key().outputId())
                && (start.dimensionId().isBlank() || record.dimensionId().equals(start.dimensionId()))
                && !start.positions().isEmpty()
                && start.positions().stream().anyMatch(record.positions()::contains);
    }

    /**
     * Snapshot click-scoped locate records for world save so active-craft
     * chat links stay usable across reload. Finished, cancelled, and broken
     * records are removed on finish and resync, so only live links persist.
     */
    public static synchronized List<LocateRecord> snapshotRecords() {
        var snapshot = new ArrayList<LocateRecord>();
        for (var record : RECORDS.values()) {
            if (record == null || record.id() == null || record.owner() == null) {
                continue;
            }
            snapshot.add(new LocateRecord(record.id(), record.owner(),
                    record.dimensionId() == null ? "" : record.dimensionId(),
                    record.positions() == null ? List.of() : List.copyOf(record.positions()),
                    record.outputName() == null ? "" : record.outputName(),
                    record.outputId() == null ? "" : record.outputId(),
                    record.createdTick(), record.networkId()));
            if (snapshot.size() >= MAX_RECORDS) {
                break;
            }
        }
        return List.copyOf(snapshot);
    }

    public static synchronized void restoreRecords(List<LocateRecord> stored) {
        dirty = true;
        RECORDS.clear();
        if (stored == null) {
            return;
        }
        for (var record : stored) {
            if (record == null || record.id() == null || record.owner() == null) {
                continue;
            }
            RECORDS.put(record.id(), new LocateRecord(record.id(), record.owner(),
                    record.dimensionId() == null ? "" : record.dimensionId(),
                    record.positions() == null ? List.of() : List.copyOf(record.positions()),
                    record.outputName() == null ? "" : record.outputName(),
                    record.outputId() == null ? "" : record.outputId(),
                    record.createdTick(), record.networkId()));
            evictEldest(RECORDS, MAX_RECORDS);
        }
    }

    /**
     * Forgets one click record, for example when its provider targets all
     * validate as broken. Finished and cancelled jobs forget all of their
     * owner's records for the scope's outputs via
     * {@link #removeRecordsForKeys}.
     */
    public static synchronized void removeRecord(UUID id) {
        dirty = true;
        if (id != null) {
            RECORDS.remove(id);
        }
    }

    /**
     * Forgets every click record for the finished scope's outputs so
     * finished and cancelled chat links expire instead of recreating a
     * highlight. Other owners and other outputs are untouched, so identical
     * outputs on another player's craft keep working links.
     */
    public static synchronized void removeRecordsForKeys(
            java.util.Collection<com.ctux.ae2craftingtime.core.ProfileKey> keys, UUID owner) {
        dirty = true;
        if (keys == null || keys.isEmpty() || owner == null) {
            return;
        }
        var keysToRemove = new java.util.HashSet<ProfileKey>();
        for (var key : keys) {
            if (key != null && key.outputId() != null && !key.outputId().isBlank()) {
                keysToRemove.add(key);
            }
        }
        if (keysToRemove.isEmpty()) {
            return;
        }
        RECORDS.entrySet().removeIf(entry -> {
            var record = entry.getValue();
            return record != null && owner.equals(record.owner()) && matchesAny(record, keysToRemove);
        });
    }

    public static synchronized void removeRecordsForKeys(java.util.Collection<ProfileKey> keys) {
        dirty = true;
        if (keys == null || keys.isEmpty()) return;
        RECORDS.entrySet().removeIf(entry -> entry.getValue() != null
                && matchesAny(entry.getValue(), keys));
    }

    private static boolean matchesAny(LocateRecord record, java.util.Collection<ProfileKey> keys) {
        return keys.stream().anyMatch(key -> key != null && key.outputId().equals(record.outputId())
                && (record.networkId().isBlank() || key.networkId().equals(record.networkId())));
    }

    public static synchronized void restoreStarts(List<StoredStart> stored) {
        dirty = true;
        STARTS.clear();
        if (stored == null) {
            return;
        }
        for (var entry : stored) {
            if (entry == null || entry.key() == null || entry.owner() == null) {
                continue;
            }
            var dimension = entry.dimensionId() != null && !entry.dimensionId().isBlank() ? entry.dimensionId()
                    : "";
            noteStart(entry.key(), entry.owner(), dimension, entry.positions(), entry.outputName(),
                    entry.displayKey());
        }
    }

    public static synchronized void clearAll() {
        dirty = true;
        RECORDS.clear();
        STARTS.clear();
    }

    private static void evictEldest(LinkedHashMap<?, ?> map, int maximum) {
        while (map.size() > maximum) {
            var eldest = map.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
    }

    private ProviderLocateRecords() {
    }
}
