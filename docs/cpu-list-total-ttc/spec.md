# Total TTC in the Crafting CPU list

Status: in-progress

Scope: Original CPU-card total TTC implementation and acceptance.

Implementation: [PR #381](https://github.com/cTux/ae2-crafting-time/pull/381) is merged and its CI passed.
Verification: full completion evidence for this plan is not recorded in that PR.
The [CPU sorting extension](../ttc-sorting/cpu-list/spec.md) records later work separately.

The [active-order TTC sorting extension](../ttc-sorting/cpu-list/spec.md) adds
full-list collection and ordering in TTC modes. Its explicit ordering and
adaptive freshness rules supersede the original visible-only refresh boundary;
this document remains authoritative for badge appearance and estimate meaning.

Tracking issue: [#324](https://github.com/cTux/ae2-crafting-time/issues/324).

## Goal

Compare the remaining time of running crafts without selecting every CPU.
Show each CPU's total time to craft (TTC) inside the top-right corner of its
card in the left Crafting CPUs list, as requested in the reference screenshot.

## Player behavior

- Show the CPU's own remaining-job total, including for unselected CPUs. Use
  the same estimate and formatting as the Crafting Status title: `~39s`,
  `~1:01`, or `~1:02:15`. The Status title now shows that TTC as its title text;
  CPU cards keep their badges.
- Preserve the current critical-path estimate and its partial-data behavior.
  This is neither elapsed time nor the final output's individual row estimate.
- Idle CPUs, unknown estimates, and zero totals have no badge, matching the
  title's existing omission rules. A stalled job keeps whatever estimate the
  title would show; do not introduce warning labels in CPU cards.
- Use the title's text color, shadow, and dark badge background for CPU cards. Align the badge
  to the card's top-right interior. Keep the card size, order, selection tint,
  click area, job icon, amount, bottom progress bar, and scrollbar behavior.
- Reserve space for TTC on the name line. Shorten an overlapping name with an
  ellipsis; the existing CPU tooltip still shows the full name. Fit long time
  values without dropping digits or spilling outside the card.
- Refresh visible and selected CPUs first without selecting them, then visit
  every other busy CPU through bounded round-robin requests. The selected card
  and title use the same frame snapshot. Hide expired values; TTC-mode expiry
  adapts to list size as specified by the active-order extension.
- Scrolling, CPU removal, job completion/cancellation/replacement, changing
  networks, closing the screen, and reconnecting must not transfer a cached
  total to a different CPU or job. Removed or idle rows stop showing the badge
  as soon as the client observes that state; otherwise the next successful
  refresh replaces the estimate.

## Compatibility and boundaries

Cover all four release rows: Forge 1.20.1, Fabric 1.20.1, NeoForge 1.21.1,
and NeoForge 26.1.2. Integrated and dedicated servers use the same server-owned
estimate path. Native CPU cards supplied by supported addons inherit the badge
when the existing profiler knows their CPU scope. Unknown addon CPU scopes show
no estimate. Separate addon screens are outside this change.

Reuse existing TTC text and the CPU tooltip; no new translated wording or
configuration is needed. Preserve English and Ukrainian support through static
resource and layout checks, including long Cyrillic CPU names. Runtime smoke
uses English (`en_us`) only, following the
[shared smoke policy](../automated-ui-testing/spec.md#smoke-policy). This feature
adds no save data and does not reconstruct estimates for jobs restored without
a retained runtime dependency graph.

## Non-goals

Do not change TTC math or learning, change item-row statuses,
add interactions/tooltips/settings, change crafting execution, create a network
monitor, or publish a release. The implementation issue remains open until the
feature PR is merged and its completion evidence is verified.

## Acceptance criteria

### Verification runtime

Focused debugging may start at phase 2 from a validated immutable phase-1
continuation/world/artifact bundle. That result is diagnostic only; acceptance
still requires a complete two-phase run at the committed head. Stalls fail with
bound callback/checkpoint evidence, and the report records phase durations and
launch count. Changed verification selects this scenario directly for the four
required targets without unrelated standard cases or optional-addon graphs. A
resume bundle is compatible only when its world marker records the same base or
catalogue mode and the same managed JAR name/hash catalogue as the selected
bundle; mismatch fails before Minecraft launches.
Current-PID callbacks must remain live throughout startup and execution. The
absolute startup deadline covers loader, world, and fixture preparation; the
60-second no-checkpoint deadline begins only in the active scenario phase.

| ID | Observable result |
| --- | --- |
| A1 | Three busy CPUs with different totals show their own badges without being selected first. |
| A2 | Selected card and title show identical formatted totals from the same snapshot; the title uses the total as its text. |
| A3 | Idle, unknown, zero, and expired values produce no badge; partial/stalled estimates retain title semantics. |
| A4 | Badges fit at the top right on selected and unselected cards; long names/times, English/Ukrainian text, and GUI scales do not overlap other content. Static resource/layout checks preserve both languages; runtime UI evidence uses `en_us` only. |
| A5 | Scrolling, list reordering/removal, same-output job replacement, completion/cancellation, menu reopen, network changes, and reconnects never reuse another context's total. |
| A6 | Requests only read CPUs from the requesting player's current menu/network; malformed, excessive, or stale requests cannot leak other grids or create unbounded work. |
| A7 | All four targets build and pass the focused regression tests; integrated and dedicated-server UI evidence demonstrates A1-A5. |

See the [technical design](technical-design.md) and
[implementation plan](implementation-plan.md).
