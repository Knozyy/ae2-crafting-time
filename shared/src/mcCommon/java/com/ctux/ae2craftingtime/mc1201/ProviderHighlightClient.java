package com.ctux.ae2craftingtime.mc1201;

import appeng.api.stacks.AEKey;
import com.ctux.ae2craftingtime.core.ProviderDisplaySelection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

/**
 * Client-side only. Holds independent rainbow edges (network, dimension,
 * block positions, expiry timestamp) for the per-loader render hooks, plus one
 * persistent red plate per warning identity (network, dimension, output).
 * Edges and plates have independent lifetimes: edges expire after
 * {@link ProviderLocateCommand#HIGHLIGHT_SECONDS} seconds or when their
 * provider breaks; plates persist until the craft ends, is cancelled,
 * recovers, or the provider breaks. Manual locates touch edges only;
 * craft-state changes touch plates only. Never touched on a dedicated server.
 */
public final class ProviderHighlightClient {
    public record Highlight(String networkId, String dimensionId, List<BlockPos> positions, String outputId,
            long expiresAtMillis, boolean chatLocate) {
        public Highlight {
            networkId = networkId == null ? "" : networkId;
            positions = positions == null ? List.of() : List.copyOf(positions);
            outputId = outputId == null ? "" : outputId;
        }

        public Highlight(String dimensionId, List<BlockPos> positions, String outputId, long expiresAtMillis) {
            this("", dimensionId, positions, outputId, expiresAtMillis, false);
        }

        public Highlight(String networkId, String dimensionId, List<BlockPos> positions, String outputId,
                long expiresAtMillis) {
            this(networkId, dimensionId, positions, outputId, expiresAtMillis, false);
        }
    }

    public record Plate(String networkId, String dimensionId, List<BlockPos> positions, String outputId,
            long highlightedAtMillis, AEKey displayKey) {
        public Plate {
            networkId = networkId == null ? "" : networkId;
            positions = positions == null ? List.of() : List.copyOf(positions);
            outputId = outputId == null ? "" : outputId;
        }

        public Plate(String dimensionId, List<BlockPos> positions, String outputId) {
            this("", dimensionId, positions, outputId, System.currentTimeMillis(), null);
        }

        public Plate(String dimensionId, List<BlockPos> positions, String outputId, long highlightedAtMillis) {
            this("", dimensionId, positions, outputId, highlightedAtMillis, null);
        }

        public Plate(String networkId, String dimensionId, List<BlockPos> positions, String outputId,
                long highlightedAtMillis) {
            this(networkId, dimensionId, positions, outputId, highlightedAtMillis, null);
        }
    }

    public record RenderPlate(String dimensionId, BlockPos position, String outputId, AEKey displayKey) {
        public RenderPlate(String dimensionId, BlockPos position, String outputId) {
            this(dimensionId, position, outputId, null);
        }
    }

    public record RenderBeam(String dimensionId, BlockPos position) {
    }

    private static final LinkedHashMap<String, Plate> PLATES = new LinkedHashMap<>();
    private static final LinkedHashMap<String, Highlight> EDGES = new LinkedHashMap<>();

    private static String keyOf(String networkId, String dimensionId, String outputId) {
        return (networkId == null ? "" : networkId) + "\0" + (dimensionId == null ? "" : dimensionId) + "\0"
                + (outputId == null ? "" : outputId);
    }

    /**
     * Shows the temporary rainbow edge for one manual locate without touching
     * any red plate. Double-clicks and chat clicks share this state, with
     * chat provenance selecting the temporary beam, so
     * recovering, finishing, or cancelling before expiry removes only the
     * plate. Empty requests clear the matching edge only, never the plate.
     * Each identity (network, dimension, output) tracks its own edge so two
     * locates within 15 seconds stay independent instead of replacing one
     * global target.
     */
    public static void show(String dimensionId, List<BlockPos> positions, int durationSeconds, String outputId) {
        show("", dimensionId, positions, durationSeconds, outputId);
    }

    public static void show(String networkId, String dimensionId, List<BlockPos> positions, int durationSeconds,
            String outputId) {
        show(networkId, dimensionId, positions, durationSeconds, outputId, false);
    }

    public static void show(String networkId, String dimensionId, List<BlockPos> positions, int durationSeconds,
            String outputId, boolean chatLocate) {
        if (outputId != null && !outputId.isBlank()
                && (durationSeconds <= 0 || positions == null || positions.isEmpty())) {
            clearEdgeFor(networkId, outputId);
            return;
        }
        if (dimensionId == null || positions == null || positions.isEmpty() || durationSeconds <= 0) {
            return;
        }
        if (outputId == null || outputId.isBlank()) {
            return;
        }
        EDGES.put(keyOf(networkId, dimensionId, outputId), new Highlight(networkId, dimensionId,
                List.copyOf(positions), outputId, System.currentTimeMillis() + durationSeconds * 1000L,
                chatLocate));
    }

