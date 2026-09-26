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

Preserve the explicit colors of collecting data, waiting, delayed, no-space,
block reasons, recurrent, and stored-variant labels. Collecting data retains its
configured Collecting color because it identifies unavailable estimates.
Compact amounts keep the color of their associated special status. With an
ordinary estimate or no status, they use the matrix above. Decide this from
status meaning, never by comparing RGB values: custom colors can be identical.

Keep text, amounts, order, layout, scaling, interactions, and shadow behavior.
The two appearance switches are client preferences, independent of server
profiling. Compact amounts still follow the matrix when profiling is disabled.
Toggling either switch must preserve saved colors and opacity, including after
save/reopen. No new preference, packet, or saved-data format is needed.

## Boundaries and acceptance

Apply this policy only to Plan and Status rows. Totals, CPU cards, tooltips,
chat, Crafting Tree, ME Requester, and other addon surfaces retain their existing
behavior. Cover Forge/Fabric 1.20.1, NeoForge 1.21.1, and NeoForge 26.1.2.

| ID | Required result |
| --- | --- |
| A1 | All four switch combinations follow the matrix for ordinary TTC and compact amounts in both screens, including compact amounts without TTC and with profiling disabled. |
| A2 | Special statuses and their compact amount colors retain precedence even when their configured RGB equals Total; text and other styles remain intact. |
| A3 | Native/foreign lines and excluded surfaces retain their behavior; normal and width-scaled row drawing use the incoming native foreground without layout or shadow changes. |
| A4 | Off/on and save/reopen preserve configured colors and opacity; no protocol or persistence migration is introduced. |
| A5 | Both renderer families build across all four targets; changed shared decisions have 100% line/branch coverage, and both GuideME locales and the GitHub wiki explain the behavior. |
| A6 | Compare native and mod text together in reviewed screenshots in both screens on all four targets, across supported GUI scales and light, dark, and tinted rows; include estimates, compact amounts, warnings, and the switch matrix. |

Record actual checks against the tested commit. Passing automated checks does
not establish A6. Keep any omitted visual work explicit in the implementation
report and scope status; the current run must not launch smoke tests.
