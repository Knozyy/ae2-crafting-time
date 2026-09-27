# AE2 Crafting Time Test Driver Spec

Status: finished

Scope: Shipped driver baseline, excluding separately tracked extensions.

Implementation: [PR #128](https://github.com/cTux/ae2-crafting-time/pull/128), [PR #208](https://github.com/cTux/ae2-crafting-time/pull/208).
Verification: [prepared-client results](../automated-ui-testing/prepared-clients-2026-09-08.md).
Stored-variant and resource-icon sections inherit their linked feature status.

## Optional connection observation extension

Issue [#537](https://github.com/cTux/ae2-crafting-time/issues/537) adds an
opt-in `ae2craftingtime.test.observeConnection` mode. It records outbound wrapper
attempts and loader send invocations for six payloads in each direction, per
connection. Unsupported peers receive bounded probes; the required result is six
attempts and zero loader sends on the installed side. Both-installed control
traffic must yield a nonzero loader send. The absent side loads neither the mod
nor its test driver. Manual plan/status screenshots and a real craft complete the
native UI evidence. See the [connection plan](../optional-connection/implementation-plan.md)
for the sixteen-cell acceptance contract. This extension is pending runtime
qualification and does not alter the shipped scenario result.

The saved Forge fixture starts within reach of its marked terminal and contains
no retained CPU jobs. Optional scenarios keep its grid, patterns, and samples.
Native CPU setup waits for the formed cluster to become active before selection.

## Compact status amounts extension

Lifecycle and acceptance: [compact status amounts](../crafting-status-amounts/spec.md).
This planned extension is outside the shipped driver baseline. Extend
`standard-status-controls` with the eight quantity combinations, item/fluid
boundaries, native tooltip comparison, independent compact/TTC controls,
Done/Cancel/resets and saved-option relaunch checks. Exercise default and wider
fonts at scales 1, 2 and Auto on all four targets. Existing status leaves retain
their real waiting, running and warning checks and also inspect the new badge.
The [feature plan](../crafting-status-amounts/implementation-plan.md#verification-prerequisites)
defines fixture ownership, synthetic-row limits, capture requirements and
environment gates; none of these new checkpoints is claimed as implemented.

## CPU-list total TTC scenario

`cpu-list-total-ttc` opens the real Crafting Status screen on eight native CPUs:
five busy jobs include distinct totals, an equal-total tie, one unknown total,
and an off-screen shortest job; three CPUs are idle. Actual rendered-card
observations retain raw AE2 serial order separately. The driver captures the
initial mode and full button cycle, verifies stable CPU groups and item sorting,
then compares selection, title, tooltip, cancellation, and badges by serial.

A focused second fixture creates 33 server-known busy CPUs. Without selecting
or scrolling first, captured outgoing request batches must cover every raw
serial, contain no duplicates, and stay at or below 32 entries. Deterministic
cache tests cover 0, 1, 25, 26, 32, 33, 100, and 1,000 busy rows, fairness,
timeouts, and adaptive expiry without inflating the runtime fixture further.

The scenario scrolls both directions, uses long English and Cyrillic fixture
names for static layout checks, and exercises reorder, removal, completion,
cancellation through the native button, and same-output replacement. It also
checks hit suppression before the first draw and for a removed prior-frame row,
wheel input before the next draw, server-selected identity, channel fallback,
late replies after mode switches, and AE2-mode expiry. It closes and reopens the menu,
switches to a second grid with overlapping names and serials, expires a delayed
reply, and reconnects without accepting stale totals. Runtime screenshots stay
English (`en_us`) and cover selected/unselected cards at the smallest and
largest GUI scales where the screen fits. Every card keeps its icon, amount,
progress bar, click area, tooltip, and scrollbar clear.

Run the same checks against integrated and connected disposable dedicated
servers on all four compatible targets. Record production/driver artifacts,
dependencies, selected adapters, server estimates, and matching client card
snapshots. Retained checkpoints include raw and displayed serial order, draw
scroll, server state, and timestamped outgoing request batches. A headless
server check is not UI evidence.

`recurrent-plan` is the bounded native Crafting Plan recurrence case. It uses
real self, two-key and three-key loops; ordinary, seeded, eligible-alternative,
successful-alternative, mixed, exact-variant, fuzzy-substitute and emitter
controls; concurrent and cancelled calculations; and CRAFT_LESS retries.
Large item quantities and fluid units exercise native amount formatting. Every
integrated case traverses all three TTC sorts without profiling samples, checks
the native quantity and cell bounds, and captures its row and tooltip. The
NeoForge 1.21.1 case also creates a real plan with more than 256 entries.
Client-thread observations record native setPlan before diagnostic chunks and
reject malformed/stale diagnostics without changing the plan. Its dedicated
counterpart is restricted to the marked loopback fixture; this does not enable
general multiplayer or arbitrary-server automation.
Every target runs one 8 GiB client with the fixed offline fixture identity.
The client visits grids with opposite results, swaps patterns/replans, and
reconnects without retaining an old diagnosis. Server acknowledgements bind
the observed player UUID, menu and revision to the captured client frame.
Unit/packet-boundary tests cover different recipients and reject wrong or stale
identity. Separate identity sessions, if used, run only after the prior client
exits. No simultaneous-player proof is required or claimed. Keep PID/start-time
and scheduled-task cleanup for the single client. Run this leaf plus
`standard-plan-controls` on all four targets in English; check Ukrainian
resources/components statically. These are required checks, not recorded passes.

All Minecraft smoke clients run sequentially, including connected scenarios;
confirm the previous client exited before launching another. A disposable
dedicated server may remain running for the client's reconnect. Remove obsolete
dual-client scheduling and resource requirements before running the revised gate.

The connected path is `scripts/run-connected-dedicated-ui-smoke.ps1`. It takes
one prepared server and the matching prepared client launch, stages only the
matching production and driver artifacts into the server, and retains separate
server/client logs, hashes, authoritative estimates, and semantic screenshots.
The prepared server is an immutable source marked by
`.ae2-crafting-time-dedicated-fixture.json`; the runner validates its target,
launch files, profile, and exactly one production/driver artifact pair before
creating a separately marked disposable copy below the report directory.

The [#482 provisioning expansion](connected-resource-fixtures/spec.md) adds a
bounded preparer for the four compatible connected targets and their supported
AppMek graphs. It seals new sources and requires explicit EULA consent for
disposable execution. Resource-only prewarm proves readiness before fixture
activation, not scenario success; older connected leaves keep their contracts.

## Stored-variant plan scenario (planned)

Lifecycle: see [stored-variant implementation and remaining verification](../exact-ingredient-mismatch/spec.md).
The heading retains its historical link anchor; the scenario is implemented.

`stored-variant-plan` verifies [#327](../exact-ingredient-mismatch/spec.md) using
real processing patterns and two damaged stacks of one item with different
exact NBT/components. Keep one native confirmation menu and summary open while
storage moves through none, near-match, none, near-match plus exact key, and
near-match again. Require automatic warning appearance, clearing and recovery
at the next ordinary synchronization after AE2's notification, with unchanged
native quantities, summary revision and Start behavior. Never seed diagnostics.

Capture each transition and the gold normal-weight row/tooltip in English.
Cover ordinary, exact-only, different-item, fluid and successful controls,
multiple variants, unrelated rows, all TTC sorts without samples, narrow layout
and coexistence with recurrence. Record server storage facts and notification,
refresh, send and receive ordering; screenshots alone do not prove those facts.
Check English/Ukrainian resources and components statically.

Run the leaf and native plan/recurrence regressions on all four compatible
targets. Its connected counterpart adds only this named leaf to the marked
loopback exception, retaining one 8 GiB client at a time. Verify live changes,
replan, network switch, cancellation, reconnect and watcher cleanup through
server UUID/menu/revision acknowledgements. Pure and packet tests cover other
recipients, reordered/duplicate updates and zero-mask clearing; no simultaneous
player claim is made. Matching immutable dedicated sources and prepared clients
must be verified before execution; missing provisioning remains a prerequisite.
This is required future coverage, not an existing runnable leaf or a pass.

## Delayed resource icon scenarios (planned)

Lifecycle: [fixture scope](connected-resource-fixtures/spec.md);
[production icon scope](../provider-locate/resource-icons/spec.md).

The fixture prerequisite [#482](connected-resource-fixtures/spec.md) merged in
[#484](https://github.com/cTux/ae2-crafting-time/pull/484).
Its explicit fixture-only runs qualify real jobs and control/lifecycle evidence;
they report production icon acceptance as NOT_RUN and cannot satisfy #376.
The following remains the final production-icon acceptance contract.

For [#376](https://github.com/cTux/ae2-crafting-time/issues/376), implement the
[resource-icon acceptance matrix](../provider-locate/resource-icons/implementation-plan.md#runtime-acceptance-matrix)
through these existing bounded cases. Their ordinary production acceptance mode
is required future coverage; the existing fixture-only mode is not an icon pass.

| Scenario | Targets and real fixture outputs |
| --- | --- |
| `delayed-resource-icons` | Forge/Fabric 1.20.1, NeoForge 1.21.1 and 26.1.2: item, water, lava and overlap; Forge also has the bucketless test fluid |
| `appmek-resource-icons` | Forge 1.20.1 and NeoForge 1.21.1 with Applied Mekanistics: oxygen, hydrogen and overlap |

Submit real processing jobs, observe provider dispatch, withhold outputs until
DELAYED, then return them through normal ME crafting insertion. Include a named
bucketless fluid on Forge as specified in the linked plan. Do not seed
production delayed state, client highlights or renderer results. Existing
`appmek-cpu` oxygen storage setup is not a chemical-output rendering check.

Capture the recognizable resource texture and tint on visible provider faces
with chat disabled and the terminal closed. Cover shared-provider winner
promotion, manual rainbow expiry independent of red, recovery, immediate
completion, cancellation, provider removal/unload and resource reload. Base-only
profiles prove items/fluids work without AppMek. Observe retained and selected
plate keys alongside screenshots; key equality alone is not visual proof.

Run fluid send/reconnect on all four connected dedicated targets and chemical
send/reconnect on both AppMek targets. The same single client reconnects to the
still-running marked disposable server; require server-approved typed plate
restoration and no restored rainbow. This adds only these two named scenarios
to the bounded connected exception below. Retain normal safety checks, sequential
8 GiB clients and exact-process cleanup; do not enable arbitrary multiplayer.

Ordinary runs of these leaves must check production acceptance; explicit
`-ResourceFixtureOnly` preserves the prerequisite contract. Keep fixture evidence
at NOT_RUN and require additional `resource-icon-evidence.json` schema 1 for
ordinary runs, as defined in the
[production design](../provider-locate/resource-icons/technical-design.md#production-acceptance-on-the-existing-fixtures).
Missing or mismatched evidence fails. Typed-key assertions cannot replace world
capture review; visual acceptance remains REVIEW_REQUIRED until reviewed.

Record resource key/type, authoritative job state, target/profile, dependency
and production/driver hashes, and unique English PNG/sidecar checkpoints for
each observed transition. Require all scenario checks, clean client exit and
reviewed world captures. Missing fixtures, unsupported APIs or absent required
integrations fail the required case rather than silently reducing its scope.

## Optional screen read recovery

`crafting-tree-read-recovery` and `merequester-read-recovery` use isolated addon
JARs with renamed read contracts. They require visible host content and no TTC
overlay. Tree also retains its original tooltip and ignores TTC SHOW/RESET
modifier clicks. Each negative case must be followed by `craft-lifecycle` in
the same process, with one original-cause WARN across both worlds. Positive
screen scenarios retain their normal TTC assertions.

## Optional selected-CPU read recovery

`advancedae-read-recovery` runs on an isolated dedicated server with a deliberately
renamed AdvancedAE menu field. It constructs a real transformed AE2 crafting
status menu on the fixture grid, reads the normal server stats context twice,
and requires the grid to survive with no selected CPU. It then submits a real
native AE2 job and requires fresh samples and successful completion. The runner
must retain exactly one WARN and the original missing-field cause. Altered addon
JARs remain disposable test inputs and never enter production artifacts.

All future smoke follows the [shared policy](../automated-ui-testing/spec.md#smoke-policy):
newest adapter per dependency/target and English (`en_us`) only. The English-only
scenario requirements below are the required next state; removing existing
bilingual driver states is follow-up implementation work. Keep Ukrainian
product translations and static resource checks.

## No-provider status scenario

`no-provider-status` submits a real 64-output processing job to an isolated
native AE2 CPU. Blocking mode and a chest hold the first batch active while
later batches remain scheduled. Removing its pattern must show NO PROVIDER in
that combined row. Check the rendered badge and both tooltip sentences in
English (`en_us`). Restore the pattern and require recovery without
reopening the menu. Install an equivalent pattern in a second provider, remove
the first pattern, and require no warning for two refresh cycles. Remove that
second provider block, observe the warning again, then replace/reconnect it and
require recovery. Cancel the job and confirm its diagnostic clears.

The scenario uses real provider inventories, grid lookups, job submission,
menu synchronization, and final frame observations. It never seeds the warning
or invokes a production reporting hook to make a UI assertion pass. Capture
before, mixed-row warning, English tooltips, both recoveries, redundant
provider, provider removal, and cancellation checkpoints. Keep the scenario
available across the shared 1.20.1/1.21.1 driver and the 26.1.2 API counterpart;
a passing smoke result applies only to the target actually launched.

## Provider-dispatch status scenarios

`no-target-status`, `input-blocked-status`, and `locked-status` reuse the same
real 64-output processing job. NO TARGET removes and restores the provider's
adjacent inventory. INPUT BLOCKED exercises both blocking mode and a completely
full target, then proves recovery through disabled blocking and partial target
capacity. LOCKED exercises while-low, while-high, until-pulse, and until-result
provider modes through redstone changes and an item returned through the
provider return inventory. Every warning is observed in a mixed active/scheduled
row with its English label, explanation, suggestion, qualifier, and contained
badge; every recovery happens in the open screen.

All four native targets run the three leaves. The Forge 1.20.1, NeoForge 1.21.1,
and NeoForge 26.1.2 AdvancedAE graphs repeat them with a job submitted directly
to an AdvancedAE CPU. Driver code detects that graph from the loaded addon and
does not link AdvancedAE classes in native or Fabric runs.

## No-space status scenario

`no-space-status` uses an isolated native AE2 CPU and a full item cell in the
disposable world. A full external furnace first leaves the warning absent.
The fixture seeds retained CPU contents through AE2's inventory API; AE2's
normal tick and menu synchronization must report rejected storage. Observe the
rendered warning, badge, and tooltip in English (`en_us`). Replace the full
cell with writable capacity and require the warning to clear in the same screen.
Keep before, English tooltip, and recovered screenshots. Pure row tests
cover active and scheduled exclusions; the fixture does not simulate a craft.

## Standard AE2 acceptance scenario

`standard-ae2` is a host-expanded group of seven independently runnable leaves:
`standard-plan-controls`, `standard-status-controls`, `waiting-status`,
`running-status`, `delayed-status`, `craft-lifecycle`, and `cpu-list-total-ttc`. Each has a fresh
native grid and its own seeded estimates; no case depends on an earlier reset,
world, job, or cached observation. Standalone standard leaves copy only world metadata and
the marker (including separate native world-generation settings when present),
then generate fresh chunks; importing saved chunks could restore
incompatible CPU jobs before the driver starts. Other scenarios keep their
tracked layouts. Shared-world suites restore pristine fixture state between
leaves instead of copying a world per leaf. The host keeps one process for the group.

Plan controls also checks highlight item resolution against the loaded registry:
known stone resolves correctly; null, malformed, and unknown IDs return empty stacks.
Before checking plan sort order, wait for both seeded row estimates to render;
a partial stats reply must restart the stable-frame gate.
Its plan intentionally lacks one input and checks that the missing row stays ahead
of the two craftable rows in AE2 order and both TTC directions.

The [leaf contracts](../automated-ui-testing/technical-design.md#groups-and-independent-standard-flow)
retain every original assertion. Waiting and running require real dependency
progress. Delayed checks the active row, bold red label, diagnostic tooltip and
recovery after actual output. Its two intermediate outputs wait on the same
provider, where it requires one stable first-retained render icon. A real row
double-click adds a rainbow edge; releasing that selected output must reveal the
surviving icon while red and rainbow remain. It then withholds the final output
until its own delayed plate arrives and imports that output with all menus closed.
Require CPU completion, fresh samples, stored output, and client plate
clearing without an intermediate progressing craft. Capture held-output status,
overlap, winner recovery, final highlight, world completion, and reopened idle
status. The world camera
shows an AE2 terminal directly in front of the final provider to review red
background and item-icon occlusion. Client plate state proves packet lifecycle;
inspect world screenshots to verify rendering. Lifecycle follows terminal, amount, plan, Start,
status, real furnace output and new samples, then reopens the idle CPU screen.
The raw JVM property accepts leaf IDs or `suite`; use the host alias for the group.

## Goal

Provide a development-only companion mod that drives and observes a real
Minecraft client for repeatable AE2 Crafting Time UI smoke tests. It runs beside
the production mod and never replaces or ships inside it.

The driver covers the standard AE2 Crafting Plan screen and add-on crafting CPU
scenarios on Minecraft 1.20.1 Forge/Fabric and 1.21.1 NeoForge. New optional-mod scenarios must plug into
the shared add-on fixture flow without adding another branch to the UI state
machine.

Wireless-terminal addons are the exception because they add terminals, not
crafting CPUs. Their scenarios open the real wireless terminal, check the
craftable-entry TTC tooltip, and follow the normal AE2 Crafting Plan flow.

ME Requester has its own screen scenario. It places and configures a real
requester on the disposable grid, opens that screen normally, and checks its
rendered TTC row, header total, badge layout, and screenshot.

This covers [issue #126](https://github.com/cTux/ae2-crafting-time/issues/126).

## Fabric full-client suite

Run `scripts/invoke-ui-smoke-codexvm.ps1 -Target 1.20.1-fabric -Scenario suite`.
The full pinned compatible graph runs in one maximized 8 GiB client. The expanded
suite manifest owns the case list, including Crafting Plan, ExtendedAE, Applied Botanics, AE2 Things DISK storage,
MEGA Cells, AE2 Wireless Terminals, ME Requester, and NO SPACE. Each case gets a
pristine fixture reset in the shared world, screenshots, and checked semantic results.
JEI and transitive libraries load with the graph but have no dedicated assertions.
Crafting Tree and Network Analyser are not pinned in this Fabric graph.

The same `-Target` works for single scenarios, latest profiles, and interactive
runs. Full suites also run as separate latest diagnostics. Fabric uses its own version-matched
`ae2-crafting-time-<mod-version>-fabric-1.20.1-test-driver.jar`, remapped by Loom,
installed into `run/mods` or `run-latest/mods`. Player JARs stay independent.

## NeoForge 1.21.1 full-client suite

Run `scripts/invoke-ui-smoke-codexvm.ps1 -Target 1.21.1-neoforge -Scenario suite`.
Use JDK 21 and the complete pinned compatible graph in one maximized 8 GiB
client. The expanded cases in `scripts/ui-smoke-neoforge-suite.json` cover the base
plan, Crafting Tree, all pinned CPU/provider fixtures, four wireless-terminal
flows, ME Requester, and NO SPACE. Each case uses a pristine fixture reset in the shared native 1.21.1 world and retains
its own semantic results and screenshots. JEI, GuideME, and transitive libraries
load with the graph but have no dedicated UI assertions. Expanded AE remains
excluded from the compatible graph because of its recorded OmniSequence conflict.
Applied Botanics, AE2 Things, and Network Analyser are not pinned in this graph.

Compatible and latest launchers install the exact
`ae2-crafting-time-<mod-version>-neoforge-1.21.1-test-driver.jar` in their managed
`mods` directory. Single scenarios and interactive diagnosis use the same target;
full suites also run as separate latest diagnostics. The companion stays inert without explicit
scenario options and never enters the production JAR or `dist`.

## NeoForge 26.1.2 full-client suite

Run `scripts/invoke-ui-smoke-codexvm.ps1 -Target 26.1.2-neoforge -Scenario suite`.
The pinned compatible graph runs its expanded suite in one maximized 8 GiB client, including
Crafting Plan, AdvancedAE, ExtendedAE, BM Addon, Lightning Tech, OMNI Cells,
Applied Flux, AE2 Wireless Terminals, Import Export Card, Infinity Booster,
and NO SPACE. Neo Vitae supports the BM Addon recipe; GuideME, JEI, and
transitive libraries load with the graph without dedicated assertions.

Each case uses a pristine fixture reset in one disposable native 26.1.2 world. The driver
builds a native AE2 grid in that copy and checks real crafting, new profiling
samples, final UI observations, and checkpoint screenshots. The Gradle launcher
uses JDK 21 and selects the JDK 25 client toolchain. The exact companion JAR
is installed by both compatible and latest launchers and stays out of player
artifacts and `dist`. Full suites also run as separate latest diagnostics.

## Artifact contract

The driver artifact name is derived from the project version:

```text
ae2-crafting-time-<mod-version>-forge-1.20.1-test-driver.jar
```

It tests exactly:

```text
ae2-crafting-time-<mod-version>-forge-1.20.1.jar
```

The driver has its own mod ID, `ae2craftingtime_test_driver`, and refuses to
load with a different AE2 Crafting Time version. It accepts every AE2 version
allowed by the matching production mod so the same driver can run the
compatible and latest development profiles.

The driver artifact is written under `build/test-driver`, never `dist`. It does
not embed production AE2 Crafting Time classes. Player JARs and published
artifacts must not contain driver classes, resources, metadata, or dependencies.

### Diagnostic relaunch fast path

`cpu-list-total-ttc` may resume phase 2 from a captured phase-1 bundle only for
diagnosis. The bundle binds the marked disposable world, continuation and
screenshots, campaign/world identity, Git head, exact artifact-tree hash,
dependency mode, and an ordinal JAR-name/hash catalogue. Phase 1 writes that
dependency identity into the disposable world marker. Capture and restore reject
a marker from any other dependency graph before scheduling Java;
any mismatch is rejected. A resume-only result is explicitly non-final. Final
approval still runs both client processes and proves their distinct PID/start
identities. The runner records launch counts and per-phase timings and may fail
early when callbacks stop advancing. While the loader, world, or fixture is
still preparing, the absolute startup deadline remains authoritative; the
60-second no-checkpoint deadline begins only after the driver reports
`state=WORLD_READY phase=ACTIVE`.

Changed selection maps this feature directly to `cpu-list-total-ttc` on all
four required targets, one primary graph each. A sealed bundle is built once per
immutable head/selection fingerprint and hash-verified on every reuse.

## Development client installation

`scripts/run-client.ps1 -Target 1.20.1-forge` builds and installs the matching
driver JAR in its development client's managed mod directory before starting Minecraft:

```text
versions/1.20.1-forge/run/resolved-mods/
  ae2-crafting-time-<mod-version>-forge-1.20.1-test-driver.jar
```

Add `-Latest` to use `run-latest`; the matching `scripts/run-client.sh` command
has the same behavior. Each launch replaces a stale
driver copy and fails before Minecraft starts if the matching driver cannot be
built or installed.

The installed driver remains inert during an ordinary development-client run.
Only the explicit test-driver launch option starts a scenario or MCP endpoint.
Installation in these managed development clients does not copy it to `dist`
or make it a published mod artifact.

See [Automated UI Testing](../automated-ui-testing/spec.md) for the eventual
cross-target and optional-dependency suite.

## Automatic Crafting Plan scenario

The runner copies the tracked Forge 1.20.1 fixture world, starts the client with
the driver explicitly enabled, and runs this sequence:

1. Enter the disposable fixture world.
2. Open its known AE2 terminal.
3. Select its known craftable output.
4. Wait for `CraftConfirmScreen` and stable plan data.
5. Check TTC rows, total TTC, badge geometry, and the sort button.
6. Cycle AE2 order, shortest first, and longest first through the real button.
7. Hover the known row and check its final tooltip.
8. Save base, each sort-mode, and tooltip screenshots and an atomic semantic result.
9. Close the exact client cleanly.

The fixture supplies retained samples so this scenario checks rendered TTC,
not the separate `No data yet` state. A fixed resolution, GUI scale,
language, fixture, and cursor position make the screenshots comparable.

The driver observes the final AE2 screen, renderer, widget, tooltip, and
framebuffer state after normal production hooks have run. A production method
reporting that it executed is not evidence that its output reached the screen.
Translation keys and output IDs are semantic identity; rendered English text is
report evidence, not the assertion key.

The `appbot-cpu` fixture mounts a real Applied Botanics mana cell, verifies
native mana insertion and extraction through the grid, and then runs the shared
craft/sample/TTC flow on a normal AE2 CPU. Separate amount, packet, and saved-data
tests cover raw mana precision and unit migration; the fixture does not claim
to automate a Botania mana-generation recipe.
The `appbot-fork-cpu` scenario reuses that fixture against the separately pinned
fork artifact. Original and fork must never be loaded together.

The `advancedperipherals-cpu` fixture connects a real ME Bridge and CC:Tweaked
computer to the grid. It submits the selected output through the bridge's
`craftItem` API using the attached computer, then observes a new server profile
sample and TTC in the normal AE2 Crafting Plan. This tests the peripheral API,
not an automated Lua editor or a separate ComputerCraft TTC display.

The `ae2things-cpu` fixture mounts the loader's real DISK inventory, removes
pre-existing cobblestone from the disposable grid, and supplies the craft
ingredients through that DISK. A native CPU craft must produce a fresh profile
sample and visible TTC. Removed Forge machines are not part of this scenario.

The `expandedae-cpu` fixture joins Expanded AE's real two-thread accelerator
to native crafting storage, verifies the CPU reports two co-processors, and
requires a new profile sample and TTC after crafting. Run it as a focused
latest-profile scenario: the full compatible graph excludes Expanded AE
because of its existing conflict with OmniSequence.

The `lightningtech-cpu` fixture submits a smooth-stone smelting job to a real
Tianshu pool, using a pattern provider, furnace, hopper, and ME interface.
It checks that the pool receives the job and requires a fresh
profiling sample for the final output and visible TTC on the next Crafting Plan.
This is a regression check for standalone outputs that go directly to ME storage.

## Single-launch suites

A named-pack or prepared-client campaign runs all selected scenarios in one Minecraft process.
The normal suite also keeps one disposable world loaded. Before each case, restore
the pristine fixture, player and profiler state, and clear client observations.
Save each case's screenshots and result before resetting. No case may consume a
previous case's blocks, inventory, job, sample or cached response. Schema-1 world
reload suites remain available for explicit isolation diagnostics.

Use `scenario=suite` with the usual profile, output directory, and first world
properties. The output directory contains `suite-plan.json`: schema 2 and a
`cases` array of `{scenario, world}` entries (1–64 unique scenarios) sharing one
marked disposable world. Schema 1 requires distinct worlds instead. All referenced
worlds must be pre-created, marked disposable copies. Reject unknown schemas and
mixed-world schema-2 plans. Validate the whole plan before acting.
Interactive mode remains single-case only.

Each case writes under `<output>/<scenario>/`. The root `result.json` records
one JVM process ID, ordered case outcomes and timings, and the overall result.
Missing cases never count as passes. Stop on the first failure, retain its
screenshot/result, mark later cases `NOT_RUN`, and close the exact client.
Only a complete suite with every case passing can report `PASS`.

The existing single-case option and result schema remain unchanged. Selecting a
suite does not silently add mods or skip missing integrations.

Prepare a suite with `scripts/prepare-ui-smoke-suite.ps1 -RuntimeDirectory <game>
-OutputDirectory <new-evidence-directory> -Scenarios <ordered-names>`. It returns
the first world and launch properties. Pass `scenario=suite`, that world, profile,
and output through the existing JVM properties, with the first world as Prism's
quick-play world. The helper never changes the pack's mod graph.

For the full prepared Forge compatible graph, run
`scripts/invoke-ui-smoke-codexvm.ps1 -Scenario suite`. The ordered cases live in
`scripts/ui-smoke-forge-suite.json`; each uses a reset fixture in one loaded world.
The wrapper validates every per-case result and screenshot plus the overall
suite result, retains shared logs, and cleans all disposable worlds. The suite
allows 40 minutes including dependency resolution/build; single cases keep their
8-minute limit. Archive the evidence before another invocation.

This suite rejects `-Latest`, `-ProjectId`, and `-Interactive`: its case list
matches the full compatible graph. The graph includes the Applied Botanics fork;
the colliding original and incompatible Expanded AE require separate graphs.
Documentation/recipe viewers and transitive libraries are loaded
but have no dedicated UI assertions. Do not report those as scenario passes.

## Optional add-on CPU fixtures

`advancedae-cpu` builds a valid Quantum Computer enclosure containing its core,
an Accelerator, and a Data Entangler. CPU selection verifies the added threads
and multiplied storage. Submission must create a job in that exact cluster
before a fresh profile sample and the resulting plan TTC can pass.

## Crafting Tree scenario

`crafting-tree-screen` opens the real tree toolbar button from a populated
Crafting Plan. It supports the original and Refreshed widget packages. Check
the rendered node badges, their bounds against node icons, and a hovered
crafted node's TTC and details/reset hints. Capture the tree and its tooltip
separately. A missing tree, badge, or tooltip fails the scenario; opening a
normal plan alone cannot pass it. Run against the prepared compatible graph
and the original Crafting Tree artifact in Project Infinity 0.1.

## Optional fixture contract

An add-on CPU scenario uses the same disposable world, UI flow, profiler checks,
result schema, and runner. Its fixture owns only the add-on-specific placement,
formation, and CPU selection. Scenario names end in `-cpu`; the runner derives
the standard add-on checks and screenshot name from that convention.

Adding an optional mod requires one fixture implementation, one registry entry,
and a driver-only compile dependency. It must not add a production dependency,
make the optional mod mandatory, or require a new runner branch.

## Wireless terminal scenarios

The `ae2wcwt-terminal` scenario links a charged Wireless Comprehensive Wireless
Terminal to a real wireless access point on the fixture grid. It opens the
terminal through normal item use, hovers the known craftable output, checks the
TTC tooltip, then clicks the entry and verifies TTC on the standard Crafting
Plan screen. The required checks are `screen`, `ttc-tooltip`, and `plan-ttc`.

The `ae2wtlib-terminal` scenario uses the same flow with AE2 Wireless
Terminals' charged wireless crafting terminal.

The `ae2importexportcard-terminal` scenario links a charged standard AE2
wireless terminal, installs a real export card, and verifies the TTC tooltip
and Crafting Plan on the addon's modified terminal screen using a deterministic
profile sample.

The `aeinfinitybooster-terminal` scenario installs a real Infinity Card in the
linked access point, moves the player beyond its normal range, and opens the
standard wireless terminal. It checks `screen` and `plan-ttc`, with terminal and
plan screenshots. The range-only addon adds no terminal TTC tooltip surface.

## ME Requester scenario

The `merequester-screen` scenario places a real requester on the fixture grid,
configures a deterministic out-of-stock diamond request and profiler sample, opens its
screen through block use, and checks `screen`, `ttc-row`, `total-ttc`, and
`layout`. The layout check includes active menu item slots, so a badge drawn
under an item cannot pass just because its text draw call was observed.
Its screenshot is `merequester-screen.png`.

## AE2 Network Analyser scenario

The `ae2networkanalyser-screen` scenario equips the real network analyser,
opens its configuration screen through normal item use, and verifies the
expected screen/menu identity and that its GUI remains inside the viewport.
It does not assert TTC text because the addon visualizes network topology and
does not expose crafting status.

## Interactive diagnosis

Interactive mode runs the same scenario but pauses at the failed or completed
step and exposes a loopback MCP endpoint. The first artifact provides only:

```text
minecraft_get_state
minecraft_get_screen
minecraft_get_ui_snapshot
minecraft_take_screenshot
minecraft_get_logs
minecraft_quit
```

The first five tools observe the launched client without changing its world.
`minecraft_quit` performs only a normal client shutdown. Synthetic click,
hover, key, command, and world-editing tools are deferred until a later
scenario proves they are needed.

The endpoint:

- listens only on `127.0.0.1` and only in explicit interactive mode;
- requires a fresh per-run secret that is not placed in command-line logs,
  result files, or screenshots;
- accepts one controller at a time;
- uses a fixed tool allowlist and bounded arguments, requests, responses, and
  timeouts;
- stops when Minecraft exits; and
- never exposes arbitrary Java calls, shell execution, Minecraft commands, or
  filesystem access.

Client state is read only on the Minecraft client thread. Integrated-server
state is read only on the server thread. Endpoint work is queued to the owning
thread and fails on a bounded timeout.

## Embedded server class loading (#353)

The interactive endpoint must resolve its shaded server classes through the
test driver's defining classloader. Forge must not report missing parent
classes for relocated Tomcat classes during endpoint startup or requests.
The reported Project Infinity run completed its focused checks despite these
errors; it does not establish a crash or a broken endpoint.

Acceptance criteria for this driver-only correction:

- **CL1:** Both the shared driver and native 26.1.2 implementation bind their
  embedded context to the driver loader before startup. An isolated-loader
  regression fails without the binding and passes with it.
- **CL2:** A prepared Minecraft 1.20.1 Forge compatible client on Java 17 runs
  `craft-lifecycle` interactively without the reported relocated-Tomcat parent
  errors. MCP initialization, tool listing, state inspection, screenshot capture,
  and normal quit succeed. Existing lifecycle checks and required screenshots
  remain complete and visually reviewed.
- **CL3:** Both endpoint implementations compile and package with the existing
  dependencies. Production artifacts remain isolated; authorization, loopback
  binding, limits, and game-thread scheduling remain intact.

Do not suppress errors globally or change dependencies without new evidence.
Prepared Forge verification is not an exact Project Infinity 0.0.52.0 /
Forge 47.4.20 pack pass.

## Fixture safety

The driver may act only when all of these are true:

- the explicit test-driver launch option is present;
- the connection is singleplayer, or an explicitly selected bounded connected
  scenario (`cpu-list-total-ttc`, `recurrent-plan`, or the planned
  `stored-variant-plan`, `delayed-resource-icons` / `appmek-resource-icons`) uses the runner-created
  loopback dedicated server and control directory;
- the opened world is the runner-created disposable copy; and
- the world contains the expected test-fixture marker and scenario data.

It refuses other multiplayer sessions, an unmarked world, the tracked source fixture, or a
world opened without test-driver mode. After a timeout it records failure and
stops taking scenario actions. The runner owns copying and deleting the
disposable world; the driver changes only the running copy.

## Layout checks

Semantic observations include screen-relative rectangles for visible rows,
text, badges, item cells, AE2 buttons, and the test target widget. A required
rectangle fails when it is outside the GUI or overlaps an owned control or item
cell. Screenshots remain the human-readable evidence for clipping, spacing,
color, and unexpected visual changes.

The first slice does not use full-frame golden-image comparison. Add a cropped
golden comparison only after a stable region has a demonstrated regression that
semantic bounds do not catch.

## Result contract

The driver writes `result.json` atomically in the scenario output directory. A
temporary or incomplete file is never a pass.

```json
{
  "schema": 1,
  "complete": true,
  "driver": "ae2-crafting-time-1.1.0-forge-1.20.1-test-driver.jar",
  "target": "1.20.1-forge",
  "profile": "compatible",
  "scenario": "craft-plan",
  "result": "PASS",
  "checks": {
    "screen": true,
    "ttc-row": true,
    "total-ttc": true,
    "sort-cycle": true,
    "tooltip": true,
    "layout": true
  },
  "screenshots": ["craft-plan.png", "craft-plan-sort-1.png", "craft-plan-sort-2.png", "craft-plan-sort-3.png", "craft-plan-tooltip.png"]
}
```

The runner independently checks the schema, completion flag, exact driver name,
target, profile, scenario, required check set, screenshot existence, clean
client exit, and fatal log entries. Missing or invalid output is a failure.

## Compatibility

- First artifact: Minecraft 1.20.1, Forge, Java 17, standard AE2 Crafting Plan.
- Optional add-on CPU fixtures currently cover AdvancedAE, Applied Flux,
  Applied Mekanistics, BM Addon, Crazy AE2 Addons, AppliedE, ExtendedAE,
  ExtendedAE-Plus, MEGA Cells, Modern AE2 Additions, NeoEco AE, OMNI Cells,
  OmniSequence: Transfinite, LightningTech's Tianshu multidimensional CPU pool, and ProjectCell.
  ExtendedAE and ExtendedAE-Plus replace the fixture's molecular assemblers
  with ExtendedAE assemblers. BM Addon installs a real Blood Pattern and its
  inputs. Crazy AE2 Addons places a native AE2 1K crafting storage CPU and
  selects that recorded cluster. Modern AE2 Additions builds a native AE2 CPU
  with its 4x co-processor and selects that recorded cluster. ProjectCell
  replaces the fixture's normal cobblestone supply with a bound EMC Storage
  Cell. AppliedE replaces that
  craft path with a player-owned Transmutation Module, furnace knowledge,
  ProjectE EMC, and a native AE2 CPU. Applied Mekanistics mounts a chemical
  storage cell containing oxygen and places a native AE2 256K CPU. The other
  provider scenarios use an existing idle CPU because those addons do not add
  one.
- AE2 WCWT and AE2 Wireless Terminals have separate Forge 1.20.1 terminal
  scenarios because they add no crafting CPU.
- ME Requester has dedicated Forge and Fabric 1.20.1 screen scenarios.
- Run it against both the compatible and latest AE2 profiles already owned by
  `scripts/run-client-versions.json`.
- Share identical 1.20.1 driver code. Keep loader entrypoints in their modules.
- The driver may compile against production classes but may not alter the
  production packet protocol, saved-data format, runtime behavior, or JAR.

## Not included

- Optional-addon behavior outside the documented scenario contracts.
- General dedicated-server or multiplayer support outside the bounded connected
  scenarios and registered server fixtures.
- General-purpose UI automation, arbitrary world setup, or remote control.
- Pixel-perfect full-frame comparisons.
- Publishing the driver on GitHub, CurseForge, or Modrinth.
- A new production config option, packet, API, or test hook.

## Acceptance criteria

- The dedicated build task creates only the version-matched driver under
  `build/test-driver`.
- The Forge 1.20.1 compatible and latest development launchers install that
  exact driver in their selected `resolved-mods` directory, remove stale driver
  versions, and stop if installation fails.
- Both loaders refuse a driver paired with the wrong AE2 Crafting Time version.
- The driver remains inactive without the explicit test option and refuses
  other multiplayer sessions, the tracked fixture, and unmarked worlds. The
  bounded connected exception requires the marked disposable server and explicit
  control-directory launch options described above.
- One command copies the fixture, runs the compatible Crafting Plan scenario,
  validates the result and logs, saves five screenshots, closes the exact
  client, and returns zero only on a complete pass.
- The same command can select the latest profile without weakening a compatible
  profile failure.
- The scenario proves the real screen, TTC row, total, three sort modes,
  tooltip, badge bounds, and non-overlap rules from final UI observations.
- A registered add-on CPU scenario reuses the common setup, selection, sample,
  result, and screenshot flow without changing the runner's scenario allowlist.
- Interactive mode exposes only the six bounded tools above and rejects missing
  authentication, a second controller, oversized input, and unknown tools.
- Client and server access stays on the owning game thread and times out rather
  than blocking indefinitely.
- `dist` and every production JAR remain free of driver artifacts and classes.
- Automated tests cover result validation, state transitions, safety refusals,
  endpoint limits, and artifact isolation.

## No-power status scenario

`no-power-status` reuses the real processing-job fixture with 64 cobblestone
per dispatch and an unfuelled furnace. Verify no warning while network energy
is sufficient. Replace the creative source with a real energy cell and keep
only enough energy for idle demand, below the next dispatch cost. Observe an
active CPU, one active output, and scheduled work, then the rendered NO POWER
badge and complete tooltip in English (`en_us`). Restore energy, require
another real dispatch and warning recovery in the same menu, cancel, and check
that an inactive CPU alone produces no warning. Retain each distinct English checkpoint.
Every full compatible suite includes this scenario and the NO PROVIDER
regression. Driver checks observe final frames and real AE2 state, never seed
production diagnostics. Shared pure tests cover threshold, expiry, priority,
CPU switching and lifecycle; packet tests cover the shared transport boundary.

## Badge background checkpoints (#532)

The `badge-background` standard AE2 leaf uses the native Client → Appearance
screen to save a nondefault badge RGB and opacity, then saves Badge background
Off and On. Capture the Crafting Plan and Crafting Status with the same live rows
in both states, including a tinted Plan row and unchanged text. Supply the
missing Plan input and replan before Start. Retain the saved client option and
selected RGB/opacity through assertions at each capture. A screenshot requires
human review to confirm the rounded fill disappears and returns while AE2's own
background remains. Missing rows, labels, or native controls fail the leaf.

When launched as a focused leaf, retain the six Plan/Status captures, then save
Off and stop the first client with a continuation bound to the campaign, world,
and saved client config hash. The runner must start a distinct second Java
process, verify Off from the saved config and native Appearance control, and
capture Off before restoring On. In that second process, exercise native Cancel,
Reset Appearance, and Reset all controls; Cancel must leave the saved Off state
and custom RGB/opacity intact, while both reset controls must show their default
draft values. Restore On with Done, reopen Appearance, and capture the saved On
control. Include small and Auto scale
status captures while Off, with contained native GUI and unchanged text. The
single-launch suite retains its existing six-check leaf without a continuation.

## Screenshot refresh checkpoints

For issue #343, `craft-lifecycle` also captures an unprofiled two-row plan,
then a plan with only the stone dependency profiled. It seeds both rows before
the first real two-furnace job. After completion it requests the final output's
actual details through Ctrl-click and captures the open chat with full job
coverage. It removes the completed output, clears only smooth-stone history,
and completes a second real job with partial prediction coverage. A second
Ctrl-click captures the resulting partial-coverage chat. Accuracy must come
from normal CPU submission and completion, never seeded accuracy samples.
Keep the original lifecycle checkpoints and add stable PNG/sidecar pairs for
each new context. Chat captures preserve the original response; published crops
exclude the player attribution. Single-case interactive mode may retain this
world for subsequent manual book checks; suites remain non-interactive.

## No-channel status scenario

For [#405](https://github.com/cTux/ae2-crafting-time/issues/405), add
`no-channel-status` with the complete [NO CHANNEL criteria](../provider-dispatch-statuses/no-channel/spec.md).
It submits a real processing job through a powered, booted provider starved by
an actual cable bottleneck. CPU, terminal and storage retain healthy channels.
Require zero learned samples, positive scheduled work, a failed dispatch and a
visible English NO CHANNEL badge/tooltip. Restore the route, observe the badge
clear, return actual output and require job completion. Verify a healthy
same-pattern alternative suppresses the warning and cover the specified
negative-state, mixed-row and lifecycle cases without seeding diagnostic maps.

Run direct status/recovery on all four native targets and all three applicable
AdvancedAE targets, retaining actual CPU/adapter identity. Missing AdvancedAE
must fail its required case rather than silently selecting native. Keep older
adapter contract/packaging checks. Record real node predicates, server ticks,
job state, result checks, English screenshots/sidecars and dependency manifest;
review badge/tooltip layout. The screenshot also supplies the canonical book
crop after visual review. No new runner, production dependency or persistent
channel diagnostic is introduced.
