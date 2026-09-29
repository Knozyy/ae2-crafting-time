# Connected resource fixture implementation plan

Lifecycle: see the [scope status and evidence](spec.md).

Tracks [#482](https://github.com/cTux/ae2-crafting-time/issues/482).
Follow the [spec](spec.md) and [design](technical-design.md). This prerequisite
merged with fixture-only qualification; #376 retains its production acceptance gate.

The original slices, RF7/RF8 expansion and paused handoff below are historical.
The [final scope evidence](spec.md) records qualification at `c0321e80` and the
merged prerequisite. Do not resume the superseded handoff or reimplement these slices.

## WATER unload/reload repair (#628)

Follow the [repair criteria](spec.md#water-unloadreload-repair-628) and
[current-code design](technical-design.md#water-unloadreload-repair-628).
The older qualification sections below do not define this repair's campaign.

1. **Retain diagnostic state (UR1/UR4).** Extend the shared fixture's existing
   failure evidence with the bounded pending-operation snapshot described in
   the design, captured before cleanup. Cover absent, pending and completed
   operations and preservation of the original failure in the nearest existing
   `ResourceFixtureControlTest` boundary. Keep native observation side-effect
   free. Review and commit through the repository hook before running tests.
2. **Verify prerequisites (UR5).** Host Java 17/21/25, VMware CLI, VMX,
   localhost VNC configuration and SSH key existence were verified during
   investigation; the VM was stopped. Before runtime, start/reuse that VM via
   `use-codex-vm`, verify SSH and its own Java 17/21/25, the four exact prepared
   native `launch.json` manifests and dependencies, the marked disposable
   fixture, guest-local runtime and exact-worktree share. Follow `dev-client.md`
   and the prepared-smoke skill. Stop on missing/mismatched prerequisites; reuse
   the existing provisioning path and do not add infrastructure under #628.
3. **Diagnose, then correct (UR1/UR2/UR4).** After the implementation PR exists,
   run the focused Forge native leaf below with diagnostics. Preserve the failed
   archive and identify command acceptance and the first blocked phase. Correct
   the demonstrated shared cause, inspect both `StandardCraftFixture` variants
   and all callers, and add the smallest regression that fails on the original
   behavior. Do not relax deadlines, skip real unload, seed delayed state or
   weaken ITEM assertions. Review all corrections before the next commit.
   The #630 Forge diagnostics proved the provider remains loaded in phase 1
   even with the player 64 chunks away. The per-tick highlight check renews a
   server chunk ticket through loading block reads. Correct that shared provider
   check with a non-loading chunk read and retain the original unload distance.
4. **Run focused checks (UR2/UR4).** Run
   `./gradlew.bat :mc_1_20_1_forge:test --tests '*ResourceFixtureControlTest'`
   for the driver boundary. If production pure logic changes, also run
   `./gradlew.bat :shared:test :shared:jacocoTestReport`. Run
   `./scripts/test-resource-fixture-contract.ps1` for retained evidence and its
   integrated/connected validators, extending it if the evidence contract changes.
   Build `distMod` and `testDriverJar` for `mc_1_20_1_forge`, `fabric_1_20_1`,
   `mc_1_21_1_neoforge` and `mc_26_1_2_neoforge`; run each target's
   `checkTestDriverArtifacts` where registered. Preserve all coverage gates and
   cover every changed decision; report Minecraft-facing coverage limits.
5. **Qualify the final head (UR2/UR3/UR5).** Review the changed-smoke selection
   with `./scripts/run-ui-smoke.ps1 -Changed -BaseRef origin/master -PlanOnly`.
   Run `./scripts/run-ui-smoke-matrix.ps1 -Target <target> -BaseOnly
   -Scenario delayed-resource-icons` sequentially for the four rows below,
   without `-ResourceFixtureOnly` or `-Latest`. Start with Forge and diagnose
   failures before expanding. A diagnostic run is not a final qualification run.
6. **Check the connected boundary (UR4/UR5).** If the shared unload operation,
   chunk retention or grid/job recovery changes, run the same production leaf
   on the existing connected Forge native graph through
   `run-connected-dedicated-ui-smoke.ps1`, supplying its validated sealed
   `ServerDirectory`, exact `PreparedLaunch`, current-head `BundleDirectory`
   and new `ReportDirectory`, with existing prewarm/dispatch setup. Validate
   source markers and dependencies first. Broaden connected targets only when
   the proved fix changes a separate native implementation. No new cold/cache
   provisioning campaign or chemical campaign is required by this repair.
7. **Finish (UR1-UR5).** Review every required capture and semantic result,
   preserve raw automatic gates and archive hashes, confirm cleanup and exact
   tested SHA, and record timestamp/duration for diagnostics and final runs.
   Report local results separately from current-head GitHub CI. Link the proved
   cause, implementation and verification before marking this scope finished;
   retain the separate #376 production-icon acceptance boundary.

| Target | Guest Java | Final integrated cases |
| --- | --- | --- |
| 1.20.1-forge | 17 | ITEM, WATER, LAVA, BUCKETLESS, FLUID_OVERLAP |
| 1.20.1-fabric | 17 | ITEM, WATER, LAVA, FLUID_OVERLAP |
| 1.21.1-neoforge | 21 | ITEM, WATER, LAVA, FLUID_OVERLAP |
| 26.1.2-neoforge | 25 | ITEM, WATER, LAVA, FLUID_OVERLAP |

WATER must include the real chunk-reloaded checkpoint and matching server
receipt. Preserve all existing captures, later cancellation/removal checks and
ITEM ownership/beam/expiry checks; do not count reaching WATER as a pass.

## Provisioning and readiness extension

1. **Source preparation (RF7/RF4/RF5).** Add
   `scripts/prepare-dedicated-ui-smoke-server.ps1` and its focused PowerShell
   contract test. Reuse release-matrix/profile/Java and managed bundle readers;
   add only fixed official installer selection, bounded fetch/cache, fresh
   staging, integrity/seal validation and atomic publication. Cover four exact
   loader launch layouts and six graphs. No new mod resolver or Java installer.
2. **Source consumption (RF7/RF6).** Extend the existing connected runner's
   schema-2 validation with optional provisioning/seal fields. Newly provisioned
   runs require the full seal. Preserve old marked-source compatibility without
   relabelling it RF7-qualified. Propagate explicit EULA consent and archive the
   installer provenance, graph, full tree identity and before/after checks.
3. **Prewarm gate (RF8/RF3).** Extend the resource branch in DedicatedCpuScenario,
   ResourceFixtureServer and the client resource scenario with the shared
   PREWARM/ARMED gate. Keep loader callbacks and native connection APIs in their
   existing version adapters, including 26.1.2. Add bounded readiness DTOs and
   one-shot receipt binding; no production hook, resource command or fixture
   construction runs before arm. Retain the replay no-republication correction.
4. **Runner integration (RF8/RF4/RF6).** Carry Prewarm through the existing
   connected host/guest launch and progress/result validators. Separate readiness
   from scenario results, retain the same warmed processes, replace outer retry
   with the single bounded prewarm budget, and preserve all post-arm watchdogs.
   Validate expected-adapters.json before starting, not after a diagnostic run.
5. **Verification after the implementation commit/hook-created PR.** Extend
   `scripts/test-run-connected-dedicated-ui-smoke.ps1`, source preparation tests,
   launch/dispatch tests and ResourceFixtureControlTest. Use local fixed HTTP
   fixtures/mocked transfers in unit tests, never live downloads. Cover unknown
   target/latest/graph, wrong Java, missing checksum, corrupt cache/seal, metadata
   mismatch, redirects/auth refusal, all byte/time/count bounds, link/ancestor/
   hardlink/path escape, interrupted publication, existing-source preservation,
   missing EULA consent and exact cleanup. Test early/stale/duplicate arm,
   connection change, no fixture mutation, cold timeout, three-attempt exhaustion,
   dead callback, post-arm failure and prohibition on retry/deadline extension.
   Exercise all four installer launch adapters and production packaging isolation.
6. **Cold and reuse qualification (RF7/RF8 plus RF1–RF6).** On the immutable
   implementation head run source preparation and a Prewarm connected resource
   campaign first with an empty task-owned artifact cache, then with the verified
   cache/source hit, for each of the six graphs below. Fresh processes and current
   readiness are required both times; do not clear global/JVM/OS caches to claim
   a stronger cold test. Record exact cold definition, download/install/startup/
   join/frame/arm timings, bytes and cleanup. Require full fixture and visual
   review after arming in both runs; prewarm alone cannot qualify a row. Preserve
   existing integrated qualification on the tested head and rerun when invalidated.

No tests or runtime work are part of this requirements-only change. Installer
checksum metadata retrieval and the exact prepared bundle are implementation
inputs, not permission to upgrade missing artifacts or substitute another graph.

## Original fixture slices

1. **Bounded control (RF3).** Add resource command/state DTOs, strict bounded
   properties parsing and pure transition checks beside existing driver control
   classes. Reuse directory/epoch, source-marker and atomic move utilities.
   Cover every action/state, identity mismatch, duplicate replay/conflict,
   sequencing overflow/gaps, malformed/oversized file, link/path refusal,
   in-flight retries and reset acknowledgement revision. No production packet.
2. **Server fixtures (RF1/RF5).** Add resource setup/hold/return/cancel/reset
   operations using StandardCraftFixture and ServerDriverPlatform seams; delegate
   from shared DedicatedCpuScenario. Keep 26.1.2 API conversions in its target
   adapter; there is no separate DedicatedCpuScenario variant to copy. Build
   optional key/cell factories only for Forge and NeoForge 1.21.1. Register the
   named Forge-only bucketless test fluid in driver initialization. Verify
   native processing dispatch, warmup, exact units, partial insertion, overlap
   and cleanup. Do not add classes/resources to production source sets.
3. **Client lifecycle (RF1/RF2/RF6).** Add one shared resource scenario for
   integrated and connected modes, with the existing 26.1.2 entrypoint adapter.
   Drive the fixed case list, native locate input, release and fresh cancel
   jobs; reconnect only after the armed server acknowledgement. Observe normal
   packet-owned plates with menus closed and chat disabled. Capture held,
   overlap, winner-promoted, rejoined, completed and cancelled states. Keep
   server facts separate from client snapshots; never populate client caches.
4. **Runner and evidence (RF3/RF4/RF6).** Extend run-connected-dedicated-ui-smoke,
   run-ui-smoke, prepare-ui-smoke-launch and existing host/guest dispatch seams
   for the exact leaves and explicit ResourceFixtureOnly option. Update
   DriverOptions, supported cases, SuitePlan, DriverResult and selection/result
   validators coherently. Preserve CPU-list/recurrence behavior. Use the same
   manifest validation, source/disposable markers, identity binding, logs,
   progress watchdogs and cleanup. Validate fixture-only evidence before a
   prerequisite pass; ordinary suites must never interpret it as icon coverage.
5. **Review and commit before tests.** Follow AGENTS.md and the driver/development
   skills. Check the whole diff, optional-class isolation and driver packaging;
   one conventional implementation commit lets the hook create the PR. Then
   run the existing driver/core/runner/launch/lifecycle checks extended above,
   shared changed-behavior coverage and all four production/driver builds.
6. **Runtime qualification (RF1–RF8).** Preflight and run the matrix below on
   the immutable PR head, sequentially. First prove integrated Forge native,
   then connected Forge native, then the remaining applicable rows. Only after
   cheap checks and representative execution pass expand the matrix. Keep
   GitHub CI distinct from local proof; resolve invalidated evidence on new SHAs.

## Runtime matrix

| Target | Java / compatible graph at planning baseline | Required runs |
| --- | --- | --- |
| Forge 1.20.1 | 17; Forge 47.4.23, AE2 15.4.10 | Integrated + connected native including bucketless; integrated + connected AppMek 1.4.3 |
| Fabric 1.20.1 | 17; Fabric 0.19.5, AE2 15.1.0 | Integrated + connected native |
| NeoForge 1.21.1 | 21; NeoForge 21.1.251, AE2 19.2.17 | Integrated + connected native; integrated + connected AppMek 1.6.3 |
| NeoForge 26.1.2 | 25; NeoForge 26.1.2.109, AE2 26.1.10-beta | Integrated + connected native |

Use explicit base-only graphs for native runs; AppMek runs use its focused graph
with transitive Mekanism dependencies, not every optional addon. Each connected
source marker must match that exact graph and launch bundle. Re-read pins from
scripts/run-client-versions.json and retain resolved artifact hashes. Do not
upgrade dependencies as part of this prerequisite.

Preflight host Java 17/21/25 (Gradle on 17/21), matching guest Java/native client
manifests and dedicated source markers, source dependency hashes, disposable
world setup, driver isolation, 12 GiB provisioning disk reserve and runtime
memory for the 4 GiB server plus 8 GiB client and OS overhead. Prepare missing
sources with the bounded provisioner; missing Java, upstream checksums, exact
bundle or explicit EULA consent remain named blockers. Never use an unmarked
server, guest Gradle build or unapproved graph. Record launch count and the
15-minute installation, 600-second prewarm and 40-minute measured scenario
budgets before starting each row. Record cap failures instead of increasing them.

## Evidence and completion

Every target records a complete fixed case list: native item/water/lava/overlap,
plus Forge bucketless, or chemical oxygen/hydrogen/overlap. Every single-output
case warms up, holds until delayed, reconnects in connected mode, releases to
completion, resets, then submits a fresh job for cancellation. Overlap releases
slot 0 and observes slot 1 before finishing it. Require server and client evidence
to agree on output identity, provider position and lifecycle; capture the actual
unfixed icon baseline without treating it as #376 success.

RF3/RF4 negative tests include wrong player/epoch/fixture/revision, invalid case
or slot, early reconnect, command replay, timeout during mutation, failed
cleanup, unsupported chemical targets, missing integration and misplaced flag.
Run existing CPU-list and recurrence runner contract tests as regression checks;
broaden their runtime only if shared behavior changes invalidate prior coverage.

## Executable coverage map

| Boundary | Executable regression | Runtime gate and retained artifact |
| --- | --- | --- |
| Identity, transition, replay/conflict, reset revision and final client acknowledgement | `ResourceFixtureControlTest` | exact receipt/state comparison in both runner validators; JUnit XML |
| Multi-tick warmup return, partial insertion and original-failure-preserving cleanup | `ResourceFixtureControlTest.multiSlotWarmupReturnPollsEveryPartialSlotUntilComplete`, `partialInsertionRetainsTheUninsertedRemainder`, `connectedAbortBindsOriginalFailureAndTerminalRevision` and `cleanupFailureKeepsTheOriginalFailureVisible` | authoritative `resourceCleanup`, abort acknowledgement and server job timing snapshots; `resource-fixture-evidence.json` |
| Focused case catalogue and graph selection on all supported targets | `scripts/test-ui-smoke-plan.ps1` | `get-ui-smoke-plan.ps1` rejects ordinary-suite inclusion and selects one compatible graph; plan JSON |
| Explicit fixture-only authorization and unsupported chemical targets | `scripts/test-ui-smoke-matrix.ps1` | `run-ui-smoke-matrix.ps1` rejects absent/misplaced flags before launch; matrix plan JSON |
| Setup deadline and active progress watchdog | resource cases in `scripts/test-ui-smoke-fast-path.ps1` | 300-second setup and 60-second active limits; progress/status JSON |
| Launch wrapper forwarding | `scripts/test-run-ui-smoke-wrapper.ps1` | executes the public wrapper against a parameter-validating matrix fixture and requires `ResourceFixtureOnly`; captured invocation result |
| Fixed captures, hashes, delayed timing, frame facts, plate/rainbow semantics, server/client agreement and mode-specific receipt traces | `scripts/test-resource-fixture-contract.ps1` exercises the validator; integrated and connected resource rows provide runtime coverage after PR creation | source-defined positive/negative validator fixtures cover identities, cardinalities, job states, receipt agreement and sidecar binding; real PNGs, sidecars, client evidence and server results remain post-PR runtime evidence |

The post-PR test command must execute every regression named above; source-text
matching is not accepted as coverage. A runtime row passes only after its mode's
validator emits the combined evidence artifact from the exact expected trace.

Ready to unblock #376 means: current-head tests/builds/CI pass, RF7 source
preparation and RF8 cold/cache-hit readiness qualify on all six graphs, all matrix
fixture checks and cleanup pass, artifacts/captures are reviewed and archived,
and every result explicitly says fixture-only with icon acceptance NOT_RUN.
Update parent driver/dependency docs from planned to implemented fixture status
only with that evidence. Leave #376 open for its production implementation,
payload/persistence boundary tests and complete visual acceptance campaign.

## Paused qualification handoff (2026-09-22)

Historical record, superseded by the [final qualification](spec.md). The statements
below describe the earlier failed and incomplete attempts, not the current PR state.

PR #484 remains open at `93a2cc54ad768dbfb0d26e821050282f3602f4c4`.
This is a fixture-only candidate, not a completed prerequisite. The exact-head
integrated, connected cold, and connected source-hit rows passed with reviewed
captures for Fabric 1.20.1 native (18/22/22), Forge 1.20.1 native (22/27/27),
and Forge 1.20.1 AppMek (14/17/17). NeoForge 1.21.1 native passed integrated
(18) and connected cold (22), but its exact-head source-hit run stalled after
the second player joined: the client issued `RECONNECT` sequence 3 while the
server retained the `REJOIN_PREPARE` sequence-2 acknowledgement. The active
checkpoint watchdog stopped that run. The cause remains unproved.

One separate diagnostic source-hit run with temporary logging passed all 22
captures; each reconnect gate was satisfied on its first poll. That diagnostic
does not replace the failed exact-head row or prove a fix. The logging was
removed, leaving the committed source unchanged. The interrupted later
exact-head retry has no verified result in this handoff. NeoForge 1.21.1
AppMek and NeoForge 26.1.2 native rows were not run. Retained failure and
diagnostic evidence is under `.omo/evidence/issue-482-remaining-93a2/` in the
task worktree; the failed archive SHA-256 is
`3AC0FBC877A4CEDA2EEFA075A85883D5894BCC74B23E7C4A99D25C8ADB460205`.

To resume, first classify the NeoForge source-hit reconnect stall with a
server-side pending-gate snapshot (player-absence observation, player and grid
identity, loaded/busy/delayed state), then correct a proved cause or obtain a
clean committed-head pass without masking the earlier failure. Qualify the two
unrun graphs, review every emitted capture, and check current-head CI/reviews
before considering #484 for merge. Keep #376 open; its typed-resource production
icon implementation and visual acceptance remain separate and unfinished.
