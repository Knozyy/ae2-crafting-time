# Why Crafting Stats Stay On The Server

Date: 2026-06-21

## The Rule

The Minecraft server owns all craft timing, throughput, first-dispatch waiting
state, delay, prediction accuracy, and bottleneck diagnostics. The client only
shows the snapshot it receives.

That matters on dedicated servers because:

- AE2 craft execution runs on the server.
- The server sees the real tick clock, pattern pushes, and completed outputs.
- The client may not have the same mod state, world state, or timing data.
- Client-side calculation would be wrong or empty in multiplayer.

Singleplayer follows the same rule. An integrated world still has a logical
server, so there is no separate shortcut that reads profiler data from the
client.

## How It Works Today

The implementation uses the server-owned path for both dedicated servers and
singleplayer:

- server-side AE2 mixins record `start` and `complete` events through
  `ProfilerBridge`
- accepted jobs register their crafted outputs until each output's first
  pattern dispatch
- client UI mixins request visible output ids through `StatsRequestC2S`
- the server looks up retained stats for the active AE2 network and output ids
- `StatsSnapshotS2C` updates `ClientStatsCache`
- UI code renders only from the client display cache

Row requests are queued until the client tick boundary. Each nonempty batch has
at most 256 IDs, with at least 500 ms between batches and a one-second cooldown
per sent key. Visible rows take the first 192 slots; waiting sort/total requests
can use the remaining slots, then either queue can fill unused space. FIFO order
keeps large lists rotating. The queue holds up to 4096 pending keys; render calls
retry keys that could not yet enter it. More than 512 active keys refresh over
multiple seconds rather than all at once. Screen, menu, CPU and connection
changes discard queued context. The wire format is unchanged.

The server rejects empty requests and separately limits each player to four
packets and 512 IDs per second. Inventory enumeration and whole-job TTC run once
per accepted batch. Logout/server stop clear rate-limit state. All Minecraft
reads remain on the server thread; there is no long-lived inventory cache.

## The Data Flow

```text
AE2 server craft hooks
  -> server RAM CraftProfiler
  -> StatsRequestC2S visible output keys
  -> server snapshots matching those keys
  -> StatsSnapshotS2C
  -> client RAM display cache
  -> AE2 / optional integration render paths
```

## Data Ownership

Server owns:

- `CraftProfiler`
- pending operation matching
- rolling sample buffers
- concurrent production-window aggregation
- per-CPU first-dispatch waiting state
- average duration and throughput calculation
- config value that affects collection: `enabled`

Client owns:

- last received display cache
- UI formatting
- visible output lookup for the currently open screen
- config value that affects local display only: `showInTree`

Client must not:

- calculate timings
- mutate server profiler state
- infer missing stats from local tick time
- receive raw pending operations

## Packet Shape

Each loader module owns its packet glue. Forge uses `SimpleChannel`, Fabric uses
Fabric networking, and NeoForge uses the NeoForge payload registrar.

### `StatsRequestC2S`

Queued while a supported UI renders. All four targets share the same scheduler;
their existing networking adapters send the batches.

Fields:

```text
keys: list<string> output ids
requestId: { session: positive long, sequence: positive long, cpuContext: long, jobId: UUID }
```

Rules:

- Client queues visible/hovered output ids with priority. Sort and total
  calculations queue the remaining ids without priority.
- Server treats keys as hints, not trusted facts.
- Requests are capped at 256 output ids and 512 ids per player per second.
- The client sends at most one batch every 500 ms. Each id has a one-second
  cooldown starting at send time, including ids with no learned stats.
- Up to 192 slots per batch prioritize rendered rows; the remaining slots take
  the oldest pending ids. Large plans rotate through the budget instead of
  promising a one-second refresh for every row.
- Pending ids and cooldown records each have a 4,096-entry memory bound.
  A newly visible id can replace the oldest background id when the queue is
  full. Other queued work stays until sent or its context closes. Deferred ids
  retry through normal render/sort lookups after space becomes available.
  If every queued id is already prioritized, further ids wait for space.
- A connection, screen, menu or selected-CPU change discards queued work and
  clears the display cache. Disconnect and screens without a player never drain
  the queue. Clearing a context preserves the 500 ms send deadline.
- Each context reset advances a client session counter, even when a container
  id or CPU serial is reused. Each sent batch gets a sequence within that session.
  The server checks the requested container/CPU against the player's current
  menu before collection and echoes the complete request identity unchanged.
- The server sends `RowStatsJobS2C` before broadcasting Crafting Status rows
  whenever the selected CPU or its crafting-link UUID changes. The menu retains
  this identity until the client has applied its CPU selection, including when
  the identity arrives before the screen is ready. No item-count or elapsed-time
  heuristic is used. Completion uses the zero UUID; unreadable addon job identity
  fails closed instead of collecting unscoped diagnostics.
