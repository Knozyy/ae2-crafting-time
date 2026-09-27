# Dependencies

Crafting Time is optional on either side of a compatible AE2 connection. Both
sides need the mod for client-facing estimates, warnings and controls. A
server-only install still profiles crafts and keeps its saved history, but
clients without the mod see native AE2. A client-only install connects without
sending Crafting Time packets and shows native AE2 until it joins a supported
server. In singleplayer, the installed client and integrated server provide the
full feature set. Minecraft, loader, AE2 and other mod requirements still apply.

On Fabric 1.20.1, Mod Menu 7.2.2 or newer can open AE2 Crafting Time's
options screen from the mod list. Mod Menu is optional: the game and the local
client options file still load when it is absent. Forge and NeoForge use their
loader's Configure button instead.

Native Crafting Plan recurrence diagnostics use required AE2 planner, summary,
menu and renderer hooks on all four supported targets. They do not select or
extend optional Tree or requester screens. Forge and NeoForge append a
versioned server-to-client packet; Fabric sends it only when the recipient
advertises the recurrence channel. The native AE2 plan packet remains unchanged.
Qualification requires integrated `recurrent-plan` and `standard-plan-controls`
plus connected `recurrent-plan` with one client per target, all clients running
sequentially. NeoForge 1.21.1 also covers a greater-than-256-row plan. Recipient
boundary tests and dedicated replan/reconnect checks verify stale-plan isolation;
no simultaneous-player proof or recurrence runtime pass is recorded here yet.

Native Crafting Plan stored-variant diagnostics use the same required AE2 menu
and renderer paths, plus AE2's storage watcher, on all four targets. They add no
optional dependency and do not change separate Tree, requester, or running-job
screens. The TestDriver's `stored-variant-plan` leaf covers real damaged-item
exact/near keys and live storage transitions; qualification also requires
`standard-plan-controls`, `recurrent-plan`, and a marked loopback connected run
per target. This documents coverage requirements, not a recorded runtime pass.

Applied Enhancements 1.0.7 clears the native plan before starting long-range
calculation, including ordinary small orders. Recurrence reset accepts that
absent plan on all targets. AELIS may bypass native recurrence observation, so
its missing rows are not yet qualified for recurrence labels. Project Infinity
0.0.52 testing with OmniSequence 1.3.9 and 2.0.3-fix tracks these separately.

## Delayed resource icon qualification (planned)

