# No Space Status

Status: finished

Scope: NO SPACE detection and display.

Implementation: [PR #203](https://github.com/cTux/ae2-crafting-time/pull/203).
Verification: [four-target prepared-client results](../automated-ui-testing/prepared-clients-2026-09-08.md).

Issue: [#122](https://github.com/cTux/ae2-crafting-time/issues/122)

## Goal

Show `NO SPACE` on stored-only crafting-status rows when AE2 says the Crafting
CPU cannot return its remaining contents to writable ME storage.

## Player behavior

- Reuse AE2's synchronized `can't store items` state. Do not infer storage
  failure from drive fill percentages or a stationary craft.
- Show the status only when the row has stored items and no active or scheduled
  work. Keep AE2's existing red title warning unchanged.
- Render normal-weight red `NO SPACE` / `Немає місця`.
- Add these tooltip lines:
  - `The ME network can't accept this item.`
  - `Free space in storage cells or add more storage.`
- Remove the status as soon as AE2 clears its `can't store items` state or the
  row no longer contains stored items.

## Compatibility

- Support every row in `scripts/release-matrix.json`.
- Read only AE2 state already synchronized to the client; add no packet or
  persisted state.
- Keep behavior limited to the standard AE2 crafting-status screen.
- Update English and Ukrainian together.

## Not included

- Claiming that a specific storage cell, drive, chest, drawer, partition, or
  type limit caused the rejection.
- Calling an external processing machine's inventory full.
- Automatically moving, deleting, or voiding items.
- Replacing AE2's existing title warning.
- A config option, new screen, or integration badge.

## Acceptance criteria

- A stored-only row shows `NO SPACE` when AE2 reports that the CPU cannot return
  its contents to network storage.
- The row tooltip explains the verified condition and suggests freeing cell
  space or adding storage.
- Adding writable capacity or freeing space removes the status without
  reopening the screen.
- Active or scheduled rows never show `NO SPACE` from this feature.
- A full external machine does not trigger the status.
- No packet, protocol, or saved-data version changes.
- The visible status and tooltip fit and read naturally in English and
  Ukrainian on every supported target.
- New executable branches have full line and branch coverage.
