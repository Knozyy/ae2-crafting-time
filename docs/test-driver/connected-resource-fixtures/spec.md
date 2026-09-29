# Connected resource lifecycle fixtures

Status: finished

Scope: Fixture lifecycle, server provisioning and prewarm (RF1-RF8).

Implementation: [PR #484](https://github.com/cTux/ae2-crafting-time/pull/484).
Verification: [18/18 rows and 358 reviewed captures](https://github.com/cTux/ae2-crafting-time/issues/482#issuecomment-5774907363).
Production icon acceptance remains NOT_RUN and belongs to #376.

Implemented fixture scope for [#482](https://github.com/cTux/ae2-crafting-time/issues/482),
the test-driver prerequisite for [#376](https://github.com/cTux/ae2-crafting-time/issues/376).
The provisioning/prewarm expansion and fixture matrix are qualified by the
completion record above. This does not qualify production resource icons.

## WATER unload/reload repair (#628)

Status: in progress; final runtime qualification pending

Scope: Diagnose and repair the production-mode resource fixture's WATER
unload/reload acknowledgment on all four supported targets.

Issue: [#628](https://github.com/cTux/ae2-crafting-time/issues/628).
Planning: [Design](technical-design.md#water-unloadreload-repair-628) and
[plan](implementation-plan.md#water-unloadreload-repair-628).

The retained runs stop after WATER command sequence 8. Their last acknowledged
sequence is 7, with `revision=3` and `ackRevision=3`; cleanup passes. They do
not record the pending unload phase, so the exact cause remains unproved.
The #630 diagnostic accepted WATER sequence 8 and stayed in unload phase 1:
the player left, the terminal chunk unloaded, and the provider chunk stayed
loaded. Moving the player 64 chunks away did not change that result. The
per-tick delayed highlight check reads the provider through `Level.getBlockState`,
which renews a short-lived server chunk ticket. The correction must inspect
the already loaded chunk without creating a ticket; final runtime checks remain
pending.
This repair is separate from the finished RF1-RF8 fixture qualification above
and does not complete [#376's icon acceptance](../../provider-locate/resource-icons/spec.md).

- **UR1:** Retained diagnostic evidence distinguishes command acceptance,
  waiting for actual chunk unload, waiting for reload, and grid/job/delayed
  readiness. Capture the pending operation before cleanup can erase it.
- **UR2:** WATER really unloads and reloads its provider chunk, recovers its
  native held job and delayed state, then acknowledges the matching command
  once. Keep the existing sequence/revision contract and observation deadlines;
  a request or teleport alone cannot count as completion.
- **UR3:** Preserve all ITEM dispatch, ownership, chat locate, roof, rainbow,
  beam, recovery and expiry assertions. Complete WATER, LAVA and FLUID_OVERLAP
  on every target and BUCKETLESS on Forge 1.20.1, including existing completion,
  cancellation, provider-removal and cleanup checks.
- **UR4:** Fix the demonstrated cause in the shared path, keep native API
  differences in existing adapters, and retain bounded replay/identity checks
  and original-failure-preserving cleanup. Cover changed decisions with a
  regression that fails before the correction. Validate connected lifecycle
  behavior when the shared operation or its chunk/grid handling changes.
- **UR5:** Bind final checks, reviewed captures and cleanup to the committed
  implementation head. Missing guest prerequisites block execution; they do
  not justify another runner, a different graph or weaker evidence.

No new player behavior, dependency upgrade, fixture-only acceptance substitute,
timeout increase or general provisioning infrastructure is in this repair.

## Goal and boundary

Provide repeatable real AE2 item, fluid and chemical processing jobs that can
be held, released, cancelled and observed through a client reconnect. Reuse the
existing prepared clients and disposable connected server. This changes only
development tooling; player behavior, profiling, highlight packets, persistence
and production rendering remain unchanged.

Provide a supported, opt-in way to prepare the exact compatible dedicated
server graphs for all four release-matrix targets. Download official loader
installers, reuse verified caches, seal a new source and never upgrade or edit
an existing source. Require explicit Minecraft EULA acceptance before writing
acceptance into a disposable runtime. No account credentials are needed.

Connected resource runs use a bounded cold-start readiness phase before fixture
activation. It may exercise ordinary login and rendering, but creates no grid,
job, sample, plate or resource command. Keep the warmed client/server processes
for the measured run. Readiness is not a fixture or visual-acceptance pass.

The parent [resource-icon contract](../spec.md#delayed-resource-icon-scenarios-planned)
still owns #376's visible-icon acceptance. #482 establishes fixture readiness
without requiring the missing production icon fix. Its explicit fixture-only
runs record `productionIconAcceptance: NOT_RUN`, even when all fixture checks
pass. They cannot satisfy or close #376. Missing icons are retained in captures,
never painted by the driver, hidden, or called a rendering pass.

## Required cases

| Scenario | Targets | Fixture cases |
| --- | --- | --- |
| `delayed-resource-icons` | Forge/Fabric 1.20.1, NeoForge 1.21.1 and 26.1.2 | Item, water, lava, shared-provider water/lava overlap; bucketless control on Forge 1.20.1 |
| `appmek-resource-icons` | Forge 1.20.1 and NeoForge 1.21.1 with AppMek | Oxygen, hydrogen, shared-provider oxygen/hydrogen overlap |

Use real encoded processing patterns, native CPUs and actual provider input
acceptance. The driver acts as a deterministic processing machine: output is
released only after its matching input was dispatched. Never seed production
delayed state, invoke highlight sends or mutate client plates. Keep every case
inside one marked disposable grid, with at most two active jobs and two outputs.

Observe automatic red plates with chat disabled and all menus closed. Exercise
release to completion, cancellation, one held-job reconnect, and promotion of
the surviving shared-provider output. A manual locate before reconnect proves
rainbow state does not return. Run a fresh cancellation job after completion;
do not interpret a command acknowledgement as completion or cancellation.

The item case also checks #488 on the connected server: its provider has an
opaque roof, a row locate yields no beam, a foreign-owner chat record is
rejected, and the owner's chat locate yields rainbow plus beam. Capture the
beam before reconnect; neither temporary effect may return after rejoin.

## Acceptance criteria

- **RF1:** Both scenarios can drive the named real jobs in integrated and
  connected modes on every applicable target. Capture dispatch, held DELAYED,
  release/completion and cancellation, with matching authoritative key/amount.
- **RF2:** The same client disconnects and reconnects while the dedicated job
  remains held. Server state survives, normal login resync restores its red
  plate, and rainbow state stays absent. Overlap promotes the surviving output.
- **RF3:** Commands are bounded and tied to one campaign, scenario, player,
  fixture and revision. Wrong identities, stale/duplicate/conflicting commands,
  invalid phases and oversized/malformed files cannot repeat a mutation.
- **RF4:** Sources remain immutable; only marked disposable copies change.
  Success and failure clean fixture jobs/state and owned processes. Unknown
  process ownership or failed cleanup is a failure, not a reason to kill broadly.
- **RF5:** Base item/fluid runs load without AppMek. Chemical cases require the
  exact supported integration and fail if it is missing. Driver dependencies,
  test resources and optional classes never enter production artifacts.
- **RF6:** Evidence binds server facts, actual client observations, reviewed
  captures, artifacts and dependency graph to one tested SHA. Fixture readiness
  and #376 visual acceptance remain separately reported.
- **RF7:** A supported provisioner produces and reuses sealed, non-linked
  sources for four native graphs and the two AppMek graphs, verifying official
  downloads, target/Java/loader, every launch dependency and source identity.
  Corruption, graph mismatch, exceeded bounds or failed installation cannot
  publish a usable source. Existing sources and unrelated files stay unchanged.
- **RF8:** Cold readiness proves a real matching client join and stable rendered
  world before one-shot fixture activation. It has finite startup/connection
  budgets, no fixture mutation and no credentials. Failure preserves evidence
  and cleans owned processes; it never relaxes post-command deadlines or retries
  a progressed fixture. Cold and cache-hit runs qualify separately on all graphs.

## Not included

No production fix, new production key type, general remote-command API, arbitrary
server support, simultaneous clients, loader upgrades or general server hosting.
The provisioner supports only the fixed compatible connected graphs, not
modpacks, latest profiles, Java installation, authentication or arbitrary URLs.
Resource reload, provider removal/unload, malformed production payloads and final
icon/tint correctness remain #376 checks using these reusable fixtures. No new
translations or player settings. Existing CPU-list, recurrence and `appmek-cpu`
contracts remain unchanged.