    /**
     * Remembers the red plate (background plus item icon) for one warning
     * identity without touching any rainbow edge. The server sends exactly
     * this for automatic warnings, so plates appear with no open window
     * and no edge; manual locates use {@link #show} for edge only. Packet
     * bounds (positions count, id lengths) still apply, but active highlights
     * are never silently evicted: every identity persists until an explicit
     * server clear, provider break, or session end.
     */
    public static void showPlate(String dimensionId, List<BlockPos> positions, String outputId) {
        showPlate("", dimensionId, positions, outputId);
    }

    public static void showPlate(String networkId, String dimensionId, List<BlockPos> positions, String outputId) {
        showPlate(networkId, dimensionId, positions, outputId, null);
    }

    public static void showPlate(String networkId, String dimensionId, List<BlockPos> positions, String outputId,
            AEKey displayKey) {
        if (dimensionId == null || positions == null || positions.isEmpty() || outputId == null
                || outputId.isBlank()) {
            return;
        }
        storePlate(networkId, dimensionId, positions, outputId, displayKey);
    }

    private static void storePlate(String networkId, String dimensionId, List<BlockPos> positions, String outputId,
            AEKey displayKey) {
        if (outputId == null || outputId.isBlank()) {
            return;
        }
        PLATES.put(keyOf(networkId, dimensionId, outputId),
                new Plate(networkId, dimensionId, positions, outputId, System.currentTimeMillis(), displayKey));
    }

    public static Highlight live() {
        return liveAt(System.currentTimeMillis());
    }

    static Highlight liveAt(long nowMillis) {
        pruneExpiredEdges(nowMillis);
        Highlight latest = null;
        for (var edge : EDGES.values()) {
            latest = edge;
        }
        return latest;
    }

    /** All live rainbow edges for the render hooks to draw independently. */
    public static List<Highlight> liveEdges() {
        return liveEdgesAt(System.currentTimeMillis());
    }

    static List<Highlight> liveEdgesAt(long nowMillis) {
        pruneExpiredEdges(nowMillis);
        return new ArrayList<>(EDGES.values());
    }

    public static List<RenderBeam> renderBeams() {
        var candidates = liveEdges().stream().filter(Highlight::chatLocate)
                .map(edge -> new ProviderDisplaySelection.Candidate<>(edge, edge.dimensionId(), edge.positions()))
                .toList();
        return ProviderDisplaySelection.firstByPosition(candidates).stream()
                .map(selected -> new RenderBeam(selected.dimensionId(), selected.position())).toList();
    }

    private static void pruneExpiredEdges(long nowMillis) {
        var expired = new ArrayList<String>();
        for (var entry : EDGES.entrySet()) {
            if (entry.getValue() == null || nowMillis >= entry.getValue().expiresAtMillis()) {
                expired.add(entry.getKey());
            }
        }
        expired.forEach(EDGES::remove);
    }

    /**
     * Removes one identity's persistent red plate without touching any rainbow
     * edge. The server sends an empty highlight for exactly this on craft
     * finish, cancel, and stall recovery, so a rainbow triggered just before
     * survives until its own 15-second expiry or provider break. The legacy
     * output-only overload clears every network/dimension sharing that output
     * for backward compatibility; new server code uses the network-aware
     * overload so finishing one CPU/network leaves the other's plate.
     */
    public static void clearFor(String outputId) {
        if (outputId == null || outputId.isBlank()) {
            return;
        }
        PLATES.entrySet().removeIf(entry -> outputId.equals(entry.getValue().outputId()));
    }

    public static void clearFor(String networkId, String outputId) {
        if (outputId == null || outputId.isBlank()) {
            return;
        }
        var wantedNetwork = networkId == null ? "" : networkId;
        PLATES.entrySet().removeIf(entry -> outputId.equals(entry.getValue().outputId())
                && wantedNetwork.equals(entry.getValue().networkId()));
    }

    public static void clearFor(String networkId, String dimensionId, String outputId) {
        if (outputId == null || outputId.isBlank()) {
            return;
        }
        PLATES.remove(keyOf(networkId, dimensionId, outputId));
    }

    /**
     * Clears live rainbow edges for the given output without touching any red
     * plate. Used for empty manual requests; craft state changes must use
     * {@link #clearFor} instead so rainbows survive recovery, finish, and
     * cancel. The legacy overload clears every identity sharing that output;
     * the network-aware overload clears only that network's edge.
     */
    public static void clearEdgeFor(String outputId) {
        if (outputId == null || outputId.isBlank()) {
            return;
        }
        EDGES.entrySet().removeIf(entry -> outputId.equals(entry.getValue().outputId()));
    }

    public static void clearEdgeFor(String networkId, String outputId) {
        if (outputId == null || outputId.isBlank()) {
            return;
        }
        var wantedNetwork = networkId == null ? "" : networkId;
        EDGES.entrySet().removeIf(entry -> outputId.equals(entry.getValue().outputId())
                && wantedNetwork.equals(entry.getValue().networkId()));
    }

