# Initial Crafting Status ETA

Status: in-progress

Scope: Initial ETA correction on standard and direct CPU screens.

Implementation: [PR #373](https://github.com/cTux/ae2-crafting-time/pull/373), [PR #432](https://github.com/cTux/ae2-crafting-time/pull/432).
Verification: [direct-screen implementation and passing CI](https://github.com/cTux/ae2-crafting-time/issues/425#issuecomment-5693073870).
The plan's full runtime acceptance is not established by that CI record.

Tracking: [#350](https://github.com/cTux/ae2-crafting-time/issues/350),
[#425](https://github.com/cTux/ae2-crafting-time/issues/425), and
[#581](https://github.com/cTux/ae2-crafting-time/issues/581).

## Expected behavior

On both the directly opened `CraftingCPUScreen` and the terminal's
`CraftingStatusScreen`, an active job with a learned total shows only
`TTC: ⏱ <estimate>` as its title. The TTC text and symbol use the native dark
title color. Do not show AE2's elapsed estimate or a separate TTC badge.
When a total is unavailable, show the normal display name, including a custom
CPU name, without an elapsed estimate. Preserve the red can't-store warning.
When Status total is off, or the job is complete, keep AE2's title behavior.

## Acceptance

- A newly submitted job with equal start and remaining counts has no native ETA
  suffix on either screen. Defensive reversed counters also hide that suffix.
- A running job with a learned total replaces the native title and elapsed
  suffix with the TTC title, including after measurable progress.
- A running job without a learned total keeps its display name and omits the
  native elapsed suffix.
- A missing status keeps AE2's supplied title. Native and custom display names
  remain intact when the initial ETA is hidden.
- The red can't-store warning is preserved when the initial ETA is hidden.
- The behavior must cover 1.20.1 Forge, 1.20.1 Fabric, and 1.21.1 NeoForge,
  with independent verification on 26.1.2 NeoForge. The separate
  26.1.2 adapter is in scope; the earlier #350 exclusion no longer applies.
- A focused ASM test checks both hooks structurally: no terminal-only type
  gate, TTC replacement, display-name fallback, and warning/copy operations.
- Current-source `no-channel-status` captures on all four targets show a normal
  zero-progress title. Record both direct and terminal screen evidence and a
  custom-name zero-progress case on each target. Add manual prepared-client
  captures when named scenarios omit a case.
- Current-source client evidence verifies screen behavior, component/name
  preservation, warning color, and learned TTC layout. Inspect
  `standard-status-controls` and `no-space-status` for measured-progress ETA and
  the red warning. Null-status handling remains a source/structural check unless
  a recorded test actually exercises it.

## Evidence status

#425 retains a direct-screen defect from the #405 Forge `no-channel-status`
run. That run later failed a recovery assertion: its screenshot proves the
observed title defect, not a scenario pass. Retained observations and source
inspection establish scope; only new, source-bound checks can verify this fix.

A separate retained #405 capture on 26.1.2 shows the same direct-screen defect.
That scenario passed its existing checks, which did not inspect the title.
The [technical design](technical-design.md#independent-2612-evidence) records
this evidence; it is not verification of the upcoming correction.

## Out of scope

This does not change AE2's elapsed-time tracker, packets, job progress, addon
CPUs, or the separate AE2 Crafting Time total TTC estimate.
