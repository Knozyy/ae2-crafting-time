# Crafting suspension implementation plan

Lifecycle and behavior: [specification](spec.md). Hook and wire decisions:
[technical design](technical-design.md). No issue-body replacement is needed.

## Ordered changes

1. Deliver and merge these three planning documents before implementation.
   Use a documentation-only conventional commit/PR with `Refs #631`; retain the
   claim. Start the implementation branch from the verified merged base.
2. Add covered pure state/request/packet validation and scoped profiler
   suspension transitions. Append the server option and description; advance
   options/loader protocol versions exactly as designed. Test disabled/default
   state, unchanged bit positions, wrong versions, malformed inputs and all
   transitions. No tests run yet: repository ordering requires the hook PR first.
3. Add only Forge logic/menu/screen mixins and the C2S/S2C registrations. Reuse
   the existing StatsNetwork channel and crafting-link identity. Trace every changed bridge,
   notifier and rendering caller. Ensure `enabled=false` profiling does not
   disable scheduling recovery. Add NBT, peer-support and API-boundary checks.
4. Extend existing driver fixtures and runners as described below. Update
   `docs/test-driver/spec.md`, `technical-design.md`, scenario selection/catalog
   and `docs/dependencies.md` with this bounded scope. Add deterministic runner
   checks to `test-run-connected-dedicated-ui-smoke.ps1` and the existing
   singleplayer runner/selection tests; retain their marker and ownership guards.
5. Add English/Ukrainian strings, GuideME feature/config/index text and equivalent
   wiki text. Refresh the separate wiki checkout before editing; use the existing
   `Feature-Configuration.md` and `Ukrainian-Feature-Configuration.md`, add linked
   `Feature-Crafting-Suspension.md` and `Ukrainian-Feature-Crafting-Suspension.md`.
   Publish only under the parent workflow's wiki authority, then read live pages
   back and retain their commit. Do not call a local draft published.
6. Review the entire candidate, commit the feature conventionally and let the
   repository hook create its PR. Only then execute the gates below. Fix related
   failures in a reviewed follow-up and invalidate changed-head evidence as
   appropriate. Merge/closure require the parent workflow's later gates.

## Bounded scenario additions

Use `crafting-suspension` for both singleplayer and connected execution, Forge
only. Register it in the existing driver scenario/catalog and runner validation
paths, including the host dispatcher. This is a native UI flow, not an addon
CPU fixture. Keep new scenario code out of production JARs.

Extend `StandardCraftFixture` with two standard CPUs and two real pattern
providers feeding real fueled furnaces, both advertising the same raw-iron to
iron-ingot processing pattern. Enable native provider blocking mode so each
machine holds bounded outstanding work. Use 64 outputs for the large job and
2 for the small job, on separate CPUs, from disjoint reserved input counts.
Record accepted plan/job UUIDs, dispatched counts, outstanding machine contents,
CPU stored/waiting/pending amounts and network outputs at every checkpoint.
Do not synthesize outputs, replace native execution, or use status-table objects
as functional evidence. Normal furnace duration keeps the scenario bounded.

Submit large, observe actual dispatch and small-job contention, then invoke the
real UI Suspend action. Assert large dispatch count stops while in-flight
outputs still return; small finishes before large resumes. Resume via UI and
assert original large job UUID, retained progress and exact total outputs.
Use fresh fixture cases for three repeated cycles, cancellation during pause,
final in-flight completion during pause, stale action after CPU switch/new job,
config disable while paused, disabled forged action and profiling-disabled
recovery. Preserve fuel/container items when checking conservation. A failed
native prerequisite fails the scenario rather than skipping it.

For diagnostics, seed learned samples through the existing fixture's normal
prewarm path, not fabricated status. Hold pause longer than the configured delay
threshold; capture Suspended, no false row warning/total, and disappearance of
only that job's automatic highlight. Resume and check the delay starts afresh;
then induce a genuine no-progress condition to prove detection still works.
Check a second active CPU throughout. Exercise disabled badge background and
normal button geometry without requiring a separate locale launch.

