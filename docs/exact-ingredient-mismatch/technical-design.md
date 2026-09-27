# Exact ingredient mismatch diagnostics: technical design

Lifecycle: see the [scope status and evidence](spec.md).

Historical planning status: planned. Implements the [specification](spec.md).

## Evidence

The source report shows a simulated plan missing a Mekanism energy cell. The
reported fix was to craft the lower tier and re-encode each following pattern
with the produced item; a second player reported the same symptom with tanks.
The discussion suggests saved item data, but does not prove which field differs.
The feature therefore reports only the near-match that the server can verify.

AE2's `CraftingPlanSummary.fromJob` builds exact-key rows, then simulates an
extraction of each key from the active grid storage to calculate `storedAmount`
and `missingAmount`. `AEItemKey` equality includes NBT on AE2 15 and the item
stack component patch on newer AE2 versions. `AEKey.getPrimaryKey()` identifies
the registered item independently of secondary data.

Inspected upstream source anchors:

- [AE2 15.0.10 summary construction](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/ffaf14d7535b3dc643e04b8407db5419ef51bee8/src/main/java/appeng/menu/me/crafting/CraftingPlanSummary.java)
- [AE2 15.0.10 item-key identity](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/ffaf14d7535b3dc643e04b8407db5419ef51bee8/src/main/java/appeng/api/stacks/AEItemKey.java)
- [AE2 19.0.24 item-key identity](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/330e16349549e9976f0891cc37291a6407f6599a/src/main/java/appeng/api/stacks/AEItemKey.java)
- [AE2 26.1.10-beta summary construction](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/3a051bb473de0b8fd329b39db4262f731d17e7e5/src/main/java/appeng/menu/me/crafting/CraftingPlanSummary.java)
- [AE2 26.1.10-beta item-key identity](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/3a051bb473de0b8fd329b39db4262f731d17e7e5/src/main/java/appeng/api/stacks/AEItemKey.java)

These are source checks, not runtime reproduction evidence. Implementation must
verify descriptors against both 1.20.1 artifacts and both NeoForge targets.

The current base already includes #320's recurrence carrier, summary revision,
chunk validator and codec, four loader packet adapters, and client-thread
observation fixtures. Extend those seams; do not add a second native-summary
revision or replace recurrence flags. Both 1.20.1 targets register
`CraftConfirmMenuMixinSrg` through shared mc1201 resources; both NeoForge
targets register `CraftConfirmMenuMixin` through their target resources.

AE2 exposes `IStorageWatcherNode.onStackChange` and `IStackWatcher.setWatchAll`.
`StorageService` owns the interest manager and calls registered watchers when
its server-end-tick inventory refresh detects changed amounts. The same
constructor, callback, and destruction seams are present in the inspected
15.0.10, 19.0.24 and 26.1.10-beta sources:

- [AE2 15 storage service](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/ffaf14d7535b3dc643e04b8407db5419ef51bee8/src/main/java/appeng/me/service/StorageService.java)
- [AE2 19 storage service](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/330e16349549e9976f0891cc37291a6407f6599a/src/main/java/appeng/me/service/StorageService.java)
- [AE2 26 storage service](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/3a051bb473de0b8fd329b39db4262f731d17e7e5/src/main/java/appeng/me/service/StorageService.java)
- [AE2 26 watcher lifecycle](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/3a051bb473de0b8fd329b39db4262f731d17e7e5/src/main/java/appeng/me/helpers/StackWatcher.java)

## Server classification

Put classification in a Minecraft-free `PlanStoredVariantDetector` under
`shared/src/main/java`, with a thin mcCommon adapter converting AE2 keys,
primary identities, amounts, and original row indices. Run it after the native
summary is created and again only after relevant storage notifications.

Snapshot `grid.getStorageService().getInventory().getAvailableStacks()` once
per initial installation or dirty refresh.
Build a set of positive exact item keys and a set of their primary item
identities. For each native summary row, set its flag only when:

```text
row.missingAmount > 0
row.what is AEItemKey
stored candidate amount > 0
candidate is AEItemKey
candidate.getPrimaryKey() == row.what.getPrimaryKey()
candidate != row.what by exact AEKey equality
no positive stored exact key equals row.what
```

Do not compare display names, serialized strings, tooltip text, profiling IDs,
damage alone, or mod-specific fields. Do not call storage extraction again or
re-run AE2's substitution logic. Primary membership plus exact-key absence
proves a different stored key without enumerating candidates per row. Work is
linear in available storage keys plus summary rows. The snapshot is local to
one refresh and never changes storage or the native summary amounts.

The detector returns original summary row indices, before any client sorting.
An initially empty result sends no payload. An empty result after a warning
must send replacement masks that clear previously flagged chunks. The detector
does not mutate the job, storage, pattern, profiler, or native summary data.

## Observe storage changes

Add a small mcCommon `StorageService` accessor for
`InterestManager<StackWatcher<IStorageWatcherNode>>`. A menu-owned adapter
constructs AE2's existing `StackWatcher` with that manager and enables
`setWatchAll(true)`. Public watcher registration requires a grid node; this
accessor avoids adding nodes, channel demand, power use, or persistence.
Register only while a valid server menu has positive missing item rows.

The callback filters to those rows' primary items and only marks the plan dirty.
Watching all keys is necessary to notice a variant not present in the initial
snapshot. Do not scan, send packets, destroy watchers, or mutate the interest
manager inside its callback iteration. Coalesce relevant notifications into one
fresh storage snapshot at the next normal menu broadcast. When nothing changed,
do not poll storage. AE2's own watcher refresh remains tick-driven.

