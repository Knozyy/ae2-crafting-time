# AE2 Addon Integration Spec

Status: in-progress

Scope: Native addon integration and versioned adapter selection.

Implementation: [PR #113](https://github.com/cTux/ae2-crafting-time/pull/113), [PR #211](https://github.com/cTux/ae2-crafting-time/pull/211).
Verification: selection is merged, but [PR #211](https://github.com/cTux/ae2-crafting-time/pull/211) records incomplete runtime acceptance.
The historical integration roadmap is not proof that every proposed layer shipped.

## Goal

Support as many AE2 addons as possible through AE2's own extension points. An
addon should get TTC support without addon-specific code when it reuses AE2's
crafting execution, key, or table UI behavior.

The integration must stay optional. Installing AE2 Crafting Time must not make
any addon required.

The version-selection requirements below were implemented in PR #211. Read
their [technical design](technical-design.md#versioned-adapter-selection) and
[implementation plan](implementation-plan.md#versioned-adapter-selection).
They extend this feature; they do not change the current support matrix.
Tracking issue: [#210](https://github.com/cTux/ae2-crafting-time/issues/210).

## Player outcomes

- Crafts run by compatible addon CPUs contribute the same throughput samples as
  normal AE2 CPUs.
- TTC and stall details stay available in addon screens that reuse AE2's
  craft-confirm or crafting-status table renderer behavior.
- New `AEKey` types use their AE2 unit size without a hard-coded addon list.
- Unsupported custom crafting loops fail safely. They must not create misleading
  samples or crash when the addon is absent.

## Requirements

### CPU profiling

1. Keep `CraftingCpuLogicMixin` as the main execution hook.
2. Treat inherited hooked methods as covered. Treat each overridden hooked
   method as a possible gap until its source is checked.
3. A service-level observer may track submission and busy/idle transitions only
   for CPUs returned by AE2's crafting service.
4. Do not call service observation universal. AE2's concrete
   `CraftingService` stores `CraftingCPUCluster` objects, and `ICraftingCPU` does
   not expose dispatch, accepted output, grid, or level.
5. Add a per-addon `@Pseudo` mixin only when the addon owns a custom execution
   method that AE2 cannot expose.
6. Avoid double recording when a CPU is visible through more than one layer.

NeoForge 1.21.1 also profiles NeoEco 21.1.1 and Lightning Tech 2.1.0-beta.2
custom CPU execution. Their smoke scenarios must submit a real craft, observe a
new server sample, and show its TTC in the reopened plan. Both remain optional.

### Keys

1. Normalize amounts through `AEKey.getAmountPerUnit()`.
2. Do not switch on addon IDs or concrete key classes when the `AEKey` contract
   gives the required value.
3. Verify profile identity before claiming support for a new key type. If two
   key types can share the same resource ID, handle that as a separate persisted
   data and packet compatibility change.
4. Applied Botanics mana (`botania:mana`) is recorded in raw mana units. Its
   pool-sized display unit must not round small positive samples to zero or
   label mana as mB. This is an explicit exception because AE2's display-unit
   size does not name the underlying mana unit.
5. Preserve existing mana history by converting recorded milli-pool amounts to
   mana (multiply by 1,000). Already-lost fractional precision cannot be
   recovered. Leave other resource histories unchanged.

### UI

1. Reuse the existing craft-confirm and crafting-status renderer hooks, text,
   color, sorting, cache, and request helpers.
2. Count an addon screen as generally covered only when it uses those concrete
   renderers or inherits the exact `getEntryDescription` and `getEntryTooltip`
   methods that own the TTC hooks. Reusing only `AbstractTableRenderer` is not
   enough.
3. Add a common `AEBaseScreen` hook only if one AE2 method supplies enough row or
   tooltip context without addon-specific reflection.
4. Keep a bespoke screen mixin as the last option.

### Compatibility and safety

- Optional-addon runtime metadata uses a minimum supported version with no
  upper cap. A tested development pin is not a maximum supported version.
  Updating an addon must not be refused merely because it is newer than our
  smoke-tested baseline. Keep Minecraft, loader, Java and required AE2 API
  boundaries separate; this does not promise compatibility with every future API.
- Record demonstrated upstream failures as diagnostics, not speculative loader
  caps. Fix our adapter when its hooks change and verify the requested versions.
- Cover all four supported release rows: 1.20.1 Forge, 1.20.1 Fabric, 1.21.1
  NeoForge, and 26.1.2 NeoForge.
- Keep optional mixins tolerant when their target addon is absent.
- Preserve server-owned stats and the existing `networkId + outputId` lookup
  unless a separately planned compatibility migration changes it.
- Do not add loader metadata or a compile dependency until real code needs it.
- Every executable branch added later needs full line and branch coverage.

### Versioned adapter selection

An integration is support for one dependency. An adapter variant implements
that support for a particular upstream API generation. One variant can contain
several cooperating hooks; those hooks are not competing integrations.

| ID | Required behavior |
| --- | --- |
| VS-01 | When an upstream API change needs different integration code, add a variant and retain the older supported variant in the same target JAR. An unchanged API continues to use its existing variant. Shared bug fixes may update all affected variants. |
| VS-02 | Select exactly one compatible variant per applicable dependency during process startup, before its hooks or callbacks are installed. Different dependencies can each have an active integration at the same time. Select zero when the optional dependency is absent, unsupported on that target/side, or has no compatible variant. |
| VS-03 | Try variants in explicit newest-API-first priority within the current Minecraft/loader target. Skip an incompatible candidate and try the next eligible candidate before activation. Do not prefer a newer variant merely because its name or version string sorts later. |
| VS-04 | Keep the decision fixed for that process. Repeated startup callbacks, Mixin configurations, screen openings, and world changes must not select another variant or duplicate hooks. Changing the installed dependency requires restarting the game/server. |
| VS-05 | Reject unselected variants before their hooks apply. Selection must not load or initialize optional gameplay classes, or client classes on a dedicated server. A feature setting can suppress selected behavior without changing the selected variant. |
| VS-06 | Preserve support for older dependency releases within our existing supported Minecraft/loader/AE2 boundaries. A newer development pin does not remove an older adapter, raise the runtime minimum, or prove compatibility. Removing support requires an explicit, separate decision. |
| VS-07 | Report the dependency, actual installed version, selected variant or skip reason, and priority decision once. Selection establishes eligibility only; successful application and observed behavior remain separate evidence. |
| VS-08 | A known unsupported optional contract disables only our adapter before activation. Do not disable the dependency itself or unrelated integrations. Unexpected probe errors, required dependency failures, and failures after activation retain their original failure behavior; never roll back applied hooks or retry another variant after a partial activation. |
| VS-09 | Prevent duplicate profiling, UI decorations, and callback registration within a dependency. Shared AE2 hooks still run where needed; native-hook reuse is not a second version of an addon adapter. |
| VS-10 | Runtime smoke covers only the newest implemented adapter per dependency and supported target, in English (`en_us`). Retained older variants keep unit/contract/packaging coverage without additional smoke campaigns. Follow the [shared smoke policy](../automated-ui-testing/spec.md#smoke-policy). |

This is one variant per dependency **per process**, not one addon for the whole
modpack. Physical clients may host both UI and integrated-server capabilities
of the same selected variant. Dedicated servers skip client-only integrations.
Client and server decide independently; their selections are not negotiated.

Required AE2 adapters follow the same preserve-and-select policy if an API
change inside an already supported target needs a new variant. Required
Minecraft, loader, Java, and AE2 validation remains mandatory. This work does
not add new AE2 majors or combine different Minecraft builds into one JAR.

### Version-selection acceptance criteria

| Check | Observable result | Requirements |
| --- | --- | --- |
| VS-AC-01 | The same target JAR retains old and new variants. Contract/selection/packaging checks cover both; only the newest adapter receives runtime smoke, in English, proving correct TTC/profiling behavior. | VS-01, VS-02, VS-06, VS-10 |
| VS-AC-02 | When two candidates are eligible, only the higher-priority variant is selected. An incompatible newer candidate permits a compatible older candidate before activation; no eligible candidate produces an explicit skip. | VS-02, VS-03, VS-08 |
| VS-AC-03 | Two different installed addons work together, with one selected variant each and no repeated profiling or UI additions. | VS-02, VS-09 |
| VS-AC-04 | Core-only clients and dedicated servers start on all four targets. Absent, wrong-target, and wrong-side variants cannot load optional/client classes. Required AE2 hooks are preserved. | VS-05, VS-08 |
| VS-AC-05 | Callback order, repeated plugin instances, screen/world changes, and feature setting changes never alter the selected variant or add a second registration. | VS-04, VS-05, VS-09 |
| VS-AC-06 | Logs identify the installed version and selection reason once, distinguish selection from actual execution, and preserve unexpected and post-activation failures without runtime fallback. | VS-07, VS-08 |
| VS-AC-07 | Published JARs contain every supported variant for their target, and metadata minima, optionality, packet layouts, and saved samples remain unchanged. Non-smoke tests cover retained contracts and selection boundaries; runtime evidence covers the newest adapter in English only. | VS-01 through VS-10 |

### Development clients

- Provide one compatible and one latest client for every supported Minecraft
  and loader row.
- Ordinary `run-*` scripts use a fully pinned dependency graph. Its loader,
  AE2, API, addon, library, and tool versions are the lowest full-stack set
  currently known to start and support the planned checks together. It is a
  curated compatibility baseline, not each project's oldest published file.
- `run-*-latest` scripts ignore the compatible pins and resolve the newest
  loader, AE2, optional addon, required library, and test tool available for
  the exact Minecraft and loader target. They are diagnostic clients and may
  fail when an upstream release is incompatible.
- A project may be marked incompatible with the ordinary profile only after a
  reproduced full-stack conflict. It remains in the latest profile with the
  reason recorded in the matrix.
- Keep both profiles in one version matrix. Updating a compatible pin or the
  candidate list must update PowerShell and Bash clients together.
- Keep latest client game data separate from the compatible sandbox so a
  broken dependency cannot rewrite its world, config, or managed mod set.
- A candidate installed for compatibility testing is not automatically a
  supported integration.

## Applied Journey investigation

Status: in-progress

Scope: Diagnose the reported missing native AE2 UI and complete a full smoke
against the supplied Applied Journey 0.0.1 export on Minecraft 1.21.1,
NeoForge 21.1.249.

Issue: [#534](https://github.com/cTux/ae2-crafting-time/issues/534).

Planning: [Investigation guide](applied-journey.md),
[design](technical-design.md#applied-journey-investigation),
[plan](implementation-plan.md#applied-journey-investigation).

Remaining gate: reproduce or explain the historical report, verify any
correction, and retain reviewed full-pack runtime evidence. No culprit or
source correction has been established.

This restores existing behavior without changing profiling, wire formats,
saved data or optional dependency requirements. With the matching mod on both
sides and profiling/UI options enabled, ordinary AE2 crafting must retain its
Plan/Status text, controls, estimates and completion samples. Missing history
shows Collecting Data when enabled. Intentionally disabled features remain off.

| Criterion | Required evidence |
| --- | --- |
| AJ-01 | Bind the historical and current-source runs to the archive, loaded JAR graph including bundled/nested mods, configuration and artifact hashes; record exact missing UI or a non-reproduction. |
| AJ-02 | Compare a consistent ordinary CPU craft in core-only, Data Energistics, OmniSequence plus required dependencies, both addons, and full-pack graphs. Distinguish channel/options, renderer and profiling causes; record blocked graphs explicitly. |
| AJ-03 | On the corrected or already-correct source, ordinary Plan/Status rows, tooltips, sort/details/reset controls, totals, real output and completion profiling pass the standard cases; no-history and disabled-profiling behavior remain correct. |
| AJ-04 | The exact full pack passes all applicable standard, installed integration and general status cases, with reviewed screenshots and assertions. List absent/unsupported cases; a reduced graph or startup-only result cannot pass. |
| AJ-05 | Any source change has required coverage and checks on affected targets; optional presentation mixin changes also verify the remapped production target and client startup. Evidence identifies the tested revision, final graph and cleanup. |

## Not included

- Guessing addon crafting methods from discovered `ICraftingCPU` classes.
- Replacing AE2 renderers.
- Exposing TTC through unrelated addon APIs unless players have a concrete use
  for it.
- Promising support from a mod name or download page without source or runtime
  verification.

## Acceptance criteria

An addon is marked covered only when all relevant checks pass:

- Its CPU path is classified as inherited AE2 execution, service-only
  lifecycle, or custom execution.
- Its key types preserve correct identity and normalized amounts.
- Its screen either reaches an existing concrete AE2 renderer hook or has a
  tested optional fallback.
- The addon is absent without mixin errors.
- The supported target rows that publish the addon pass CI and an in-game craft
  that starts, produces output, finishes, and shows TTC.
- Every ordinary run script resolves only its pinned compatible graph, while
  every matching `-latest` script resolves current versions without fallback.
- The four ordinary clients and four latest clients use separate managed mod
  directories, and resolution failures remain visible to the caller.
