# Provider Locate Technical Design

Lifecycle: see scope status and evidence for [baseline](spec.md), [shared-provider icons](spec.md#planned-shared-provider-icon-correction), [resource icons](resource-icons/spec.md), and [red sky beams](spec.md#planned-red-sky-beam).

## Research findings

Issue #231 asks for three facts the delayed warning does not currently have:
which provider ran the craft, a red "delayed" word, and state that survives a
world reload.

AE2's supported APIs expose the needed seams:

- `CraftingService.getProviders(IPatternDetails)` returns the providers that
  offer a pattern right now.
- `IGrid.getNodes()` plus `IGridNode.getService(ICraftingProvider.class)`
  identifies which grid node hosts a given provider object by identity.
- `InWorldGridNode.getLocation()` turns such a node into a `BlockPos`. This
  is the same recipe AE2 Network Analyser's `wrapGridNode` uses; nodes that
  are not in-world resolve to nothing and are skipped.

The current mod already observes every pattern dispatch with its outputs:

```text
CraftingCpuLogic.executeCrafting  -> ProfilerBridge.observeProviders(scope, pattern, outputs, hasProvider)
AdvancedAE/ECO/TimeWheel          -> the same ProfilerBridge method
```

`observeProviders` builds the pattern-to-output map and then drops patterns
that have a provider. Only the missing-provider failures are retained. The
location work therefore needs no new mixin: retain the successful patterns
per crafting CPU and output, and resolve positions later, at notify time, so
a provider that moved between dispatch and stall never yields a stale box.

Intercepting the exact `pushPattern` acceptor was rejected: it would touch
four different CPU dispatch paths, including addon-owned logics, while
resolving current candidates at notify time is enough for the first version.

## Server state

Add a Minecraft-facing (not pure-core) tracker beside `CraftProfiler`,
keyed by crafting CPU object identity with network-scoped `ProfileKey`s:

```text
dispatchedPatterns: Map<CpuScope, Map<ProfileKey, Set<IPatternDetails>>>
```

`ProfilerBridge.observeProviders` records the pattern for each of its output
keys. Only the standard and AdvancedAE dispatch paths call it today; NeoEco
and TimeWheel jobs keep working warnings with plain names until their
dispatch paths expose patterns too. `ProfilerBridge.startJob` and
`finishJob` clear the scope, alongside the existing pending/owner cleanup,
so a new job starts a fresh link set.

Position resolution runs only on the notify path with the live grid:

```text
for each stored pattern for (scope, key):
  craftingService.getProviders(pattern)
    -> for each provider, scan grid.getNodes()
    -> keep node.getService(ICraftingProvider.class) == provider
    -> keep InWorldGridNode locations only
```

Cap resolved positions at `PacketLimits.MAX_HIGHLIGHT_POSITIONS` (16).
Dimension comes from the grid pivot level. An empty result means "no
locatable target": the name renders as plain text.

Delayed notify sends two things independently: a plate-only highlight sync
that never needs chat, and chat itself gated by `notifyOnDelayed`. When the
owner is offline the transition is not consumed, so it still fires on
reconnect. Login resync (`ProfilerBridge.resyncPlatesForPlayer`, on every
loader's join event) re-sends plates for still-delayed crafts with
server-side broken-target filtering; chat is never re-sent there.

Blocked warnings (`NO SPACE`, `NO POWER`) share the message shape and click
records but never touch highlights. Their once-per-episode memory lives in a
tiny pure-core tracker (one instance per reason) keyed by crafting CPU identity:

```text
tick: blockReasons(scope, grid, tick) filtered to NO POWER -> poll episode
tick: probe(logic).isCantStoreItems + stored/outstanding per output -> poll episode
status request: NO POWER backup through the same episode memory
```

Power observation runs on the standard and AdvancedAE dispatch paths, so
`NO POWER` warnings cover those CPU types. The `NO SPACE` probe reads the
AE2-mirrored status methods every CPU logic in the mod supports (`isCantStoreItems`, `getAllWaitingFor`, `getStored`,
`getWaitingFor`) through reflection, so addon-owned logics need no direct
type reference; logics without those methods simply never report. A key
counts when stored items exist with nothing still outstanding, mirroring the
client row predicate. Finishing a job or reloading runtime state clears both
episode memories beside the other per-scope cleanup. Clearing a blocked
episode never clears red: only the delayed lifecycle (recovery, finish,
cancel) or provider break removes plates.

Locate records live in a bounded server registry:

```text
recordId (UUID) -> owner UUID, dimension id, positions, output name, created tick
```

Cap the registry at 256 records with eldest eviction. Records are
click-scoped: a locate click only serves a record owned by the clicking
player. Finished, cancelled, and broken records are removed so only live
links persist and stale chat links expire.

The persisted fallback is per profile key (network + output), not per record:

```text
ProfileKey(networkId, outputId) -> owner UUID, dimension id, positions, display name
```

Cap at 512 entries for persistence size only; active fallbacks are never
silently dropped to fit the cap. Live dispatch data always wins; the
persisted copy only fills gaps after a reload. The stored dimension travels
with the fallback so login resync never re-derives it from the network id
alone. Persist it in the existing world `SavedData` beside `outputs`, read
tolerantly so old saves load with empty provider state and no save-version
bump is needed. Rainbow edges are never persisted.

## Command and packet flow

The item name carries `ClickEvent(RUN_COMMAND, "/ae2craftingtime locate
<recordId>")`. Raw coordinates never travel through the click, so the packet
cannot be abused to probe arbitrary positions.

One shared Brigadier builder lives in Minecraft-facing code; each loader
registers it on its own command event. The tree is open to every command
source; the handler itself validates the player and record ownership, and
non-player sources get no answer:

- 1.20.1 Forge: `RegisterCommandsEvent` on the Forge event bus;
- 1.20.1 Fabric: `CommandRegistrationCallback`;
- 1.21.1 and 26.1.2 NeoForge: `RegisterCommandsEvent` on the NeoForge event
  bus.

The handler loads the record, rejects foreign or missing records with the
expiry notice, and otherwise sends the highlight packet to the clicker only,
followed by a private "Highlighting <provider> at <coords> in <dimension>"
system message built by `DelayedChatText.highlightingMessage`. The message
names the provider block at the first resolved position
(`Block.getName()`, with a generic `chat.provider` fallback) instead of the
crafted item, and every coordinate is an underlined literal with a
`RUN_COMMAND` `/tp @s x y z` click and a `chat.teleport.hint` hover, joined
with ", ". The builder lives in the per-version `DelayedChatText` copies
because the click/hover constructors differ between the 1.20.1/1.21.1 and
26.1 mappings; the shared `ProviderLocateCommand.providerName` helper
resolves the name from the level. `chat.highlighting` keeps three
placeholders in both languages. The double-click path
(`ProviderLocateServer.locate`, both source sets) sends the same message
after its highlight. All three send points were reworked for
[issue #241](https://github.com/cTux/ae2-crafting-time/issues/241).
No packet layout changes anywhere in that batch, so no compatibility
boundaries moved.

The highlight packet carries `network id, dimension id, positions, output id,
duration seconds, plateOnly, optional typed display key`. The output id remains
the profile key id; the typed key supplies the plate icon. `plateOnly = true` means "red plate only, no
rainbow edge" (automatic delayed pings and login resync). `plateOnly = false`
means "rainbow edge only, no plate change" (chat-link and double-click
locates). An empty-positions packet with duration zero means "clear this
plate, keep rainbow". Each loader wraps it in its own S2C packet following the
existing snapshot pattern (additive `networkId` tail with tolerant reads for
older packets):

```text
automatic delayed ping (no click, no open window needed)
  -> ProviderHighlightS2C(network, dimension, positions, output id, 15s, plateOnly=true)
  -> client shows red plate only

locate click (command, runs as the clicker, silent)
  -> record lookup (owner must match clicker) + active-job + valid-target check
  -> ProviderHighlightS2C(network, dimension, positions, output id, 15s, plateOnly=false)
  -> client shows rainbow edge only

 double-click any resolvable active crafting item (ProviderLocateC2S(output id), no record)
   -> resolve the clicker's open CPU scope and grid, require job ownership
   -> live positions resolve -> same edge-only highlight packet to the clicker only
   -> nothing resolves -> private expiry notice

 stall recovery / finish / cancel
   -> ProviderHighlightS2C(network, "", [], output id, 0, plateOnly=false)
   -> client clears that plate only, rainbow survives until expiry
  ```

 Clicking the chat link closes the chat via a `ChatScreenMixin` (one copy
 per version group, same fully qualified name) that watches vanilla
 `handleComponentClicked` at return and closes the screen only when it
 handled our own `/ae2craftingtime locate` run-command. A double-click
 locate closes the CPU screen the same way, right after the request is
 sent.

Adding the packet changes the wire registry. Current boundaries (same commit):

- 1.20.1 Forge channel protocol: `21`;
- 1.20.1 Fabric: `provider_highlight_v5` for plates + edges, plus
  `provider_locate_v1` for the double-click request (existing
  channels keep their versions because their layouts do not change);
- 1.21.1 and 26.1.2 NeoForge registrar version: `20`.

The typed-key tail is bounded to 16 KiB. Older packets and saves without it
still decode and show a plate without an icon; unknown optional key types
leave the plate visible.

## Client behavior

A small client store keeps independent rainbow edges (network, dimension,
positions, output, expiry) and persistent red plates (network, dimension,
positions, output). Identity is job + network + dimension + output +
provider: identical outputs on different CPUs or networks track
independently and two rainbow targets never replace each other. Both blink
on a shared one-second pulse. Manual locates
touch edges only; craft-state changes touch plates only:

- Automatic delayed ping (`plateOnly=true`) shows the plate with no edge.
- Chat-link or double-click (`plateOnly=false`) shows the edge for 15
  seconds with no plate change.
- Empty-positions clear drops the plate only; rainbow survives until expiry.
- Empty manual request clears the edge only; the plate survives.

Plates are server-authoritative. `shouldShowPlates` never consults the UI
stats cache, so opening another CPU, the planning screen, or closing all
windows cannot hide a still-delayed plate. `prunePlates` is a no-op kept for
loader compatibility. Active plates and edges are never silently evicted:
every identity (network, dimension, output) persists until an explicit
server clear, provider break, or session end. Two locates within 15 seconds
stay independent.

Finish cleanup includes notified and queued-for-clear outputs, even when the
last returned item has already removed them from pending work. A notification
poll with no pending outputs queues their clears instead of discarding them.
This covers releasing a machine's held final output and finishing in the same
tick, without an intermediate recovery update or an open terminal screen.

Session end (disconnect, world switch) clears all plates and edges on every
loader. Red plates return only through server login resync for crafts still
delayed; rainbow timers are never serialized or restored.

A broken-provider trim runs in every render hook, in that dimension only:
positions whose actual target is gone (air, missing block entity,
replacement non-provider, surviving host without provider service) drop
from edges and plates. Unloaded chunks and unreadable grid stay unknown and
keep the highlight, so reload never clears intact red. The same
`ProviderBlockTargets` check runs on all loaders and on the server during
login resync and chat-link validation.

Render hooks per loader:

- 1.20.1 Forge: game-bus subscriber on the translucent-particles render
  stage;
- 1.20.1 Fabric: `WorldRenderEvents.AFTER_TRANSLUCENT`;
- 1.21.1 NeoForge: game-bus subscriber on the render-level stage event;
- 26.1.2 NeoForge: game-bus subscriber on the render-level stage event for
  edges and plates, plus a `SubmitCustomGeometryEvent` subscriber for the
  item icons (the 26.1 submit pipeline requires items to go through its
  collector).

Fabric owns an immediate buffer for this late render hook and flushes all of
it before returning, including item icons. Vanilla has already flushed its
world buffers before `AFTER_TRANSLUCENT`; leaving icons in those buffers can
draw them later with the wrong depth contents, over an intervening terminal.
The dedicated buffer keeps plates and icons in the current world pass without
flushing another renderer's queued work.

Edge color cycles rainbow hues on a time-based phase (instead of static
red) so the box contrasts with any environment. Vanilla
`RenderType.lines()` width is fixed, so thicker edges use multi-offset
strokes on 1.20.1/1.21.1 and the `ShapeRenderer` line-width path on 26.1.2.
Face plates are thin red filled boxes (`debugFilledBox` on 1.20.1/1.21.1,
`debugFilledBox` on 26.1, where both pipelines are `QUADS`-mode) with the
output item rendered item-frame style (`FIXED` display context) at half
scale; the client renders the packet's typed resource through AE2 and keeps
the plate visible when no renderer is available. Only faces pointing toward
the camera render (at most 3 per block). On 1.20.1/1.21.1 each plate is one
thin box from vanilla `LevelRenderer.addChainedFilledBoxVertices`, flushed
with its own `endBatch` per face, after the invisible-plate follow-up in
[issue #241](https://github.com/cTux/ae2-crafting-time/issues/241):
`debugFilledBox` there is `TRIANGLE_STRIP` with culling and has no vanilla
callers, so appending each face (and the item icon writes between faces)
to one shared strip continued the strip out of phase and the culled
pipeline dropped the plates; every face now uploads as its own
self-contained strip before its icon draws. Raw vertex calls differ
between 1.20.1 (`vertex`/`endVertex`) and 1.21.1 (`addVertex`/`setColor`),
so the shared shapes go through the vanilla static, which exists
identically on both (verified against the 1.20.1 sources and the mapped
1.21.1 jar). On 26.1 the same quads keep feeding its `QUADS`-mode
`debugFilledBox` pipeline unchanged (that matches vanilla
`DrawableGizmoPrimitives`, which accumulates quads into one filled-box
buffer). Every loader keeps driving the box
opacity from one shared one-second pulse so the highlight blinks instead of
sitting static.

Render hooks batch one render type per pass and never hold a non-fixed
consumer across other-type writes: on 1.20.1/1.21.1 the line and filled
buffers share one fallback builder, so writing plates or items and then
reusing a cached lines/filled consumer hits an ended builder and crashes
(`BufferBuilder not started`, fixed for issue #237 follow-up). Edges draw
for all positions first, then plates and icons; each plate face is
flushed with its own `endBatch` before its icon draws (see
[issue #241](https://github.com/cTux/ae2-crafting-time/issues/241)).

The dev-client log for that follow-up also showed the chat auto-close
mixin never applying on 1.20.1 (`handleComponentClicked` is declared on
`Screen`, not `ChatScreen`, so the subclass target never resolves).
The injection now targets `Screen.handleComponentClicked` and returns
early for non-chat screens. Because the 1.21.1 toolchain cannot remap
vanilla targets while the 1.20.1 production mappings need the remap, the
mixin ships as twins: `ChatScreenMixin` (`remap = false`, listed only in
the 1.21.1 config, which the 1.20.1 builds still compile harmlessly) and
`ChatScreenMixinSrg` (remapped, listed only in the shared 1.20.1 config
and excluded from the 1.21.1 compile). 26.1 keeps its own private-method
mixin unchanged.

A delayed row's tooltip appends the gray `locate_hint` line right after the
stall breakdown (only when a stall is present, so only locatable rows offer
it), just before the shared Ctrl-Click details hint; other rows are
untouched (see
[issue #241](https://github.com/cTux/ae2-crafting-time/issues/241)).

The warning message splits the status word out of the sentence so it can be
styled without breaking translation order:

```text
chat.delayed: "%s %s: no output for %s (typically %s)"
chat.delayed.word: "is delayed" / "затримується" (red)
```

The name is underlined with a hover hint while clickable. Placeholder counts
stay matched between English and Ukrainian. Blocked warnings use one shared
three-placeholder sentence with a per-reason red status word and the
existing reason explanation as detail. Two meanings stay explicit:
double-click means "any active crafting item" (`ProviderLocateClick`
accepts any non-blank id; the server validates resolvability), while
`notifyOnDelayed` means "chat only" and never gates plates, clears, or
resync.

## State flow

```text
pattern dispatched
  -> retain pattern per (CPU scope, output key)

output becomes DELAYED, owner online
  -> resolve provider positions through the live grid
  -> create locate record, persist per-key fallback (network + dimension)
  -> send plate-only highlight (red plate, no edge) + private warning with
     clickable red-accented message when notifyOnDelayed allows chat

manual locate (chat click or double-click any active item)
  -> record lookup (owner must match) + active-job + valid-target check
  -> edge-only highlight packet -> thick rainbow boxes for 15s, red unchanged
  -> private "Highlighting <provider> at <coords>" message, close originating screen

click with foreign/missing/finished/broken record
  -> private expiry notice, no highlight, forget broken record

TTC returns to normal while craft still runs
  -> clear plate only, rainbow continues until expiry

job finishes / scope cleared / profiler disabled
  -> clear plates (even with closed screen), drop live links, records for
     that scope, and per-key fallback on next save; rainbow survives

provider breaks
  -> render trim (client) and resync/link validation (server) drop only that
     provider's positions from plates and edges

leave and re-enter world
  -> client clears all plates and edges; per-key fallback + records load from NBT
  -> login resync re-sends plates for still-delayed crafts (chat never re-sent)
  -> rainbow never restored; still-delayed craft warns again when owner is online
     and offline-delayed crafts resync without needing an open window

blocked (NO POWER / NO SPACE)
  -> chat with clickable edge-only record, no plate, no fallback update
  -> clearing the episode never clears red
```

## Failure handling

- Missing grid, empty pattern set, or no offering providers: plain name, no
  click action, warning still sent.
- Providers without world positions are skipped silently.
- Oversized or malformed highlight packets are rejected before allocation;
  positions beyond the cap are never stored.
- Mixed client/server versions fail the loader's compatibility boundary
  instead of decoding an unknown packet.
- A command from a non-player source or without permission does nothing.
- Command and render registrations are client/server separated so a
  dedicated server never loads client classes.

## #443: one display per provider position

Research baseline: `818809c57cf06540b23430bb4836e06f7fd82466`.
This section describes a planned correction for
[issue #443](https://github.com/cTux/ae2-crafting-time/issues/443).
The report supplies no exact game or loader version. Source inspection proves
duplicate submissions on all four targets; a controlled in-game reproduction
has not yet run.

`ProviderHighlightClient` correctly retains plates in a `LinkedHashMap` keyed
by network, dimension and output. `showPlate` replaces an existing identity
without changing its insertion order. `plates()` returns every identity.
Each renderer then loops over every plate and every position without selecting
one output for a shared position. The face transform depends only on position
and direction, so different outputs use the same center, scale and depth.

| Current source | Relevant boundary |
| --- | --- |
| `shared/src/mcCommon/java/com/ctux/ae2craftingtime/mc1201/DelayedNotificationServer.java` | Sends one automatic plate per newly delayed output. All four S2C handlers call `showPlate`; recovery clears only the matching identity. |
| Both `ProfilerBridge` copies in `shared/src/mc1201` and `shared/src/mc2612` | Finish/cancel cleanup and login resync preserve independent output lifetimes. No change needed here. |
| `shared/src/mcCommon/java/com/ctux/ae2craftingtime/mc1201/ProviderHighlightClient.java` | Owns retained order, raw plate snapshots, position trimming and session cleanup. Add a separate rendering view here; retain the raw `plates()` contract used by tests and the driver. |
| Forge 1.20.1 and NeoForge 1.21.1 `ProviderHighlightRender`, Fabric 1.20.1 `Ae2CraftingTimeClient` | Currently loop over raw plates, then call the mc1201 `ProviderHighlightShapes.renderFacePlatesAndIcons`. |
| NeoForge 26.1.2 `ProviderHighlightRender` | Both `onRenderLevelStage` and `onSubmitGeometry` independently loop over raw plates. Both must consume the same selection rule. |
| Both `ProviderHighlightShapes` copies and shared `ProviderFaceIcons` | Already center icons and select camera-facing faces. Preserve these geometry and buffer boundaries. |

Select once per `(dimension, block position)`, in retained candidate order.
The first candidate wins, including when another network identity references
the same physical position. Duplicate positions within one candidate also
collapse. Selection is derived from current retained state and does not delete
losers or change their positions, timestamps or network/output identity.
Different dimensions never compete. Apply the current nonblank-output gate
before selection; do not filter by successful item-registry resolution.

Keep the selection operation Minecraft-free in `shared/src/main/java`, with
tests under `shared/src/test/java`. The shared client adapter converts positions
to that operation's value representation and returns selected render entries.
Use ordinary ordered collections; no new dependency, persistent winner cache,
timer, packet field or configuration is needed. Both 26.1.2 passes derive their
selection from the same ordered state, so unchanged state cannot choose two
different winners. World rendering remains on the existing client thread.

Removing the winner naturally exposes the next retained candidate. Removing a
loser leaves the display unchanged. An update that removes a position releases
only that position; retained insertion order still applies elsewhere. Session
end empties the source state, and resync establishes a fresh retention order.
An unknown/non-item winner renders the existing plate-only fallback. Typed
resource icons remain the separate planned #376 correction.

Preserve the older per-face filled-buffer flush, Fabric's immediate buffer and
26.1.2's separate item-submit phase. Do not change edge enumeration or timing,
server delay detection, persistence, protocol versions, translations or addon
support. No dedicated-server behavior changes are required.

The existing `ProviderPlatesTest` and `ProviderHighlightTriggerTest` cover raw
state and independent lifetimes. `StandardAe2Scenario` supplies the runtime
boundary: its delayed leaf dispatches stone and glass through the provider at
offset 4, retains both identities, checks the single first-retained render view,
locates the selected row, and releases that winner before checking survivor and
rainbow preservation. Its final provider at offset 8 retains the existing
completion and cleanup coverage.

## Red sky beam design

Planned for [issue #488](https://github.com/cTux/ae2-crafting-time/issues/488).
Source baseline: `13176744d3edb08638b7e824e326e77b99bd1f9b`.
The earlier #443 investigation above is historical: this baseline already has
`ProviderDisplaySelection.firstByPosition` and `renderPlates()` on all targets.

### State and ownership

The corrected scope includes all eight warning statuses in the spec, not only
DELAYED. Earlier delayed-only lifecycle and blocked-warning descriptions in
this document are the existing behavior, superseded by this planned section.
`TtcText.blockReason`, `TtcText.noSpace` and the delayed rendering identify the
warning set; `CraftingBlockReason` contains the six blocked reasons. Do not
derive eligibility by inspecting RGB values or client UI caches.

Extend the shared server highlight reconciliation at the existing CPU tick
boundary. Combine current delayed evidence, `ProfilerBridge.blockReasons` with
its current freshness/pending rules, and `NoSpaceProbe.stuckKeys` using the same
stored-only predicate as the row. The current `blockReasons` merges live and
persisted remembered reasons: expose its live calculation for plate eligibility
without restoring transient plate causes from that merge. Reuse these predicates
rather than inventing another delay threshold. Call reconciliation even when the result is empty,
offline or chat-disabled, and without requiring `StatsRequestHandler` traffic.

Keep runtime contributions by job/scope, owner, network, dimension and output,
with their validated target positions. Reconcile the union per recipient and
wire identity before sending: send the existing plate packet when positions
appear/change, send an explicit clear only when its final contribution is gone.
Changing warning reason without changing targets requires no clear or resend.
Ending one CPU/job must not clear another owner's or surviving job's marker.
Reuse existing collections where possible; a small runtime reconciliation map
is appropriate, but a second client timer/state machine is not.

Own `DelayedNotificationServer`, both `ProfilerBridge` copies (tick integration,
finish/disable/reload cleanup and `resyncPlatesForPlayer`) and the existing CPU
tick callers. Separate highlight reconciliation from once-per-episode chat in
`BlockReasonNotifier`; its chat set remains NO POWER/NO SPACE. Delayed chat also
retains its existing policy. All old direct delayed-only clear paths must route
through the union check. Check standard, AdvancedAE and existing optional CPU
callers without adding new addon detection or claiming unsupported statuses.

Provider resolution must work before first successful dispatch. Reuse
`ProviderStartTracker` pattern observations and the actual failed-dispatch
provider candidates from `ProviderDispatchObserver`; retain bounded associated
positions with that evidence when inactive providers disappear from a fresh
offering lookup. Resolve and validate targets on the server with existing
`ProviderBlockTargets` rules. Never scan arbitrary nearby blocks or choose an
unrelated provider. NO PROVIDER with no surviving validated association yields
no marker. Preserve unknown/unloaded handling without force-loading chunks.

Reconnect sends current owner-bound contributions, with a first tick refresh
when live state is not ready. Restart rebuilds transient reasons from fresh
dispatch evidence; no new persisted reason or stale remembered-status fallback
is allowed. Existing saved delayed/provider data may retain its existing role
only for a still-valid active job. Re-evaluate NO SPACE from live CPU contents.

### Chat locate provenance and lifetime

`ProviderLocateCommand.locate` handles the owned chat record and validates its
provider targets. `ProviderLocateServer.locate` handles the crafting-row request.
Both currently produce the same manual highlight payload; the client cannot
infer its source from positions or plate state.

Add one chat-locate boolean to `ProviderHighlightCodec.Highlight`, every loader's
`ProviderHighlightS2C`, and the client `Highlight` record. Only the successful
`ProviderLocateCommand` path sets it. Automatic sends, clears, row locates and
compatibility constructors default to false. Keep ownership and target validation
on the server. Typed execution of the same owned locate command follows chat-link
semantics; do not add a client click detector or trust client UI provenance.

Store the flag in the existing edge entry with its positions and expiry. A chat
locate replaces one entry with the flag set; a row locate replaces it with the
flag clear, so it cannot extend an earlier beam. Preserve the flag when trimming
positions. Existing expiry and session cleanup remove both transient effects;
plate clears never modify the pair. Add no separate beam map or timer.

Select beam positions from live chat edges after target trimming and dimension
filtering. Reuse `ProviderDisplaySelection.firstByPosition` to draw once per
physical position without deleting overlapping locate identities. A surviving
chat edge keeps the beam when another expires. Use the same frame pulse for
rainbow and beam. `renderPlates()` stays independent: neither plate presence nor
icon resolution gates the beam.

The codec currently rejects trailing bytes. Coordinate the new layout by bumping
Forge protocol 24 to 25, both NeoForge registrars 23 to 24, and Fabric's highlight
channel from `provider_highlight_v5` to `provider_highlight_v6`. Retain existing
optional-channel guards; never send new bytes under the old identifier. Update
protocol tests and docs together. Preserve legacy decoder cases with chat
provenance defaulting to false and keep malformed/trailing-data rejection.
No save migration is needed; edge provenance and timers are never persisted.

### Geometry and render boundaries

Render a vertical translucent, full-bright red column, centered at
`(x + 0.5, z + 0.5)`, starting at `y + 1`, with a 0.2-block square cross-section.
Use the plate red `(1.0, 0.15, 0.15)` and the rainbow highlight's pulse opacity.
End it at
`max(dimension upper build boundary, provider top + client render distance in
blocks)`. This keeps a positive column above high providers and reaches above
dimension roofs without scanning blocks or hard-coding Overworld height.
Normal camera/fog distance still applies; infinite-distance visibility is not
part of the feature. Do not cull the whole column just because its base is
outside the camera frustum.

Use a beam-specific render type/pipeline with translucent blending, no culling,
no depth test and no depth writes, so opaque geometry cannot hide the column
and it cannot corrupt depth for later draws. Keep the existing plate/icon/edge
pipelines unchanged. Define render state in that pipeline, not global depth
toggles around deferred buffered writes. Flush its batch at the world hook
before returning, and never retain a consumer across writes to another type.
No vanilla beacon block entity, beacon sky-access check, texture or dependency
is needed: use the existing filled-shape emission approach with the dedicated
state. Restore the pose stack and let pipeline setup/teardown own render state.

| Owned seam | Planned change |
| --- | --- |
| `shared/src/mc1201/java/com/ctux/ae2craftingtime/mc1201/ProviderHighlightShapes.java` | Add the beam draw entry point using the older filled-shape API. Keep API differences in loader adapters where necessary. |
| `shared/src/mc2612/java/com/ctux/ae2craftingtime/mc1201/ProviderHighlightShapes.java` | Equivalent camera-relative geometry for the newer render API. |
| Forge 1.20.1 and NeoForge 1.21.1 `ProviderHighlightRender` | Submit beams from selected live chat edges in their existing world stage, with a beam-specific render type. |
| Fabric 1.20.1 `Ae2CraftingTimeClient` | Same selection and geometry in `AFTER_TRANSLUCENT`; keep its dedicated immediate buffer and flush before return. |
| NeoForge 26.1.2 `ProviderHighlightRender` | Draw beams once in `onRenderLevelStage` using a dedicated pipeline. Do not submit them again in the separate item-only `onSubmitGeometry` pass. |

Keep the plate store independent from locate provenance. The new packet field
and network versions distinguish chat from row locates; no persisted transient
reasons or world migration are required. Update English/Ukrainian GuideME text,
the GitHub wiki and protocol documentation when implementing. Dedicated servers
must never load rendering classes. All four release targets need implementation.

### Validation and failure boundaries

Reuse `ProviderPlatesTest` and `ProviderHighlightTriggerTest` for automatic
plates versus manual edges, recovery/finish/cancel and session cleanup. Extend
focused checks for chat versus row provenance, failed/foreign requests, common
expiry and refresh, row replacement, shared-provider survivor, empty selection,
dimension filtering and unresolved icon. Check positive geometry height at
both build limits. Do not duplicate the delayed-state machine in tests.

Add server boundary cases for each warning, no-warning inputs, red-to-red
transitions, simultaneous delayed/blocked causes, first-dispatch failure,
shared output on two CPUs, separate owners, NO PROVIDER without a target,
reason expiry and replacement blocks. Verify recovery of DELAYED cannot clear
NO POWER/NO SPACE/dispatch warnings, including no-menu ticks, chat-disabled
operation, offline/reconnect and restart with expired transient evidence.

Visual captures must prove opaque-roof penetration and render-state isolation;
state assertions or compilation cannot prove either. Check from below and
above a roof, beside the provider, and while its base is off-screen but the
column is visible. Observe nearby translucent blocks, item icons and rainbow
edges before and after removal. Missing level/state means no draw. Unknown
unloaded targets keep the existing plate policy; no render-time chunk scan or
fallback beam is allowed. #376 is related icon work, not a dependency.

## Original feature sources checked

- [Issue #231](https://github.com/cTux/ae2-crafting-time/issues/231).
- Repository code: `CraftProfiler`, `ProfilerBridge` (both source sets),
  `DelayedNotificationServer`, `StatsRequestHandler`, `StatsNetwork` and
  packet records on all four loaders, `Ae2CraftingTimeSavedData` on all four
  loaders, `PersistedSamplesTag`, `PacketLimits`, `ClientStats`.
- Local AE2 artifacts: `ICraftingService.getProviders`,
  `IGrid.getNodes`, `IGridNode.getService`, `ICraftingProvider`,
  `IPatternDetails` from AE2 Forge 15.4.10; `InWorldGridNode.getLocation`
  call shape confirmed in the cached AE2 Network Analyser jar
  (`ItemNetworkAnalyzer.wrapGridNode`).