- A job change retires queued and outstanding work and clears cached diagnostics
  without resetting the send deadline. Requests carry the job UUID and the server
  checks it against the live job before collection; snapshots echo it unchanged.
  Equal-sized and rapid consecutive replacements therefore cannot reuse the
  previous job's responses. The job notification is fixed at 24 bytes.
- Before applying any snapshot fields, every loader checks the echoed session,
  job UUID, issued sequence and both requested and server-observed CPU contexts. Closed
  contexts, earlier visits to the same CPU, duplicate responses, unsent sequences
  and responses older than the latest applied batch are rejected. Rejected or
  unanswered batches do not accumulate tracking records; normal render retries
  use the existing cooldown and queue budget.
- The server accepts at most four row-stat packets per player per one-second
  window, independently of the key budget. Empty requests, unnegotiated
  connections, mismatched menus and requests without a live AE2 grid do no
  collection work.
- Context, notifications and the remaining-job total are collected per batch,
  rather than per row. All collection stays on the logical server thread.
- Only ME Requester menus need `networkAmounts`. Their batch enumerates the
  inventory once and keeps the existing output-id aggregation across variants.
  Normal plan, status and tree requests leave this map empty and never enumerate
  inventory through the row-stat path. Stored-variant mismatch detection has
  its own inventory observation and is unchanged.
- Server replies with known stats for the player's active AE2 network and silently omits unknown keys.

The row-stat wire boundary is Forge protocol `28`, NeoForge registrar `27` on
both targets, and Fabric channels `stats_request_v4` / `stats_snapshot_v13`.
Update both client and server. Older peers cannot negotiate this row-stat
exchange; single-key clients must not silently hit the new four-packet limit.

