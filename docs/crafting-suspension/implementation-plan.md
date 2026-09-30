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
3. Build/package on host and run only the new base scenario first:

   ```powershell
   ./scripts/invoke-ui-smoke-codexvm.ps1 -Target 1.20.1-forge -BaseOnly -Scenario crafting-suspension
   ```

   Use the English prepared runtime, production JAR and isolated test-driver.
   Assert actual button rendering, positioning, labels, and server accounting;
   inspect required screenshots, sidecars and automatic-review outcomes. Capture
   running, suspended, small-complete, resumed, cancelled and disabled states.
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

