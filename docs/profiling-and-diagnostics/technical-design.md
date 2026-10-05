# Profiling And Diagnostics Technical Design

Lifecycle: see scope status and evidence for [baseline](spec.md) and [live-learning extension](spec.md#planned-normalized-sample-details-114).

The sections above the planned #114 design describe the current runtime.

## Ownership

`CraftProfiler`, `TtcAccuracyTracker`, and their immutable values live in
`shared/src/main/java`. They have no Minecraft dependency, so every target uses
the same calculations and tests.

The logical server owns all mutable profiling state. AE2-facing mixins report
events through the version-specific `ProfilerBridge`; clients receive only
bounded aggregate snapshots.

## Row-stat context isolation (#615)

The shared `StatsRequestQueue` keeps the batching introduced in #653. Context
resets preserve its 500 ms deadline, and newly visible rows can displace one
background request when its 4,096 slots are full.

`RowStatsSession` assigns each batch a session, sequence and requested
container/CPU. The server validates that context before collection and echoes
the identity. The client rejects old sessions, duplicates, out-of-order replies,
unsent sequences and mismatched contexts before updating display state.
Returning to the same CPU or reusing a menu id starts a new session. Only ME
Requester menus collect inventory amounts; ordinary craft screens skip that
scan. All four transports have a new protocol/channel version.

See the [wire and scheduling contract](../server-client-stats.md#statsrequestc2s).
Regression sources are added. Compilation, executed tests, coverage and runtime
performance for this follow-up remain unverified.

## Production Windows

`CraftingCpuLogicMixin` observes two AE2 execution points:

```text
expected output dispatch -> ProfilerBridge.start(...)
accepted returned output -> ProfilerBridge.complete(...)
```

Pending queues use the concrete crafting CPU object as an identity scope. A
`BusyWindow` uses `ProfileKey(networkId, outputId)` and spans every CPU scope
that is currently producing that output. Completion consumes only the matching
CPU queue. The window closes and creates one sample after no scope has matching
pending work.

This split keeps cancellation safe while still measuring real network-wide
parallel throughput. `finishJob(...)` clears the CPU scope and rebuilds any
affected network window from work that other CPUs still own.

Before the first window closes, a stats request may preview its completed amount
over its elapsed ticks. This lets a running status row leave `No data yet`
without waiting for the next order. The preview is always low confidence and is
never retained or persisted; the normal completed window remains the only
throughput sample.

## Throughput Calculation

Each retained sample stores normalized amount, unit, and duration ticks. The
profiler:

1. keeps the newest `maxSamples` values in an `ArrayDeque`;
2. calculates average and latest duration from the full retained window;
3. after five samples, filters duration-per-unit outliers around the median;
4. assigns usable samples weights `1..n` from oldest to newest; and
5. divides weighted amount by weighted duration for `amountPerTick`, then
   multiplies by 20 for `amountPerSecond`.

`ProfileStats` carries both total and used sample counts. The client can explain
low confidence without receiving mutable profiler state.

## Prediction Accuracy

When `trySubmitJob(...)` succeeds, `ProfilerBridge.startJob(...)` estimates the
accepted plan using the same normalized row formula as the craft-confirm UI.
`TtcAccuracyTracker` stores one pending job by CPU identity. `finishJob(true)`
converts it into a bounded sample containing predicted seconds, tick time, wall
time, and row coverage. Any other finish removes the pending job without adding
a sample.

The aggregate exposes:

- fully covered jobs over recorded jobs;
- mean absolute percentage error;
- mean actual-to-predicted ratio;
- mean signed seconds error;
- average row coverage; and
- latest prediction, tick time, wall time, and coverage.

Only samples whose known-row count equals total-row count enter the three error
metrics.

## Delayed Output Diagnostics

`CraftProfiler` keeps last-progress ticks per CPU/output and a recent
`CapacityState` per CPU. The status request resolves the player's selected CPU,
then asks for a `StallDiagnostic` alongside its network/output stats.

The delay threshold is:

```text
max(600 ticks, ceil(averageDurationTicks * 2))
```

Capacity expires after 20 ticks. `StallDiagnostic.hints(...)` selects at most
one parallelism hint from scheduled work and recent capacity, then adds the
machine-speed hint.

## Persistence And Requests

Completed throughput samples are exported as `PersistedOutputSamples` and saved
under `ae2-crafting-time`. Accuracy, pending work, busy windows, progress ticks,
and capacity are deliberately absent from NBT.

`StatsRequestHandler` resolves the active grid and selected standard or
AdvancedAE CPU on the server. The response contains aggregate `StatsEntry`
values and current network amounts. Packet collection and string limits are
defined by `PacketLimits` and checked while decoding.

## Version Adapters

- `shared/src/mcCommon`: common AE2 execution hooks, request handling, codecs,
  text, and table integrations.
- `shared/src/mc1201` and `shared/src/mc2612`: API-specific key conversion,
  bridge, persistence tag, input, and screen code.
- `shared/src/neoforge`: optional AdvancedAE execution hooks shared by both
  NeoForge targets.
- `versions/<target>`: entrypoints, networking registration, and `SavedData`
  glue.

The adapters should remain conversion and delegation only. New calculations or
branches belong in the covered pure-Java core.

## Planned design for #114: per-unit sample presentation

Historical planning status: not implemented. This section includes live completion-interval learning
and supersedes the baseline Production Windows behavior above. See
[requirements N1–N12](spec.md#requirements) and the
[implementation plan](implementation-plan.md). Inspected runtime baseline:
`493d277757489ef4d1f0970509c0cfbb5a8bbdb6`.

### Evidence and ownership

Paths are repository-relative. Existing seams verified from source:

| Component | Current behavior | Planned responsibility |
| --- | --- | --- |
| `shared/src/main/java/com/ctux/ae2craftingtime/core/CraftProfiler.java` | CPU pending queues, network busy window; retains only at network/output idle | Retain completion intervals while queues remain nonempty |
| Same file, `stats`, `filteredSamples` | Weighted amount/time; outliers on time/unit; reliable at three unfiltered samples | Reuse formulas against new observations |
| Same file, `clearPending`, `rebuildBusyWindow` | Rebuilds unfinished whole windows after scope cleanup | Preserve interval cursor and accepted bucket when other scopes survive |
| `shared/src/mc1201/java/com/ctux/ae2craftingtime/mc1201/ProfilerBridge.java` and `mc2612` copy | Saves on `complete` returning true; entry prefers retained history to preview | Finalize and snapshot from server tick, irrespective of GUI requests |
| `versions/<target>/src/main/java/com/ctux/ae2craftingtime/mc1201/Ae2CraftingTime.java` | Owns loader lifecycle registration | Register one logical-server end-tick flush per target |
| Same target directory, `ClientStatsRequests.java` | One-second per-key cooldown | Preserve visible requests, including cached entries |
| `shared/src/mcCommon/java/com/ctux/ae2craftingtime/mc1201/StatsRequestHandler.java` | Collects current entries and server remaining-job total | Return newly retained rate on next response |
| Same shared directory, `TtcText.java` and `StatsChatServer.java` | Client tooltip and independent server chat format raw durations | Both show normalized details |
| `shared/src/main/java/com/ctux/ae2craftingtime/core/ProfileStats.java` | Raw amount/duration lists exist | Derive ratios without new DTO fields |
| Shared `net/StatsPacketCodec.java` and both `PersistedSamplesTag.java` adapters | Bounded raw integer pairs | Keep layout; finalized intervals use existing representation |

All state stays on the logical server thread. Existing standard/optional CPU
mixins continue reporting dispatch and accepted output. No machine hooks or
client-authored samples. The whole-order baseline and temporary preview cannot
remain a competing source of newly retained samples.

### Collection algorithm

Keep CPU-identity pending queues for attribution. Replace the throughput role of
`BusyWindow` with one runtime `CompletionInterval` per `ProfileKey`: `unit`,
`anchorTick`, optional `returnTick`, and `returnedAmount`. A dirty-key set contains
positive unflushed returns, so normal flushing visits changed keys only. There
is no object or loop per completed output unit.

1. First positive dispatch in an idle episode establishes `anchorTick = tick`.
   Later dispatches in the active episode do not move it. Waiting state still
   clears on actual dispatch.
2. Accepted output consumes only its CPU's pending queue, capped at expected
   amount. Add only consumed output to the key's bucket. Unmatched excess and
   simulation add nothing. Progress/job-remaining bookkeeping runs once per event.
3. Same-key returns in the same tick add to the same bucket across CPUs and
   callbacks, including another same-tick dispatch after a queue emptied.
   Do not expose a retained sample inside `complete`.
4. At end server tick, `CraftProfiler.flushCompletedSamples()` finalizes each
   positive bucket once as `(returnedAmount, max(1, returnTick - anchorTick))`,
   using existing `addSample` retention. Return whether retained history changed.
   Repeated flushes with no new output are no-ops.
5. If pending work survives, advance the cursor to `returnTick` and clear the
   bucket. Otherwise remove interval state. Next idle episode starts at its
   actual dispatch, excluding idle time.
6. No return means no sample and no cursor advance. The next return includes
   active waiting time. Do not emit zero-output samples or reuse a cumulative
   prefix as another observation.

Flush after world/CPU processing at the loader's end-server-tick phase, using
its latest supported listener ordering. Do not flush on individual CPU ticks:
that splits cross-CPU returns. Integration tests must prove ordering with a late
optional CPU callback. Use event timestamps, not another dimension's clock.

If an event has a newer tick while an older bucket remains unflushed, finalize
the old bucket before accepting the new event and mark history dirty for the
bridge flush. Tick regression discards unfinalized state for that key and
re-anchors surviving work at the current event tick, adding no negative sample.
Use checked bucket addition; overflow discards that interval and re-anchors,
never creating wrapped or saturated evidence.

`complete` may retain its boolean signature, but its documented result becomes
positive matched progress. Bridges stop treating it as a serialization signal.
Flush reports history changes, including defensive finalization on newer events.

### Lifecycle and persistence

- Finish/cancel clears its CPU's unmatched work and job diagnostics. Keep accepted
  buckets until flush; these outputs remain valid evidence after cancellation.
  Do not append a whole-order sample. If other CPUs survive, preserve their
  cursor; rebuilding from original dispatch would count time twice.
- Cancellation with no surviving work or accepted bucket removes the interval.
  A later dispatch starts a fresh episode.
- Output reset removes history, bucket, cursor, dirty-key entry, and any tail
  reference together. Later flush cannot restore reset evidence. Existing reset
  invalidation of pending queues stays; unmatched later returns are ignored.
- Disable discards unfinalized state and pending work, preserving finalized
  history. Load clears runtime queues/buckets; restored outputs are not replayed.
- Both bridge copies snapshot/replace SavedData once after all changed keys
  finalize, never per item, CPU, or callback. Skip unchanged ticks. This updates
  an in-memory snapshot and dirty flag; Minecraft keeps its disk-save cadence.
- Before world-save serialization and graceful server stop, flush positive
  buckets. Use the four SavedData `save` methods before reading sample fields,
  and a server-stopping callback before final save. Never invoke save recursively.

A save may occur mid-tick. Keep a same-key/return-tick reference to the most
recent retained tail until the next tick. If another same-tick return follows
an early flush, add its amount to that tail; do not change its duration, position,
or sample count. This amendment also applies to late end-tick callbacks, preserving
one observation per key/tick. Mark the amended history dirty for the next snapshot.
A same-tick tail amendment bypasses new interval creation; keep the cursor at
that return tick if pending work survives. Reset/load/disable clears references.
Bound references to keys touched in the current tick and prune on tick advance,
including idle keys. Tests must cover save, more output, and repeat save in one tick.

Old raw records stay as broader observations until normal eviction. No source-kind
field or schema bump: labels say observations, never exact executions. Identical
raw histories keep identical math; newly collected finer intervals can change
rate and confidence. Preserve saved statuses/provider records unchanged.

### Confidence, remaining TTC, and delay

After each finalized/amended observation, the next `stats` read recalculates rate, used count,
and `reliableEstimate`. Three clean observations suffice; five or more activate
existing outlier filtering. `?` may disappear, persist, or return. Do not promote
a preview or duplicate a batch to reach three. `maxSamples < 3` stays unreliable.

The profiler reuses immutable statistics until history changes. Append/eviction,
same-tick amendment (including overflow removal), reset, load and configuration
updates invalidate the cached result. Unknown outputs are never cached. Stall
idle time and capacity expiry remain live; they are not part of this cache.

Provider positions use one lazy identity index per grid and server tick, shared
by dispatch-candidate capture and warning lookup. The server end-tick hook and
world cleanup discard the index, so it cannot retain old grids across ticks or
worlds. Disabled profiling/scopes bypass observer creation and diagnostic tick
work; crafting suspension keeps its independent hooks.

CPU warning reconciliation updates only that CPU's plate contribution. The
server end-tick hook merges all contributions and sends their difference once,
before clearing provider-position indexes. Explicit clears join that same flush;
login replay remains immediate. Shared recipients stay highlighted until their
last CPU contribution clears, and offline recipients remain pending.

Remove cumulative `inProgressStats` from throughput lookup when the interval
collector is installed: the first real interval is retained at end tick and
provides the useful one-sample estimate. Before then, use retained history or
existing missing-data text. No repeated prefix contributes confidence.

`ProfilerBridge.estimateSeconds` already uses retained stats, so the dependency-
path remaining-job total uses the new rate without a graph change. `startJob`
still freezes prediction accuracy at submission; never refresh that prediction.

Keep `stall`'s `max(200, ceil(averageDurationTicks * 2))` and progress reset. The raw
average now means completion interval, including legacy broad observations until
eviction. Never divide the diagnostic baseline by amount. During implementation,
correct baseline references to 600 ticks: inspected runtime already uses 200.
This constant is not a new threshold; its learned-duration input changes meaning.

### UI and normalized values

Visible status rows already call `ClientStatsRequests.request` before cache lookup.
Preserve this with all four wrappers. `ClientStatsCache` replaces requested stats,
including confidence; the next render recomputes `TimeEstimate.format`. Verify
polling with a populated cache and no hover. No packet-per-item push or new
subscription is needed. If a blocked row skips polling, its existing blocker
refresh must allow it to resume polling when production resumes.

Add computed `ProfileStats.sampleTicksPerUnit(index)`, `averageTicksPerUnit()`,
and `latestTicksPerUnit()` returning OptionalDouble. Use double division of
positive raw pairs. Unequal/empty arrays yield no aggregate detail; a bad pair
omits that pair and makes average unavailable. Latest needs a valid final pair.
Average includes all retained observations; filtering applies to weighted rate.
Keep raw fields/constructors and wire types. Do not sum raw longs for the average.

`TimeEstimate.formatSampleTicks(double)` returns Optional<String>: reject
nonpositive/nonfinite input, use `<0.001` below that bound, otherwise BigDecimal
half-up at scale three, stripped zeros, plain decimal output. Do not use the
whole-second `formatTicks` path or feed rounded text back into calculations.

Both `TtcText` and `StatsChatServer.details` use the derived values. Reuse the
single-argument `value.window` text with formatted ticks and the time symbol. Add
singular item without changing the rate's plural unit. Label the list `Samples
(per unit)` and explain effective throughput once. Average/latest contain the
complete per-unit value; when either is missing, use rate/count-only text.
Server messages remain translatable components, without client-only I18n.
English/Ukrainian keys and placeholders match. Explain that new samples arrive
while crafting and that the sample limit is not the reliability threshold.

All four targets share core/text/resources. Existing AdvancedAE, ECO, AE2-LT,
fluid/chemical, and mana adapters feed this collector where supported. Do not
change availability. Existing optional details inherit normalization; TTC-only
surfaces need no new sample list. The proposed CPU-bound stats design must adopt
interval collection if implemented later; this plan adds no persistent CPU keys.
