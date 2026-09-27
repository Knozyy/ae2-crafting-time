# AE2 Crafting Time Test Driver Technical Design

Lifecycle: see the [scope status and evidence](spec.md).

## Optional connection observation

The observation mode uses test-driver mixins on all twelve `StatsNetwork` send
overloads. Each records entry and the exact loader send invocation after the
production guard. The required injection count fails loading when an invocation
changes. `ConnectionObservation` writes target, artifact hashes, connection
ordinal, timestamps, and per-packet counts; the host verifier rejects stale or
missing receipts. The connected runner stages production and driver together
only where installed, checks artifact inventories, and uses a reviewed native
world marker plus manual UI checkpoints. Forge suppresses its driver-only fluid
registration in this mode. See the [feature design](../optional-connection/technical-design.md)
for the qualification sequence and limits.

## Compact status amounts extension

Extend the existing shared `StandardAe2Scenario` and `StandardCraftFixture`,
with changed native APIs in the existing 26.1.2 fixture/platform adapters.
Reuse native menus, Options controls, final-frame observations and capture
sidecars. Synthetic quantity fixtures may supply raw native menu entries only;
record them as synthetic and retain separate real craft-transition evidence.
Never seed formatted text or observation snapshots. Reuse existing status
fixtures for warnings and color checks.

