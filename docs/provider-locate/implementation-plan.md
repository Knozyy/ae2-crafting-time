# Provider Locate Implementation Plan

Lifecycle: see scope status and evidence for [baseline](spec.md), [shared-provider icons](spec.md#planned-shared-provider-icon-correction), [resource icons](resource-icons/spec.md), and [red sky beams](spec.md#planned-red-sky-beam).

The phases below describe the original feature. Later sections record #443
and the planned red sky beam for #488; they do not repeat the original work.
Let the commit hook create the PR, then use required CI as the first Gradle
test run.

## Phase 1: Track dispatched patterns per craft

1. Add a Minecraft-facing tracker keyed by crafting CPU identity holding each
   output's dispatched patterns.
2. Record the pattern for its output keys inside both `ProfilerBridge`
   `observeProviders` paths (standard/AdvancedAE and ECO/TimeWheel funnel
   through the same two methods).
3. Clear one scope in `ProfilerBridge.startJob` and `finishJob` beside the
   existing pending/owner cleanup.
4. Resolve positions only at notify time: providers from the crafting
   service, identity-matched to grid nodes, `InWorldGridNode` locations
   only, capped at the new packet limit.

## Phase 2: Message, command, and highlight packet

1. Split `chat.delayed` so the status word is a separate red translatable;
   keep four matching placeholders in English and Ukrainian.
2. Underline the output name with a hover hint and a run-command click event
   while a record exists; render a plain name otherwise.
3. Add the shared Brigadier locate command (permission 0, owner-must-match
   plus active-job and valid-target checks) and register it on each loader's
   command event.
4. Add the shared highlight codec (`networkId, dimensionId, positions,
   outputId, durationSeconds, plateOnly`) plus one S2C wrapper, registration
   line, and send helper per loader. `plateOnly=true` is red-plate-only
   (automatic); `false` is rainbow-edge-only (manual); empty positions with
   zero duration clears one plate and keeps rainbow.
5. Set Forge channel protocol `14`, Fabric `provider_highlight_v4` plus
   `provider_locate_v1`, NeoForge registrars `13` on 1.21.1 and 26.1.2.
   Keep `networkId` and stored `dimension` additive with tolerant reads.
6. Add the client highlight store with independent lifetimes: plates persist
   until server clear, provider break, or session end; edges expire after 15
   seconds. Both blink. Never consult the UI cache; never silently evict.
   One render hook per loader draws rainbow edges and red plates in the
   matching dimension, with shared broken-target trim.

Changing the wire registry affects every supported loader, so complete these
steps in the same commit.

## Phase 3: Persist links and re-warn after reload

1. Add the per-key provider-start section (network, output, owner, dimension,
   positions, display name) to all four `Ae2CraftingTimeSavedData` files with
   tolerant reads; do not bump the samples version. Never persist rainbow.
2. Snapshot the section whenever records change, a job owner is set, or a
   load happens; restore it in `ProfilerBridge.load`.
3. Fall back to the persisted owner and positions when live dispatch data is
   absent, so resumed crafts warn again with a working link after reload.
   Login resync re-sends plates for still-delayed crafts (chat never re-sent)
   with server-side broken-target filtering; unloaded stays unknown and kept.
4. Answer clicks on missing, foreign, finished, cancelled, or broken records
   with the private expiry notice and forget broken records.
5. Double-click means "any active crafting item" (server validates scope,
   ownership, and resolvability); `notifyOnDelayed` means "chat only" and
   never gates plates, clears, or resync. Document both explicitly.

## Phase 4: Blocked warnings (NO SPACE, NO POWER)

1. Add a tiny pure-core episode tracker (one instance per reason) with tests
   for once-per-episode, re-arm, scope independence, and null safety.
2. Add the server-side `NO SPACE` probe reading the AE2-mirrored status
   methods through reflection, with graceful empty results.
3. Notify per reason from every CPU tick path (power everywhere, space
   everywhere) plus a power-only backup on status requests, sharing chat
   records only. Blocked warnings never create or clear red plates and never
   update the provider fallback.
4. Add the shared three-placeholder blocked sentence with per-reason red
   status words in both languages; reuse the clickable name for edge-only
   manual locates.
5. Extend the message, placeholder, and language tests.
6. Update the spec, design, and protocol doc sections.

## Phase 5: Tests, docs, and verification

1. Add codec round-trip tests (network, positions, dimension, duration,
   plateOnly, oversize rejection, legacy defaults) and NBT round-trip tests
   (records, owners, stored dimension, legacy saves).
2. Add message-component tests: red status word, underlined clickable name
   with hover text, plain fallback, and placeholder parity.
3. Add lifecycle tests: auto plate without edge, manual edge without plate,
   independent clear, provider-break trim, session-end clear, no silent
   eviction, network-scoped independence, blocked never touches red.
4. Update `docs/server-client-stats.md` (packet, command, persistence) and
   index the feature in `docs/feature-coverage.md` and `README.md`.
5. After the hook-created PR exists:
   1. Let required CI run every supported Gradle row and the coverage gate.
   2. Check the full warning/error sweep and fix repository-owned warnings.
   3. In a development client, stall a craft and verify red plates appear
      with no click; click the item name or double-click any active item and
      verify rainbow edges blink for 15 seconds without changing red.
   4. Recover one output and verify red clears while rainbow continues;
      finish or cancel and verify the same. Break one provider and verify
      only its plate and outline drop.
   5. Re-enter the world and verify red returns for still-delayed crafts,
      rainbow never returns, and active chat links still work.

## #443: one delayed output per provider

Historical planning status: planned, not implemented. Follow the
[specification](spec.md#acceptance-for-443) and
[source evidence and design](technical-design.md#443-one-display-per-provider-position).
Complete the correction as one conventional fix commit after review; do not
run local tests before the hook-created implementation PR exists.

1. **Confirm verification access.** Before a build or smoke campaign, check the
   exact CodexVM, guest JDKs, prepared native launch manifests and disposable
   fixture path below. Resolve missing installations through the documented
   prepared-client workflow. Do not silently add a runner or provisioning
   system. A missing prerequisite without an existing provisioning path needs
   a separately documented prerequisite or explicit authority before expanding
   this issue.
2. **Select the display.** Add the smallest pure selection operation under
   `shared/src/main/java` and a separate rendering view in
   `ProviderHighlightClient`. Retain raw `plates()` and every delayed identity.
   Select the first retained candidate per dimension/position, collapsing
   duplicate positions while preserving other positions and dimensions.
3. **Wire every renderer.** Replace raw plate iteration in Forge 1.20.1,
   Fabric 1.20.1 and NeoForge 1.21.1. Update both the plate and item-submit
   loops in NeoForge 26.1.2. Keep geometry, buffer flushes and edge paths intact.
4. **Add focused regressions.** Cover the new pure operation's lines and
   branches completely. Extend the nearest client boundary tests for stable
   updates, selection after clear/trim and retained losing identities. Review
   every renderer call site against the same output contract.
5. **Verify after PR creation.** Run the focused tests and shared coverage
   checks, then compile the affected four targets. Track required GitHub CI
   separately (`test jacocoTestReport`, including the shared 100% gate).
   Preview `scripts/run-ui-smoke.ps1 -Changed -BaseRef origin/master -PlanOnly`
   and retain its selection. Run the required selection after the representative
   overlap case passes; report any broader suites selected by current policy.
   Selection rules can choose CPU-list or full-suite coverage for these paths,
   so also run the explicit overlap check below. Do not weaken selection rules
   to obtain a smaller campaign.
6. **Review evidence and close the documentation gap.** Bind checks, captures
   and reports to the implementation head. Review the single-icon center,
   several successive frames, survivor transition, red pulse and rainbow
   behavior. Keep unrun cases and visual-review requirements explicit. Only
   after the criteria pass should the documents describe the fix as shipped.

### Criteria mapped to checks

| Criteria | Change and check |
| --- | --- |
| P443-1, P443-4 | Pure selection: empty input, one candidate, several candidates, repeated positions, partial overlap, different dimensions and different networks at one position. Assert exact selected outputs and unique positions. |
| P443-2 | Pure ordering checks and `ProviderPlatesTest`: repeated reads, updating the winner, updating a loser and adding a later candidate preserve the first retained winner. |
| P443-3, P443-5 | Client boundary checks: clear winner/loser, trim a position, clear the last candidate and end the session; assert retained identities and independent edge state as well as selected displays. Check unknown/non-item output selection without inventing a registry fallback. |
| P443-1, P443-2, P443-6 | Review all four render adapters and both 26.1.2 passes; compile all targets. Reviewed overlap captures prove centering and stability in actual render pipelines. |
| P443-3, P443-5, P443-6 | Run `delayed-status` for existing automatic plate and final-output cleanup coverage; perform the additional overlap/recovery/manual-locate visual check below. Existing shape and timer code stays unchanged. |

### Runtime scope and prerequisites

Investigation at `818809c57cf06540b23430bb4836e06f7fd82466` verified host Java
17, 21 and 25 selection and Gradle wrapper 8.12. CodexVM exists but was stopped.
Guest Java, SSH/VNC access and prepared manifest contents remain unverified.
This is a feasibility gap, not a successful smoke run.

Use the compatible profiles in `scripts/run-client-versions.json`:

| Target | Java | Loader / AE2 at investigation | Visual role |
| --- | --- | --- | --- |
| 1.20.1 Forge | 17 | 47.4.10 / 15.4.10 | First overlap reproduction and older render path |
| 1.20.1 Fabric | 17 | 0.19.4 / 15.1.0; Fabric API 0.92.11+1.20.1 | Distinct immediate-buffer path |
| 1.21.1 NeoForge | 21 | 21.1.238 / 19.2.17 | Compile and client boundary coverage; run additional smoke required by change selection |
| 26.1.2 NeoForge | 25 | 26.1.2.99 / 26.1.10-beta | Separate plate and item-submit passes |

Recheck profile versions before execution. Follow `docs/dev-client.md` and the
prepared-smoke skill: build on the host, stage immutable artifacts, and launch
only the matching installed native loader in CodexVM. Check the prepared
`launch.json` at the documented guest root for the exact target/resolved loader.
Retain evidence under `build/ui-smoke`, archive it, and stop only the recorded
client before the next target. No dedicated-server campaign is needed for this
client-only selection change.

Tracked source markers exist for Forge and both NeoForge targets and use
`disposableWorldId: SOURCE_ONLY`. Fabric intentionally uses the Forge source
fixture. Use the existing runner's disposable copy and marker validation;
never launch or modify the tracked source world.

### Overlap check

The `delayed-status` leaf uses its existing prepared client and disposable world
to dispatch two distinct intermediate outputs through one provider. It waits for
both retained identities, stabilizes the single first-retained render selection,
and performs a real row locate before capturing `delayed-world-overlap.png`.
It releases that winner while the other output remains delayed, then requires
the survivor icon, red plate, and independent rainbow edge before capturing
`delayed-world-winner-recovered.png`. The existing final-output stages verify
the remaining plate clears. Review both new captures on Forge 1.20.1, then on
Fabric 1.20.1 and NeoForge 26.1.2; an automatic assertion is not visual approval.

## Red sky beam implementation

Historical planning status: planned, not implemented. Tracks
[issue #488](https://github.com/cTux/ae2-crafting-time/issues/488), the
[beam criteria](spec.md#beam-acceptance-criteria) and
[render design](technical-design.md#red-sky-beam-design). The planning PR must
leave #488 open. #443's selected render view exists at the design baseline;
reuse it. #376 does not block this work.

1. **Reconcile all warning causes (B1, B3-B6).** Own shared server highlight
   reconciliation in `DelayedNotificationServer`, both `ProfilerBridge` copies,
   existing CPU tick callers and `ProviderStartTracker`/dispatch-observer target
   capture. Combine DELAYED, NO SPACE and all six `CraftingBlockReason` values
   using existing predicates, freshness and job ownership. Track the union of
   current contributions before sending plate updates or clears. Update finish,
   disable/reload and login resync paths together. Preserve chat policy in
   `BlockReasonNotifier`; never gate highlights on chat or menu requests. Validate
   first-dispatch targets and handle NO PROVIDER with no target as no marker.
   Keep transient reasons runtime-only and refresh them after restart.
2. **Carry chat provenance and reuse edge lifetime (B1, B3-B6).** Add the
   chat-locate flag to the shared DTO/codec, four loader adapters and client edge
   record. Set it only after the owned chat command resolves valid targets;
   row locates and automatic sends keep false. Preserve it during trimming.
   Repeated chat refreshes the pair; a row request replaces that identity with
   edge-only state. Select live chat-edge positions with the existing display
   selection helper. Add no beam timer, cache or persistence. Bump Forge 24 to
   25, NeoForge 23 to 24 and Fabric highlight v5 to v6 together; verify channel
   guards and legacy decoding. Own the four render hooks and two shape helpers
   named in the design, reusing frame pulse and dimension/target filtering.
3. **Isolate rendering (B2, B6).** Implement the specified column dimensions,
   height and red color through dedicated no-depth-test/no-depth-write render
   types/pipelines. Keep vertex API differences at the existing version/loader
   boundaries. Preserve Fabric's immediate-buffer flush and draw only once in
   the 26.1.2 world stage. Review all callers for consumer lifetime, pose cleanup
   and accidental changes to existing plate, icon or edge rendering.
4. **Cover the boundary (B1, B3-B6).** Extend existing plate/trigger tests for
   empty and manual-only inputs, shared-provider survivor/removal, dimension
   filtering, session clear/resync and missing icon. Add a focused geometry
   check for providers at both height extremes. Keep tests beside the existing
   source-set tests, using the repository's existing test setup.
   Add server tests for all eight warnings, no-menu/chat-disabled operation,
   red-to-red transitions, overlapping reasons, two jobs sharing an output,
   different owners, transient expiry, reconnect/restart and invalid/replaced
   targets. Assert exact packet updates and no premature clear. Cover packet
   provenance round-trips/bounds, automatic/resync/row beam absence, chat refresh,
   row replacement, exact common expiry and plate recovery without edge loss.
5. **Commit, then verify (B7).** Use one conventional feature commit and let
   the hook create its PR before running local tests. Run focused tests and
   compile all four release-matrix targets; run required coverage checks and
   report current-head GitHub CI separately. Follow the prepared-client smoke
   skill and preview `scripts/run-ui-smoke.ps1 -Changed -BaseRef origin/master
   -PlanOnly`; run its required selection without weakening it.
6. **Capture the actual effect (B1-B7).** Extend the existing `delayed-status`
   scenario in `StandardAe2Scenario` with an opaque roof over the provider in
   its disposable fixture. Capture automatic plate-only state, a successful chat
   locate with rainbow/beam, overlapping locates, recovery while the pair survives
   and simultaneous expiry. Add glass/fluid and roofed-dimension
   observations, row-only locate, chat-disabled plate activation without beam,
   re-entry and wrong-dimension/other-player absence checks. Use the same server-owned path
   in singleplayer and a dedicated-server ownership check. Never mutate the
   tracked source world. Run and review the visual cases on Forge 1.20.1,
   Fabric 1.20.1, NeoForge 1.21.1 and NeoForge 26.1.2, including high providers,
   off-screen bases and nearby translucent geometry. Bind captures and logs to
   the implementation commit and report unrun cases honestly.
   Extend existing warning-status scenarios to verify plates without beams for all
   eight statuses on valid targets, including failures before first successful
   dispatch. For NO PROVIDER additionally prove no-target absence. Exercise a
   blocked-only warning and DELAYED-to-blocked transition without plate loss.
   Extend an existing connected resource fixture narrowly for chat provenance
   and owner-bound packet/lifecycle checks; do not create another server runner.

   Before approving or launching runtime checks, verify guest native
   `prepared/<target>/launch.json`, matching loaders, Java 17/17/21/25, SSH access,
   disposable fixture markers and the existing connected fixture entry point.
   Host runtimes, tracked source worlds and launch scripts were inspected;
   CodexVM was off and guest manifests were not inspected during planning.
   Missing native installations are prerequisites; do not substitute another
   target or silently add substantial test infrastructure. After PR creation,
   preview changed selection, pass one representative focused scenario, then
   run the required selected matrix and connected checks. Bind evidence to the
   exact implementation head and shut down only CodexVM afterward.

   **Post-PR views outside the bounded fixture:** first run
   `scripts/run-ui-smoke.ps1 -Changed -BaseRef origin/master -PlanOnly` and
   its selected matrix. On each of the four supported targets, run the
   existing `delayed-resource-icons` item case and review `item-held.png`
   (owned chat beam under stone), integrated `item-recovery-pair.png`,
   `item-completed.png` (both effects expired), and connected
   `item-rejoined.png` (neither returned). Inspect the sidecar's `beams`,
   `rainbows.chatLocate`, owner-bound server job, and exact implementation
   SHA; confirm the foreign-link expiry response in the driver check. Run
   `delayed-status`, `no-space-status`, `no-provider-status`, `no-power-status`,
   `no-channel-status`, `no-target-status`, `input-blocked-status` and
   `locked-status` for the eight warning rows. At each valid associated
   provider, capture an automatic plate with no beam before any click; for
   NO PROVIDER without a valid target, capture marker absence. These status
   leaves do not assert every plate yet, so review the actual client plate
   state and images rather than treating a row assertion as proof.

   In a marked disposable interactive world for each target, place a held
   craft's provider below opaque stone, glass and water in separate runs,
   then in a roofed dimension. Use its real private chat link and capture
   the beam from below and above each roof. Move until the base leaves the
   view frustum while the beam remains visible; capture nearby translucent
   geometry and a provider near the upper build boundary. Capture row-only,
   expired/foreign-link, wrong-dimension and second-player negatives. For
   every view record target, world marker, coordinates, beam/border start
   and end frames, screenshot sidecars, render logs and head SHA. Report
   every unrun case explicitly; one target cannot stand in for another.
7. **Close only with evidence.** Match each B1-B7 criterion to passing checks
   and reviewed captures. Update these docs from planned to shipped only after
   that gate passes. Merge the implementation and verify #488 closes; the docs
   merge alone does not deliver the feature.
   Update English/Ukrainian player guidance and the protocol behavior docs to
   describe automatic all-warning plates, chat-only temporary beams, row-only
   rainbow locates and the coordinated packet version change. Reconcile the
   historical manual-outline wording in resource-icon docs with this extension.
