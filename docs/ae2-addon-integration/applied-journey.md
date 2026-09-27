# Missing UI in Applied Journey

Tracking: [#534](https://github.com/cTux/ae2-crafting-time/issues/534).

This is an investigation plan, not a reproduced compatibility diagnosis. The
report says Crafting Time UI and estimates disappear on ordinary AE2 CPUs in
Applied Journey on NeoForge 1.21.1. Neither Data Energistics nor OmniSequence:
Transfinite has been established as the cause.

## Reproduction boundary

The supplied export identifies Applied Journey 0.0.1, Minecraft 1.21.1,
NeoForge 21.1.249, and 200 manifest entries. Its relevant CurseForge IDs are:

| Mod | Project | File |
| --- | ---: | ---: |
| AE2 Crafting Time | 1591476 | 8955379 |
| AE2 | 223794 | 7027323 |
| Data Energistics | 1565514 | 8841396 |
| OmniSequence: Transfinite | 1624558 | 8941493 |

The export contains no runtime logs or crash reports. Obtain the pack through
an authorized source; the unpublished archive is not part of this repository.
Keep private server details and account information out of retained evidence.

The supplied archive's SHA-256 is
`015d222a3577cfd8f746812db1fc3e734d530fc893d7111d89a21605802bb344`.
It also bundles `overrides/mods/ae2_extended-1.0-SNAPSHOT.jar`, outside the
200 manifest downloads. Its metadata identifies `ae2_extended` version
`1.0-SNAPSHOT`; its required mixins include `MixinCraftingCPUCluster` and
storage/network hooks. Include it in the exact graph. This observation does
not establish a conflict.

The issue's [acceptance criteria](spec.md#applied-journey-investigation),
[design](technical-design.md#applied-journey-investigation), and
[execution plan](implementation-plan.md#applied-journey-investigation) also
cover the requested full pack smoke.

## Investigation sequence

1. Reproduce in the exact pack on a normal AE2 CPU. Record the screen, expected
   and missing elements, actual loaded versions, configuration, logs, and image.
   Distinguish absent row text from missing history or disabled profiling.
2. Compare AE2 plus Crafting Time alone, then Data Energistics, then
   OmniSequence with its required dependencies, then both addons, then the pack.
   Keep the recipe, CPU, options, and screen consistent between comparisons.
3. Inspect the shared Crafting Plan/Status mixins and `TtcText` rendering path,
   client options, server snapshots, and `IntegrationCatalog`/`IntegrationSelection`
   diagnostics. Use the first differing runtime evidence to narrow the fault;
   update history alone does not prove an addon conflict.
   Record the negotiated Crafting Time channel and client/server options:
   an unavailable channel or disabled server profiling intentionally hides UI.
4. Fix the confirmed shared seam, or retain a minimal reproducer and link an
   external follow-up when the defect belongs upstream. Do not add an adapter
   merely because an addon appears in the pack.

## Full pack smoke

Install the supplied export through Prism into its **Codex** group, then stage
a disposable guest-local copy in CodexVM. Preserve the original exported mod
graph and configuration for the historical baseline; record the one Crafting
Time artifact replacement for current-source verification. Inventory enabled
JAR metadata, nested mods, file hashes and overrides before selecting cases.

Use `prepare-ui-smoke-suite.ps1` and its `standard-ae2` expansion, plus every
applicable installed integration and general status case from the NeoForge
suite. Record each selected case and every absent or unsupported case with its
reason. Do not add mods just to satisfy the prepared-client suite. Run one
shared-world suite per installed graph, restoring fixture, player, profiler
and client cache state between cases. Review screenshots as well as assertions;
startup alone cannot satisfy this request.

Keep controlled addon comparisons separate from the unchanged full graph.
Record any temporary diagnostic mod/config change and restore the exact graph
for the final full run. A failing dependency graph or VM prerequisite is a
reported blocker, not permission to call a reduced graph a full-pack pass.

## Completion evidence

Record the tested commit, dependency graph, configuration, logs, and reviewed
screenshots for the failing baseline and corrected case. Ordinary CPU estimates
and controls must work with and without the implicated addons on NeoForge
1.21.1. Mark unrun graphs explicitly; a successful launch is not a UI check.

[#459](https://github.com/cTux/ae2-crafting-time/issues/459) covers qualification
of a newer OmniSequence graph; [#518](https://github.com/cTux/ae2-crafting-time/issues/518)
covers Data Energistics metadata. Neither proves this regression fixed.
Update the [integration specification](spec.md) and dependency guidance only
after the behavior is verified. This documentation change does not close #534.