The [feature plan](../crafting-status-amounts/implementation-plan.md#verification-prerequisites)
owns the exact checkpoints, test-only wider-font pack, resource-reload wait,
scale restoration and two-launch config persistence check. Keep the pack and
saved config in the disposable runtime, with capture/artifact identity in the
campaign evidence. Add no general fixture engine or second runner.

## Capture evidence binding

`CaptureEvidence` writes a sidecar only after the PNG completes, preserving the
existing snapshot fields and adding capture identity, dimensions, renderer,
SHA-256 and capture duration. Shared synchronous capture and 26.1.2's callback
path use the same writer; the latter keeps its completion future. World and
unobserved screens get their own full-frame snapshot instead of an old menu's
geometry. Candidate source and JAR hashes remain campaign metadata, so ordinary
code changes can still compare against an approved visual reference.

The standard flow retains its eight-frame minimum and now compares relevant
rendered geometry, row order, text keys/styles, widgets and tooltip keys.
Changing elapsed digits does not reset this layout check; existing value and
server assertions still run. State transitions log UTC and monotonic durations.
The [host gate](../ui-smoke-evidence.md#automated-evidence-gate) independently
validates PNG bytes and qualified visual regions after the client exits.

Apply the [planned smoke-policy enforcement](../automated-ui-testing/technical-design.md#planned-smoke-policy-enforcement)
to shared and version-specific scenarios. English-only requirements below do
not claim the current bilingual driver states have already been removed.

## CPU-list total TTC scenario

`CpuListTtcScenario` owns one bounded multi-CPU fixture and reuses the normal
Crafting Status menu, final-frame observation store, screenshot writer, suite
reset, and result model. The fixture submits real jobs to specific native CPUs,
seeds only the retained throughput samples needed to make their totals distinct,
and records the authoritative server estimate for each CPU. It never inserts a
client snapshot or calls a production renderer.

The CPU-list observation mixin records each rendered card's serial, full tooltip
name, current-job identity, bounds, final TTC text, and badge rectangle after
the production mixin runs. Assertions bind by serial plus current job, not row
index or name. Request delay/drop is driver transport control around the real
packet boundary; the production cache still owns sequence, session, replacement,
and expiry decisions.

Integrated and connected dedicated modes share fixture transitions and client
assertions. Dedicated setup runs on the disposable server, then the matching
prepared client opens that grid normally. Both modes record target, profile,
dependency/adapter identity, artifact hashes, server totals, and client values.
The 1.20.1/1.21.1 implementation lives in `shared/src/testDriver1201`; the
26.1.2 source set keeps only its changed registry, rendering, and connection APIs.

The `recurrent-plan` standard-AE2 leaf owns a managed native crafting provider
beside `StandardCraftFixture`, with encoded real patterns for the recurrence,
negative-control, retry, exact-key, amount and chunk-boundary cases in the spec.
It proves the production diagnosis through the native
plan row and tooltip rather than seeding driver state. Its connected counterpart
keeps the same marked-loopback, bounded-control, identity, and cleanup rules as
the CPU-list runner. The planned resource-icon cases below use that same bounded
exception; no general multiplayer scenario is authorized.
Every target uses one client process with the fixed offline fixture name/UUID
and its own runtime, evidence and control directory. The client visits separate
grids with opposite recurrence outcomes, swaps real patterns/replans, and
reconnects. The server binds acknowledgements to the observed recipient UUID,
menu and revision. The phase policy rejects old-phase commands; successful
acknowledgements remain idempotent across waiting frames. Pure transition and
packet-boundary tests cover recipient mismatches, sequential sessions and cleanup.
Remove the concurrent-client branch, second role and dual-client memory gate;
retain the existing one-client path on NeoForge 1.21.1 as on the other targets.
All clients run sequentially, with one 8 GiB client at a time and verified exit
before the next launch. The dedicated server may persist through reconnect.
These checks establish single-client lifecycle and recipient-boundary evidence,
not simultaneous-player behavior.
Driver-only observers bind every accepted diagnostic to the native menu
revision on the client thread and compare native quantities before and after.
Recurrence runs, including selected-case suites, retain the runner's existing
20-second callback and 60-second active-checkpoint watchdogs. Fixture case and
sort are part of the reported checkpoint.

Connected mode uses an atomic properties-file rendezvous inside the disposable
run directory. Commands only ask the server fixture to perform the next real
mutation; acknowledgements carry the active terminal and authoritative totals.
The production request/snapshot packets remain the only source of client values.
Reconnect uses Minecraft's normal server connection screen while the disposable
server and fixture persist.
The connected runner never mutates the prepared server. It requires a target-
matched source marker, validates launch and artifact identities before copying,
writes a disposable marker in a report-owned runtime, and launches Java through
a UTF-8 argument file so result/control paths containing spaces remain one argument.

## Stored-variant plan scenario (planned)

Implement `stored-variant-plan` through the existing `StandardAe2Scenario`,
`StandardCraftFixture`, recurrence fixture/observation, and dedicated control
seams. Reuse real damaged-item AE2 keys and processing-pattern encoding; keep
fixture changes on the server and final-frame observations on the client.
Shared 1.20.1/1.21.1 driver code owns common transitions; 26.1.2 retains only
its changed API adapters. The [feature plan](../exact-ingredient-mismatch/implementation-plan.md)
owns the exact acceptance/check mapping.

Capture the original native summary identity, revision, quantities, Start state
and grid node count. Issue each storage mutation once, retain its pending
operation and wait for native notification/synchronization before observing
the final row. Never force production flags, callbacks or renderer results.
Driver-only observers record callback, dirty refresh, packet ordering and
watcher teardown; fail if idle frames trigger storage rescans or a destroyed
watcher remains registered. Observe replacement masks, including all-zero
clears, without mutating the production diagnosis to satisfy UI assertions.

Extend existing runtime/support/SuitePlan, host scenario validation,
groups/impact/selection, required results/screenshots and connected dispatch
for this leaf. Preserve loopback, immutable-source/disposable markers,
artifact/dependency identity, bounded phase acknowledgements, existing
20-second callback and 60-second checkpoint watchdogs, and exact-process cleanup.
The dedicated run uses the same single client's network/replan/reconnect flow;
unit boundaries cover recipient mismatches. Add no grid node, general runner,
arbitrary server automation or provisioning implementation to this scenario.

## Delayed resource icon fixtures (planned)

The [#482 fixture design](connected-resource-fixtures/technical-design.md) owns
the bounded server protocol, typed fixtures and fixture-only evidence gate.
Its [implementation plan](connected-resource-fixtures/implementation-plan.md)
separates prerequisite qualification from the final #376 icon assertions below.

Extend the existing `delayed-resource-icons` and `appmek-resource-icons` against the
[scenario contract](spec.md#delayed-resource-icon-scenarios-planned) and
[production design](../provider-locate/resource-icons/technical-design.md).
Reuse `ResourceFixtureClient`, `ResourceFixtureServer` and their bounded control
protocol from merged #484, backed by the existing `StandardCraftFixture` grid,
processing-pattern, native CPU and output-return seams. Shared driver code owns
common transitions; target adapters keep only their native API differences.
AppMek key construction stays in the two target-specific optional fixtures,
loaded only for that selected case. Keep all fixture content out of production.

Encode real water/lava or oxygen/hydrogen output patterns on a provider. Retain
the actual typed key and amount on the server; hold dispatched output, poll the
real delayed transition, then release the correct key through normal crafting
insertion. Existing fixtures size storage and CPU capacity for the job. Forge's
registered `ae2craftingtime_test_driver:resource_fixture_fluid` uses the same
flow with no bucket, water textures and cyan tint `0xff00ffff`. Keep registration
and appearance metadata in the evidence.

Read `plates()` and `renderPlates()` without changing either. Bind each world
capture to its server output key and selected provider position; exercise two
outputs sharing one provider and verify the survivor's key after winner recovery.
World capture stages cannot depend on menu-frame observations while the terminal
is closed. Preserve each asynchronous action until completion, issue it once,
and use existing readiness/progress deadlines. Restore fixture settings and
clear jobs/blocks/state on teardown, including failure cleanup.

Extend the existing connected runner's mode validation, driver lifecycle
guard and server/client dispatch only for these two leaves. Reuse its bounded
commands and acknowledgements for create/hold/release/cancel and reconnect;
bind them to the active fixture, player and phase. Production packets alone
create client highlights. Preserve loopback addressing, schema-2 immutable-source
and disposable markers, exact dependency/launcher/artifact hashes, idempotent
acknowledgements, timeouts and PID/start-time cleanup. The
[bounded provisioner and prewarm design](connected-resource-fixtures/technical-design.md#provisioning-evidence-and-ownership)
now belongs to #482; no second smoke runner, general server hosting or arbitrary
remote command is part of this work. If existing
seams cannot support it without an independently large infrastructure change,
deliver that prerequisite separately before implementing dependent checks.

Route these existing leaves by scenario name in both driver runtimes, so ordinary
runs perform production acceptance and explicit `-ResourceFixtureOnly` keeps its
NOT_RUN contract. Update supported-case/SuitePlan checks, host/VM launch guards,
connected/prewarm validation and `DriverResult`/screenshot contracts together.
Keep fixture evidence schema 1 unchanged and require separate
`resource-icon-evidence.json` schema 1 for ordinary runs under the
[production evidence contract](../provider-locate/resource-icons/technical-design.md#production-acceptance-on-the-existing-fixtures).
Extend the nearest driver and PowerShell boundary tests after the hook-created PR.
Keep the existing `appmek-cpu` contract unchanged: these are resource lifecycle
cases, not additional addon CPU fixtures. Define checks for dispatch, delayed
plate/key, selected winner, recovery/finish/cancel, reconnect and cleanup; name
captures by scenario, resource and checkpoint to avoid overwriting evidence.
Add bounded resource reload and provider removal/unload checkpoints to these
existing transitions; await reload completion and verify surviving/restored
state under the production lifetime rules. Server mutations use the existing
revision/sequence guards. Review icon appearance separately from semantic
assertions; missing evidence fails and unreviewed images remain REVIEW_REQUIRED.

## Badge background standard leaf (#532)

Reuse `StandardAe2Scenario` and its existing standard fixture. At stable Plan
and Status frames, pause the usual flow to navigate native Client → Appearance
widgets. Set a distinctive badge RGB and opacity through the actual input boxes,
save, capture On, save Off, capture Off, then save On and capture restoration.
The same status rows must survive each client-only change. Keep the Plan's
missing-input tint through all three captures, then supply its cobblestone and
replan through the native menu before Start. Use the existing
frame wait and `DriverScreenshots` sidecar path; add the six distinct Plan/Status
Off/On screenshots to `ui-smoke-groups.json`. Keep observation checks for text
and layout, and leave actual pixel judgment to manual review. The leaf ends on
the native Status screen and leaves client settings restored On.

## Single-world fixtures and transitions

The [four-client qualification](../automated-ui-testing/prepared-clients-2026-09-08.md)
exposed boundaries that matter when cases reuse one loaded world:

- Clear blocks, entities and retained fixture state on every reset. An all-air
  structure needs clearing, not `StructureTemplate.placeInWorld`; failed
  placement of a nonempty structure still fails the suite.
- Open a terminal or submit its amount once per transition. Poll for the server
  response instead of pressing again each frame. Reset the submission guard
  only when entering the next transition.
- Place native fixture blocks first, then poll grid, drive and CPU readiness in
  the existing bounded completion phase. A missing node immediately after a
  reset is not proof of incompatibility. Do not suppress exceptions or count
  incomplete readiness as success.
- Once a server operation starts, keep polling that same future even if the UI
  warning changes while it runs. Capture each checkpoint once before advancing.
- Choose output quantity explicitly and supply enough inputs, storage and CPU
  bytes for that quantity. A large recovery workload must not silently become
  the default for unrelated cases. Drain the provider's exact pending inputs
  before testing a fresh rejection. Use a multi-item input batch with less than
  one batch of free space to demonstrate partial insertion.
- Establish a real LOW neighbor notification before the later HIGH pulse.
  Setting AIR where AIR already exists is not a new edge. Preserve native lock
  behavior and the existing recovery bound. Lock recovery means LOCKED clears;
  INPUT BLOCKED may then be correct if the destination is still full.

Shared and 26.1.2-native implementations must preserve the same behavior while
using their own Minecraft APIs. For a packaged Fabric injection failure, compare
the actual target invocation with named and intermediary descriptors; retain
required injection counts and verify both mappings. A successful compile does
not establish that the packaged native client can load the mixin.

## No-provider status scenario

`NoProviderScenario` owns an isolated native CPU, drive, energy cell, and two
providers. Server-thread operations place the fixture, calculate and submit a
64-diamond processing job, and mutate real pattern inventories. One cobblestone
batch sits in a chest; blocking mode keeps the remaining batches scheduled.
The calculation future is polled without blocking the server thread.

The shared frame observer checks the final label, badge bounds, and tooltip.
Set English (`en_us`) before inspecting text; do not add language-switch steps. Recovery must
reach the same menu within two seconds. Cancellation also checks the profiler's
server-side state. Capture each distinct English UI checkpoint; no language-duplicate images are required.
The version-specific `DriverPlatform.processingPattern` method converts the
array/list API difference; the 26.1.2 counterpart uses its native identifiers
and registry API. No code seeds missing-provider evidence.

## No-space status driver

`NoSpaceScenario` runs after the shared disposable-world safety check and owns
only its native storage fixture and bounded UI checkpoints. It uses no optional
addon. The full cell and retained CPU items are real AE2 inventories; no test
sets the menu warning flag or calls production display helpers. Existing frame
observers include Crafting CPU screens and capture final text draws, badge
bounds, and hovered tooltips. Restore writable capacity on the server, then
require the same client menu to clear its synchronized warning.

## Fabric 1.20.1 port

The shared `shared/src/testDriver1201` source set owns the existing scenario
state machine, observation mixins, suite orchestration, and portable fixtures.
Forge and Fabric keep separate entrypoints and loader adapters. Forge forwards
render events; Fabric uses screen render events and a driver-only end-of-frame
GameRenderer mixin. Both run assertions after a completed frame.

Fabric Loom remaps its driver and optional compile dependencies. The shared
Shadow configuration packages the existing MCP libraries only in the companion.
The artifact check rejects driver content in production and production classes
in the companion. The driver metadata requires the exact production version.

The existing launcher and VM dispatch chain carry `-Target` through status,
runtime paths, suite selection, and result validation. Forge keeps its default
and `resolved-mods`; Fabric uses `mods`. Both reuse the same marked source world,
copying it once per schema-2 suite and restoring it between cases. Schema-1
per-case copies are explicit isolation diagnostics. Fabric provisions an item cell and, unless
the scenario builds its own CPU, a native AE2 CPU because the source world uses
Forge-only addons. The DISK scenario then removes that supply and must craft
from its own DISK. `scripts/ui-smoke-fabric-suite.json` owns the current Fabric case list;
unavailable Forge-only addons stay out. Read the expanded campaign plan for
required cases and separate dependency graphs instead of relying on an old count.
The common ExtendedAE fixture checks the actual registered assembler block and
its AE2 node, avoiding the upstream package-name difference between loaders.

For phase-2 diagnosis, `prepare-ui-smoke-resume.ps1` atomically captures and
validates the phase-1 world/evidence continuation against the exact staged
bundle. The bundle profile declares `base` or `catalogue`; the runner hashes the
ordinal managed JAR name/hash set and writes both values into the disposable
world marker. Capture and restore compare those values before any Java process
can start, so a reduced graph cannot relabel a world created with addon data.
`run-ui-smoke.ps1` restores validated inputs into a new runtime copy and
passes `resumeOnly=true`; that path has one launch and can never produce final
approval. `driver-progress.json` is atomically refreshed from the runtime tick
boundary and advances a separate checkpoint timestamp only when the scenario
checkpoint changes. The host watchdog retains PID, process start, head, bundle,
campaign, world, and last progress values before terminating a stalled client.
It checks callback liveness for every current-PID stage. Loader, world, and
fixture preparation use the absolute startup deadline; only
`state=WORLD_READY phase=ACTIVE` arms the 60-second checkpoint-stall deadline.

`use-ui-smoke-bundle-cache.ps1` seals a relative-path artifact hash tree under
head/fingerprint/target/profile/graph. Reuse recomputes that tree; it never
silently rebuilds or accepts changed bytes. Final approval bypasses the
diagnostic shortcut and executes the complete two-process flow.

## NeoForge 1.21.1 port

Reuse the shared driver state machine, observations, result checks, and suite
orchestration. NeoForge owns its client entrypoint, changed addon APIs, and
Minecraft 1.21.1 data-component boundaries. Identical addon fixtures live in
`shared/src/testDriverAddons`, included by Forge and NeoForge only. Its companion uses ModDev's mapped
compile classpath and a separate Shadow artifact; NeoForge uses named runtime
classes, so the driver needs no Forge reobfuscation or Fabric remapping.

The dispatch chain selects JDK 21 for `1.21.1-neoforge`, carries the exact target
and artifact identity through validation, and selects the NeoForge suite list.
NeoForge copies its tracked native 1.21.1 world; it never upgrades the Forge
fixture. The base fixture copies its small AE2 grid into open sky inside each
disposable world, then supplies native storage, a CPU, a real furnace pattern
and assembler, and a retained UI sample before addon setup. Addon cases clear that
sample and require a new one after actual crafting.
Runtime preparation mutates only disposable copies; the tracked native fixture
preconfirms NeoForge's experimental-world warning. Production protocols stay unchanged.

## NeoForge 26.1.2 port

Reuse the driver result model, safety checks, suite orchestration, and scenario
assertions. Keep changed Minecraft 26 and addon APIs in the target driver source
set. Observe `GuiGraphicsExtractor` text, fills, tooltips, and AE2 table output,
then capture the rendered framebuffer after the frame completes. Route the
new input events and asynchronous screenshot readback through the target API.

The native fixture creates its grid only after disposable-world verification.
Every addon craft clears the retained sample and requires a fresh sample from
that actual craft. `ui-smoke-neoforge-26.1.2-suite.json` owns the exact pinned
scenario list. The existing runner and VM dispatcher retain their process,
world cleanup, evidence, and independent result-validation contracts.

## Original Forge implementation evidence

- `:mc_1_20_1_forge` already owns the Forge 1.20.1 client, Java 17 toolchain,
  AE2 dependency, production source sets, reobfuscation, and `distMod` task.
- `scripts/run-client.ps1` already resolves compatible or latest dependencies
  from `scripts/run-client-versions.json` and selects an isolated run directory
  through `runtimeRunDirectory`.
- Production Crafting Plan behavior is contributed by
  `CraftConfirmScreenMixin`, `CraftConfirmTableRendererMixin`,
  `AbstractTableRendererMixin`, `TtcSortButton`, `ClientStatsRequests`, and
  `StatsChatMessages`.
- `distMod` copies one explicitly named production JAR into `dist`; release and
  deploy scripts derive published artifacts from `release-matrix.json`.
- The repository has no Forge 1.20.1 fixture, driver source set, UI-smoke
  runner, or MCP dependency to reuse.

The design therefore adds one isolated source set and one runner to the existing
Forge module. It does not change production Java, packets, persistence, or the
release matrix.

## Build and source ownership

Add `testDriver` Java and resource source sets in
`versions/1.20.1-forge/build.gradle`:

```text
versions/1.20.1-forge/src/testDriver/java/
versions/1.20.1-forge/src/testDriver/resources/
```

The source set compiles against `sourceSets.main.output` and the Forge/AE2
compile classpath. `testDriverJar` packages only `testDriver.output`, applies
the normal Forge reobfuscation step, and writes to the root project:

```text
build/test-driver/
  ae2-crafting-time-<mod-version>-forge-1.20.1-test-driver.jar
```

The driver JAR contains `ae2craftingtime_test_driver` as a second development
mod. Its `mods.toml` declares an exact dependency range of `[<mod-version>]` on
`ae2craftingtime` and the same Minecraft and Forge ranges as the production
module. Its bootstrap registers behavior only on the physical client and stays
inert without the explicit test option. It relies on the matching production
mod for AE2 compatibility instead of declaring a narrower AE2 range.

The driver has its own mixin config and refmap. No driver directory is added to
`sourceSets.main`, `jar`, `reobfJar`, or `distMod`. The MCP SDK is pinned only in
the driver configuration and included only in the driver artifact. Use the official Java
SDK and its Streamable HTTP server transport; do not implement JSON-RPC framing
or protocol negotiation by hand. Verify and pin a Java 17-compatible SDK version
against the current official protocol during implementation.

## Development client installation

Use the shared client scripts directly so there is one installation path:

```text
scripts/run-client.ps1 -Target 1.20.1-forge
scripts/run-client.sh -Target 1.20.1-forge
```

For the Forge 1.20.1 target, each shared script resolves the selected profile,
runs `:mc_1_20_1_forge:testDriverJar` with the same runtime version properties,
removes other `ae2-crafting-time-*-forge-1.20.1-test-driver.jar` files from that
profile's managed mod directory, and copies the exact artifact to:

```text
compatible -> versions/1.20.1-forge/run/resolved-mods/
latest     -> versions/1.20.1-forge/run-latest/resolved-mods/
```

The installed filename joins the managed manifest so dependency readback and
later cleanup see the actual client contents. Driver build, exact-name check,
stale cleanup, or copy failure stops the launch. Other targets do not build or
install a driver.

Add one optional runtime-directory parameter to both shared scripts. It defaults
to the current `run` or `run-latest` directory and is
used consistently for dependency installation and Gradle's
`runtimeRunDirectory`. The UI-smoke runner supplies its isolated directory under
`build/ui-smoke`; it does not maintain a second driver-install path.

Forge discovers the installed JAR through the module's existing
`runtimeRunDirectory/resolved-mods` file dependency. The production mod remains
the normal `sourceSets.main` development mod. An ordinary launcher therefore
contains the driver but executes no test behavior; the UI-smoke runner adds the
explicit scenario option.

## Fixture and runner ownership

Track the source fixture at:

```text
versions/1.20.1-forge/run/saves/ae2-crafting-time/
```

The saved player faces a known AE2 terminal. The world contains retained
production stats, a craftable output, and
`.ae2-crafting-time-test-fixture.json` with a fixed schema, scenario ID,
terminal position, output ID, and source-fixture ID. The driver reads only this
known marker path; it does not offer filesystem access.

Add `scripts/run-ui-smoke.ps1`. Its first version supports only `craft-plan`
and `1.20.1-forge`, with `-Latest` and `-Interactive` switches. It:

1. calls the existing Forge 1.20.1 launcher path for the chosen profile, which
   resolves dependencies and installs the matching driver;
2. builds the production development classes;
3. reuses `build/ui-smoke/1.20.1-forge/<profile>/runtime` and supplies it as
   the shared launcher's runtime directory, preserving resolved dependencies
   and Gradle/runtime caches between runs;
4. copies the tracked fixture into that runtime with a new disposable ID;
5. sets fixed client options and the explicit scenario property;
6. starts the module's `runClient` with that runtime directory;
7. records only the launched process tree;
8. validates output and logs after exit; and
9. removes the disposable world after evidence has been copied out.

`run-ui-smoke-codexvm.ps1` incrementally mirrors the shared checkout into one
stable guest-local directory, pins JDK 17, and dispatches the runner into the
logged-in Codex session. `run-ui-smoke.ps1` writes stdout, stderr, and an atomic
`status.json` to the shared report directory. The status exposes the current
phase, exact child PID, Java home, exit code, and artifact paths. OpenSSH is the
normal host transport; VMware `runProgramInGuest` remains a second transport.
Neither requires VNC terminal polling.

The automatic runner requests normal shutdown first. On timeout it terminates
only the process tree it launched. It never searches for or kills Java
processes globally.

The compatible run is the required gate. `-Latest` writes to a separate output
directory and reports upstream setup/startup failure distinctly; it cannot
convert a compatible failure into a pass.

## Driver state machine

`CraftPlanScenario` owns one bounded state machine:

```text
STARTING
  -> WORLD_READY
  -> TERMINAL_OPEN
  |  -> RESULT_WRITTEN (ME Requester)
  -> PLAN_OPEN
  -> PLAN_STABLE
  -> BASE_CHECKED -> SORTS_CHECKED -> TOOLTIP_CHECKED
  |  ADDON_CPU_SELECTED -> ADDON_CRAFT_SUBMITTED
  |  -> ADDON_SAMPLE_RECORDED -> ADDON_PLAN_OPEN
  -> RESULT_WRITTEN
  -> QUIT_REQUESTED
```

Each transition has an absolute deadline and records expected and observed
state on failure. A timeout enters `FAILED`; automatic mode writes evidence and
quits, while interactive mode stops scenario actions and leaves the read-only
diagnostic endpoint available until `minecraft_quit` or process exit.

The fixture positions the player and terminal so the driver can use normal
client interaction. It locates the visible target by output ID, derives click
coordinates from the actual widget or item bounds, and calls the screen's normal
input path on the client thread. Sort modes are changed only by clicking the
real `TtcSortButton`.

For the CPU-list leaf, `CPUSelectionListObservationMixin` records each card at
the native name-render call with its displayed serial, job identity, geometry,
selection, frame, and draw scroll offset. `UiSnapshot` also carries the separate
raw menu serial order for the AE2-mode oracle. The fixture's isolated 33-busy
phase captures real client request batches with monotonic send timestamps and
proves full off-screen coverage and one-second cadence within the existing
32-serial packet boundary. Driver-only input controls call native wheel,
hit-test, selection, and cancel paths; checkpoint JSON retains their raw order,
draw scroll, client cards, requests, and authoritative server state.

`AddonCpuFixture` owns the shared asynchronous place/finish/select lifecycle.
Its registry maps each `*-cpu` scenario to one driver-only implementation.
AdvancedAE, AppliedE, Applied Mekanistics, BM Addon, Crazy AE2 Addons,
ExtendedAE, ExtendedAE-Plus, MEGA Cells, NeoEco AE, OMNI Cells, OmniSequence, LightningTech,
and ProjectCell contain only their
mod-specific fixture code. ExtendedAE replaces the disposable world's AE2 molecular assemblers and
selects an existing idle CPU;
ExtendedAE-Plus reuses that setup after verifying its mod is loaded. BM Addon
places its Blood Assembler, installs a real Blood Pattern, supplies its inputs,
and selects an existing idle CPU. Crazy AE2 Addons places a native AE2 1K
crafting storage CPU and selects that recorded cluster.
ProjectCell removes the normal cobblestone supply, mounts a player-bound EMC
Storage Cell, and verifies that the grid can supply the furnace craft from
ProjectE EMC before selecting an existing idle CPU. AppliedE grants the player
furnace knowledge and EMC, mounts a powered Transmutation Module, and verifies
its native EMC crafting pattern before selecting a native AE2 CPU. Applied
Mekanistics mounts a chemical storage cell, fills it with oxygen through the
addon's native AE2 key, and places a native AE2 256K CPU. The
Applied Botanics fixture mounts its real mana cell and verifies the native
`ManaKey` through the storage grid before using the existing CPU selection and
profiling flow. It supplies a native AE2 CPU and normal recipe ingredients so
focused addon runs do not depend on CPUs or cells from absent addons. A new
optional dependency extends that registry and adds a
`testDriverCompileOnly` dependency when it is not already on the inherited
compile classpath. `appbot-fork-cpu` reuses the mana fixture because the published
fork's mana key/type bytecode is identical. The runtime matrix uses
`replaces_project_id` to suppress the original when the fork is selected, and
`modrinth_dependencies` supplies Botania for a focused CurseForge run.
A fixture may override the marker output only when the add-on
requires its own pattern type; it does not add a scenario branch to
`CraftPlanScenario` or `run-ui-smoke.ps1`.

The shared CPU flow delegates submission to the fixture; its default still
calls `CraftConfirmMenu.startJob`. Advanced Peripherals overrides only that
submission boundary to invoke the real ME Bridge `craftItem` method with its
attached CC:Tweaked computer. Placement supplies a native CPU, storage and
ingredients, and waits for both the AE2 connection and computer attachment.
The existing sample/TTC checks remain unchanged.

LightningTech supplies a real smooth-stone processing pattern above a fueled
vanilla furnace. A hopper returns the result through an ME interface on the same
grid. This exercises external-machine output insertion rather than only the
crafting-table batch path. It selects its Tianshu pool again inside the same server-thread
submission call, then requires one active job in that pool. AE2 can refresh the
CPU list between driver steps and reset a field-only selection. This check keeps
a different CPU's sample from passing the LightningTech scenario.

AE2 Things composes the native CPU fixture and mounts a `DISKCellInventory`
in the existing drive. It seeds the cell directly and verifies grid visibility
before reusing the common native submission and sample/TTC checks.

Expanded AE composes the native fixture, places `ExpBlocks.CPU_2` beside its
storage, and forms a two-block native CPU cluster. The fixture checks the
actual co-processor count before the unchanged submission/sample/TTC flow.
It does not bypass the full-profile incompatibility exclusion.

Wireless terminal scenarios are intentionally separate from the `*-cpu`
registry because they add no crafting CPU. `WirelessTerminalFixture` links the
selected addon's charged terminal to a real wireless access point on the
disposable grid. The scenario opens it through normal item use, captures the
final tooltip drawn for the known craftable entry, and then reuses the standard
Crafting Plan observation path. The current fixtures cover AE2 WCWT and AE2
Wireless Terminals. The AE2 Import Export Card fixture uses the same flow with
a real export card installed in the addon's modified standard terminal and a
deterministic profile sample for its disposable grid.

The AEInfinityBooster fixture installs an Infinity Card into the linked access
point and moves the player beyond that access point's normal range. It reuses
the wireless item-use and Crafting Plan flow, but does not request a terminal
tooltip check because the addon only changes range. The fixture keeps its grid
chunk loaded in the disposable world and seeds a deterministic profile sample.

ME Requester also uses a separate screen flow. Its fixture places and configures
one requester with a deterministic profiler sample on the disposable AE2 grid.
The final frame records active menu slot bounds in GUI coordinates and checks
badges against those bounds and the screen's widgets.

AE2 Network Analyser uses a bounded screen-only flow. The fixture equips its
real analyser item, opens `GuiAnalyser` through normal item use, and verifies
the matching menu and viewport bounds without inventing a TTC hook for a
topology-only tool.
The shared scenario opens that block through normal client interaction and validates
the final translation-keyed TTC row, total, badge geometry, and screenshot.

Plan data is stable after the same screen and ordered output IDs are observed
for three consecutive rendered frames. A new screen, changed row order, or
changed plan restarts the count.

The focused `crafting-tree-read-recovery` and `merequester-read-recovery` cases
reuse these fixtures with isolated addon JARs whose reflective read member has
been renamed together with its upstream references. The driver observes the
original rendered content and absence of TTC additions; Tree also exercises
the original tooltip and real details/reset clicks without a stats response.
Pair each recovery case with `craft-lifecycle` in one suite, then check the
launcher log for one bounded warning and its original reflection failure.

## Single-launch orchestration

`TestDriverRuntime` sequences the existing `CraftPlanScenario` instances for an
explicit suite plan. `SuitePlan` validates bounded, unique scenario IDs,
the matching first world, and non-interactive execution. Output paths are derived
from validated scenario names. Preflight every fixture marker and reject linked
world paths before loading any suite world.

After a case reaches `RESULT_WRITTEN`, intercept its normal quit transition.
Write the suite progress atomically. In schema 2, keep the integrated server and
world loaded. Restore the pristine bounded fixture on the server thread, including
block entities and inventories; remove case jobs and entities, restore player and
profiler state, and clear client caches and driver observations before constructing
the next scenario. Capture the pristine state before the first case mutates it.
Do not snapshot a completed case as the next case's baseline. Await reset completion
and normal rendered readiness with bounded deadlines; reset failure aborts the
suite. No production test hooks are added. Schema 1 keeps distinct world IDs and
the normal `clearLevel` / world-open flow for explicit isolation diagnostics.
Verify the running server's actual save path, not just a marker in another folder.

The final case closes normally. A failed case aborts the suite; untouched cases
remain `NOT_RUN`. Record a single process ID plus per-case start/end timestamps.
Wireless checkpoints wait for an actually rendered item tooltip, including
range-only terminals that do not require tooltip TTC. Stable plan captures also
require drawn TTC badges and total text, not only populated row data.

The normal per-case result files and screenshots remain the source of assertion
evidence. Test plan validation, summary completion/failure, and world-path guards
at their pure boundary; verify shared-world resets and cache isolation in CodexVM.

## UI observation boundaries

The dispatch observer hooks the server-aware `finishJob` overload. The older
overload delegates to it, so each completed job is counted once regardless of
which overload the integration calls.

Crafting Tree reuses the plan-opening flow and then clicks its actual toolbar
button. Its small scenario adapter reads the upstream widget's layout to find
a crafted node. Final GuiGraphics badge fills and rendered tooltip components
are the evidence; production TTC helpers are never treated as proof of display.
Both original and Refreshed screen names are recognized without loading either
optional class. Each tree checkpoint is captured after a completed frame.

AdvancedAE's fixture forms and validates a 3x3x5 enclosure through the addon's
calculator. Its three interior blocks are the core, accelerator, and entangler.
It checks actual cluster capacity against the installed blocks and submits the
real plan through that cluster's `submitJob`, then verifies its active job before
accepting the shared new-sample check. AdvancedAE 1.3.6's service mixin falls
back to automatic selection for the `AdvCraftingCPU` wrapper; using the cluster
API prevents a different computer from satisfying this fixture's assertions.
This scenario verifies CPU execution and profiling, not the addon's menu routing.

### CodexVM rectangular atlas probe

Opt in with `-Dae2craftingtime.test.vmTextureProbe=true` only during an explicit
driver run. VMware SVGA3D reports `GL_MAX_TEXTURE_SIZE=16384`, but its proxy test
rejects a 16384-square RGBA texture (1 GiB) while accepting the pack's 16384x8192
atlas (512 MiB). An independent guest LWJGL probe also successfully allocated the
actual rectangular texture with no GL error. Vanilla incorrectly lowers its
dimension limit to 8192 because it probes only squares.

The driver-only `RenderSystemMixin` changes only that one SVGA3D proxy request
to 16384x8192. The real OpenGL proxy result still determines success. It does not
forge GPU capabilities, change actual texture allocation, downscale assets, or
run in normal clients without the opt-in. Larger square atlases can still fail;
this workaround is not a promise of unlimited VM graphics memory. All normal
startup/atlas error checks remain required. Player JARs contain none of this code.

Driver mixins target AE2 and Minecraft UI boundaries, not production reporting
methods. Use a lower mixin priority than the production config so observations
run after normal AE2 Crafting Time injections.

`UiObservationStore` retains only the latest completed frame:

- active screen and menu class;
- the list passed into `CraftConfirmTableRenderer.render`, which is the actual
  post-sort visible order;
- final description and tooltip components returned by the AE2 renderer;
- actual `GuiGraphics.drawString` calls for AE2 Crafting Time translation keys;
- actual background-color fill calls, merged into badge rectangles;
- `TtcSortButton` identity, tooltip/state, and bounds;
- the final tooltip rendered while a registered wireless terminal is active;
- the final ME Requester screen text and badge bounds;
- item-cell and AE2-owned widget bounds; and
- GUI bounds and scroll position.

The store swaps frames only after rendering completes, so MCP and scenario
checks never read a partially collected frame. Captured components keep their
translation keys and arguments; rendered text is added only for diagnostics.

The layout validator checks containment and rectangle intersections. It does
not duplicate production TTC calculations. Sort checks compare observed output
IDs across clicks: original AE2 order, ascending known TTC, then descending
known TTC, with unknown rows stable at the end. The tooltip check uses the final
AE2 return value while the cursor is over the known row.

Screenshots use Minecraft's framebuffer capture after three stable frames. The
base image moves the cursor outside the GUI; the tooltip image places it at the
observed row center. Each of the three sort-mode observations also saves its
own `craft-plan-sort-<step>.png` before the next click. Files are limited to the
scenario output directory.

## Thread ownership

`DriverScheduler` has one bounded client queue and one bounded integrated-server
queue. A queued call carries a deadline and completes a future. Client screen,
input, widget, and framebuffer access runs through `Minecraft.execute`.
Integrated-server checks run through `MinecraftServer.execute`.

The scenario tick and MCP request threads never retain live game objects across
thread boundaries. They exchange immutable snapshots containing strings,
numbers, booleans, and rectangle values. Queue saturation or timeout returns a
structured failure and does not retry automatically.

## MCP endpoint

Interactive mode starts the official SDK's Streamable HTTP server on an
ephemeral `127.0.0.1` port. The runner supplies a random 256-bit bearer token in
an inherited environment variable and passes the token to the MCP client
without printing or persisting it. The endpoint rejects non-loopback peers,
missing or invalid authorization, a second active controller, requests over 64
KiB, responses over 1 MiB, unknown tools, and calls exceeding five seconds.

The first tool set maps to immutable snapshots:

| Tool | Result |
| --- | --- |
| `minecraft_get_state` | scenario state, step, elapsed time, last failure |
| `minecraft_get_screen` | screen/menu names, GUI bounds, resolution, scale |
| `minecraft_get_ui_snapshot` | observed rows, components, widgets, rectangles |
| `minecraft_take_screenshot` | saves a bounded PNG and returns its relative name |
| `minecraft_get_logs` | bounded tail of this client's current log |
| `minecraft_quit` | queues normal shutdown of this client |

No tool accepts a filesystem path. Screenshot names are generated by the
driver, and log reads are fixed to the current runtime's `latest.log` with a
64-KiB returned tail. The server closes before Minecraft process teardown.

### Embedded context loader (#353)

Both `InteractiveMcpServer.createTomcat` implementations call `Tomcat.addContext`
without assigning a parent loader: one in `shared/src/testDriver1201`, the other
in `versions/26.1.2-neoforge/src/testDriver`. Their construction callers are the
corresponding `TestDriverRuntime` constructors, gated by `options.interactive()`.
`scripts/test-driver.gradle` shades Tomcat 11.0.10 and MCP 2.0.1 into the driver.

The issue archive records seven Forge EventBus parent-resolution errors with a
`ParallelWebappClassLoader` parent of `AppClassLoader`. Examples include relocated
`AuthenticatorBase`, `ValveBase`, `LifecycleBase`, and `ManagerBase`. Tomcat's
inherited container parent defaults to the system loader; Forge's EventBus
transformer uses the thread context loader to resolve superclass bytes. The
system loader cannot be assumed to see mod-loader classes. This supports a
loader-boundary hypothesis, not a demonstrated runtime failure or confirmed fix.

Set `context.setParentClassLoader(InteractiveMcpServer.class.getClassLoader())`
immediately after `addContext` in both implementations, before startup. Preserve
the delegation policy, servlet/filter setup, relocation, and shutdown flow.
The shared endpoint serves Forge/Fabric 1.20.1 and NeoForge 1.21.1; 26.1.2 keeps
its native implementation.

The regression must place driver/server classes outside the system classpath,
exercise the actual context configuration, and resolve representative shaded
superclasses through the resulting webapp loader. Removing the binding must
fail the regression. If it cannot distinguish old and corrected behavior,
revisit the hypothesis before reporting the root cause as proven.

## Result and validation flow

Scenario actions run at the end of a rendered frame, not a client tick. A
screen can open during a tick before its first render; reading the framebuffer
then would save the previous screen while the screen-object check passes.
The smoke run must also inspect every saved checkpoint image.

Placed AE2 nodes wait for their normal first-tick initialization. Fixture setup
returns pending while `isReady()` is false; it must not call `onReady()` itself,
because AE2 already queues that callback and a second initialization crashes.

The driver builds one immutable result and writes `result.json.tmp` beside the
destination. It flushes and atomically renames the file to `result.json`; an
unsupported or failed atomic move is a scenario failure, never a pass.

`schema` is `1`, `complete` is true only after every required check and
screenshot write completes, and `checks` contains exactly the required set for
the selected scenario. Failures add a structured object with step, code,
expected, and observed values. Secrets, absolute user paths, and arbitrary log
text are excluded.

After the client exits, `run-ui-smoke.ps1` independently validates the JSON,
required files, production/driver version pair, and exit code. It copies the
managed dependency manifest to `resolved-mods.json`, copies only the launched
client's log, and scans that log for fatal loader, mixin, resource, and crash
signatures. Driver `PASS` plus a fatal log entry is a runner failure.

## Safety and failure handling

- Driver bootstrap stays inert unless the explicit scenario or interactive
  property is present.
- A normal Forge 1.20.1 development launch includes the driver JAR but starts no
  scenario, endpoint, input, fixture access, or result writer.
- World mutation requires integrated singleplayer, the disposable folder ID,
  and a valid marker whose source ID matches the tracked fixture.
- The tracked source fixture path is rejected even if its marker is valid.
- Scenario actions stop after failure or timeout; read-only snapshots remain
  available only in interactive mode.
- A missing plan, target output, retained sample, widget, or tooltip is a
  test failure with evidence, not a fallback or synthetic pass.
- A driver/production version mismatch is rejected by Forge before scenario
  code runs.
- Release tasks remain allowlist-based and unchanged; artifact tests prove that
  neither production JAR nor `dist` contains driver entries.

## Compatibility and later ports

The original implementation started in the Forge 1.20.1 module; the Fabric port now shares identical code. Keep result-model,
state-machine, and rectangle code free of Minecraft types where that falls out
naturally, but do not create shared source sets or loader interfaces until a second target needs them.

When a second target is approved, move only already-identical code into a shared
test-driver source set. Screen adapters remain at the Minecraft/AE2 API
boundary, and loader bootstraps remain in their version modules.

Build production and test-driver JARs on the host using the Java setup in
[the client workflow](../dev-client.md#host-build-and-vm-staging). Share the
session worktree with CodexVM and copy the built JARs into the exact client or
Codex-group Prism modpack. Never build JARs inside the VM. Client UI checks run
inside CodexVM, while each Minecraft runtime stays on guest-local NTFS.
Development clients receive an 8 GiB maximum heap. Test-driver launches
maximize their GLFW window before the scenario starts; other UI checks maximize
the exact client through the VM display before inspection.

## Alternatives rejected

- Production test hooks: they would ship test behavior and make the mod attest
  to its own output.
- A custom MCP protocol implementation: protocol, transport, and authentication
  edge cases are not project value; use the official SDK in the isolated JAR.
- Full-frame golden images: animated items and renderer differences add noise
  without improving the first semantic checks.

## No-power status scenario

`no-power-status` reuses the real processing-job fixture with 64 cobblestone
per dispatch and an unfuelled furnace. Verify no warning while network energy
is sufficient. Replace the creative source with a real energy cell and keep
only enough energy for idle demand, below the next dispatch cost. Observe an
active CPU, one active output, and scheduled work, then the rendered NO POWER
badge and complete tooltip in English (`en_us`). Restore energy, require
another real dispatch and warning recovery in the same menu, cancel, and check
that an inactive CPU alone produces no warning. Retain all distinct English behavior checkpoints.
Every full compatible suite includes this scenario and the NO PROVIDER
regression. Driver checks observe final frames and real AE2 state, never seed
production diagnostics. Shared pure tests cover threshold, expiry, priority,
CPU switching and lifecycle; packet tests cover the shared transport boundary.

## Independent standard cases

The shared `StandardAe2Scenario` uses named stages and a leaf ID, with fresh
fixture preparation for every leaf. Both target runtime implementations use
the same dispatch and exact check contracts. The 26.1.2 fixture/observer adapters
retain their native APIs. `ui-smoke-groups.json` owns host alias expansion and
required evidence; Java `DriverResult` enforces the matching check sets.
`SuitePlan` and the host accept 1–64 unique cases. Schema 2 shares one marked
world; schema 1 requires unique worlds for explicit diagnostics. Group results live in
the campaign report; existing schema-1 leaf and flat-suite reports are preserved.
Runtime acceptance still requires independent, group and full-suite evidence
on all four targets; code or contract tests alone do not establish a UI pass.


The delayed leaf observes `ProviderHighlightClient.plates()` and
`renderPlates()` without mutating them. Both `StandardCraftFixture` adapters
dispatch stone and glass through one provider and return them at separate,
controlled recovery points. Require two retained identities, one stable
first-retained render icon, a real locate edge, and survivor selection after the
winner returns. Capture `delayed-world-overlap.png` and
`delayed-world-winner-recovered.png`. The final provider then holds smooth stone;
close the menu, aim at that provider and the intervening AE2 terminal, and
capture `delayed-world-highlight.png`. Release
the held output through normal ME insertion in one server operation. After the
CPU is idle, output is stored, new samples exist, and all plates are absent,
capture `delayed-world-finished.png` before reopening idle status. World stages
run independently of the menu-frame observer, which has no fresh frames when
menus are closed. Rendering correctness still requires image review.

## Lifecycle gallery evidence

`StandardAe2Scenario` extends the existing lifecycle leaf, without a new runner
or scenario identifier. Before dispatch, fixture sample preparation controls
zero, one, then two known recipe rows; observations wait for the corresponding
rendered no-data/estimate state. Both jobs run the existing real furnace pump.
The second job removes the first stored output and clears the final recipe's
sample and accuracy history, retaining dependency history. Verify the server's
accuracy sample has respectively 2/2 and 1/2 known rows before Ctrl-clicking the
final recipe in a reopened plan. Clear unrelated chat before that real request,
close the menu, open ChatScreen and wait for rendered frames before capture.
The screenshot helper records current-screen sidecars even without an AE2 menu.

## No-channel status fixture

Extend the existing dispatch scenario infrastructure for #405. The current
`DispatchStatusFixture` uses direct `GridHelper.createConnection` edges and
requires an already-active native batch; neither proves initial channel
starvation. Add a physical controller/normal-cable branch with enough real
channel consumers to saturate its bottleneck. Keep CPU, terminal and storage
on a healthy branch, wait for pathing, and inspect the tested provider's actual
node presence/power/boot/channel predicates. Never assume allocation order or
set channel fields directly. If the intended provider is not starved, fail the
fixture or adjust the physical route within its existing deadline.

Reuse native and `AdvancedAeStatusFixture` construction/submission/menu seams.
Allow a new job with positive scheduled and zero active output; do not wait for
first dispatch as readiness. A required AdvancedAE case must prove its selected
CPU type and adapter. Restore a real route, wait for boot completion, observe
successful dispatch, return the output and wait for the job to finish. Repeat
with a healthy duplicate pattern and the negative/lifecycle controls in the
[implementation plan](../provider-dispatch-statuses/no-channel/implementation-plan.md#4-add-and-run-actual-channel-starvation-scenarios).
Restore channel mode and clear fixture blocks, jobs and samples during teardown.

Keep each asynchronous server action pending until completion; issue UI actions
once and poll readiness with existing progress deadlines. Register the leaf in
both driver runtimes and support registry, `SuitePlan` validation, host scenario
validation, groups/impact/selection and required result/screenshot checks.
Extend the nearest driver and PowerShell contract tests after hook-created PR.
Use shared 1.20.1/1.21.1 fixture code with the existing 26.1.2 API counterpart,
not a new launcher. Retain unique screenshots for blocked, tooltip, recovered,
alternative and negative/lifecycle checkpoints with matching authoritative
server facts. Existing source markers and disposable-copy/reset rules apply;
restore original channel mode even on failure and report failed cleanup.

## Badge background focused relaunch (#532)

Keep the suite's `badge-background` case as one launch. For the direct focused
case, reuse the status-option continuation seam: after the existing Plan and
Status captures, save Off, write an atomic continuation carrying campaign, world,
client config hash, completed checks and screenshot names, and stop Java phase 1.
The host checks the persisted config hash and distinct Java identities before
accepting phase 2. Phase 2 reads the same continuation, verifies runtime Off and
the native Appearance toggle, then drives Cancel, Reset Appearance, Reset all,
and final Done through the existing Options button helper. Capture the draft
reset states, then reopen Appearance after Done to capture the saved On state;
validate the final screenshot and
continuation set in the existing host evidence gate. Keep process provenance in
`relaunch-evidence.json`; no new runner or fixture format is introduced.