    /**
     * Drops positions the {@code keep} predicate rejects (for example broken
     * provider blocks observed during render) from every live edge and every
     * plate in the given dimension. Entries left with no positions disappear;
     * other dimensions are untouched.
     */
    public static void trimPositions(String dimensionId, Predicate<BlockPos> keep) {
        if (dimensionId == null || keep == null) {
            return;
        }
        var emptiedEdges = new ArrayList<String>();
        for (var entry : EDGES.entrySet()) {
            var edge = entry.getValue();
            if (!dimensionId.equals(edge.dimensionId())) {
                continue;
            }
            var kept = edge.positions().stream().filter(keep).toList();
            if (kept.isEmpty()) {
                emptiedEdges.add(entry.getKey());
            } else if (kept.size() != edge.positions().size()) {
                entry.setValue(new Highlight(edge.networkId(), edge.dimensionId(), kept, edge.outputId(),
                        edge.expiresAtMillis(), edge.chatLocate()));
            }
        }
        emptiedEdges.forEach(EDGES::remove);
        var emptied = new ArrayList<String>();
        for (var entry : PLATES.entrySet()) {
            var plate = entry.getValue();
            if (!dimensionId.equals(plate.dimensionId())) {
                continue;
            }
            var kept = plate.positions().stream().filter(keep).toList();
            if (kept.isEmpty()) {
                emptied.add(entry.getKey());
            } else if (kept.size() != plate.positions().size()) {
                entry.setValue(new Plate(plate.networkId(), plate.dimensionId(), kept, plate.outputId(),
                        plate.highlightedAtMillis(), plate.displayKey()));
            }
        }
        emptied.forEach(PLATES::remove);
    }

    /** Persistent plates for the render hooks to filter by dimension. Server authoritative. */
    public static List<Plate> plates() {
        return new ArrayList<>(PLATES.values());
    }

    public static List<RenderPlate> renderPlates() {
        var candidates = PLATES.values().stream()
                .filter(plate -> shouldShowPlates(plate.outputId()))
                .map(plate -> new ProviderDisplaySelection.Candidate<>(plate, plate.dimensionId(), plate.positions()))
                .toList();
        return ProviderDisplaySelection.firstByPosition(candidates).stream()
                .map(selected -> new RenderPlate(selected.dimensionId(), selected.position(),
                        selected.value().outputId(), selected.value().displayKey()))
                .toList();
    }

    /**
     * Legacy snapshot hook kept for loader compatibility. Server-owned craft
     * state is authoritative for plates: snapshots from another CPU, the
     * planning screen, or a closed window must never remove a still-delayed
     * plate, so this is now a no-op. Plates disappear only on explicit server
     * clear (recovery, finish, cancel) or provider break (render trim).
     */
    public static void prunePlates(List<String> requestedKeys) {
        return;
    }

    static void clearPlates() {
        onSessionEnd();
    }

    /**
     * Drops all client highlight state at session boundaries (disconnect,
     * world switch). Called by every loader's client disconnect handler so a
     * rainbow cannot survive reconnect and plates never leak between worlds
     * with matching coordinates. Red plates return only via explicit
     * server-approved resync; rainbow timers are never serialized or
     * restored.
     */
    public static void onSessionEnd() {
        PLATES.clear();
        EDGES.clear();
    }

    /**
     * Plate gate for every loader's render hook. Server-owned craft state is
     * authoritative: any stored plate shows until the server explicitly clears
     * it (recovery, finish, cancel) or the provider breaks (render trim).
     * Never consults the general UI stats cache, so opening another CPU, the
     * planning screen, or closing all windows can no longer hide a
     * still-delayed plate. Wall-clock plays no role here: the 15-second window
     * gates edges only.
     */
    public static boolean shouldShowPlates(String outputId) {
        return outputId != null && !outputId.isBlank();
    }

    /**
     * Smooth one-second blink shared by every loader's render hook, so the
     * box reads as an alert rather than a static frame. Ranges from 0.35
     * (dim) to 1.0 (full) opacity.
     */
    public static float pulseAlpha() {
        var phase = (System.currentTimeMillis() % 1000) / 1000.0;
        return 0.35f + 0.65f * (0.5f - 0.5f * (float) Math.cos(phase * Math.PI * 2.0));
    }

    /** Full rainbow color cycle period in milliseconds. */
    public static final long RAINBOW_PERIOD_MILLIS = 3000L;

    /**
     * Time-cycling rainbow color shared by every loader's render hook, so the
     * highlight contrasts with any environment instead of blending into
     * red-tinted builds. Returns {@code {red, green, blue}} with each
     * component in {@code [0, 1]}.
     */
    public static float[] rainbowRgb() {
        return rainbowRgb(System.currentTimeMillis());
    }

    static float[] rainbowRgb(long timeMillis) {
        var phase = (timeMillis % RAINBOW_PERIOD_MILLIS) / (double) RAINBOW_PERIOD_MILLIS * Math.PI * 2.0;
        return new float[] {0.5f + 0.5f * (float) Math.cos(phase),
                0.5f + 0.5f * (float) Math.cos(phase - Math.PI * 2.0 / 3.0),
                0.5f + 0.5f * (float) Math.cos(phase + Math.PI * 2.0 / 3.0)};
    }

    private ProviderHighlightClient() {
    }
}