Extend `DedicatedCpuScenario` with this scenario and reuse the existing file
rendezvous with per-role subdirectories, epoch and monotonically acknowledged
commands. Extend the connected runner and client role allowlist with `beta`;
launch Alpha and Beta as distinct offline test profiles, each with its own
runtime/output/PID ledger. This narrow two-client case is required for CS-4;
other scenarios keep their current sequential launch behavior. Both clients
must observe the same job before and after Alpha's real menu action. Beta sends
a retained stale request after selection changes and observes rejection.

Add exactly two connected server phases in the existing runner. Phase 1 saves
a genuinely paused fixture, records UUID/counts and cleanly halts the server
through the driver. Stop both owned clients and await their exit. Phase 2
restarts the same disposable world with the same artifact hashes and a new
connection epoch, reopens both clients, verifies retained state, then resumes
and completes. Do not recreate the grid or resubmit the job after reload.
Store continuation metadata outside the world with the campaign/head identity;
validate it against the actual loaded job. Finally cleanly stop server and both
clients. No process-wide Java kill and no source-fixture mutation.
Keep suspension enabled for this persistence restart. The supplemental
file-disabled recovery check uses its own disposable world and retained backup;
it does not add a phase or modify this campaign's world, config or backup.

## Verification ladder and commands

All commands below execute only after the implementation PR exists. Record the
actual immutable PR head and generated bundle paths; placeholders mean those
recorded outputs, not guessed directories.

1. Review selection without launch/build:

   ```powershell
   ./scripts/run-ui-smoke.ps1 -Changed -BaseRef origin/master -PlanOnly
   ```

   Add a precise Forge native `crafting-suspension` selection rule for these
   hooks. Do not silently run all addon cases because shared code changed;
   inspect and explain all materially affected selections.
2. With host Java selected by the existing build scripts, run covered core,
   protocol and loader boundary checks:

   ```powershell
   ./gradlew.bat test jacocoTestReport
   ./gradlew.bat :mc_1_20_1_forge:checkTestDriverArtifacts
   ./scripts/test-run-connected-dedicated-ui-smoke.ps1
   ./scripts/test-run-ui-smoke.ps1
   ```

   Run the actual changed selection/dispatcher self-checks too. Preserve 100%
   changed pure-Java line/branch coverage; no exclusions or weakened assertions.
   Compile against minimum AE2 15.0.10. Inspect the remapped Forge production
   JAR/refmap with ASM or javap for each new hook descriptor and both presentation
   targets against prepared AE2 15.4.10. Check the Forge-only mixin config is
   absent from every other artifact. Parse locale JSON, compare new keys and
   placeholders, check guide/wiki links and `git diff --check`.
