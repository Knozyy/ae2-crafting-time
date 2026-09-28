# No Power Status

Status: finished

Scope: NO POWER detection and display.

Implementation: [PR #207](https://github.com/cTux/ae2-crafting-time/pull/207).
Verification: [four-target prepared-client results](../automated-ui-testing/prepared-clients-2026-09-08.md).

Issue: [#121](https://github.com/cTux/ae2-crafting-time/issues/121)

## Goal

Show `NO POWER` on a crafting-status row when an active AE2 network cannot
supply the energy required to dispatch the next batch of that pattern.

## Player behavior

- Show `NO POWER` only after AE2's real simulated energy extraction returns
  less than the current pattern dispatch requires.
- Describe AE2 network energy. Do not imply that Crafting CPU byte storage or
  the external processing machine's FE/RF buffer is the problem.
- Do not label an inactive CPU `NO POWER`; inactivity can also mean a broken
  multiblock, lost channel, or network split.
- Allow the status while earlier batches of the same output remain active.
- Refresh through the existing one-second status request cycle and remove a
  resolved status within one refresh.
- Render normal-weight red `NO POWER` / `Немає енергії`.
- Add these tooltip lines:
  - `The ME network can't power the next pattern dispatch.`
  - `Increase network power generation or stored energy.`
- Give `NO PROVIDER` priority when both blockers affect patterns contributing
  to the same output row. Otherwise `NO POWER` wins over `Waiting`, `DELAYED`,
  TTC, and `No data yet`.

## Compatibility

- Support every row in `scripts/release-matrix.json`.
- Keep the logical server authoritative in singleplayer and multiplayer.
- Replace the shipped NO PROVIDER field with one shared blocker map using the
  additional compatibility bump approved below.
- Apply the same lifecycle to standard AE2 and supported AdvancedAE CPUs where
  the exact energy-check seam exists.
- Update English and Ukrainian together.

## Not included

- Diagnosing the external machine's energy, speed, or recipe state.
- Calling every inactive or offline CPU a power failure.
- Measuring which energy cell, controller, or generator is responsible.
- Automatic power changes, a config option, or a new screen.
- Craft-plan, Crafting Tree, or ME Requester badges.
- Persistence across world reloads.

## Acceptance criteria

- An active network whose available AE energy is below the next dispatch cost
  shows `NO POWER` for the affected scheduled output after the next refresh.
- Supplying enough AE energy removes the status within one refresh and allows
  the normal dispatch path to continue unchanged.
- External-machine FE/RF shortage alone never produces `NO POWER`.
- An inactive CPU alone never produces `NO POWER`.
- `NO PROVIDER` wins deterministically if both blockers contribute to one row.
- Finish, cancellation, disable, and runtime reload clear the state.
- The visible status and tooltip fit and read naturally in English and
  Ukrainian on every supported target.
- New executable branches and packet boundaries have full line and branch
  coverage.

## Approved implementation update (2026-09-03)

NO PROVIDER has since shipped a bounded missing-output set. Replace that field
with one bounded `outputId -> CraftingBlockReason` map shared by both statuses.
The user approved this additional compatibility change: Forge 8 -> 9, Fabric
stats_snapshot_v6 -> v7, and both NeoForge registrars 7 -> 8. This supersedes
the earlier single-bump assumption; persisted data stays unchanged.

Keep NO PROVIDER's exact-pattern revalidation. Track power failures by CPU and
pattern, and merge fresh positive outputs into the shared map with NO PROVIDER
priority. Clear a pattern on a successful simulated check; otherwise expire its
power observation after 20 ticks. Match AE2's `extracted < required - 0.01`
comparison, verified in every supported AE2 and AdvancedAE artifact. The hook
observes ordinal 0 (SIMULATE), returns its value unchanged, and uses the exact
pattern captured by the existing provider lookup. Never observe MODULATE.
