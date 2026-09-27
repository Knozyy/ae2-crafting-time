# Initial Crafting Status ETA Technical Design

Lifecycle: see the [scope status and evidence](spec.md).

Follow-up [#581](https://github.com/cTux/ae2-crafting-time/issues/581)
supersedes the title composition below for active jobs while Status total is
on. The same `setTextContent` hooks now replace AE2's elapsed title with a
single `TTC: ⏱ <estimate>` component when a total exists. They use the native
dark foreground for text and symbol and no longer draw a separate title badge.
Without a total, they return `getGuiDisplayName` without AE2's elapsed suffix.
Both paths append the red can't-store warning when present. Null or completed
status and a disabled Status total option retain AE2's supplied title.

Tracks [issue #425](https://github.com/cTux/ae2-crafting-time/issues/425), following
the initial correction in [issue #350](https://github.com/cTux/ae2-crafting-time/issues/350).

## Root cause

AE2 15.4.10's `CraftingCPUScreen.updateBeforeRender()` estimates remaining time
as `elapsed / max(1, start - remaining) * remaining`. Before measurable progress,
`start` and `remaining` are both `Integer.MAX_VALUE`, so even a short elapsed time
produces an unsupported large ETA.

The #350 correction in the mc1201 title hook checks
`instanceof CraftingStatusScreen`. It handles the terminal subclass but skips the
direct `CraftingCPUScreen`, which uses the same title path. #425 records
`Crafting Status - 2189204:11:29` on that direct screen with all 64 operations
still scheduled. The later recovery failure does not invalidate this retained
observation, but it also does not establish a passing scenario.

### Independent 26.1.2 evidence

The retained #405 capture from campaign `20260915T072552836Z-be3eed71`, artifact
`26.1.2-neoforge/primary/run/evidence/no-channel-status/no-channel-en-us.png`,
shows `Crafting Status - 617553:43:17` on the direct `CraftingCPUScreen` before
progress. Its scenario result was PASS, but the old assertions did not inspect
the title. This is a defect observation, not verification of the fix.

Inspection of AE2 `26.1.10-beta` bytecode independently confirms the same ETA
formula, `getGuiDisplayName` base title, `isCantStoreItems` warning, and
`setTextContent` hook anchor. The inspected JAR's SHA256 is
`8412A2208E56989B90E783E0A594C91A955AB8785D7D020FA4D8F342346110F2`.
The mc2612 replacement lacks the zero-progress guard, so #425 includes this
adapter and replaces #350's earlier exclusion.

## Change

Reuse `TimeEstimate.hasMeasuredProgress`: progress exists only when
`remainingItems < startItems`. Change the existing
`ae2craftingtime$appendStatusTotalTtc` hook in the
[mc1201 mixin](../../shared/src/mc1201/java/com/ctux/ae2craftingtime/mc1201/mixin/CraftingCPUScreenMixin.java)
and its [mc2612 counterpart](../../shared/src/mc2612/java/com/ctux/ae2craftingtime/mc1201/mixin/CraftingCPUScreenMixin.java).
Apply the guard whenever status exists, without restricting it to the terminal
subclass. Rebuild the zero-progress base using
`getGuiDisplayName(GuiText.CraftingStatus.text())`, then append AE2's can't-store
warning with its red style when needed. Do not mutate the display-name component.

The mc1201 change serves 1.20.1 Forge/Fabric and 1.21.1 NeoForge. Apply the same
behavior through the existing mc2612 hook for 26.1.2 NeoForge. Keep null status
and measured-progress titles unchanged. Run the existing learned-TTC badge
logic after title correction, retaining its data, activity, and width rules.

## Failure boundaries

Null status keeps the current title. Completed progress keeps AE2's title.
Equal or reversed progress counters hide only the unsupported native ETA; they
do not suppress the separate TTC badge or status warnings.

## Verification boundary

`TimeEstimateTest` executes the equal, reversed, and progressed-counter cases.
Add a focused ASM boundary test for both compiled title hooks. Its claims are
structural only: no terminal-only type gate, calls to `hasMeasuredProgress` and
`getGuiDisplayName`, and the warning and component-copy operations. It does not
execute either screen or prove component/name preservation, warning color, or
TTC layout. Treat null-status handling as source/structural evidence unless a
recorded test actually exercises it.

Review the changed smoke plan, then run its selected checks and
`no-channel-status` on all four targets. Record direct and terminal screen
evidence and a custom-name zero-progress case on each target; use manual
prepared-client captures if named scenarios omit them. Current-source client
evidence must verify live screen behavior and component/name preservation.
Inspect `standard-status-controls` and `no-space-status` for measured-progress
ETA, red warning style, and learned TTC layout. Record each run's source and
artifacts separately from retained defect evidence; neither an old capture nor
successful structural assertions prove the corrected title renders properly.
