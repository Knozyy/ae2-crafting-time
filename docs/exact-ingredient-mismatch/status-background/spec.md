# Stored variant status background

Status: ready-to-implement

Scope: The missing badge background on the existing Stored variant status.

Issue: [#565](https://github.com/cTux/ae2-crafting-time/issues/565).

Planning: [Technical design](technical-design.md) and [reviewed implementation plan](implementation-plan.md).

Verification: Documentation review only. The fix and its runtime checks remain pending.

## Behavior

Give `Stored variant` the same rounded background as other AE2 Crafting Time
status labels in Crafting Plan rows. Its foreground follows the neutral row
color rule, including on tinted missing-item rows.

Use the existing client Badge background setting, color, and opacity. On draws
the configured badge, including intentionally transparent opacity zero. Off
hides the background while keeping the label visible. Preserve normal-weight
text and the existing Text shadow option.

Fit both `Stored variant` and `Інший варіант у сховищі` within the row using the
existing width-limited badge layout. The label and background must not overlap
the item icon, Missing amount, or a coexisting Recurrent label. Native row tint,
quantities, tooltips, sorting, diagnosis updates, and Start behavior retain the
[original feature semantics](../spec.md).

This is a presentation correction across Forge/Fabric 1.20.1, NeoForge 1.21.1,
and NeoForge 26.1.2. It adds no option, packet, saved data, or new diagnosis.
The development skill requires the shared background for every mod status label
in Plan/Status rows; tooltip explanations and native AE2 text are outside that rule.

## Acceptance criteria

| ID | Required result |
| --- | --- |
| B1 | With backgrounds On and nonzero opacity, Stored variant uses the same configured rounded background as neighboring mod status labels. |
| B2 | Off removes only the badge fill; On restores the saved appearance. Opacity zero remains transparent. Text and shadow settings keep their existing behavior. |
| B3 | English and Ukrainian labels fit the row, including narrow layouts and coexistence with Missing/Recurrent, without icon or text overlap. |
| B4 | Removing the diagnosis removes its label and badge together. Sorting keeps them on the correct row; quantities, tooltips, native tint, and Start behavior do not change. |
| B5 | Shared regression checks cover badge membership and width limiting. Both renderer families build, all four targets receive focused English UI evidence, and Ukrainian text receives static/component validation under the repository smoke policy. |

The implementation is complete only after the plan's checks pass and their
tested revision and retained evidence are linked here. Merging these docs does
not finish issue #565.