[Connected resource fixtures #482](test-driver/connected-resource-fixtures/spec.md)
merged in [#484](https://github.com/cTux/ae2-crafting-time/pull/484).
Fixture-only PASS means real jobs, bounded
control and lifecycle evidence; production icon acceptance remains NOT_RUN.
Its approved expansion also requires sealed-source provisioning and cold/cache-hit
prewarm qualification for four native and two AppMek connected graphs. Exact
Java, loader and graph identities are defined in the
[fixture design](test-driver/connected-resource-fixtures/technical-design.md#provisioning-evidence-and-ownership).
The prerequisite PR records its fixture qualification; it does not establish
production icon coverage or change dependency minimums.

[Issue #376](https://github.com/cTux/ae2-crafting-time/issues/376) requires
`delayed-resource-icons` on all four native targets and `appmek-resource-icons`
on Forge 1.20.1 / NeoForge 1.21.1. These scenarios currently run only with explicit
`-ResourceFixtureOnly`; ordinary production acceptance still needs the
[mode and evidence extension](provider-locate/resource-icons/technical-design.md#production-acceptance-on-the-existing-fixtures).
Existing `appmek-cpu` mounts oxygen storage and a native CPU;
it does not prove a delayed chemical output or a visible provider icon.

Use the [resource-icon matrix](provider-locate/resource-icons/implementation-plan.md#runtime-acceptance-matrix)
and [driver contract](test-driver/spec.md#delayed-resource-icon-scenarios-planned):
water/lava and item controls on all four compatible profiles, oxygen/hydrogen on
both integration profiles, Forge's driver-only bucketless
`ae2craftingtime_test_driver:resource_fixture_fluid`, base-only
absent-integration controls, and matching connected send/reconnect cases.
AppMek remains optional and gains no Fabric or 26.1.2 dependency. A driver-only
bucketless fixture adds no production resource, metadata requirement or mod.

Compile/API qualification covers AE2 15.0.10, 19.0.24 and 26.1.10-beta minimums.
At the documented research baseline the compatible runtime AE2 pins are 15.4.10
(Forge), 15.1.0 (Fabric), 19.2.17 (NeoForge 1.21.1) and 26.1.10-beta; AppMek pins
are 1.4.3 and 1.6.3 respectively. Re-read `scripts/run-client-versions.json` and
record exact artifacts before running. Verify the native typed serialization
and world renderer on each applicable API boundary, including AppMek 1.6.3 and
26.1.2 geometry submission. Source signatures and successful compilation do not
qualify texture, tint or resource-reload behavior. Keep results bound to the
tested artifact hashes and reviewed world captures; no runtime pass is claimed
by this prerequisite documentation and no dependency minimum changes here.

## Startup adapter selection

Optional hooks use one fixed startup choice per dependency. The first matching
API contract wins; older contracts stay packaged. Required AE2 hooks and all
loader dependency ranges are unchanged. A selected adapter is eligible for
application, not proof that its hooks have run. The immutable selection snapshot
is available through `IntegrationMixinPlugin.snapshot()`.

| Dependency | Adapter priority and target boundary |
| --- | --- |
| Crafting Tree | `tree-layout`, then `tree-helper`; client only, pre-26 targets. Fabric retains its declaration but has no verified published Tree artifact. |
| NeoEco | Forge: `batched-long`, then `pending-accounting`. NeoForge 1.21.1: `batched-int`. One dispatch adapter shares lifecycle hooks; neither family is enabled on Fabric or 26.1.2. |
| AdvancedAE | `advanced-cpu`; Forge and both NeoForge targets, both physical sides. |
| Lightning Tech | `time-wheel`; Forge and NeoForge 1.21.1, both physical sides. Its 26.1.2 native coverage does not imply a custom adapter. |
| ME Requester | `requester-screen`; pre-26 clients only. |

Raw class metadata is inspected before optional mixin application, without
initializing addon or Minecraft classes. Expected missing contracts skip the
whole family; unexpected read or bootstrap errors propagate. Selection is never
changed by later world, setting, or hook failures.

The retained [contract fixtures](../shared/src/test/resources/integration-contracts)
record released artifact URLs, SHA-512 hashes, and inspected member descriptors.
See [verification evidence](mod-automation-coverage.md#versioned-adapter-verification)
for the runtime acceptance state.

Pick the JAR that matches your Minecraft version and loader. Required versions,
optional integrations, development-client pins, and TestDriver coverage live here.
[Client and modpack coverage](mod-automation-coverage.md) records actual smoke
campaigns, client setup, exclusions, and the limits of those results.

The automated cross-target suite records every selected project in `coverage.json`:
direct UI, direct behavior, coexistence, tooling, or excluded with a reason.
Declared driver coverage is separate from each run's PASS/FAIL/NOT_RUN result.
`standard-ae2` adds plan/status input, real smelting, row states and output checks
to all four drivers. See [the suite specification](automated-ui-testing/spec.md).
Its CPU-list leaf additionally cycles both-list TTC sorting, binds input to
actual rendered CPU identities, and captures bounded full-list request coverage.

## Required dependencies

These are required. The mod will not load unless your instance matches the row
for its JAR.

| Target jar | Required dependencies |
| --- | --- |
| `1.20.1 Forge` | Minecraft `[1.20.1,1.21)`, Forge `[47.1.3,)`, Applied Energistics 2 `[15.0.10,16.0.0)`; GuideME `[20.1.15,)` is optional for the in-game guide |
| `1.20.1 Fabric` | Minecraft `1.20.1`, Fabric Loader `>=0.14.21`, Fabric API `>=0.83.1`, Java `>=17`, Applied Energistics 2 `>=15.0.10 <16.0.0` |
| `1.21.1 NeoForge` | Minecraft `[1.21.1]`, NeoForge `[21.1.1,)`, Applied Energistics 2 `[19.0.24,20.0.0)`; GuideME `[21.1.0,)` is optional for the in-game guide |
| `26.1.2 NeoForge` | Minecraft `[26.1.2]`, NeoForge `[26.1.2.71,)`, Java `>=25`, Applied Energistics 2 `[26.1.10-beta,27.0.0)`; GuideME `[26.1.10-alpha,)` is optional for the in-game guide |

### Why the 1.20.1 JARs are larger

The Fabric and Forge 1.20.1 JARs embed MixinExtras `0.5.5`; the NeoForge
JARs use the copy included with NeoForge. Fabric keeps its own copy because
the supported Fabric Loader minimum, `0.14.21`, predates Loader's built-in
MixinExtras. Version `0.5.5` also prevents world-entry crashes when newer
Fabric Mixin versions compile `Redirect.at` as an array. Forge uses the
library's recommended Jar-in-Jar setup.
The 1.21.1 summary hook uses `WrapOperation`, available in NeoForge `21.1.1`'s
bundled MixinExtras `0.3.5`, so the loader floor stays unchanged.

MixinExtras is intentionally not a separate player-installed dependency.
Externalizing it would mostly move the same bytes into another JAR and add a
missing-dependency failure point. The Fabric copy could be removed by raising
the minimum Fabric Loader version to `0.15.0`, but Forge 1.20.1 would still
bundle it. See the [MixinExtras setup instructions][mixinextras-setup].

## Dependency and integration matrix

Optional addons are not required to run AE2 Crafting Time. Declared optional
ranges have a minimum and no upper cap. Development-client pins make tests
repeatable; they do not freeze modpacks or guarantee future addon compatibility.

The four version columns record compatible development-client pins, with
focused-only and unpinned exceptions marked.
They are not minimum versions or a claim about the latest upstream release.
**Declared targets** and **Minimum version** describe loader metadata; `—` in
those columns means no separate optional dependency declaration is recorded.
It does not erase the native integration or development coverage in the row.
Required versions are listed above.

The prepared Fabric client disables Fabric's development-only mod shuffle so
initialization order matches a normal installed client. With random dev order,
ExtendedAE `1.20-1.0.2-fabric` can call AE2WTLib `15.2.1` before its universal
terminal item exists and crash in `WUTHandler.addTerminal`. This is a development
profile setting, not an upstream addon fix; all pinned addons remain installed.

`Yes (base)` means the Forge driver exercises the dependency in its base AE2
scenario; `Yes (scenario)` or a scenario name means a dedicated driver exists.
`No` means no driver for that target; `Not pinned` means declared support without
a compatible-client artifact; `—` in a target column means it is not part of that
target's recorded inventory. Driver availability is not a passing smoke result:
see the [campaign evidence and known visual failure](mod-automation-coverage.md).
AdvancedAE's Forge fixture verifies an enclosed Quantum Computer with an
Accelerator and Data Entangler, exact CPU submission, a fresh sample, and plan TTC.
It is development coverage, not a declared Forge dependency. Crafting Tree now has
a Forge `crafting-tree-screen` scenario for node badges, spacing, one copy of each
tooltip hint, and real details/reset input; Fabric has a sixteen-case compatible
suite. NeoForge 1.21.1 has a 30-case compatible suite, and NeoForge 26.1.2 has a
nineteen-case compatible suite.

Focused read-recovery scenarios cover Crafting Tree on Forge 1.20.1 and NeoForge
1.21.1, and ME Requester on all three pre-26 targets. They require isolated addon
fixtures with a renamed read member, verify that the original screen remains
usable without TTC additions, and run `craft-lifecycle` afterward in the same
client process. These negative fixtures are not part of the compatible suite. The NeoEco fixture
mounts a native item cell and supplies its ingredients so the newest-adapter graph
does not depend on storage supplied by other addons.
Fabric Crafting Tree has no available runtime artifact and is excluded from
issue #193's runtime acceptance by explicit user decision.

`no-channel-status` uses a real controller and normal-cable bottleneck on every
native target. It checks the provider node's power, boot and channel predicates,
then frees a physical channel and requires the real job to continue. The same
scenario directly selects an AdvancedAE CPU on Forge 1.20.1 and both NeoForge
targets when AdvancedAE status mode is enabled. Other provider overrides are
covered only when they execute AE2's observed provider activity guard.

`no-provider-status` is a focused native AE2 scenario on all four targets.
Its historical Forge 1.20.1 smoke checks real pattern/provider removal, bilingual
tooltips, recovery, redundant providers, and cancellation. New campaigns use
English only. The other target
drivers compile; this result does not claim their runtime smoke or a full
optional-mod graph run.

| Mod Name | 1.20.1-forge | 1.20.1-forge TestDriver | 1.20.1-fabric | 1.20.1-fabric TestDriver | 1.21.1-neoforge | 1.21.1-neoforge TestDriver | 26.1.2-neoforge | 26.1.2-neoforge TestDriver | Declared targets | Minimum version | Integration / limits | Project links / tracking issue |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Minecraft (required) | `1.20.1` | Yes (base) | `1.20.1` | Yes (base suite) | `1.21.1` | Yes (base suite) | `26.1.2` | Yes (base suite) | — | See required dependencies | Required runtime / base AE2 crafting. | — |
| Java (required) | `17` | Yes (base) | `17` | Yes (base suite) | `21` | Yes (base suite) | `25` | Yes (base suite) | — | See required dependencies | Required runtime / base AE2 crafting. | — |
| Forge (required) | `47.4.23` | Yes (base) | — | — | — | — | — | — | — | See required dependencies | Required runtime / base AE2 crafting. | — |
| Fabric Loader (required) | — | — | `0.19.5` | Yes (base suite) | — | — | — | — | — | See required dependencies | Required runtime / base AE2 crafting. | — |
| Fabric API (required) | — | — | `0.92.11+1.20.1` | Yes (base suite) | — | — | — | — | — | See required dependencies | Required runtime / base AE2 crafting. | — |
| NeoForge (required) | — | — | — | — | `21.1.251` | Yes (base suite) | `26.1.2.109` | Yes (base suite) | — | See required dependencies | Required runtime / base AE2 crafting. | — |
| Applied Energistics 2 (required) | `15.4.10` | Yes (`craft-plan`, `no-space-status`, `no-provider-status`) | `15.1.0` | Yes (`craft-plan`, `no-space-status`, `no-provider-status`) | `19.2.17` | Yes (`craft-plan`, `no-space-status`, `no-provider-status`) | `26.1.10-beta` | Yes (`craft-plan`, `no-space-status`, `no-provider-status`) | — | See required dependencies | Required runtime / base AE2 crafting. | — |
| GuideME (`guideme`; optional) | `20.1.15` | Yes (guide book) | AE2 bundled guide | Yes (guide book) | `21.1.19` | Yes (guide book) | `26.1.12-beta` | Yes (guide book) | `1.20.1 Forge`, `1.21.1 NeoForge`, `26.1.2 NeoForge` | Forge: `>=20.1.15`; 1.21.1: `>=21.1.0`; 26.1.2: `>=26.1.10-alpha` | Every target has the same craftable bilingual guide. Forge and modern NeoForge use optional GuideME; Fabric uses AE2 15.x's bundled guide renderer and declares no GuideME dependency. Without GuideME, Forge and NeoForge load without the guide recipe. | [MR](https://modrinth.com/mod/guideme) / [#144](https://github.com/cTux/ae2-crafting-time/issues/144) / [#265](https://github.com/cTux/ae2-crafting-time/issues/265) / [#266](https://github.com/cTux/ae2-crafting-time/issues/266) |
| ae2ct (Crafting Tree) (`ae2ct`; optional) | `1.0.1` | Yes (scenario) | Not pinned | No | `1.0.1` | Yes (`crafting-tree-screen`) | — | — | `1.20.1 Forge`, `1.20.1 Fabric`, `1.21.1 NeoForge` | `>=1.0.1` | TTC lines and details in AE2: Crafting Tree UI. | [CF][ae2ct-cf] / [#69](https://github.com/cTux/ae2-crafting-time/issues/69) |
| Applied Mekanistics (`appmek`; optional) | `1.4.3` | Yes (`appmek-cpu`) | — | — | `1.6.3` | Yes (`appmek-cpu`) | — | — | `1.20.1 Forge`, `1.21.1 NeoForge` | Forge: `>=1.4.3`; NeoForge: `>=1.6.2` | TTC profiling and Crafting Plan display for chemicals stored in the ME network through Applied Mekanistics' native AE2 key. | [CF][appmek-cf] / [MR][appmek-mr] / [#68](https://github.com/cTux/ae2-crafting-time/issues/68) |
| AdvancedAE (`advanced_ae`; optional) | `1.3.6-1.20.1` | Yes (scenario) | — | — | `1.6.12-1.21.1` | Yes (`advancedae-cpu`) | `26.1.7` | Yes (`advancedae-cpu`) | `1.21.1 NeoForge`, `26.1.2 NeoForge` | 1.21.1: `>=1.6.11`; 26.1.2: `>=26.1.7` | TTC profiling for AdvancedAE crafting CPUs, including the Quantum Computer. | [CF][advancedae-cf] / [MR][advancedae-mr] / [#67](https://github.com/cTux/ae2-crafting-time/issues/67) |
| ME Requester (`merequester`; optional) | `1.20.1-1.2.1+forge` | Yes (`merequester-screen`) | `1.20.1-1.1.4+fabric` | Yes (`merequester-screen`) | `1.21.1-1.5.0+neoforge` | Yes (`merequester-screen`) | — | — | `1.20.1 Forge`, `1.20.1 Fabric`, `1.21.1 NeoForge` | Forge/Fabric: `>=1.20.1`; NeoForge: `>=1.21.1` | TTC row labels below the amount fields, beside a shortened status bar, plus header total hints. The Forge layout check includes item-slot bounds. A focused Forge smoke and the refreshed diamond-request screenshot confirm the icon overlap is fixed. | [CF][merequester-cf] / [MR][merequester-mr] / [#70](https://github.com/cTux/ae2-crafting-time/issues/70) / [#197](https://github.com/cTux/ae2-crafting-time/issues/197) |
| NeoEco AE (`neoecoae`; optional) | `20.4.2` | Yes (scenario) | — | — | `21.1.1` | Yes (`neoeco-cpu`) | — | — | `1.20.1 Forge`, `1.21.1 NeoForge` | Forge: `>=20.3.0`; NeoForge: `>=21.1.1` | TTC profiling for C-series ECO crafting CPUs, including normal and FastPath dispatch. Version 20.4.2 passed the Project Infinity update graph's CPU smoke with LightningTech and Thunderbolt installed; results for other dependency graphs can differ. | [CF][neoecoae-cf] / [MR][neoecoae-mr] / [#66](https://github.com/cTux/ae2-crafting-time/issues/66) |
| AE2 Lightning Tech (`ae2lt`; optional) | `2.1.0-beta.4-forge.1.20.1` | Yes (scenario) | — | — | `2.1.0-beta.5` | Yes (`lightningtech-cpu`) | `1.0.1alpha-26.1.2neoforge` | Yes (`lightningtech-cpu`) | `1.20.1 Forge`, `1.21.1 NeoForge` | `>=2.1.0-beta.2` | TTC profiling for Tianshu time-wheel crafting CPUs on 1.20.1/1.21.1. Runtime smoke pins use CurseForge because the former Modrinth project is no longer available. The 26.1.2 alpha has no Tianshu CPU; its smoke case verifies real crafting through its overloaded pattern provider and a native AE2 CPU. | [CF][ae2lt-cf] / [#72](https://github.com/cTux/ae2-crafting-time/issues/72) |
| OmniSequence: Transfinite (`molecularmanipulator`; optional) | `1.3.9-forge` | Yes (scenario) | — | — | `1.3.9-hotfix` | Yes (`omnisequence-cpu`) | — | — | `1.20.1 Forge`, `1.21.1 NeoForge` | Forge: `>=1.3.8-hotfix-forge`; NeoForge: `>=1.3.9-hotfix` | TTC profiling for Omni-Computation Core crafting CPUs through AE2's native execution path. | [CF][omnisequence-cf] / [#71](https://github.com/cTux/ae2-crafting-time/issues/71) |
| ExtendedAE (optional) | `1.20-1.4.18-forge` | Yes (scenario) | `1.20-1.0.2-fabric` | Yes (`extendedae-cpu`) | `1.21-2.2.35-neoforge` | Yes (`extendedae-cpu`) | `26.1-1.0.3-neoforge` | Yes (`extendedae-cpu`) | — | — | Assembler/provider integration through AE2 UI and execution paths; Forge and Fabric drivers available. | [CF][extendedae-cf] / [#73](https://github.com/cTux/ae2-crafting-time/issues/73) |
| ExtendedAE-Plus (`extendedae_plus`; optional) | `1.5.5` | Yes (scenario) | — | Yes (`extendedae-cpu`) | `1.6.2` | Yes (`extendedae-plus-cpu`) | — | — | `1.20.1 Forge`, `1.21.1 NeoForge` | Forge: `>=1.5.5`; NeoForge: `>=1.6.2` | TTC profiling and UI coexistence with ExtendedAE-Plus crafting changes. | [CF][extendedaeplus-cf] / [MR][extendedaeplus-mr] / [#74](https://github.com/cTux/ae2-crafting-time/issues/74) |
| BM Addon (`bmaddon`; optional) | `1.0.4` | Yes (`bmaddon-cpu`) | — | — | `BMAddon1.21.1-beta1` | Yes (`bmaddon-cpu`) | `beta3` | Yes (`bmaddon-cpu`) | `1.20.1 Forge`, `1.21.1 NeoForge`, `26.1.2 NeoForge` | Forge: `>=1.0.4`; 1.21.1: `>=1.21.1-beta1`; 26.1.2: `>=1.0.2` | TTC profiling and Crafting Plan display for crafts handled by the Blood Assembler. | [CF][bmaddon-cf] / [MR][bmaddon-mr] / [#75](https://github.com/cTux/ae2-crafting-time/issues/75) |
| Crazy AE2 Addons (`crazyae2addons`; optional) | `2.6.2` | Yes (`crazyae2addons-cpu`) | — | — | — | — | — | — | `1.20.1 Forge` | `>=2.6.2` | TTC profiling and Crafting Plan display with Crazy AE2 Addons CPU priorities. Provider dispatch uses a composable wrapper so Crazy AE2 Addons keeps its CPU bookkeeping. The CPU list correction now uses Crafting Time's shared AE2 render/input seam instead of an addon-specific mixin; exact-pack verification remains tracked in #421. | [CF][crazyae2-cf] / [MR][crazyae2-mr] / [#76](https://github.com/cTux/ae2-crafting-time/issues/76) / [#316](https://github.com/cTux/ae2-crafting-time/issues/316) / [#421](https://github.com/cTux/ae2-crafting-time/issues/421) |
| AE2 WCWT (`wcwt`; optional) | `1.20.1.10` | Yes (`ae2wcwt-terminal`) | — | — | `1.3.8` | Yes (`ae2wcwt-terminal`) | — | — | `1.20.1 Forge`, `1.21.1 NeoForge` | Forge: `>=1.20.1.7-hotfix`; NeoForge: `>=1.3.8` | One-item TTC in craftable entries of the wireless comprehensive work terminal. Crafting still uses AE2's normal CPU detection and Crafting Plan. | [CF][ae2wcwt-cf] / [MR][ae2wcwt-mr] / [#77](https://github.com/cTux/ae2-crafting-time/issues/77) |
| AE2 Wireless Terminals (`ae2wtlib`; optional) | `15.3.3-forge` | Yes (`ae2wtlib-terminal`) | `15.2.1-fabric` | Yes (`ae2wtlib-terminal`) | `19.5.1` | Yes (`ae2wtlib-terminal`) | `26.1.1-beta` | Yes (`ae2wtlib-terminal`) | All supported targets | 1.20.1 Forge: `>=15.3.3`; Fabric: `>=15.2.1`; 1.21.1: `>=19.5.1`; 26.1.2: `>=26.1.1-beta` | One-item TTC in craftable entries of its wireless crafting terminal. Crafting still uses AE2's normal CPU detection and Crafting Plan. | [CF][wireless-cf] / [MR][wireless-mr] / [#78](https://github.com/cTux/ae2-crafting-time/issues/78) |
| MEGA Cells (`megacells`; optional) | `forge-2.4.6` | Yes (`megacells-cpu`) | `fabric-2.4.6` | Yes (`megacells-cpu`) | `4.11.0` | Yes (`megacells-cpu`) | — | — | `1.20.1 Forge`, `1.20.1 Fabric`, `1.21.1 NeoForge` | Forge/Fabric: `>=2.4.6`; NeoForge: `>=4.11.0` | TTC profiling and Crafting Plan display for MEGA crafting CPUs through AE2's native execution path. | [CF][megacells-cf] / [MR][megacells-mr] / [#79](https://github.com/cTux/ae2-crafting-time/issues/79) |
| OMNI Cells (`ae2omnicells`; optional) | `1.1.6-1.20.1-forge` | Yes (`omnicells-cpu`) | — | — | `1.1.6-1.21.1-neoforge` | Yes (`omnicells-cpu`) | `1.1.7-26.1.2-neoforge` | Yes (`omnicells-cpu`) | `1.20.1 Forge`, `1.21.1 NeoForge`, `26.1.2 NeoForge` | 1.20.1/1.21.1: `>=1.1.6`; 26.1.2: `>=1.1.7` | TTC profiling and Crafting Plan display for OMNI crafting CPUs through AE2's native execution path. | [CF][omnicells-cf] / [#80](https://github.com/cTux/ae2-crafting-time/issues/80) |
| ProjectCell (`projectcell`; optional) | `1.0.1` | Yes (`projectcell-cpu`) | — | — | `1.0.3` | Yes (`projectcell-cpu`) | — | — | `1.20.1 Forge`, `1.21.1 NeoForge` | `>=1.0.0` | TTC profiling and Crafting Plan display when ProjectCell supplies ingredients from ProjectE EMC through AE2's native storage and crafting paths. | [CF][projectcell-cf] / [MR][projectcell-mr] / [#81](https://github.com/cTux/ae2-crafting-time/issues/81) |
| AppliedE / AppliedE TPS Fix (`appliede`; optional) | `0.14.7-fix2` | Yes (`appliede-cpu`) | — | — | `1.0.1-beta` | Yes (`appliede-cpu`) | — | — | `1.20.1 Forge`, `1.21.1 NeoForge` | Forge: `>=0.14.0`; NeoForge: `>=1.0.0-beta` | TTC profiling and Crafting Plan display when AppliedE supplies ingredients from ProjectE EMC through AE2's native key and crafting paths. Install either AppliedE or its TPS Fix fork, not both. | [CF][appliede-cf] / [MR][appliede-mr] / [#82](https://github.com/cTux/ae2-crafting-time/issues/82) |
| Applied Flux (`appflux`; optional) | `1.20-1.3.7-forge` | Yes (`appflux-cpu`) | — | — | `1.21-2.1.5-neoforge` | Yes (`appflux-cpu`) | `26.1-1.0.1-neoforge` | Yes (`appflux-cpu`) | `1.20.1 Forge`, `1.21.1 NeoForge`, `26.1.2 NeoForge` | 1.20.1: `>=1.20-1.3.7-forge`; 1.21.1: `>=1.21-2.1.4-neoforge`; 26.1.2: `>=26.1-1.0.1-neoforge` | TTC profiling and Crafting Plan display for FE stored in the ME network through Applied Flux's native AE2 energy key. | [CF][appliedflux-cf] / [MR][appliedflux-mr] / [#83](https://github.com/cTux/ae2-crafting-time/issues/83) |
| Modern AE2 Additions (`mae2`; optional) | `2.0.1` | Yes (`modern-ae2-additions-cpu`) | — | — | — | — | — | — | `1.20.1 Forge` | `>=1.1.0` | TTC profiling and Crafting Plan display for CPUs using Modern AE2 Additions dense co-processors through AE2's native crafting path. | [CF][modernae2-cf] / [MR][modernae2-mr] / [#84](https://github.com/cTux/ae2-crafting-time/issues/84) |
| AE2 Import Export Card (`ae2insertexportcard` / `ae2importexportcard`; optional) | `1.20.1-1.3.0` | Yes (`ae2importexportcard-terminal`) | — | — | `1.21.1-1.9.0` | Yes (`ae2importexportcard-terminal`) | `26.1.2-2.3.0` | Yes (`ae2importexportcard-terminal`) | — | — | Terminal/card integration with direct one-item TTC tooltips; Forge driver available. | [CF][ae2iec-cf] / [MR][ae2iec-mr] / [#85](https://github.com/cTux/ae2-crafting-time/issues/85) |
| AE2 Network Analyser (`ae2netanalyser`; optional) | `1.20-1.0.6-forge` | Yes (`ae2networkanalyser-screen`) | `1.20-1.0.1-fabric` | No | `1.21-2.1.5-neoforge` | No | `26.1-1.0.0-neoforge` (beta) | No | — | — | Visual analyser compatibility smoke (`GuiAnalyser`); no extra TTC hook. | [CF][ae2na-cf] / [#86](https://github.com/cTux/ae2-crafting-time/issues/86) |
| AEInfinityBooster (`aeinfinitybooster`; optional) | `1.20.1-1.0.0+20` | Yes (`aeinfinitybooster-terminal`) | — | — | `1.21.1-1.0.0.58` | Yes (`aeinfinitybooster-terminal`) | `26.1.2-1.0.0.57` | Yes (`aeinfinitybooster-terminal`) | — | — | Wireless range compatibility through AE2's normal Crafting Plan; no extra TTC hook. | [CF][aeinfinity-cf] / [MR][aeinfinity-mr] / [#87](https://github.com/cTux/ae2-crafting-time/issues/87) |
| Applied Botanics (original) (`appbot`; optional) | `1.5.2` | Yes (`appbot-cpu`: native mana storage + item craft) | `1.5.2` | Yes (`appbot-cpu`) | Not pinned | No | — | — | `1.20.1 Forge`, `1.20.1 Fabric`, `1.21.1 NeoForge` | 1.20.1: `>=1.5.0`; 1.21.1: `>=1.6.0-alpha.3` | Raw mana amounts and mana unit labels through its native AE2 key. Existing milli-pool histories are converted on load. NeoForge alpha is not runtime-pinned or smoke-verified. | [MR](https://modrinth.com/mod/applied-botanics) |
| Applied Botanics (Fork; alternative `appbot`) | `1.5.2` | Yes (`appbot-fork-cpu`: native mana storage + item craft) | — | — | — | — | — | — | — | — | Alternative `appbot` artifact using the same native mana integration; install either the original or fork. | [CF][appliedbotanics-cf] / [#88](https://github.com/cTux/ae2-crafting-time/issues/88) |
| Advanced Peripherals (`advancedperipherals`; optional) | `1.20.1-0.7.48r` | Yes (`advancedperipherals-cpu`: real ME Bridge API) | — | — | `1.21.1-0.8.1a` (alpha) | Yes (`advancedperipherals-cpu`) | — | — | — | — | ME Bridge submits through AE2's native crafting service; Forge driver uses a real attached CC:Tweaked computer. No ComputerCraft TTC API or display. | [CF][advancedperipherals-cf] / [MR][advancedperipherals-mr] / [#89](https://github.com/cTux/ae2-crafting-time/issues/89) |
| AE2 Things (`ae2things`; original Fabric / Forge port; optional) | `1.2.1` | Yes (`ae2things-cpu`: real DISK storage craft) | `1.3.2` | Yes (`ae2things-cpu`) | Not pinned (`1.4.2-beta` supported) | No | — | — | — | — | Native DISK storage and crafting; Both 1.20.1 drivers supply ingredients from a real DISK. No TTC overlays for Fabric-only machine UIs. | [MR][ae2things-mr] / [Forge port](https://www.curseforge.com/minecraft/mc-mods/ae2-things-forge) |
| Expanded AE (`expandedae`; optional) | `1.2.2` (focused only; excluded from full compatible set) | Yes (`expandedae-cpu`, latest focused profile) | — | — | `2.1.1` (excluded from compatible set) | No | — | — | — | — | Native CPU profiling and Crafting Plan; Forge driver uses a two-thread accelerator. Focused testing only; no OmniSequence coexistence claim. | [CF][expandedae-cf] / [MR][expandedae-mr] |

## Compatibility boundaries

### Known conflicts

- **Crazy AE2 Addons `2.6.2` overrides Crafting Time's CPU-list TTC order on
  Forge 1.20.1.** Its client mixin sorts the list again by priority, name, and
  serial immediately before slicing visible cards, then supplies the same raw
  order from a cancellable hit-test hook. Project Infinity 0.0.52.0 reproduced
  the mismatch with AE2 15.4.10. Profiling and Crafting Plan integration remain
  available. A shared AE2-method correction replaces the failed addon-specific
  checkpoint adapter, but CPU-list ordering and input compatibility remain
  pending exact-pack verification in
  [#421](https://github.com/cTux/ae2-crafting-time/issues/421).

- **AE Crafting Tracker `0.2.2` conflicts with AE2 Crafting Time `1.2.2`
  on NeoForge 1.21.1.** Both mods redirect the same provider `isBusy()` and
  `pushPattern()` calls in standard AE2 and AdvancedAE crafting logic. When
  installed together, mixin application can fail and crash the server. Do not
  install these versions together until [#295](https://github.com/cTux/ae2-crafting-time/issues/295)
  is resolved.

The AdvancedAE hook also ships on Forge 1.20.1. Its table entry lists only
loader-declared dependencies; Forge currently has no optional AdvancedAE
dependency declaration. Runtime diagnostics report the packaged hook separately.

- `NO PROVIDER` observes the standard AE2 dispatch lookup on all four targets
  and AdvancedAE's equivalent lookup on Forge 1.20.1 and both NeoForge targets.
  The verified AdvancedAE artifacts are `1.3.6-1.20.1`, CurseForge file `7849217`,
  and `26.1.7`. Other custom dispatch engines are not claimed by this diagnostic.
  This source/bytecode verification does not add a live UI-smoke result.

- Lightning Tech `1.0.1alpha-26.1.2neoforge` uses `@OnlyIn`, which triggers a
  NeoForge development warning screen. The 26.1.2 client runs use NeoForge's
  `neoforge.warnings.onlyin.hide` option to skip that screen while keeping the
  diagnostics in the log. This does not fix Lightning Tech's annotations or
  change the server run; the reported client log ended with a normal shutdown.
- Applied Mekanistics has Forge/NeoForge releases for these targets. Applied
  Flux has no stable Fabric 1.20.1 release. Neither is declared for Fabric.
- Applied Botanics original and fork share `appbot`, its version, and mana key;
  install one, not both. Both use raw mana and Botania 455 on Forge 1.20.1.
  The fork has no recorded Fabric, NeoForge 1.21.1, or 26.1.2 artifact.
  Original 1.21.1 support starts at `1.6.0-alpha.3` and requires Botania
  `454-SNAPSHOT` (`vazkii.botania:botania-neoforge-1.21.1:454-20260621.181850-47`),
  with no matching published Modrinth release. No original 26.1.2 artifact is
  recorded. The Forge driver covers native mana storage plus normal item
  crafting, not a mana-generation recipe.
- AppliedE and AppliedE TPS Fix are alternative artifacts: install one, not both.
  The NeoForge 1.21.1 client pins AppliedE `1.0.1-beta`: ExtendedAE
  `1.21-2.2.35-neoforge` expects `LEARNING_CARD` to be a `DeferredItem`, while
  AppliedE `1.0.0-beta` exposes a `Supplier` and crashes during common setup.
  `1.0.1-beta` is the first release with the matching field type. This client
  constraint does not raise AE2 Crafting Time's standalone AppliedE minimum.
- AEInfinityBooster uses AE2's normal Crafting Plan and has no recorded Fabric
  release for the supported target. Advanced Peripherals has no recorded Fabric
  1.20.1 or NeoForge 26.1.2 release; NeoForge 1.21.1 uses an alpha.
- AE2 Things uses the original Fabric release and a separate Forge/NeoForge
  port. The latter has no old Inscriber or Crystal Growth Chamber. NeoForge
  `1.4.2-beta` is compile-checked, not promoted to the compatible runtime without
  a smoke run. No 26.1.2 artifact is recorded.
- Expanded AE has no recorded Fabric 1.20.1 or NeoForge 26.1.2 artifact. Its
  Applied Flux/OmniSequence conflict excludes it from the full compatible set;
  the [client coverage notes](mod-automation-coverage.md#client-setup-and-exclusions)
  explain the focused profile and missing upstream prerequisite declarations.
- WCWT, AE2 Wireless Terminals, and Import Export Card label one-item estimates
  `TTC: ~1s` or `TTC: No data yet`, after AE2's amount, craftability, and advanced
  details. Other mods may append content afterward. Compact row badges retain
  their existing text.

## Integration approach

Start with AE2's CPU, key, and UI contracts. Add addon-specific code only when
source inspection proves that an addon bypasses them. Reuse `ClientStats`,
`TimeEstimate`, and `AeKeyAmounts`; add a compile-time dependency only when AE2
APIs or an optional string-target mixin cannot reach the screen or key type.

| Path | What it covers | Boundary |
| --- | --- | --- |
| Native CPU execution | `CraftingCpuLogicMixin`, including OmniSequence and other addons inheriting its methods | An override bypasses only the method it replaces. Provider observation uses the selected iterator, so it doesn't compete with OmniSequence's provider lookup wrapper. AdvancedAE uses the same observation point. |
| Custom CPU adapter | AdvancedAE, NeoEco, and LightningTech call `ProfilerBridge` at dispatch, insertion, finish, and capacity points | Use a small optional `@Pseudo` mixin for custom execution. NeoEco uses `cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic`; LightningTech uses `com.moakiee.ae2lt.crafting.timewheel.Ae2LtTimeWheelCraftingCpuLogic`. |
| Native resource key | `AeKeyAmounts` normalizes `AEKey` through `getAmountPerUnit()` | Verify each key type; use `AEKeyType`, not addon class checks. Preserve raw-mana precision. |
| Existing AE2 UI hooks | Craft-confirm and crafting-status renderers and screens inheriting their hooked methods | `AbstractTableRendererMixin` only decorates TTC lines; inheritance alone does not prove every screen path is covered. |
| Bespoke UI hook | Crafting Tree, ME Requester, WCWT, Wireless Terminals, and Import Export Card | Add an optional screen-specific mixin only where the shared UI path does not reach. Advanced Peripherals uses native submission and needs no extra TTC API. |
| Planned service observation | A proof-of-coverage spike may observe `CraftingService.submitJob`, `getCpus`, and busy-state changes | Only covers exposed CPUs and job lifecycle; cannot produce output-throughput samples. A shared `AEBaseScreen` hook also remains conditional on one usable common method. |

Automatic `ICraftingCPU` discovery and a Mixin plugin do not replace execution
adapters: the interface exposes status, capacity, and cancellation, not dispatch
and accepted-output events. `getGrid()` and `getLevel()` are concrete CPU methods;
check the supplying addon class before using them in an adapter.

The [addon integration spec](ae2-addon-integration/spec.md),
[technical design](ae2-addon-integration/technical-design.md), and
[implementation plan](ae2-addon-integration/implementation-plan.md) retain the
full requirements and rollout. Tracking issues in the matrix identify that work;
they do not claim a current issue state.

## Build tools and sources

Build the 1.20.1 modules with Java 17, 1.21.1 NeoForge with Java 21, and
26.1.2 NeoForge with Java 25.

Declared support comes from [the release matrix](../scripts/release-matrix.json)
and loader metadata. Client pins come from
[run-client-versions.json](../scripts/run-client-versions.json); scenarios live in
[the Forge test driver](../versions/1.20.1-forge/src/testDriver).

[advancedae-cf]: https://www.curseforge.com/minecraft/mc-mods/advancedae
[advancedae-mr]: https://modrinth.com/mod/advancedae
[neoecoae-cf]: https://www.curseforge.com/minecraft/mc-mods/neo-eco-ae-extension
[neoecoae-mr]: https://modrinth.com/mod/neoecoae
[omnisequence-cf]: https://www.curseforge.com/minecraft/mc-mods/omnisequence-transfinite
[ae2lt-cf]: https://www.curseforge.com/minecraft/mc-mods/ae2-lightning-tech
[ae2lt-mr]: https://modrinth.com/mod/ae2-lightning-tech
[extendedae-cf]: https://www.curseforge.com/minecraft/mc-mods/ex-pattern-provider
[extendedaeplus-cf]: https://www.curseforge.com/minecraft/mc-mods/extendedae-plus
[extendedaeplus-mr]: https://modrinth.com/mod/extendedae-plus
[bmaddon-cf]: https://www.curseforge.com/minecraft/mc-mods/bmaddon
[bmaddon-mr]: https://modrinth.com/mod/bmaddon
[crazyae2-cf]: https://www.curseforge.com/minecraft/mc-mods/crazy-ae2-addons
[crazyae2-mr]: https://modrinth.com/mod/crazy-ae2-addons
[ae2wcwt-cf]: https://www.curseforge.com/minecraft/mc-mods/ae2-wcwt
[ae2wcwt-mr]: https://modrinth.com/mod/ae2-wcwt
[wireless-cf]: https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2-wireless-terminals
[wireless-mr]: https://modrinth.com/mod/applied-energistics-2-wireless-terminals
[megacells-cf]: https://www.curseforge.com/minecraft/mc-mods/mega-cells
[megacells-mr]: https://modrinth.com/mod/mega
[omnicells-cf]: https://www.curseforge.com/minecraft/mc-mods/omni-cells
[projectcell-cf]: https://www.curseforge.com/minecraft/mc-mods/projectcell
[projectcell-mr]: https://modrinth.com/mod/projectcell
[appliede-cf]: https://www.curseforge.com/minecraft/mc-mods/appliede-tps-fix
[appliede-mr]: https://modrinth.com/mod/appliede
[appliedflux-cf]: https://www.curseforge.com/minecraft/mc-mods/applied-flux
[appliedflux-mr]: https://modrinth.com/mod/applied-flux
[modernae2-cf]: https://www.curseforge.com/minecraft/mc-mods/modern-ae2-additions
[modernae2-mr]: https://modrinth.com/mod/modern-ae2-additions
[appmek-cf]: https://www.curseforge.com/minecraft/mc-mods/applied-mekanistics
[appmek-mr]: https://modrinth.com/mod/applied-mekanistics
[ae2ct-cf]: https://www.curseforge.com/minecraft/mc-mods/ae2-crafting-tree
[merequester-cf]: https://www.curseforge.com/minecraft/mc-mods/merequester
[merequester-mr]: https://modrinth.com/mod/merequester
[ae2iec-cf]: https://www.curseforge.com/minecraft/mc-mods/ae2-import-export-card
[ae2iec-mr]: https://modrinth.com/mod/ae2-import-export-card
[ae2na-cf]: https://www.curseforge.com/minecraft/mc-mods/ae2-network-analyser
[aeinfinity-cf]: https://www.curseforge.com/minecraft/mc-mods/aeinfinitybooster
[aeinfinity-mr]: https://modrinth.com/mod/aeinfinitybooster
[appliedbotanics-cf]: https://www.curseforge.com/minecraft/mc-mods/applied-botanics-fork
[advancedperipherals-cf]: https://www.curseforge.com/minecraft/mc-mods/advancedperipherals
[advancedperipherals-mr]: https://modrinth.com/mod/advancedperipherals
[ae2things-mr]: https://modrinth.com/mod/ae2things
[expandedae-cf]: https://www.curseforge.com/minecraft/mc-mods/expanded-ae
[expandedae-mr]: https://modrinth.com/mod/expanded-ae
[mixinextras-setup]: https://github.com/LlamaLad7/MixinExtras/blob/master/README.MD#setup

The native AE2 and supported AdvancedAE dispatch hooks now share the NO PROVIDER /
NO POWER status transport. NO POWER observes only the real simulated AE network
energy extraction. All four prepared compatible suites include both native
status scenarios; their results apply only to the actual tested artifacts.

Standard AE2 coverage now expands into six independent leaves before full-suite
execution (34/16/30/19 cases for Forge 1.20.1, Fabric 1.20.1, NeoForge 1.21.1,
and NeoForge 26.1.2). A focused leaf pass does not certify a dependency's full
standard group. Optional integrations keep their existing scenario names and
newest-adapter obligations; change selection does not alter dependency pins.

## Runtime integration diagnostics

Startup logs include actual installed versions, target/side skips, pending
capabilities, and a compact integration summary. Confirmations come from the
named operation returning successfully. Shared-hook confirmations explicitly
leave addon-specific crafting unverified; compatibility-only entries make no
health claim. The [diagnostics design](startup-integration-diagnostics/technical-design.md)
defines the reporting scope.

ExtendedAE is one reporting row with metadata aliases: Forge's inspected
1.20-1.4.18-forge uses expatternprovider; inspected NeoForge
1.21-2.2.35-neoforge and 26.1-1.0.3-neoforge use extendedae.
Tree package variants use the existing selector's choice. A failed read disables
only the selected Tree variant's TTC additions, the Requester TTC overlay, or
AdvancedAE selected-CPU lookup. Host behavior and server samples remain intact.
This is not recovery from an incompatible CPU adapter or a failed transformation.