Bind the watcher to the actual plan and grid objects. At each normal broadcast,
check menu validity, active player menu, and current grid identity before any
refresh/send. On native replacement, destroy the old watcher, clear old state,
and bind the new plan. On grid replacement/loss, send zero masks for prior flags
when the same menu is still active, destroy the watcher, and suspend diagnosis
of that old summary until AE2 generates a new one. Never classify an old plan
against a new network. Removal, cancellation that closes the menu, disconnect,
and a plan with no positive missing item rows release the watcher too.

Registration/destruction run on the server thread and are idempotent. Keep
dirty filtering and lifecycle decisions in covered pure-Java state; the AE2
adapter owns references and delegates. Recheck actual artifact descriptors and
exercise cleanup on all four targets, since the accessor is an internal API seam.

## Transport and lifecycle

Add a mod-owned `PlanStoredVariantsS2C` payload to the existing `StatsNetwork`.
Do not change AE2's native plan packet. Reuse #320's server/client summary
revision. Extend its client clear-on-`setPlan` hook to clear stored-variant flags
and update watermarks independently of recurrence. Keep native-summary revision
changes independent from storage refreshes.

Send initial diagnostic chunks after AE2's native plan packet from the same
broadcast; send later changed chunks at the next dirty broadcast. Each chunk
contains `containerId`, positive `summaryRevision`, `entryCount`, `offset`,
`rowCount` from 1 through 256, a fixed 32-byte replacement mask, and a positive
`updateRevision` (a positive VarLong). On the wire, write/read that revision first, followed by
the existing chunk fields through `PlanRecurrenceChunk` and
`PlanRecurrenceCodec`. Its existing end-of-buffer validation then still applies
without changing recurrence packets. Keep the outer decoder bounded and reject
malformed/truncated revisions or trailing bytes.

Each refresh that changes flags increments a menu-local update revision and
sends only chunks whose masks differ from their previously sent state. Send
zero masks to clear old warnings. Apply every bit in an accepted chunk as a
replacement, not an additive set. Row indices address original summary order.

The client handler runs on its normal client executor and requires the active
`CraftConfirmMenu`, matching container, revision, and installed entry count.
Require nonnegative `offset`, `offset % 256 == 0`, and
`rowCount == min(256, entryCount - offset)` using overflow-safe checks. Reject
bits outside `rowCount`. Never allocate from packet-controlled `entryCount`;
validate it against the installed native summary. Allocate per-chunk update
watermarks only from that trusted summary size. Reject nonpositive update
revisions and revisions no newer than the addressed chunk's watermark. Use
per-chunk watermarks, not one global watermark: omitted unchanged chunks and
reordered deliveries must remain independent. Set flags only on positive
missing `AEItemKey` rows; clear false bits throughout the valid chunk. Invalid,
early, late, duplicate, or mismatched chunks do nothing. Reset watermarks on
native plan replacement; never buffer early packets or build a full snapshot
from packet-controlled sizes. Fail closed by clearing/suspending this diagnostic
if a local revision counter would overflow; never wrap an identity counter.

Packets go only to the player who already received the server-authorized plan.
There is no C2S request, selected-network input, cache keyed by display name, or
world persistence. A closed menu, reconnect, or different container discards
late data. Connection order must be verified on every loader.

Forge appends the message and increments the current channel protocol.
NeoForge appends a clientbound payload and increments each current registrar
version. Fabric registers `plan_stored_variants_v1` and checks receiver support
before sending. Existing packet layouts stay unchanged.

Extend #320's existing row-flag carrier with a separate stored-variant field.
Its recurrence packet layout and additive recurrence behavior stay unchanged.

## Client rendering

Add a transient stored-variant flag carrier to `CraftingPlanSummaryEntry`. In
the shared `CraftConfirmTableRendererMixin`, append the neutral normal-weight
`Stored variant` component only when the flag is set, `missingAmount > 0`, the
key is an item, and the mod is enabled. Append both explanation lines to the
tooltip before the current `craftAmount <= 0` TTC guard so missing-only rows
receive them.

The single mcCommon `TtcText` supplies the three components from matching keys in
`shared/src/main/resources/assets/ae2craftingtime/lang/{en_us,uk_ua}.json`.
Do not replace AE2's Missing/Recurrent component, classify the warning as a TTC
badge, or alter sorting. The flag follows the summary entry object when the
screen sorts a copied list.

## Source sets and failure handling

Keep classification, render predicates, and update/lifecycle decisions in
covered pure Java. Keep AE2 key conversion, watcher access, menu/entry carriers,
renderer delegation, and codecs in mcCommon when signatures match. Update the
existing 1.20.1 `Srg` menu counterpart together with the common menu mixin; use
other adapters only for actual API differences. Register the storage accessor
and server hooks on both physical sides and renderer hooks client-side through
every effective mixin list.

A missing required hook is a compatibility failure during verification. A
malformed or unsupported diagnostic packet may lose the extra warning but must
leave AE2's plan usable. No new dependency, optional-mod integration, saved-data
version, configuration, or permission boundary is introduced.

## Acceptance mapping

| Spec | Design path and required evidence |
| --- | --- |
| V1-V2 | Positive same-primary membership and exact-key absence; real NBT/component fixtures with exact-only and exact-plus-near controls, and live add/remove/restore without replacing the plan. |
| V3-V4 | Positive missing/item predicates and set output; ordinary, fluid, successful, zero-count, multi-variant, and unrelated-row tests. |
| V5 | Container/summary/count validation, per-chunk update watermarks and zero masks, clear-on-summary, watcher cleanup, original row indices and per-player packets; reordered, duplicate, sorted, reconnect and two-network tests. |
| V6 | Shared rendering on all targets in English; static locale/component checks in both languages; coexistence with shipped #320. |
| V7 | Event-triggered read-only snapshots and separate payload; unchanged native plan/result, quantities, grid topology, storage, profiler, persistence and optional screens. |
