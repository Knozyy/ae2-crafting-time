# AE2 Crafting Time Test Driver Implementation Plan

Lifecycle: see the [scope status and evidence](spec.md).

Implement this as one feature commit. Let the commit hook create the PR before
running Gradle checks, then report local verification and GitHub CI separately.

## Recurrent fixture option ownership (#602)

See the [repair scope and criteria](spec.md#recurrent-fixture-option-ownership-602).
Merge these documents before implementation. The original feature rollout and
unrelated addon matrices are not this driver repair's verification plan.

1. Implement **RF602-1/2** in `RecurrentPlanFixture`: remember the original switch
   once, enable/synchronize before calculation, and restore/synchronize on close.
   Wire integrated completion and dedicated replacement/success/failure cleanup
   to that lifecycle. Preserve repeated preparation and make unused/repeated
   close harmless. Change no production default, pattern, packet or runner policy.
2. Add the smallest regression for **RF602-2/4** at the existing driver/core test
   boundary: initial false/true, repeated setup, case transitions, unused/repeated
   close and unchanged siblings. Check dedicated replacement/failure cleanup
   wiring. Keep new pure logic under the existing 100% line/branch gate and use
   boundary checks for adapters. Run no tests before the hook-created PR exists.
3. After PR creation, run `scripts/run-ui-smoke.ps1 -Changed -BaseRef origin/master -PlanOnly`
   and retain selection reasons. Run the focused regression, existing option
   default/copy checks and shared coverage. Compile affected shared driver
   consumers on all four targets and verify production/driver artifact isolation.
   Retain failing-control and passing-fix regression evidence with exact identities.
4. For **RF602-3**, first run compatible Forge 1.20.1 (Forge 47.4.23, AE2 15.4.10,
   Java 17): `recurrent-plan`, then `standard-plan-controls` in one schema-2
   disposable world through the existing selected-case suite. Record original,
   enabled and restored options, including the following leaf's value. Keep all
   existing semantic assertions and capture/inspect the English row and tooltip.
5. After the integrated case passes, run the marked dedicated `recurrent-plan`
   path on compatible Fabric 1.20.1 (loader 0.19.5, AE2 15.1.0, Fabric API 0.92.11,
   Java 17). Use `run-connected-dedicated-ui-smoke.ps1` with matching sealed
   dependencies. Verify grid replacement, replan, reconnect and restoration.
   Do not substitute an older server. Review selection for additional materially
   different changed paths; do not default to full four-target suites.
6. Archive semantic results, option-state observations, logs, PNGs/sidecars,
   head/artifact identities and measured timings. Inspect non-PASS visual checks.
   Confirm owned process exit, then cleanly shut down CodexVM after all visual
   follow-up and verify its VMX is absent from `vmrun list`. Report CI separately.

Preflight on 2026-09-28 verified current-compatible native manifests for all four
loaders, guest Java 17/21/25, all 94 Forge 47.4.23 classpath entries, and an existing
sealed Fabric 0.19.5 dedicated source. Older issue320 sources pin older loaders.
Recheck the exact manifest, fixture marker, seal and dependency equality before
launch. The dispatcher can add the session share; the existing dedicated
provisioner covers a missing matching source. Surface unexpected provisioning
work before expanding scope. Preflight launched no Minecraft process.

Budget one integrated client launch at ten minutes and one dedicated server/client
pair at fifteen minutes, excluding builds and visual review. These are planning
budgets, not measured durations. Retain 300-second startup, 20-second callback and
60-second active-checkpoint watchdogs. Diagnose from retained checkpoints; final
proof requires a clean run on the frozen implementation head.

Completion requires **RF602-1/2/4** regression/lifecycle evidence, **RF602-3**
integrated and dedicated results, inspected row/tooltip evidence and current-head
CI. A passing calculation alone is insufficient. Preserve default-off behavior
and the broader feature's separate status.

## Issue #353: Restore embedded context class loading

This focused correction uses the checks below without repeating the original
feature rollout or unrelated scenario matrices. Merge these documents before
implementation; defer checks until the commit hook creates the PR.

1. For **CL1**, bind the context parent immediately after `addContext` in both
   `InteractiveMcpServer` implementations. Add one isolated-loader regression
   exercising the actual configuration and representative shaded superclass
   resolution. Retain failing evidence without the binding and passing evidence
   with it; do not use a source-text assertion as the causal proof.
2. For **CL3**, run that regression and existing endpoint-policy checks, then
   build `:mc_1_20_1_forge:testDriverJar` and
   `:mc_26_1_2_neoforge:testDriverJar` on the host. Check both driver JARs and
   corresponding production JARs against the existing artifact-isolation
   contract. The Forge build covers the shared endpoint; the other covers the
   native implementation. Keep dependency versions unchanged.
3. Before **CL2**, start or reuse CodexVM through the existing VM workflow and
   inspect current client ownership. Recheck the prepared
   `1.20.1-forge/1.20.1-47.4.10/launch.json`, installed Java 17, and disposable
   fixture marker. Prepare a current-head compatible bundle through the existing
   matrix preparation path. This profile does not prove an exact pack pass.
4. For **CL2**, invoke `scripts/run-ui-smoke-codexvm.ps1` from the guest's
   interactive desktop PowerShell session with `-Target 1.20.1-forge
   -Scenario craft-lifecycle -Interactive -HeadSha <head>
   -BundleDirectory <guest-visible-bundle>`, without `-Scheduled`. Use the normal
   report and prepared-launch locations. A controlling guest process generates
   a random 256-bit lowercase-hex `AE2CT_TEST_DRIVER_TOKEN` in memory, starts
   the runner as its child, and retains it for loopback MCP calls. Never print
   the token or put it in scripts, arguments, task definitions, or reports.
   The host dispatcher selects scheduled Java, whose action does not inherit
   this process token; use the existing non-scheduled route, not a new launcher.
5. Wait for this run's `mcp-endpoint.json`. Send authenticated MCP `initialize`,
   the initialized notification, and `tools/list`, preserving the negotiated
   session header. Call `minecraft_get_state`, wait for lifecycle completion,
   call `minecraft_take_screenshot`, then `minecraft_quit`. Verify the PNG,
   recorded client's normal exit, endpoint shutdown, and every current lifecycle
   check/screenshot in `scripts/ui-smoke-groups.json`. Review the images under
   the existing evidence contract. Scan this client's full log for the reported
   EventBus/relocated-Tomcat parent errors; retain other warnings separately.
   Clear the controller's token in a `finally` block.
6. Budget one clean final Minecraft launch, with the existing 300-second startup
   deadline and 30-minute interactive ceiling. Record actual timings and bound
   diagnostic retries by progress. Bind artifacts, logs, protocol outcomes, and
   checks to the exact PR head. Report local checks separately from GitHub CI.

Completion requires **CL1-CL3**, including discriminating regression and real
endpoint-backed smoke evidence. An endpoint URL file or automatic scenario
alone is insufficient. Resolve missing guest access, manifest, fixture, or
interactive desktop control before launch; do not weaken verification or
silently add infrastructure.

## Phase 1: Isolate the driver artifact

1. Add the `testDriver` Java/resources source set to
   `versions/1.20.1-forge/build.gradle`, compiling against but not packaging
   production output.
2. Add driver-only Forge metadata, mixin config, refmap, and bootstrap under
   `versions/1.20.1-forge/src/testDriver` with mod ID
   `ae2craftingtime_test_driver` and an exact production-version dependency.
3. Add the reobfuscated `testDriverJar` output under the root project's
   `build/test-driver` directory. Keep `jar`, `reobfJar`, `distMod`, the release
   matrix, and production source sets unchanged.
4. Pin the current official MCP Java SDK version that supports Java 17 and
   Streamable HTTP in the driver-only configuration. Package its runtime only
   in the driver JAR and resolve any Forge class collision through driver-only
   relocation.
5. Add an artifact check that opens both JARs and proves the production JAR has
   no driver package, metadata, mixin config, or MCP classes, while the driver
   JAR has no embedded production classes.

Completion gate: the dedicated task produces one correctly named driver, Forge
rejects a mismatched production version, and normalized production artifact
content is unchanged apart from the normal versioned build inputs.

## Phase 2: Install it in Forge development clients

1. Update `scripts/run-client.ps1` and `scripts/run-client.sh` for target
   `1.20.1-forge` to build `testDriverJar` with the selected profile's runtime
   properties before `runClient`.
2. Copy the exact artifact into the selected `run` or `run-latest`
   `resolved-mods` directory and include its filename in the managed manifest.
3. Remove stale Forge 1.20.1 driver versions from that same directory without
   touching unrelated mods. Stop the launch on build, name, cleanup, or copy
   failure.
4. Verify that compatible and latest shared-script commands both inherit
   installation while other targets remain unchanged.
5. Add a shared-script runtime-directory parameter that defaults to the current
   `run` or `run-latest` location and controls both dependency installation and
   Gradle's `runtimeRunDirectory`.
6. Verify an ordinary development launch loads the driver mod but starts no
   scenario, endpoint, fixture access, input, or result writer.

Tests: extend the existing client-script tests for compatible/latest destination
selection, exact managed filename, stale-driver cleanup, failure propagation,
target exclusion, and PowerShell/shell parity.

Completion gate: `scripts/run-client.ps1 -Target 1.20.1-forge` and its `-Latest`
counterpart start only after the matching driver is present in the selected
client, while a normal run remains inert.

## Phase 3: Add the disposable fixture and runner

1. Create the smallest Forge 1.20.1 fixture at
   `versions/1.20.1-forge/run/saves/ae2-crafting-time`: player facing a known
   AE2 terminal, one craftable target, retained TTC samples, and the fixed-schema
   fixture marker.
2. Add `scripts/run-ui-smoke.ps1` for the fixed `craft-plan` target, with only
   `-Latest` and `-Interactive` switches.
3. Reuse the Forge 1.20.1 client launch path and
   `scripts/run-client-versions.json`, including its driver installation; do not
   duplicate dependency pins, downloads, or artifact selection.
4. Supply the profile-specific `build/ui-smoke` runtime through the shared
   runtime-directory parameter, copy the fixture there, give the copy a new
   disposable ID, set fixed client options, and enable the driver through an
   explicit system property.
5. Track the exact launched process tree, request normal shutdown, and terminate
   only that tree after a bounded timeout.
6. Validate and collect `result.json`, screenshots, the managed dependency
   manifest, and this client's log; return nonzero for missing/invalid output,
   required-check failure, abnormal exit, or fatal log signatures.

Tests: PowerShell checks for fixture-copy refusal of the source world, profile
output separation, result-schema failures, missing screenshots, fatal-log
detection, and exact-process cleanup selection.

Completion gate: one command can prepare and launch the compatible fixture
without modifying its tracked source, and every setup failure is attributable.

## Phase 4: Observe the real Crafting Plan UI

1. Add the immutable frame snapshot, rectangle, result model, atomic writer,
   layout validator, and bounded scenario state machine to the driver source
   set.
2. Add test-only mixins/accessors that capture the post-sort renderer input,
   final description and tooltip components, actual TTC draw/fill calls, widget
   bounds/state, item-cell bounds, GUI bounds, and scroll position.
3. Keep the snapshot swap at end-of-frame and use translation keys/output IDs
   for assertions. Wait for three identical completed frames before acting.
4. On the client thread, use the fixture's observed UI bounds to open its target
   craft, click the real sort button through all three modes, and hover the
   known row.
5. Check screen identity, TTC row, total, row order in each mode, tooltip,
   containment, and non-overlap. Capture base and tooltip framebuffer images.
6. On every terminal state, write the atomic result; automatic mode then queues
   normal client shutdown.

Tests: pure Java tests cover every state transition and timeout, stable-frame
reset, ascending/descending/unknown sort observations, rectangle boundary and
overlap cases, exact result keys, atomic-write failure, and redaction. Minecraft
tests verify mixin application, final component capture, real widget clicking,
and screenshot creation in the fixture.

Completion gate: the scenario fails if any production mixin stops applying or
if rendered rows, sorting, tooltip targeting, or geometry diverges.

## Phase 5: Add bounded interactive diagnosis

1. Start the official SDK Streamable HTTP server only with `-Interactive`, on an
   ephemeral loopback port with the runner-provided 256-bit bearer token.
2. Enforce one controller, the six-tool allowlist, 64-KiB request and log-tail
   limits, 1-MiB response limit, generated screenshot names, and five-second
   tool/thread deadlines.
3. Implement immutable snapshot responses for state, screen, and UI; queue
   screenshot and quit work to the client thread.
4. On scenario failure or completion, stop scenario mutation and keep only the
   endpoint active until `minecraft_quit` or process exit.
5. Close the endpoint during Minecraft shutdown and never persist its token.

Tests: reject non-loopback requests, missing/wrong auth, concurrent controller,
unknown tools, oversized requests/responses, paths supplied as screenshot
names, queue saturation, timeouts, and post-shutdown calls. Verify every tool
touches live game state only through the owning-thread scheduler.

Completion gate: an MCP client can inspect a paused scenario and quit it, while
no first-slice tool can click, press keys, execute commands, edit the world, or
read an arbitrary file.

## Phase 6: Verify the feature

After the hook-created PR exists:

1. Run the documentation/link checks, `git diff --check`, client-script tests,
   driver unit tests, Forge 1.20.1 module tests, and the artifact-isolation
   check.
2. Run the compatible automatic scenario twice from a fresh fixture copy and
   compare semantic results for deterministic keys/order.
3. Run the latest profile separately and record dependency resolution and
   startup results without weakening the compatible gate.
4. Prove refusal for no launch option, wrong production version, multiplayer,
   source fixture, unmarked copy, and marker mismatch.
5. Force one UI assertion failure, inspect it through MCP, save an additional
   screenshot, and quit through `minecraft_quit`.
6. Inspect production JAR, driver JAR, and `dist`; confirm no test-driver entry
   can be published by the release matrix or deploy scripts.
7. Launch both `scripts/run-client.ps1 -Target 1.20.1-forge` profiles normally;
   confirm the exact driver is installed and loaded but remains inert.
8. Review the full warning/error sweep, fix repository-owned warnings, and
   report proven third-party warnings separately.
9. Read back required GitHub CI after local checks complete.

Done means every acceptance criterion in `spec.md` has a passing automated or
fixture check, the compatible scenario is repeatable, the endpoint boundaries
are exercised, production artifacts remain isolated, and required CI is green.

## Independent standard cases

The shared `StandardAe2Scenario` uses named stages and a leaf ID, with fresh
fixture preparation for every leaf. Both target runtime implementations use
the same dispatch and exact check contracts. The 26.1.2 fixture/observer adapters
retain their native APIs. `ui-smoke-groups.json` owns host alias expansion and
required evidence; Java `DriverResult` enforces the matching check sets.
`SuitePlan` and the host accept 1–64 unique cases/worlds. Group results live in
the campaign report; existing schema-1 leaf and flat-suite reports are preserved.
Runtime acceptance still requires independent, group and full-suite evidence
on all four targets; code or contract tests alone do not establish a UI pass.
