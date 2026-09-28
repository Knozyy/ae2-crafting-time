# Recurrent ingredients in the crafting plan

Status: in-progress

Scope: Recurrence detection baseline and its verification.

Implementation: [PR #404](https://github.com/cTux/ae2-crafting-time/pull/404), [PR #410](https://github.com/cTux/ae2-crafting-time/pull/410).
Verification: merged implementation and passing CI are recorded; full baseline
acceptance evidence is not established by those records. Styling #496 is separate below.

Original feature: [#320](https://github.com/cTux/ae2-crafting-time/issues/320).

## Goal

When crafting A needs B and crafting B needs A, the crafting plan should explain
the loop instead of presenting the affected ingredient as an ordinary shortage.
Give that missing row the status `Recurrent`, in red text.

## Player behavior

- Replace the affected row's `Missing` label with `Recurrent`. Keep its existing
  missing quantity and unit, for example `Recurrent: 1`.
- Apply the same label to the missing-amount line in the row tooltip. Add the
  explanation: `This ingredient is missing because its crafting recipe depends on itself, directly or through other recipes.`
- Mark only missing rows for which the current calculation proves that recursion
  prevented a recipe from being used. Do not mark every item in a circular
  recipe catalogue, or every ancestor of a blocked ingredient.
- Cover direct self-dependencies, A -> B -> A, and longer loops. A row represents
  the actual missing ingredient reported by AE2; a two-recipe loop need not
  produce two missing rows.
- Keep ordinary shortages as `Missing`. A rejected circular alternative alone
  is not enough when an eligible alternative remains: show the shortage that
  AE2 actually reaches along that alternative.
- A successful plan, including one made possible by seed ingredients or a valid
  alternative, has no Recurrent rows. Preserve AE2's own treatment of requested
  output already in storage; this feature does not change what counts as a seed.
- If one row combines ordinary and recurrent shortages, use Recurrent when any
  positive part has proven recurrence. Keep AE2's total missing amount; do not
  claim it is an exact recurrent-only quantity or invent split quantities.
- Replace the status when the plan changes. Closing the menu, reconnecting, or
  opening another network must not carry the previous plan's status forward.
- Keep stored/craft quantities, TTC lines, TTC colors, row order, and the Start
  button's existing behavior. A red label must remain red in every TTC sort mode.

## Compatibility and text

Support Forge and Fabric 1.20.1, NeoForge 1.21.1, and NeoForge 26.1.2. Use the
same logical-server result in singleplayer and multiplayer. The feature must
work without learned timing samples.

English uses `Recurrent`, not `Recursive`, in Minecraft red.
Ukrainian uses `Циклічне`; its tooltip is
`Цього інгредієнта бракує, бо рецепт його виготовлення залежить від нього самого — безпосередньо або через інші рецепти.`
Text and the tooltip explain the status without relying on color alone.

### Planned warning style (#496)

Status: ready-to-implement

Scope: shared red warning presentation ([#496](https://github.com/cTux/ae2-crafting-time/issues/496)).

Planning: [PR #498](https://github.com/cTux/ae2-crafting-time/pull/498); merging the plan does not implement the style change.

Use the existing red warning presentation, such as `NO PROVIDER`: normal-weight Minecraft
red (`0xFF5555`) text with a shadow and the same compact, rounded dark background
behind the complete `Recurrent: <amount>` line. Match its padding and opacity.
The background belongs to the status label, not the whole table row; retain
AE2's native missing-row tint and hover treatment.

Use the same normal-weight red text for the tooltip's `Recurrent: <amount>` line. Keep
the normal tooltip panel background and ordinary explanation text. Both locales
must fit the existing cells with their formatted amounts and units. Apply this
style without requiring learned timing samples and in every TTC sort mode.

The shipped style is currently normal-weight red without a compact badge. R8
replaces that presentation when implemented. Detection, quantities, wording,
sorting, and TTC colors retain the rules above. Recurrent remains a pre-craft
diagnosis; this visual change does not trigger provider highlights or sky beams.

Native AE2 plan tables opened from supported terminals inherit the feature.
Crafting Tree's separate tree, ME Requester, and running crafting-status rows
are outside this change. An unsupported custom planner or a peer without the
diagnostic capability keeps ordinary Missing text; absence of evidence never
becomes a positive diagnosis.

## Non-goals

Do not repair recipes, resolve loops, modify AE2's calculation or submission,
add a graph viewer, change TTC estimation, reorder missing rows, add a setting,
persist diagnoses, or release mod JARs as part of this feature repair.
Missing-first sorting is tracked separately in
[#318](https://github.com/cTux/ae2-crafting-time/issues/318) and is already present;
preserve its missing-amount comparator.

## Acceptance criteria

| ID | Observable result |
| --- | --- |
| R1 | A -> B -> A without a usable seed shows a red Recurrent label and unchanged quantity on the actual missing row, with the explanation on hover. |
| R2 | Direct self-dependency and a three-recipe loop receive the same diagnosis. |
| R3 | Ordinary shortages and failures on an eligible nonrecursive alternative keep Missing; a successful seeded or alternative plan has no Recurrent label. |
| R4 | Mixed rows follow the aggregation rule; unrelated missing rows stay Missing. Resource variants remain distinct. |
| R5 | Replan, menu replacement, another network, cancellation, disconnect, and late packets cannot show an old diagnosis. Dedicated single-client sessions and recipient/menu/revision boundary tests verify isolation. |
| R6 | Both locales, all four targets, all TTC sort modes, no-sample plans, and quantities/units remain correct. Long text stays within the existing table layout. |
| R7 | Craftability, calculation results, timing data, saved data, and optional separate screens are unchanged. Missing or rejected diagnostic data leaves the native plan usable. |
| R8 (planned, #496) | Recurrent uses the existing normal-weight red warning text, shadow, and compact dark badge in the plan row, with matching normal-weight red tooltip text. Amounts, units, explanation, native row tint, tooltip panel, and other statuses retain their existing presentation. Both locales and all four targets preserve layout, all TTC sort modes, and no-sample behavior. Clearing the diagnosis or disabling the mod removes the Recurrent text and badge together. |

Verify both locales through translation/component checks. Runtime smoke uses
English only under the [current smoke policy](../automated-ui-testing/spec.md#smoke-policy);
Ukrainian remains a supported product locale. The bounded test-driver extension
in the implementation plan is part of verification, not a new player feature.
Every Minecraft smoke client runs sequentially, including connected checks.
Use one 8 GiB client at a time and one connected client per target. The dedicated
session verifies the logical-server path, recipient binding, replanning and
reconnect; boundary tests cover different recipient identities. Simultaneous
player testing and claims of simultaneous-player proof are outside this gate.

## Native-plan regression #408

The reported setup uses Minecraft 1.21.1 NeoForge, AE2 19.2.17,
AdvancedAE 1.6.12's Quantum Computer, Wireless Comprehensive Work Terminal
1.3.9, and AE2: Crafting Tree 1.21.1-1.1.1. With processing patterns for A from B,
B from A, and C from A, requesting 100 C can leave the native shortage row at
`Missing: 100`. The expected result is `Recurrent: 100` in red, with the same
quantity and tooltip explanation. This restores R1, R2, R5 and R7; it does not
introduce a new kind of recurrence or change which ingredient AE2 reports missing.

Native-plan diagnostics must survive another addon's summary enrichment,
including a cancellable return callback. Crafting Tree's separate tree remains
outside this feature even when the addon is installed. The observed addon
combination is a regression target, not proof that the CPU or terminal caused
the failure, and not a new dependency minimum.

Compare requests for 1 and 100 on the same fixture. Verify the final calculation
keys, server summary flags, native client summary, diagnostic chunk and rendered
row together. A successful base-only smoke does not cover this addon boundary.
The focused investigation and required checks are in the implementation plan;
all existing R1-R7 behavior remains required.

See the [technical design](technical-design.md) and
[implementation plan](implementation-plan.md).