3. Build/package on host and run only the new base scenario first, retaining
   its fixture for the supplemental manual checks:

   ```powershell
   ./scripts/invoke-ui-smoke-codexvm.ps1 -Target 1.20.1-forge -BaseOnly -Scenario crafting-suspension -Interactive
   ```

   Use the tested implementation branch containing the existing
   [interactive completion fix](https://github.com/cTux/ae2-crafting-time/blob/efa6093b37ea61f9fb07e68f9aa80631c2588bc4/scripts/ui-smoke-progress.ps1#L56),
   included in PR #637's `d5b10fe` head. It exempts interactive
   `state=QUIT_REQUESTED` from checkpoint aging while retaining callback,
   process and overall deadline supervision. This documentation-only base
   does not supply the implementation runner; do not disable its other guards.

   Use the English prepared runtime, production JAR and isolated test-driver.
   Assert actual button rendering, positioning, labels, and server accounting;
   inspect required screenshots, sidecars and automatic-review outcomes. Capture
   running, suspended, small-complete, resumed, cancelled and disabled states.
   After the leaf completes, use the retained fixture to perform all five
   [supplemental runtime checks](technical-design.md#supplemental-runtime-checks-after-the-automated-leaf).
   Save the observed UI snapshots, screenshots, accuracy counts, warning and
   highlight transitions, delay timing and conserved item counts alongside the
   leaf report. Record the tested head, bundle hashes, timestamps and each
   manual result; a leaf PASS does not replace these observations.
4. Reuse that exact head's immutable native bundle. Dispatch connected execution
   through the same host wrapper, with `-BundleDirectory <host-bundle>` and
   `-ServerDirectory <verified-guest-source>` plus `-AcceptMinecraftEula` under
   existing task consent. Its guest invocation is:

   ```powershell
   ./scripts/run-connected-dedicated-ui-smoke.ps1 -Target 1.20.1-forge `
     -Scenario crafting-suspension -ServerDirectory <verified-guest-source> `
     -PreparedLaunch <verified-guest-launch> -BundleDirectory <guest-bundle> `
     -ReportDirectory <fresh-guest-report> -HeadSha <tested-head> -AcceptMinecraftEula
   ```

   Extend existing dispatch to preserve these exact paths and the new scenario;
   no guest Gradle builds. Prove two-player synchronization, disabled/stale
   rejection and the clean restart sequence. Attach phase/PID/start/stop and
   count evidence, both clients' matching-state screenshots, and file hashes.
   Also retain the supplemental non-operator permission evidence. Run the
   stopped-file `craftingSuspension = false` recovery check in a separate
   disposable-world campaign as described in the technical design, preserving
   its original stopped-world/config backup. Record that campaign's identity,
   server revision, saved config, job counts and post-restart state separately;
   do not add a phase to the two-phase persistence run or alter its backup.
5. Because synchronization must preserve optional installation, use existing
   observation mode on the same Forge source/bundle for server-only and
   client-only peers. Open the native CPU/status screen and prove no addon
   button/custom packet or native GUI sync failure; normal native Cancel works.
   Both-installed behavior is covered by step 4. Unsupported target boundary
   tests prove no new mixin or visible control on Fabric/NeoForge; their native
   suspension methods are not called or replaced by this feature.
6. Publish/read back wiki guidance, observe required CI on the exact tested head,
   finish visual review, then shut down only CodexVM and verify its VMX is absent
   from `vmrun -T ws list`. Report local results separately from GitHub checks.

## Environment and budget

Preflight on 2026-09-30 found the compatible Forge 47.4.23 / AE2 15.4.10 manifest
at guest `C:/Users/Public/Documents/AE2CraftingTimeSmoke/prepared/1.20.1-forge/1.20.1-47.4.23/launch.json`.
Java 17.0.20.1 is installed. The matching schema-2 source is
`C:/Users/Public/Documents/AE2CraftingTimeSmoke/prepared-dedicated-issue482-84171020/1.20.1-forge`
with AE2 15.4.10 and GuideME 20.1.15. Revalidate manifest, marker, launcher and
full dependency hashes against the bundle before use. If mismatched, reuse
`prepare-dedicated-ui-smoke-server.ps1`; never relabel an incompatible source.

CodexVM has 16 GiB RAM and 16 vCPUs. Both clients retain the required 8 GiB
maximum heap; use the native dependency graph and a 2 GiB server maximum.
These are ceilings, not initial allocations. Check guest committed memory and
free disk before the simultaneous phase, retain memory-pressure evidence, and
stop with an exact environment blocker if it cannot support both clients.
Changing VM memory is outside this implementation; do not silently reduce the
required clients or substitute fake players.

Budget before execution: host build/checks 15 minutes; one singleplayer launch
and cases 12 minutes; two dedicated server starts plus four client starts and
cases 25 minutes; two optional-install observation cells 10 minutes; visual
review/archive/shutdown 8 minutes. Total runtime-verification budget 70 minutes,
not a measured forecast. Cold-start time has not been measured for this head.
Keep 300-second startup deadlines and the existing 60-second no-progress guard;
long cooking waits must report observable furnace/output progress. Do not retry
known functional failures or spend the whole budget on a stale startup. Record
actual phase timings and failed attempts. A diagnostic continuation cannot
replace the clean final two-phase sequence.

## Acceptance-to-check map and completion

| Criteria | Changes | Check |
|---|---|---|
| CS-1 | Forge menu/screen/logic hooks | Minimum API and production bytecode; singleplayer rendered button assertions. |
| CS-2/3 | State, dispatch gate, NBT and validated C2S action | Core/boundary tests; real provider conservation and lifecycle cases. |
| CS-4 | Forge state packet and persistent job flag | Two real clients and same-world clean dedicated restart. |
| CS-5 | Existing server options, protocol, tick reconciliation | Config/permission/wire tests; live toggle, forged disabled action and profiling-off recovery. |
| CS-6 | Scoped profiler, notifier and UI overrides | Covered transitions; paused/resumed diagnostics and independent second CPU. |
| CS-7 | Locales, guides/wiki and loader/peer boundaries | Format/link checks, published wiki readback, optional-install screen checks and artifact isolation. |
| CS-8 | Driver/runner additions and evidence | All selected checks, reviewed captures, same-head CI and VM shutdown readback. |

Mark the spec in-progress when implementation starts. Mark finished only with
merged implementation, published wiki, all required checks and runtime evidence
linked. Missing two-client/reload proof or skipped production UI hooks remains
an incomplete gate, even when unit tests pass.

## Addon CPU extension

Lifecycle and scope: [replacement addon CPUs](spec.md#replacement-addon-cpus).
This extends, rather than rewrites, the completed standard-CPU delivery.

1. Approve the exact final issue text, update/read back #647 and reconcile it
   with these documents. Only then move the addon scope to ready-to-implement.
2. Extend the shared pure policy/contract tests for capability, native pause
   transitions, config recovery, job replacement and selection. Retain complete
   line/branch coverage. Add Forge-only suspension selection to the existing raw
   class-inspection machinery, with independent selection from profiling. Record
   `(dependency, capability)` keys throughout candidate ownership, decisions,
   snapshots and diagnostics as specified in the technical design; keep the
   installed-mod lookup keyed by dependency. Test independent success/failure
   and fallback for both capabilities, including LightningTech beta.2/beta.3
   profiling-only and the beta.4 suspension floor.
   full released member contracts and negative absent/incompatible variants.
3. Implement Forge adapters in `versions/1.20.1-forge/src/main/java`:
   `AdvancedCraftingSuspensionLogicMixin`, NeoEco adapters for both retained
   dispatch families, and `LightningTechCraftingSuspensionLogicMixin`.
   Register through the Forge suspension configuration. AdvancedAE uses covered
   state and the namespaced job NBT flag; other adapters use native pause state.
   Guard every selected dispatch entrance and preserve in-flight returns,
   lifecycle/cancellation and scheduling cleanup. Keep the native AE2 mixin's
   priority-2100 regression protection.
4. Extend `CraftingSuspensionAccess` and add the Forge selected-CPU resolver.
   Route `CraftingSuspensionMenuMixin` and `CraftingSuspensionC2S` through it.
   Add boundary coverage for all addon menu slots, grid/serial consistency,
   diagnostic-option independence, stale UUID, duplicate, closed/wrong menu,
   unsupported adapter and forged disabled actions. Do not change the wire shape.
5. Mirror pause changes to each engine's existing profiler scope before any
   warnings/estimates run, including native actions and reload. Cover config
   recovery while offline or profiling-disabled, and prove NeoEco internal
   suspension survives Resume. Route `StatsRequestContext.current` and
   `StatsRequestHandler` selected-row requests through the verified selected
   adapter's profiler scope. Route `CpuTtcRequestHandler.collect` estimates
   through each listed CPU's adapter scope while keeping CPU/serial/grid
   validation unchanged. Cover both paths for standard, AdvancedAE, NeoEco and
   LightningTech, competing jobs and profiling switches off. These routing
   corrections are required, not optional inspection follow-ups.
6. Extend `AddonCpuFixture`, `AdvancedAeFixture`, `NeoEcoFixture` and the existing
   LightningTech fixture/scenario registration under the test-driver workflow.
   Parameterize the existing suspension scenario with real replacement engines.
   Keep standard, NeoEco 20.3.0, NeoEco 20.4.2 and LightningTech variants explicit.
   Do not reinterpret a native CPU run as addon evidence. Connected variants use
   the existing two-phase dedicated restart procedure with Alpha and Beta.
7. Update English/Ukrainian GuideME and wiki, `docs/dependencies.md` and test-driver
   spec/design/plan. Keep support claims bound to exact verified artifacts.
   Update the `ServerConfigFile` craftingSuspension description from standard
   CPUs only to standard and supported replacement CPUs, and its test assertion.
   Preserve the default-off behavior and explicit saved choices from #655.
   Inspect production screen targets and real startup for any presentation
   change. Preserve optional-peer screens and ensure there is one pause control.
8. Review the full diff and `git diff --check`, then make one conventional
   implementation commit. The hook creates/updates the implementation PR before
   local test execution. Run required core/boundary checks and focused production
   smoke under the applicable skills; report GitHub CI separately. Merge and
   publication require separate authorization. Finish only after the gates below.

### Addon acceptance map

### NeoEco 20.3 runtime graph

Implementation must add an immutable `neoeco-20.3` verification profile to the
version matrix and bundle preparation. Clone the compatible Forge graph,
replace only NeoEco with the hash-pinned 20.3.0 artifact in addon-evidence, and
seal client and dedicated-server bundles with the same graph identity. Preserve
the normal compatible 20.4.2 graph. Add `-VerificationProfile neoeco-20.3` to
preparation and `run-ui-smoke-codexvm.ps1`; retain the profile and dependency
hashes in the prepared manifest and dedicated source contract. Extend connected
validation to accept this named sealed graph while rejecting latest, unsealed
and mismatched graphs. Do not replace mods in an existing sealed bundle.

After those script changes, run singleplayer with
`pwsh scripts/run-ui-smoke-codexvm.ps1 -Target 1.20.1-forge -VerificationProfile neoeco-20.3 -Scenario crafting-suspension -HeadSha <commit>`.
Prepare its matching sealed dedicated server, then run
`pwsh scripts/run-ui-smoke-codexvm.ps1 -Target 1.20.1-forge -VerificationProfile neoeco-20.3 -Scenario crafting-suspension -ServerDirectory <sealed-server> -HeadSha <commit>`.
The wrapper passes that graph's prepared launch/bundle to the connected runner
for both Alpha/Beta restart phases. Repeat on compatible 20.4.2. Script self-tests
cover profile propagation, pinned hashes, client/server mismatches and restart
manifest reuse. Runtime results record the loaded NeoEco version/hash; a 20.4.2
run cannot count as 20.3 evidence. These flags and bundles are required future
implementation work, not available commands or evidence from this docs PR.

### Acceptance checks

| Criteria | Required evidence |
| --- | --- |
| ACS-1 | Contract fixtures and loader registration; real UI selection for each engine/API family; absent/unknown negative cases. |
| ACS-2 | Actual provider dispatch/return counters, conserved raw/final counts, competing CPU completion and same resumed UUID. Include NeoEco FastPath and LightningTech budgeted scheduling. |
| ACS-3 | Repeated cycles, rejected submission, final in-flight completion, Cancel/soft-cancel, CPU switch and replacement UUID boundary/runtime cases. |
| ACS-4 | Alpha/Beta snapshots before and after clean dedicated save/restart; same world/job, valid resume and exact final counts. |
| ACS-5 | Live switch and startup-file disable on each engine, profiling/diagnostic-off cases, native pause setter synchronization and unchanged NeoEco internal flag. |
| ACS-6 | Paused selected rows/title/cards, scoped warning/highlight cleanup, independent active job, fresh post-resume delay and accuracy exclusion. |
| ACS-7 | Coverage and contract checks, raw/remapped/transformed hook evidence, reviewed captures, locale/link checks, wiki readback, other-target artifact isolation and current-head CI. |

Use focused Forge addon profiles plus the standard suspension regression. Include
the shared-provider setup and dedicated-restart case for each replacement engine;
both NeoEco API families need dispatch and persistence evidence. A unit pass,
successful client launch or native addon pause alone cannot satisfy this matrix.
Retain job UUID, CPU/logic type, addon hashes, source commit, dispatch/return counts
and reviewed screenshots in each report. Verification is not run by this plan.

