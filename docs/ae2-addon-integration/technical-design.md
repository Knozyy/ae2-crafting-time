# AE2 Addon Integration Technical Design

Lifecycle: see the [scope status and evidence](spec.md).

## Versioned adapter selection

Historical planning status: planned. Implements VS-01 through VS-10 in the
[specification](spec.md#versioned-adapter-selection). The remaining sections
describe the existing integration layers that this change must preserve.

### Research and evidence

Source inspection on 2026-09-03 used fetched `origin/master` at
`24cd545dc6061217e8340ed1d3838b984f0d5014`. No new game, server, or compatibility
test was run for this planning change. Historical smoke results below are
evidence for regression targets, not fresh verification.

| Repository evidence | Finding and design consequence |
| --- | --- |
| [Shared pre-26 mixins](../../shared/src/mc1201/resources/ae2craftingtime.mixins.json), [1.21.1 mixins](../../versions/1.21.1-neoforge/src/main/resources/ae2craftingtime.mixins.json), [26.1.2 mixins](../../versions/26.1.2-neoforge/src/main/resources/ae2craftingtime.mixins.json) | No configuration plugin currently chooses variants. `required: false`, `@Pseudo`, and optional injections do not express mutual exclusion. |
| [Old Tree widget](../../shared/src/mc1201/java/com/ctux/ae2craftingtime/mc1201/mixin/CraftingTreeWidgetMixin.java), [new Tree widget](../../shared/src/mc1201/java/com/ctux/ae2craftingtime/mc1201/mixin/CraftingTreeNewWidgetMixin.java) | Both are listed. The old target is `com.neuvillette.ae2ct.gui.CraftingTreeWidget`; the new target is `com.vcwdfca.ae2ct.gui.CraftingTreeWidget`. Node access, tooltip method, click return type, and spacing behavior differ. This is already additive support, but selection relies on which classes exist. |
| [NeoEco CPU mixin](../../shared/src/mc1201/java/com/ctux/ae2craftingtime/mc1201/mixin/ECOCraftingCpuLogicMixin.java) | One redirect targets three `recordPushedPattern` descriptors. History `58a19dcf`, `205f5265`, and `27f2cce1` identifies the accounting-object, Forge long/boolean, and NeoForge integer-batch contracts. Explicit dispatch variants can preserve them without several competing redirects. |
| [Forge build](../../versions/1.20.1-forge/build.gradle), [extra AdvancedAE config](../../versions/1.20.1-forge/src/main/resources/ae2craftingtime-advancedae.mixins.json) | Forge packages AdvancedAE through an additional config and a shared source directory. Both configs must share the same selection state. |
| [Lightning Tech mixin](../../shared/src/mc1201/java/com/ctux/ae2craftingtime/mc1201/mixin/Ae2LtTimeWheelCraftingCpuLogicMixin.java), [ME Requester mixin](../../shared/src/mc1201/java/com/ctux/ae2craftingtime/mc1201/mixin/MERequesterScreenMixin.java) | Each currently has one adapter. Do not manufacture historical variants without an actual distinct supported contract. Lightning Tech's returned-output fix is a correctness change, not evidence that an old bug must be retained. |
| [Dependencies](../dependencies.md), [campaign evidence](../mod-automation-coverage.md) | The curated Forge NeoEco pin is 20.3.0; Project Infinity records 20.4.0 and 20.4.2. Both sides of that contract change need regression proof. Source inspection alone does not identify the first released version of every Tree package. |
| [Startup diagnostics design](../startup-integration-diagnostics/technical-design.md) | At this research baseline, #193 was planned; its current delivery and verification are tracked in the [diagnostics spec](../startup-integration-diagnostics/spec.md). Its inventory and observed-capability states must consume selection results rather than select a second time. |

Crafting Tree is also a fork boundary, not just a numerical version boundary.
Live [original releases](https://www.curseforge.com/minecraft/mc-mods/ae2-crafting-tree/files/all)
and [Refreshed metadata](https://api.modrinth.com/v2/project/a1RwDz90/version)
identify the following concrete fixtures. Downloaded original JARs and the
cached prepared Refreshed Forge JAR were inspected as ZIPs, without execution.
Both projects use mod ID `ae2ct`; Refreshed's numerically smaller `1.0.1` owns
the newer adapter contract. Never order these forks by their version strings.

| Contract | Forge 1.20.1 fixture | NeoForge 1.21.1 fixture |
| --- | --- | --- |
| `tree-helper` | Original `1.20.1-1.1.1`, [CF file 7182165](https://www.curseforge.com/minecraft/mc-mods/ae2-crafting-tree/files/7182165); JAR contains `com/neuvillette/ae2ct/gui/CraftingTreeWidget.class`. | Original `1.21.1-1.1.1`, [CF file 7182163](https://www.curseforge.com/minecraft/mc-mods/ae2-crafting-tree/files/7182163); same old widget namespace. |
| `tree-layout` | Refreshed `1.0.1`, [MR version flhDmaU7](https://modrinth.com/mod/ae2-crafting-tree-refreshed/version/flhDmaU7); inspected JAR contains `com/vcwdfca/ae2ct/gui/CraftingTreeWidget.class` and `tree/LegacyTreeLayout`. | Refreshed `1.0.1`, [MR version 35O4yt0D](https://modrinth.com/mod/ae2-crafting-tree-refreshed/version/35O4yt0D); metadata verified, its widget contract still needs artifact verification during implementation. |

The checked original file catalogue lists Forge/NeoForge, and the Refreshed
version API returns only those two loader releases. No Fabric artifact was
found in these sources. Preserve the repository's existing Fabric declaration
and packaged variants, but require negative/side/selection tests there, not a
fictional positive smoke. Do not claim Fabric Tree runtime support was verified.

Upstream mechanisms checked for this design:

- Mixin's [configuration plugin contract](https://github.com/SpongePowered/Mixin/blob/master/src/main/java/org/spongepowered/asm/mixin/extensibility/IMixinConfigPlugin.java)
  supplies `shouldApplyMixin` as an early veto and warns plugins against game
  class references. Mixin priority orders transformations; it does not choose
  one adapter. Normal mod initialization is too late to veto those transforms.
- Mixin's [bytecode provider](https://github.com/SpongePowered/Mixin/blob/master/src/main/java/org/spongepowered/asm/service/IClassBytecodeProvider.java)
  supports reading an ASM `ClassNode` with transformers disabled. Use metadata
  and bounded reads of named classes, not `Class.forName`, construction,
  reflective method invocation, or a classpath scan.
- [Fabric Loader 0.14.21](https://maven.fabricmc.net/docs/fabric-loader-0.14.21/net/fabricmc/loader/api/FabricLoader.html)
  exposes mod containers and physical environment.
  [Forge 1.20.x](https://github.com/MinecraftForge/MinecraftForge/blob/1.20.x/fmlloader/src/main/java/net/minecraftforge/fml/loading/FMLLoader.java)
  and [FML 4.0](https://github.com/neoforged/FancyModLoader/blob/4.0/loader/src/main/java/net/neoforged/fml/loading/FMLLoader.java)
  expose an early loading mod list. [Current FML](https://github.com/neoforged/FancyModLoader/blob/main/loader/src/main/java/net/neoforged/fml/loading/FMLLoader.java)
  uses `FMLLoader.getCurrent().getLoadingModList()`. Keep this difference in the
  target-specific bridge. These upstream branches explain the API; compile and
  packaged startup checks against the repository's pinned loaders are required.

The design inference is to use a small fixed catalogue and a Mixin gate. It
does not need a plugin framework, runtime downloads, dependency scanning, or a
new library. Mixin and ASM are already part of the loader toolchain.

### Ownership and bootstrap

Use three small seams, with no game-class references in their static state:

1. `core/IntegrationSelection.java` under `shared/src/main/java`: immutable
   candidate/decision values and deterministic first-compatible selection. Plain
   strings, target/side values, ordered lists, and compatibility booleans only.
   Reuse JUnit in `shared/src/test/java` for decision tests.
2. `integration/IntegrationMixinPlugin.java` under `shared/src/mcCommon/java`
   (package `com.ctux.ae2craftingtime.integration`, outside the Mixin package):
   the fixed catalogue, bounded bytecode probes, process-local decisions, and
   `IMixinConfigPlugin` implementation. Share decisions across plugin instances.
   `getMixins` returns null; retain static config lists and gate them with
   `shouldApplyMixin`. Do not mutate foreign configs in `acceptTargets` or
   rewrite target bytecode in `preApply`/`postApply`.
3. `integration/IntegrationPlatform.java` in each target's main Java source:
   the same compile-time class name with loader-specific metadata and physical
   side access. Fabric uses `FabricLoader`; Forge and NeoForge use the early
   FML loading lists, never the later `ModList` singleton. No reflection to
   guess which loader is running. Each published JAR contains exactly one
   platform implementation.

Register the plugin in all four actual configs, including Forge's extra
AdvancedAE config. The shared pre-26 config serves both Forge and Fabric.
Keep the bootstrap helper out of config/mixin packages that Mixin reserves;
it must not initialize `ProfilerBridge`, client helpers, normal entrypoints,
or mutable game configuration. Use direct SLF4J for the bounded selection log.

Compute each family's complete decision atomically on its first relevant
`shouldApplyMixin` call, before returning any acceptance for that family.
Reject target and physical-side mismatches before reading optional bytecode.
Cache an immutable result keyed by canonical dependency ID for the process.
Singleton groups obey the same path. Never cache temporarily unavailable loader
metadata as an absent dependency: an unexpected early-bootstrap failure must
propagate with context. There is no retry after Mixin starts applying a family.

### Catalogue and priority

The catalogue is an ordered Java list, newest supported API first **within a
target**, not a sort of upstream release strings or Mixin annotation priorities.
Each candidate has a dependency ID, stable variant ID, target set, applicable
side, cooperating mixin names, and a contract predicate. Priority is list order;
duplicate variant IDs or cross-family mixin ownership are programming errors
rejected by tests. Same-family common hooks are allowed as described below.

| Dependency | Initial variants and order | Target / side |
| --- | --- | --- |
| `ae2ct` | `tree-layout` (`CraftingTreeNewWidgetMixin`), then `tree-helper` (`CraftingTreeWidgetMixin`) | Forge/Fabric 1.20.1 and NeoForge 1.21.1; client only |
| `neoecoae` | Forge: `batched-long` then `pending-accounting`; NeoForge 1.21.1: `batched-int` | Forge 1.20.1 and NeoForge 1.21.1; both physical sides |
| `advanced_ae` | `advanced-cpu`, containing current CPU hooks | Forge 1.20.1 and both NeoForge targets; both physical sides |
| `ae2lt` | `time-wheel`, containing current CPU hooks | Forge 1.20.1 and NeoForge 1.21.1; both physical sides |
| `merequester` | `requester-screen`, containing current screen hooks | Forge/Fabric 1.20.1 and NeoForge 1.21.1; client only |

Singleton adapters retain existing injection requirements. Their eligibility
uses target, side, installed ID, and target-class presence; this is not a claim
that every method is compatible. Additional structural rejection predicates
are added only for known API breaks with retained alternatives. Unsupported
signatures inside an otherwise selected singleton retain current Mixin/runtime
failure behavior, not an invented promise of safe recovery.

Crafting Tree predicates require the four integer shadow fields (`outputX`,
`outputY`, `spacingX`, `spacingY`), `draw` with five arguments and void return,
and `drawNode` with two arguments, the matching addon-owned second argument,
and void return. The old variant additionally requires `mouseClicked(DDI)Z`,
`getMousePoint(DD)Ljava/awt/Point;`, and the old `CraftingTreeHelper$Node`.
The new variant requires `mouseClicked(DDI)V`, `updateTooltip`, and
`LegacyTreeLayout$Entry` with `node()` and `point()` accessors. Check method
names and argument/return shapes without resolving the Minecraft graphics
class. Existing injection requirements remain responsible for exact call sites.
Inspect only those named classes and required declared superclass members.
Contract research and runtime tests must establish that the chosen widget is
the active UI path; a signature probe cannot prove an unused shim is live.
Do not guess a numeric release boundary from the two package names.

NeoEco predicates require the exact addon-owned `recordPushedPattern` descriptor:

```text
pending-accounting:
  (Lcn/dancingsnow/neoecoae/api/me/ECOCraftingCPULogic$PendingPatternAccounting;)V
batched-long:
  (Lcn/dancingsnow/neoecoae/api/me/ExecutingCraftingJob;Lcn/dancingsnow/neoecoae/impl/crafting/fastpath/ECOExtractedPatternExecution;JZ)V
batched-int:
  (Lcn/dancingsnow/neoecoae/api/me/ExecutingCraftingJob;Lcn/dancingsnow/neoecoae/impl/crafting/fastpath/ECOExtractedPatternExecution;I)V
```

Split only NeoEco's dispatch redirect into three small optional mixins with
one descriptor each. Keep the existing `ECOCraftingCpuLogicMixin` as the shared
lifecycle/capacity/output component, included in each mutually exclusive
variant's bundle. Move its existing network-aware `ProfilerBridge.start` call
behind one narrow `NeoEcoDispatchObserver` interface in `mcCommon`; the common
mixin implements it, and the selected dispatch mixin calls it after the original
inventory insertion. Do not duplicate grid state, deferred finish handling, or
profiler logic. Common membership within one family is intentional; its mixin
is accepted once whenever that family has a selected variant. Common membership
across different families remains an error.

If an upstream class contains multiple compatible overloads, prefer the newer
variant only when its hook observes the active dispatch path, including normal
and FastPath modes. Verify callers and delegate chains so both variants cannot
observe the same insertion. A signature's existence is necessary, not proof of
execution. Retain legacy support; never broaden one redirect to all overloads.

Raw bytecode probes use the addon-owned descriptors and primitive/argument
shapes above, avoiding comparisons between development Minecraft names and
obfuscated production names. Keep reflection-dependent UI behavior, full
descriptors involving game classes, and injector success as runtime validation
boundaries. Probes cannot certify bytecode after other mods transform it.

Native AE2 hook reuse, key normalization, wireless class/interface filters, and
AdvancedAE's selected-CPU lookup remain shared behavior. They do not need fake
per-addon variants. When a future API break affects those paths, add a family
and gate the entire owned capability bundle through the same cached decision;
ordinary callbacks must read that decision before registering. Do not copy the
selector into each callback. Core required AE2 mixins always pass this optional
gate. A future required-AE2 variant family must fail clearly if none matches,
rather than silently disabling required profiling.

### Decisions, failures, and diagnostics

```text
early loader metadata + target + physical side
  -> ordered, bounded candidate checks
  -> one immutable dependency decision
  -> allow selected/common mixins; veto other variants
  -> real hooks -> existing profiler/cache/UI
```

Decision reasons are `selected`, `absent`, `unsupported_target`,
`wrong_side`, or `no_compatible_variant`. Store the selected variant and bounded
rejection reasons for newer candidates. Missing named classes/members are
expected incompatibility; I/O errors, malformed bytecode, unavailable metadata,
and programming/linkage errors are not ordinary absence. Preserve their cause
and stop that startup through the original failure path. Do not catch `Throwable`.

When no optional candidate matches a known contract, veto the complete owned
bundle before application and emit one WARN. Normal absence/side/target skips
and successful selections use INFO. No runtime Mixin rollback, lowered
`require`, replacement of an upstream crash, or fallback after a callback fails.
Existing shared AE2 profiling and other addons continue unless startup itself
fails. No game objects, class nodes, or throwable objects remain in the cache.

Issue #193's diagnostics will consume this snapshot. `selected` maps to pending
applicable capabilities, not initialized. A preflight contract rejection maps
to unavailable/disabled optional capabilities with its reason; it is distinct
from a runtime failure. Do not count each Tree/NeoEco variant as an integration
or emit the same selection from every config. Until #193 exists, keep the single
selection line in the bootstrap plugin; do not implement its wider report here.

### Compatibility, alternatives, and validation

Keep runtime optional ranges open-ended and required ranges unchanged.
Internal API predicates are not loader version caps. Do not reject an unknown
future version just because it is newer than a development pin; use its known
contract if it still matches. A semantic break with unchanged signatures still
requires upstream research and a new variant/predicate; structural matching
cannot promise compatibility with arbitrary future code.

All retained variants ship in each applicable target JAR. Compile against the
existing dependency where possible; string targets and shared API types avoid
loading two upstream versions. If a later variant needs conflicting concrete
types, isolate its compile-only source set and merge its output, without bundling
either dependency. Do not add that build machinery until a real variant needs it.

No packet, persistent data, profile identity, configuration, locale, or player
UI change is involved. Only process-local startup decisions are new.

Rejected alternatives: replacing the old adapter loses supported installs;
setting Mixin priority still applies both hooks; ordinary entrypoint selection
is too late; class loading as a probe can trigger transformations; a global
single winner prevents unrelated addons from working together; universal
version ranges cannot distinguish forks or semantically different APIs.

The [implementation plan](implementation-plan.md#versioned-adapter-selection)
maps all acceptance checks to pure decisions, packaging, startup, and behavior.
Retain old/new artifact contracts for non-smoke checks. Runtime smoke exercises
only the newest implemented adapter per dependency/target, in English (`en_us`),
under the [shared smoke policy](../automated-ui-testing/spec.md#smoke-policy).
For Forge NeoEco this is `batched-long`; `pending-accounting` keeps contract
coverage without a 20.3 smoke. NeoForge's `batched-int` remains that target's
newest adapter. For Tree, smoke `tree-layout` and retain `tree-helper` contract
checks. Compilation or selection logs alone cannot replace newest-adapter smoke.

## Native-first layers

### 0. AE2 crafting execution

`CraftingCpuLogicMixin` remains the authoritative path for normal AE2 CPUs. It
records five different facts at their real owners:

```text
CraftingCpuLogic.executeCrafting -> ProfilerBridge.start
CraftingCpuLogic.insert          -> ProfilerBridge.complete
CraftingCpuLogic.trySubmitJob    -> ProfilerBridge.startJob
CraftingCpuLogic.finishJob       -> ProfilerBridge.finishJob
CraftingCpuLogic.tickCraftingLogic -> ProfilerBridge.updateCapacity
```

A subclass gets these hooks only while it inherits the hooked method. An addon
override must be checked method by method. This matters for OmniSequence: it
uses AE2 logic but also redirects part of `executeCrafting`, so another redirect
at the same call site can conflict.

### 1. AE2 crafting service observation

A future `CraftingServiceMixin` may observe:

- successful `submitJob` calls;
- CPUs returned by `getCpus()`;
- `isBusy()` and `getJobStatus()` transitions during `onServerEndTick`.

This is a fallback for job lifecycle, not a throughput profiler.
`ICraftingCPU` does not expose pattern dispatch or accepted output. AE2's
concrete service also keeps a `Set<CraftingCPUCluster>`, so a custom CPU is
visible only if the addon deliberately adds it to the service result or routes
submission through that service.

The observer must key state by CPU object identity and ignore CPUs already owned
by an execution mixin. The state transition logic belongs in pure shared Java;
the mixin should only read AE2 objects and delegate.

### 2. Custom execution adapters

When an addon owns the crafting loop, use one optional string-target mixin for
that loop. The adapter maps its real events to the same five `ProfilerBridge`
calls used by AE2. It must not introduce a second profiler, estimator, cache, or
packet.

Known cases:

- AdvancedAE already uses this layer.
- NeoEco needs hooks around its own CPU logic and pattern-bus dispatch.
- AE2 Lightning Tech needs hooks around its time-wheel CPU logic.
- OmniSequence stays in layer 0 unless runtime proof shows a missing event.

NeoForge 1.21.1 registers the shared NeoEco and Lightning Tech adapters. The
NeoEco expected-output redirect also accepts 21.1.1's integer-batch signature;
it still observes only the first waiting-output insertion, not returned containers.
Lightning Tech uses the same time-wheel method descriptors on both loaders.
No profiler, packet, or saved-data format changes are needed.

### 3. AE2 key contract

`AeKeyAmounts.normalize` already uses `AEKey.getAmountPerUnit()`. Because that
method delegates to `AEKeyType`, fluids, chemicals, energy, EMC, and future key
types can describe their own units without addon checks.

Applied Botanics uses `botania:mana` with 1,000,000 mana per displayed pool.
The generic milli-unit conversion would lose samples below 1,000 mana. A shared
pure-Java amount helper keeps this resource in raw mana and selects the `MANA`
profile unit. All other keys retain their current normalization. Tooltip and
chat labels come from the same profile-unit translation key.

On history load, convert only `botania:mana` entries recorded as `MILLIBUCKET`
from milli-pools to mana by multiplying each representable positive amount by
1,000. New `MANA` samples and unrelated entries remain unchanged. The named NBT
unit field stays compatible; snapshot protocol versions change on all loaders
because older clients cannot decode the new enum value.

`ProfilerBridge.key` currently identifies a profile with `AEKey.getId()`. Do not
silently change that format. Adding the `AEKeyType` ID would affect saved data,
packet request IDs, cache lookup, and reset messages, so it needs its own format
version and migration if a real collision is found.

### 4. AE2 UI paths

`CraftConfirmTableRendererMixin` and `CraftingStatusTableRendererMixin` are the
behavior seams. They append TTC and tooltip details through
`getEntryDescription` and `getEntryTooltip`. An addon is covered automatically
only when it uses those concrete renderers or a subclass inherits those hooked
methods.

`AbstractTableRendererMixin` decorates TTC lines and calculates row colors. It
does not add TTC to a custom renderer by itself.

An `AEBaseScreen` mixin is not assumed to exist. Add it only if source review
finds one stable method across supported AE2 versions that supplies the key,
amount, row position, and current network/CPU context needed by existing
helpers. Otherwise a small `@Pseudo` screen mixin is safer and more honest.

## Data ownership

The existing boundaries stay unchanged:

```text
server CPU/service hook
  -> ProfilerBridge
  -> CraftProfiler and TtcAccuracyTracker
  -> saved samples and bounded snapshot packets
  -> ClientStats cache
  -> AE2 or addon UI hook
```

The server owns sampling, accuracy, stalls, resets, and persistence. The client
only requests aggregate data and renders it.

## Duplicate and failure handling

- One CPU scope may have one active profiling owner.
- A service observer must skip an execution-instrumented CPU.
- Optional mixins use string targets and tolerate an absent addon, but a present
  supported target must fail CI or runtime verification when its required hook
  no longer matches.
- A failed job clears pending scope without recording successful accuracy.
- Unknown key types use their native amount contract. They are not coerced to a
  guessed addon unit.

## Version boundaries

Optional Forge/NeoForge metadata uses `[minimum,)`; Fabric suggestions use
`>=minimum`. Do not derive maximum versions from the pinned client graph.
The optional-integration validator rejects closed or capped ranges on every
release row. It leaves required Minecraft/loader/AE2 boundaries untouched.
Pins remain exact for repeatable development runs, while named-pack tests use
the explicitly selected pack versions and retain any real API/startup failure.

The hook contract is shared where the AE2 API matches, but every source layer
must be checked:

| Target | Main source layer | Extra concern |
| --- | --- | --- |
| 1.20.1 Forge | `mcCommon` + `mc1201` | Legacy AE2 15 signatures and optional Forge addons |
| 1.20.1 Fabric | `mcCommon` + `mc1201` | No NeoForge-only addon classes |
| 1.21.1 NeoForge | `mcCommon` + `mc1201` + `neoforge` | AE2 19 signatures |
| 26.1.2 NeoForge | `mcCommon` + `mc2612` + `neoforge` | AE2 26/Minecraft identifier changes and fewer optional UI integrations |

## Applied Journey investigation

See the [scope and acceptance](spec.md#applied-journey-investigation) and
[archive boundary](applied-journey.md). The current report has no runtime
evidence, so the cause and correction remain open.

Ordinary `CraftingCpuLogicMixin` observes dispatch, accepted output, job finish
and capacity. `ProfilerBridge` scopes and normalizes these events for
`CraftProfiler` and saved history. `StatsRequestHandler` uses
`StatsRequestContext` to resolve the authoritative grid/CPU; snapshots populate
`ClientStats`. Shared `CraftConfirmTableRendererMixin` and
`CraftingStatusTableRendererMixin` append text through `TtcText`, using craft
amount and active-plus-pending amount respectively. Missing samples can show
Collecting Data; they do not by themselves explain every control disappearing.

`ClientOptionsRuntime.enabled` checks the negotiated stats channel, local
options and server profiling state. NeoForge `StatsNetwork.canSend` checks
`NetworkRegistry.hasChannel`. Inspect those gates before changing rendering.
`IntegrationSelection.shouldApply` keeps required AE2 hooks outside optional
adapter selection. Data Energistics and OmniSequence have no dedicated entry
in that selector at this investigation baseline.

Trace a confirmed defect through every sibling caller before fixing it:
NeoEco, Lightning Tech and AdvancedAE use `ProfilerBridge`; plan/status,
wireless, Crafting Tree and ME Requester use shared requests/cache/text.
Prefer the confirmed shared seam. Preserve network-plus-output identity,
normalized units, bounded packets and saved history. An addon adapter requires
evidence of a distinct execution or UI path.

The export's bundled AE2 Extended CPU/storage mixins and other installed UI
addons are candidates for isolation, not diagnosed causes. Compare observed
hook execution, actual screen class, channel availability, options, request
responses and completion samples at the first differing graph. Retain the
historical artifact baseline separately from the current-source replacement.
If current source already fixes the report, identify the existing correction
through before/after evidence rather than introducing speculative code.

Existing Prism staging, the tracked NeoForge source fixture and schema-2
`prepare-ui-smoke-suite.ps1` cover provisioning and suite isolation. This scope
does not authorize building a replacement runner. Before execution, verify the
exact Codex instance, Java 21, guest-local runtime, fixture marker and matching
production/driver hashes. Missing downloads or guest prerequisites are explicit
preflight gates. Install the supplied export when no matching instance exists.

## Development-client profiles

`scripts/run-client-versions.json` is the source of truth for development
clients. Each target records:

- the projects installed in both profiles, plus any compatible-profile
  exclusion and its reproduced reason;
- the compatible loader, AE2, and Fabric API versions;
- exact compatible Modrinth version IDs for every top-level and transitive
  project in the locked graph;
- SHA-512-locked compatible and latest files for CurseForge-only dependencies.

Ordinary wrappers select `compatible`. The resolver rejects a compatible graph
when a required project has no lock entry, so a supposedly stable client cannot
silently pull a newer library. Latest wrappers select `latest`, ignore every
version lock, and resolve current target-compatible files from Modrinth and the
loader Maven metadata. CurseForge-only latest files are updated explicitly in
the same matrix because no anonymous version API is available.

Compatible clients keep the existing `versions/<target>/run` directory. Latest
clients use `versions/<target>/run-latest`. Gradle receives the selected game
directory so Forge's resolved-mod repository and every loader's world and
config state stay aligned with the chosen profile.

The release matrix remains authoritative for published targets and platform
dependency metadata. A regression check requires the run-client matrix to have
exactly the same target IDs, but development candidates do not become published
optional dependencies merely by appearing in a run profile.
