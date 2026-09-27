# Compact crafting-status amounts

Status: merged in PR #524. Crafting Plan follow-up: #525.

Scope: One quantity line in the native AE2 crafting-status cell.

Issue: [#438](https://github.com/cTux/ae2-crafting-time/issues/438)

Planning: [Reviewed implementation plan](implementation-plan.md),
[technical design](technical-design.md), and [research](research.md).

Implementation and verification: [PR #524](https://github.com/cTux/ae2-crafting-time/pull/524).
Four-target native item/fluid and installed addon-key evidence, connected-client
Options checks, font and tooltip review, option relaunch, builds, and CI are
recorded in the
[research follow-up](research.md#implementation-verification-2026-09-25).

## Behavior

Replace the separate Available, Crafting, and Scheduled quantity lines with one
compact line above the existing TTC/status line. The order is always available,
crafting, scheduled. These are separate amounts, not a total or a progress fraction.

| Available | Crafting | Scheduled | Visible quantity line |
| --- | --- | --- | --- |
| 4 | 10 | 200 | `4/10/200` |
| 0 | 10 | 200 | `-/10/200` |
| 10 | 0 | 200 | `10/-/200` |
| 4 | 10 | 0 | `4/10/-` |
| 10 | 0 | 0 | `A10` |
| 0 | 10 | 0 | `C10` |
| 0 | 0 | 10 | `S10` |
| 0 | 0 | 0 | No quantity line |

With two or three positive amounts, keep all three positions and use `-` for
each absent amount. With one positive amount, use its `A`, `C`, or `S` prefix.
Use no spaces around slashes or after prefixes. Non-positive amounts are absent,
matching AE2's existing visibility rule. Do not add the amounts together.

Each value uses AE2's compact formatting for that resource, including its units
and abbreviation rules. Preserve fractional fluids and addon resource semantics;
never treat fluid storage units as item counts. Tooltips retain AE2's full labeled
amounts. Zero categories remain omitted there, as in AE2 today.

## Appearance and controls

- Use the same rounded background, configured color and opacity, padding, and
  shadow as TTC. Keep the quantity line separate from TTC/status.
- Copy the displayed TTC/status line's text color, including fast/slow colors
  and red warnings. Waiting and collecting use AE2's row color without a badge,
  or `TOTAL` (initially `#E0E0E0`) with a badge. When no TTC/status line is
  present, compact amounts follow that same neutral rule.
  Keep quantity text regular weight, even beside a bold warning.
- Fit the complete quantity line into the existing text area without wrapping,
  dropping a category, or overlapping the icon or adjacent cell. Retain AE2's
  compact values and horizontally shrink only when the measured text exceeds
  the available width. Full amounts stay readable in the tooltip.
- Show **Compact crafting amounts** under **Options > Client > Displays**,
  default **off**. Off restores AE2's original quantity lines and removes the
  compact-format legend, without hiding TTC/status. The existing status-row TTC
  switch remains independent: compact amounts can stay on with row TTC off.
  Quantity formatting also remains available when server profiling is off.
  **Done** saves and applies the choice without a restart; **Cancel** discards
  edits. Reset section/all restores on, and the saved choice survives relaunch.
- Keep `A`, `C`, `S`, `/`, and `-` stable in English and Ukrainian. Add a localized
  tooltip legend identifying the order and prefixes; retain native localized
  labels and full values. The legend appears even for available-only rows and
  when detailed profiling tooltips are disabled.
- Preserve cell colors, icon, hover area, sorting, selection, scrolling, details,
  reset, provider-locate actions, TTC calculation, and warning precedence.

## Compatibility and boundaries

Cover all four release targets: 1.20.1 Forge, 1.20.1 Fabric, 1.21.1 NeoForge,
and 26.1.2 NeoForge. Native AE2 item/fluid keys and installed addon keys use the
same display contract. Only the local option is persisted. No server, network
protocol, profiling, world-save persistence,
craft-confirm, Crafting Tree, or ME Requester behavior changes are included.

Preserve other mods' description lines. If the original AE2 quantity components
cannot be identified safely, leave the native quantities intact and continue the
existing TTC behavior. Do not erase unknown text or claim arbitrary mixin
compatibility before testing the actual mod combination.

## Acceptance criteria

| ID | Observable result |
| --- | --- |
| Q1 | All eight rows in the table above match exactly; live transitions update without stale prefixes or placeholders. |
| Q2 | Large item values, fractional fluids, and supported addon keys retain AE2 formatting and full tooltip values without arithmetic overflow. |
| Q3 | Quantity background and RGB match the current TTC/status styling and live configuration; available-only fallback is defined above. |
| Q4 | The complete quantity badge fits the text area with default and wide resource-pack fonts at supported GUI scales; TTC and icon remain readable. |
| Q5 | Native full-value tooltips, translated legend, controls, sort, selection, hover and warning precedence remain correct. |
| Q6 | The independent compact-amount option restores native quantities when off and survives saving/relaunch; all four compact/TTC on-off combinations, Cancel and resets work. Server profiling off does not disable compact amounts. Foreign lines survive and unrecognized native lines take the documented fallback. |
| Q7 | Tests, builds and reviewed English UI smoke evidence cover all four targets; Ukrainian text and format parity pass non-smoke checks. |

The [plan](implementation-plan.md) maps each criterion to its verification gate.
