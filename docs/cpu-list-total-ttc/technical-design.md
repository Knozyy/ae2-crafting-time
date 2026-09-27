# CPU-list total TTC technical design

Lifecycle: see the [scope status and evidence](spec.md).

Historical planning status: implementation in progress in [PR #381](https://github.com/cTux/ae2-crafting-time/pull/381).
Implements [the specification](spec.md) for
[issue #324](https://github.com/cTux/ae2-crafting-time/issues/324).

## Verified seams

Paths below are relative to the repository root. Java package paths below
`shared/src/*/java` use `com/ctux/ae2craftingtime`.

- `shared/src/mcCommon/java/.../mc1201/StatsRequestHandler.java` currently
  returns one `totalTtcSeconds` and `cpuContext`, for the selected CPU only.
- `StatsRequestContext.cpuContext` combines menu container id and selected CPU
  serial. `current` resolves the actual server CPU and active grid, including
  the existing optional AdvancedAE selected-CPU fallback.
- Both `shared/src/mc1201/java/.../mc1201/ProfilerBridge.java` and the `mc2612`
  equivalent expose `remainingJobSeconds(Object scope)`. This uses
  `core/CraftProfiler` and `core/CraftingJobEstimate`, the authoritative remaining
  dependency-path estimate. Do not add another estimator.
- `core/ClientStatsCache` stores a single context-bound total. `mcCommon`'s
  `ClientStats.totalTtcSeconds()` supplies both API variants of
  `mixin/CraftingCPUScreenMixin`. They use `TimeEstimate.formatTotal`, `TtcText`,
  and `TtcBadge` to draw the title. A CPU-list cache must not be cleared by
  `CraftingStatusMenuMixin`'s selected-CPU-only invalidation.
- AE2 `CraftingStatusScreen` installs `CPUSelectionList` as `selectCpuList`.
  `CPUSelectionList.drawBackgroundLayer` renders the visible slice, and
  `CraftingStatusMenu` owns `cpuSerialMap`, `lastCpuSet`, and `cpuList`.
  Serial numbers identify CPUs within a menu; names and row indexes do not.

Evidence: inspected local AE2 Fabric 15.4.10 sources for the widget and menu,
and `javap -private` signatures in AE2 19.0.24 and 26.1.10-beta. All have the
listed menu fields and widget methods. The 26.1 renderer takes
`GuiGraphicsExtractor`; the older boundary takes `GuiGraphics`. In the inspected
15.4.10 renderer, cards begin at widget origin + (9, 19), advance by button
height + 1, and render names at (3, 3) with 0.8 scale. Use target widget geometry,
not screenshot pixels. Later-target injection descriptors and draw calls must
be checked against their pinned bytecode during implementation.

## Data flow and ownership

Add a small CPU-list request/snapshot pair, separate from output-row statistics.
This avoids sending every CPU's full output stats or consuming the selected
CPU's slot in `StatsSnapshotS2C`. Keep existing output-stat packets unchanged.

1. A common client-only `CPUSelectionListOrderMixin` observes the full raw menu
   list and gives the selected and rendered serials priority. `CpuTtcCache`
   fills the remaining packet slots from a persistent busy-CPU round-robin
   queue. Send at most once per second with one outstanding request.
2. `CpuTtcRequestC2S` carries `containerId`, a client screen-session `long`, a
   nonnegative monotonically increasing request sequence `long`, and at most
   32 positive CPU serials. The selected serial has priority if the cap is ever
   reached. Ordinary AE2 shows six cards, so this covers every visible card.
3. A common `CpuTtcRequestHandler` runs on the server thread. Require an open,
   valid `CraftingStatusMenu` with the matching container id and a live grid.
   Read the menu's existing serial map through a mixin accessor. Resolve only
   requested serials present in both the current menu list and that grid's live
   crafting CPU set. Never call `selectCpu` or assign new serials to service a
   request. The actual `ICraftingCPU` object is the profiler scope; native addon
   CPUs already tracked under that object need no separate estimate adapter.
4. Return one `CpuTtcSnapshotS2C` to that player, echoing session and sequence.
   Its bounded entries contain every requested serial and an optional
   nonnegative seconds value. Missing, idle, unresolvable, or disabled estimates
   are explicitly empty. Obtain values with `ProfilerBridge.remainingJobSeconds`
   after validating live membership. No notifications, storage scan, or profiler
   mutations are needed.
5. A Minecraft-free `core/CpuTtcCache` holds complete observed membership and
   per-job generations. It validates each exact reply set, merges requested
   entries (including explicit unknowns), retains unrelated fresh entries, and
   binds sequences to the active screen session. The common client publishes a
   frozen display snapshot for title, badge, draw, tooltip, and hit-test use.
6. In a `CraftingStatusMenu` screen, route `ClientStats.totalTtcSeconds()` to the
   selected serial in this same cache. Other CPU screens retain their existing
   single-total path. The title mixins now render the selected total as the
   title text rather than a separate badge; the CPU card badge remains;
   list and title format the exact same snapshot value.

## Bounds and lifecycle

- Check counts before allocating on both decoders: 0-32 only. Reject duplicate
  or nonpositive serials, negative sequences/known seconds, and truncated data.
  Reject response entries outside the requested set.
- Rate-limit server requests separately: four packets and 128 serials per player
  per second, counting empty packets. Bound limiter state to connected players
  and clear on disconnect/server stop. Reuse existing limiter code only if it
  supports both limits without changing the output-stat budget.
- Session ids increase per client process and renew for each screen instance.
  They are correlation tokens; authorization comes from the live menu and grid.
- In TTC modes expire each value after
  `max(3, ceil(busy / 25) + 2)` seconds; shrinking membership shortens existing
  deadlines and growth never extends them. AE2 mode uses three seconds and
  prunes background values. Clear on screen/connection changes and reject old,
  consumed, or timed-out replies, including reused container ids.
- Observe list changes before rendering. Drop removed/idle serials and invalidate
  outstanding requests when the current-job value changes or elapsed time
  decreases (same-output replacement). Before such a transition reaches the
  client, the next snapshot corrects it. Do not invent a local countdown.
- Changing selected CPU clears row diagnostics as today, but not unrelated list
  totals. A newly selected off-page CPU stays blank until its fresh batch arrives.
  Title and card never fall back to different caches.
- Unknown addon objects and restored jobs without runtime graphs yield empty
  totals. No persistence or data migration is added.

## Rendering

Add `CPUSelectionListMixin` in each of `mc1201` and `mc2612`. Share lookup and
formatting through `mcCommon`; retain the two rendering API boundaries.
Register widget mixins client-only and the menu accessor common in all loaders.

Hook per-card name rendering and draw the badge for that exact entry. Avoid
overwriting the full widget renderer. Read button dimensions, scroll range,
and origins from the live widget. Preserve AE2's selection, info bar, and progress.

Reuse title color/shadow and `TtcBadge.BACKGROUND`, with two logical pixels of
horizontal padding and one of vertical padding. Badge outer right/top edges sit
two pixels inside the card. Start with 0.6 text scale so the badge stays above
the bottom info bar. Further limit scale by the actual space above that bar and
the card's interior width; never truncate TTC digits. Reserve badge width plus a
two-pixel gap for the name. Ellipsize only the name passed to the draw call,
not `getCpuName`, so the tooltip stays complete. If no name fits, omit its visible
text and retain the tooltip. Restore transforms/clipping after drawing. Cards
without TTC keep their original name rendering.

Extract title style constants only as needed for reuse. No new theme, tooltip,
translation key, or interaction is needed.

## Compatibility and protocol

Add wrappers and registrations in each `versions/<target>/src/main/java` under
`.../mc1201/net` and `StatsNetwork`. Share a bounded `CpuTtcPacketCodec` under
`mcCommon`, following the existing codec pattern.

| Target | UI boundary | Current network boundary | Implementation change |
| --- | --- | --- | --- |
| 1.20.1 Forge | mc1201 | SimpleChannel 16 | Append message ids; protocol 17 |
| 1.20.1 Fabric | mc1201 | stats_snapshot_v9 | Keep old packets; add cpu_ttc_request_v1 and cpu_ttc_snapshot_v1 |
| 1.21.1 NeoForge | mc1201 | registrar 15 | Register pair; registrar 16 |
| 26.1.2 NeoForge | mc2612 | registrar 15 | Register pair; registrar 16 |

Recheck the baseline before implementation; increment the then-current numeric
version if intervening work used these numbers. On Fabric, request only when the
server advertises the new channel; otherwise leave list badges absent and retain
the existing title path. Version-negotiated loaders retain normal incompatible
peer rejection. Use matching client/server builds for acceptance tests.

## Validation mapping

| Criteria | Required evidence |
| --- | --- |
| A1-A3 | Distinct CPU totals; unknown/zero/disabled/partial/stalled cases; title and card share cache; unchanged estimate math. |
| A4 | Four-target `en_us` screenshots with long names/times, selected/unselected cards, and smallest/largest usable GUI scales; static resource/layout checks cover English and Ukrainian text, including long Cyrillic names. |
| A5 | Ordering/session/expiry tests; real scrolling, same-output replacement, cancellation, removal, reopen, network switch. |
| A6 | Malformed/max/duplicate/negative codec cases; wrong menu/grid; both rate limits and disconnect cleanup. |
| A7 | Four-target tests/builds and integrated/dedicated UI evidence with target/mod versions. |

Use the `cpu-list-total-ttc` driver scenario in each target's prepared
`compatible` profile. Follow the
[smoke policy and change-selection gates](../automated-ui-testing/spec.md).
Record the selected adapter identity; use a focused prepared fixture when the
compatible graph does not exercise the newest implemented adapter.

Dedicated UI evidence requires a connected prepared client opening the real
status menu on a matching disposable dedicated server. The existing
`DedicatedCpuScenario` runs headless addon-profiler checks with a fake player;
it cannot prove card rendering, selection, or client snapshot lifecycle. Reuse
the multi-CPU fixture and client observations for the connected case. Record
client/server artifact identities and server-side estimates alongside the
rendered card/title snapshot. Never substitute an integrated-server screenshot
or a headless pass for this evidence.

The relaunch runner supports a non-final resume bundle for focused phase-2
diagnosis. Its manifest binds Git head, campaign, disposable world, continuation
hash, retained evidence tree, production/driver bundle tree, dependency mode,
and managed JAR catalogue hash. Phase 1 records the latter two values in the
disposable world marker; restore rejects a mismatch before scheduling Java.
Restore copies the world and evidence into a fresh runtime and launches only phase 2. A
progress heartbeat distinguishes missing callback delivery from an unchanged
scenario checkpoint and causes a fast evidence-bearing failure. Callback
liveness is enforced throughout the process, while loader/world/fixture
preparation remains governed by the absolute startup deadline. The 60-second
checkpoint deadline starts only after `state=WORLD_READY phase=ACTIVE`. Final
verification always starts at phase 1 and requires the distinct relaunched PID.

The Changed impact map owns this feature as the single
`cpu-list-total-ttc` leaf on each supported target. The compatible primary
bundle for an immutable head/fingerprint is sealed once and hash-verified for
integrated and connected-dedicated reuse.

Copying the selected total misidentifies jobs. Deriving TTC from elapsed time or
progress changes semantics. Cycling selection changes player state. Broadcasting
every grid CPU without a bound creates unnecessary work. The chosen pair sends
only small visible totals.
