# Native Plan and Status row colors

Status: ready-to-implement

Scope: Ordinary row foreground when both decoration switches are off.

Issue: [#533](https://github.com/cTux/ae2-crafting-time/issues/533)

Planning: [Technical design](technical-design.md) and [reviewed plan](implementation-plan.md).

Verification limit: The user explicitly excluded smoke testing for the current
2026-09-26 implementation run. Automated checks can establish color selection
and compatibility; visual acceptance below remains unverified by this run.
This does not permanently remove that acceptance requirement.

## Behavior

Ordinary numeric TTC and compact amounts in Crafting Plan and Crafting Status
must inherit the same foreground as AE2's nearby Available, Crafting, and
Scheduled labels when **Badge background** and **Fast-to-slow colors** are off.
Use the active AE2 screen palette, including on tinted rows. Do not substitute
fixed white or the configured Total color.

| Badge background | Fast-to-slow colors | Ordinary row foreground |
| --- | --- | --- |
| Off | Off | Native AE2 amount-label foreground |
| Off | On | Existing configured color behavior |
| On | Off | Existing configured fallback behavior |
| On | On | Existing configured color behavior |

Waiting, collecting data, and Stored variant use one neutral foreground with
ordinary TTC: AE2's native row foreground without a badge, or the configured
Total foreground (initially `#E0E0E0`) with a badge. Their symbols match the
text. Red warnings, including delayed, no-space, blocked, and recurrent labels,
keep their warning colors in both modes. Fast-to-slow TTC coloring stays active
when enabled, regardless of badge mode. Compact amounts follow the associated
row color; without a status they use the neutral foreground. Tooltip colors and
text remain unchanged. The old Waiting color stays readable in saved configs
but is no longer offered as a row-color setting.
The Crafting Plan total uses AE2's dark text color without a badge and the
configured light Total color with a badge.

Keep text, amounts, order, layout, scaling, interactions, and shadow behavior.
The two appearance switches are client preferences, independent of server
profiling. Compact amounts still follow the matrix when profiling is disabled.
Toggling either switch must preserve saved colors and opacity, including after
save/reopen. No new preference, packet, or saved-data format is needed.

## Boundaries and acceptance

Apply the row policy only to Plan and Status rows. CPU cards, tooltips,
chat, Crafting Tree, ME Requester, and other addon surfaces retain their existing
behavior. Cover Forge/Fabric 1.20.1, NeoForge 1.21.1, and NeoForge 26.1.2.

| ID | Required result |
| --- | --- |
| A1 | All four switch combinations follow the matrix for ordinary TTC and compact amounts in both screens, including compact amounts without TTC and with profiling disabled. |
| A2 | Red warnings and fast-to-slow estimates retain their colors. Waiting, collecting, Stored variant, and status-free compact amounts use the neutral foreground in both badge modes; symbols and compact amounts match their row text. |
| A3 | Native/foreign lines and excluded surfaces retain their behavior; normal and width-scaled row drawing use the incoming native foreground without layout or shadow changes. |
| A4 | Off/on and save/reopen preserve configured colors and opacity; the legacy Waiting color remains loadable without a protocol or persistence migration. Tooltip colors are unchanged. |
| A5 | Both renderer families build across all four targets; changed shared decisions have 100% line/branch coverage, and both GuideME locales and the GitHub wiki explain the behavior. |
| A6 | Compare native and mod text together in reviewed screenshots in both screens on all four targets, across supported GUI scales and light, dark, and tinted rows; include estimates, compact amounts, warnings, and the switch matrix. |

Record actual checks against the tested commit. Passing automated checks does
not establish A6. Keep any omitted visual work explicit in the implementation
report and scope status.