This follow-up builds on the batching from [#653](https://github.com/cTux/ae2-crafting-time/pull/653)
and row-stat generation isolation from [#615](https://github.com/cTux/ae2-crafting-time/issues/615).
Deterministic regressions cover batches of 1/32/256 ids, a 1,024-id queue, duplicate
requests, foreground/background fairness, cooldowns, context cancellation,
full-queue visible-row admission, response-context rejection, A-to-B-to-A visits, duplicate/out-of-order responses,
unsent sequences, reset-resistant pacing, memory limits, packet budgets,
inventory aggregation, correlation codec round trips and malformed/old layouts.
They are added as
source; no build, tests, coverage measurement, benchmark or client run has been
performed for this working-tree change. Do not treat the static review as a
runtime performance result.

### `StatsChatC2S`

Carries only a bounded output id, amount, and `SHOW` or `RESET` action. The
server resolves the player's current AE2 network, reads or clears authoritative
stats, and formats the translatable message. `SHOW` details are broadcast as
player-attributed chat; the `RESET` confirmation is sent as a private system
message visible only to the player who triggered it. Clients never send
chat text for the server to relay. A reset is accepted only when that output has
retained stats on the player's current network.

### `StatsSnapshotS2C`

Sent from server to only the requesting player.
Fields:

```text
requestedKeys: list<string>
requestId: { session: positive long, sequence: positive long, cpuContext: long, jobId: UUID }
cpuContext: long (server-observed container and selected CPU)
networkAmounts: map<string, long>
waitingTicks: map<string, nonnegative long>
entries: list {
  key: string
  unit: item | millibucket | mana
  sampleCount: int
  averageDurationTicks: double
  amountPerTick: double
  amountPerSecond: double
  lastDurationTicks: long
  sampleDurationTicks: list<long>
  sampleAmounts: list<long>
  stall: optional {
    idleTicks: long
    typicalDurationTicks: double
    activeBatches: int
    usedParallelSlots: int
    totalParallelSlots: int
  }
}
```

Rules:

- Snapshot is immutable display data.
- A sample describes one continuous production window for an output across all
  crafting CPUs on the AE2 network, not one individual pattern push. This makes
  its amount-per-time rate include parallel batches.
- Pending pattern outputs are still matched per crafting CPU. Finishing or
  cancelling a CPU job discards its unmatched pending outputs so they cannot
  inflate a future sample.
- A stall diagnostic is included only for the selected crafting CPU after its
  no-progress threshold is reached. Live data always wins; after a reload the
  last remembered stall fills the row until fresh observations arrive (see
  status persistence below).
- Waiting ticks are included for requested outputs that the selected crafting
  CPU has not dispatched yet, even when those outputs have no retained stats.
  The map is bounded to 256 output ids. Live data always wins; after a reload
  remembered waiting rows return until the craft dispatches or finishes.
- Client drops cache entries for requested keys before applying returned stats.
- Missing stats or waiting values therefore remove old client state instead of
  leaving stale values behind.

### CPU-list TTC packets

`CpuTtcRequestC2S` carries the open container id, a per-screen session id, a
monotonic sequence, and at most 32 positive, unique CPU serials. The client sends
the visible six plus the selected CPU at most once per second. The server accepts
four packets and 128 serials per player per second, then resolves only CPUs in
the current Crafting Status menu's existing serial map and live grid CPU set.

`CpuTtcSnapshotS2C` echoes session and sequence and returns one present-or-empty
total for every requested serial. The client accepts only the latest outstanding
request for the active screen, replaces the whole batch, and expires it after
three seconds. Idle, unknown, zero, removed, replaced, or foreign CPUs stay
blank. The selected card and title read this same batch.

### `ProviderLocateC2S`

Sent from client to server when any crafting-item row in the crafting CPU
screen is double-clicked, including normal TTC rows.

Fields:

```text
outputId: string, at most 128 chars (profile key id)
```

Rules:

- Double-click means "any active crafting item". The client sends for any
  non-blank id; the server resolves the clicking player's open CPU scope and
  grid, requires job ownership and live resolvable positions, and answers
  with edge-only `ProviderHighlightS2C` or the private expiry notice. Manual
  locates never create or clear red plates. No locate records are involved.
- Oversized or malformed ids are rejected before any lookup.

### `ProviderHighlightS2C`

Sent from server only to the warning owner or clicking player. Plates and
temporary locates share positions; the beam shares the chat rainbow lifetime:

| Event | Red plate | Rainbow edge | Red beam |
| --- | --- | --- | --- |
| Any of eight red warnings | Appear automatically for valid targets | Unchanged | Unchanged |
| Successful chat locate | Unchanged | Blink 15s | Blink with rainbow 15s |
| Crafting-row double-click | Unchanged | Blink 15s | Absent for that locate |
| Last warning clears / finish / cancel | Disappear | Continue until expiry | Continue with chat rainbow |
| Provider breaks | Remove that plate | Remove that outline | Remove that beam |
| Leave and re-enter | Restore current valid warnings | Never restore | Never restore |

Fields:

```text
networkId: string (additive tail, "" for legacy packets)
dimensionId: string
positions: list<BlockPos>, at most 16
outputId: string, at most 128 chars (profile key id, e.g. an item id)
durationSeconds: nonnegative int (15)
plateOnly: boolean (true for automatic warning plates: no rainbow edge)
chatLocate: boolean (true only for a successful owned chat locate; versioned before displayKey)
displayKey: optional AE2 typed key (bounded to 16 KiB; absent for clears and edge-only locates)
```

Rules:

- Positions resolve server-side through live grid nodes at notify time;
  clients never send positions.
- Automatic red warnings (`plateOnly=true`) show the plate with no edge and
  need no open window. Manual locates (`plateOnly=false`) show the edge with
  no plate change. Empty positions with zero duration clears one plate and
  keeps rainbow.
- Plates are server-authoritative, never UI cache. Snapshots from another
  CPU, the planning screen, or a closed window never remove a plate. Active
  plates and edges are never silently evicted; identity is job + network +
  dimension + output + provider with independent rainbow targets.
- Session end clears all plates and edges. Login replays only the runtime
  reconciled plates; the next CPU tick refreshes current warnings. Saved
  statuses alone cannot restore a plate. Rainbow and beams are never restored.
- Every loader trims broken targets in that dimension only: air, missing
  block entity, replacement non-provider, or surviving host without provider
  service drops. Unloaded chunks and unreadable grid stay unknown and kept.
- The locate command (`/ae2craftingtime locate <record>`) serves only
  records owned by the clicking player, resolved against the active job plus
  still-valid targets. Missing, foreign, finished, cancelled, or broken
  records answer with a private expiry notice and highlight nothing, and
  broken records are forgotten.
- All eight red warning statuses contribute to automatic plates when an
  associated provider is valid. Chat links keep their existing notification
  policy. A successful chat locate adds a red beam to the same 15-second
  rainbow entry; a row double-click replaces it with rainbow-only state.
- The client draws thick (2-3x) rainbow-cycling outline boxes while in the
  same dimension until the duration expires, plus the typed output resource
  centered on a red plate on each camera-facing face. Unknown or unavailable
  key types leave the red plate visible without an icon. On 1.20.1/1.21.1 each plate is one thin filled box
  flushed with its own batch per face (the strip-mode `debugFilledBox`
  has no vanilla callers, so faces must never share one strip)
  (see [issue #241](https://github.com/cTux/ae2-crafting-time/issues/241)).
- Every locate is also answered with a private "Highlighting <provider> at
  <coords> in <dimension>" system message naming the provider block,
  whatever triggered it, with clickable coordinates that teleport to each
  position
  (see [issue #241](https://github.com/cTux/ae2-crafting-time/issues/241)).
  The packet layout appends a bounded optional typed key after `networkId`;
  older packets without that field retain a plate without an icon.

Wire versions: Forge channel protocol `28`; Fabric uses `provider_highlight_v6`
with the row-stat channels listed above; NeoForge registrars are `27`. The new
chat provenance field has a marker before the optional typed key so old
packets decode with no beam.

### Provider-start persistence

Per-output provider links (network, owner, dimension, provider positions,
display name, optional typed display key) persist in the world `SavedData` beside throughput samples
under a `providers` section. Old saves without the section load with empty
provider state. The stored dimension travels with the fallback so resync
never re-derives it alone. Rainbow edges are never persisted.

Click records also persist their verified network ID. An old record without
that field works only when one owner-bound start matches its output; it never
selects another network's provider. An initial empty start can retain a
captured blocked-warning target for a live click record. Later empty target
resolution removes that record. Login replays only runtime contributions,
and the next CPU tick can rebuild current plates; persisted warning text alone
never creates one. Finished, cancelled, and invalidated links expire.

### Status persistence

Per-output statuses (delayed, waiting, no provider, no power) persist in the
world `SavedData` under a `statuses` section beside samples and provider
links:

```text
statuses: [
  { networkId, key, kind: delayed | waiting | no_provider | no_power,
    idleTicks, typicalTicks, acceptedAtTick }
]
```

Bounded to 256 entries, tolerant reads, no save-version bump; old saves
load with no remembered statuses. Live dispatch data always wins: any new
pending craft drops the remembered status for its output, finishing or
cancelling drops it, and a still-remembered row only shows while nothing
live contradicts it. `NO SPACE` stays live-only because the client derives
it from the open CPU screen each frame.

## UI Flow

1. An AE2 or optional integration UI renders or rebuilds.
2. Client collects output ids from currently visible nodes.
3. Client sends `StatsRequestC2S`.
4. Server reads stats and first-dispatch waiting time from server
   `CraftProfiler`.
5. Server sends `StatsSnapshotS2C` to that player.
6. Client stores snapshot in `ClientStatsCache`.
7. UI mixins render stats from `ClientStatsCache`.

Optional UI mod not installed:

- Client sends no requests.
- Server still may collect stats when `enabled = true`, but no UI exists.
- No fallback screen.

Singleplayer:

- Integrated server collects samples.
- Local client sends `StatsRequestC2S` to the integrated server.
- Integrated server replies with `StatsSnapshotS2C`.
- UI renders exactly as it does on a dedicated server.

## Config

Server config:

```text
enabled = true
showChatMessages = true
notifyOnDelayed = true
```

Client config:

```text
showInTree = true
```

`showChatMessages` controls the public Ctrl-click TTC details and the private reset confirmation. Reset still works when this is false.
`notifyOnDelayed` controls chat only: the private delayed and blocked messages
sent to the craft owner. The server owns generation; the recipient remains the job initiator. Plates, plate clears, and login resync ignore this setting and always sync while the craft stays delayed.

Config storage is loader-specific, but ownership stays the same: `enabled`,
`showChatMessages`, and `notifyOnDelayed` affect server behavior, while `showInTree` affects local
display only.

## Version Layout

Shared module:

- `CraftProfiler`
- `ProfileKey`
- `ProfileStats`
- packet DTOs if they can stay Minecraft-free
- client display cache if Minecraft-free

`versions/<minecraft>-<loader>`:

- loader packet registration and send helpers
- packet encode/decode/handlers or payload codecs
- AE2 mixins
- optional UI mixins
- AE2 key conversion: `AEKey` to output id

## Security / Trust

This is observational data and the risk is low, but the boring safety rules
still apply:

- client requests are hints only
- every collection and string is bounded while decoding, before allocation
- clients send structured chat actions, never server-relayed text
- server computes all stats
- server sends only aggregate stats, not pending tasks or machine internals
- packets should be handled on the correct server/client thread

## Sources

- Forge SimpleImpl networking docs: https://docs.minecraftforge.net/en/latest/networking/simpleimpl/
- Forge community packet targeting notes: https://forge.gemwire.uk/wiki/Sending_Packets
- AE2 local source inspected: `CraftingCpuLogic`, `ExecutingCraftingJob`, `ICraftingPlan`, `ICraftingCPU`
