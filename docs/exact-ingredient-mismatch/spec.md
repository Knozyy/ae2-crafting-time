# Exact ingredient mismatch diagnostics

Status: in-progress

Scope: Live stored-variant diagnostics, including connected verification.

Implementation: [PR #493](https://github.com/cTux/ae2-crafting-time/pull/493) is merged.
Verification: [completion report and remaining checks](https://github.com/cTux/ae2-crafting-time/issues/327#issuecomment-5771676732).
Connected Fabric 1.20.1 and both NeoForge target checks remain outstanding.

Tracking issue: [#327](https://github.com/cTux/ae2-crafting-time/issues/327).

Follow-up: [Stored variant status background](status-background/spec.md) tracks
the presentation correction in [#565](https://github.com/cTux/ae2-crafting-time/issues/565).

Source report: [Problem with autocrafting](https://www.reddit.com/r/allthemods/comments/1w5bqj5/problem_with_autocrafting/).

## Goal

When AE2 reports an item as missing while the ME network stores the same base
item with different saved data, make that near-match visible in the crafting
plan and explain the safe next step. This helps players find patterns encoded
with a different NBT tag or data-component patch without changing AE2's plan.

## Player behavior

- Keep AE2's existing `Missing` amount and add a neutral `Stored variant` line to
  that row when the current ME network contains a positive amount of the same
  item under a different exact AE2 item key, with no positive stored exact key.
- Add these tooltip lines:
  - `The ME network stores this item with different saved data.`
  - `Re-encode the pattern using the item the network actually produces or stores.`
- Diagnose only positive missing amounts for item keys. The stored near-match
  must have the same registered item and a different exact key. NBT on 1.20.1
  and data components on newer targets remain part of exact identity.
- Treat the line as evidence of a stored near-match, not proof of the particular
  field or mod behavior that created it. Do not name ownership, energy, security,
  damage, or another component unless AE2 exposes that fact directly.
- Update the line while the same confirmation screen stays open. Adding a
  near-match shows it; removing the last near-match or adding any positive
  amount of the exact key clears it. Removing that exact key restores it when
  a near-match remains. An exact key suppresses the warning even when stored
  near-matches coexist or the exact amount cannot satisfy the whole plan.
- Updates require no Replan, reopening, or other player action. They arrive at
  the next normal menu synchronization after AE2 detects the storage change,
  followed by normal network delivery. AE2 detects storage changes on server
  ticks; this does not promise zero wall-clock latency.
- Clear the line when the plan is replaced, the menu closes, the player
  reconnects, or another network/menu becomes active. A plan retained across a
  network change stays undiagnosed until a new native plan is generated.
- Preserve stored/craft/missing quantities, Start-button behavior, TTC text,
  TTC colors, and every sort order. The warning follows its row after sorting.
- If the recurrent-ingredient feature from #320 also diagnoses the row, keep
  both independent facts: its `Recurrent` replacement and this additional
  `Stored variant` line.

## Compatibility and text

Support Forge and Fabric 1.20.1, NeoForge 1.21.1, and NeoForge 26.1.2. Use the
logical server's current storage in singleplayer and multiplayer. The
feature works without learned timing samples and with substitutions enabled or
disabled; it reports AE2's resulting exact missing key rather than re-evaluating
substitution rules.

English uses `Stored variant`. Ukrainian uses `Інший варіант у сховищі`.
Ukrainian tooltip text is:

- `У ME-мережі є цей предмет з іншими збереженими даними.`
- `Перекодуйте шаблон, використавши предмет, який мережа справді виробляє або зберігає.`

The wording explains the condition without relying on color. Native AE2 craft
confirmation tables opened from supported terminals inherit the feature.
Crafting Tree's separate tree, ME Requester, running crafting-status rows, and
unsupported custom planners are outside this change.

## Non-goals

Do not repair or rewrite patterns, toggle substitutions, normalize or ignore
item data, change AE2 extraction/crafting, identify the differing field, list
every stored variant, add a comparison screen, diagnose fluids or chemicals,
persist diagnoses, add a setting, or release mod JARs as part of this task.
A near-match outside the active ME network is not reported. Updating a warning
does not recalculate the native plan or its Missing quantity.

## Acceptance criteria

| ID | Observable result |
| --- | --- |
| V1 | A positive missing item row plus a stored same-item/different-key stack, with no positive exact key, shows `Stored variant` and both tooltip instructions. Adding/removing stored keys updates it in the same open plan without player action; the native missing quantity and Start behavior are unchanged. |
| V2 | Any positive exact stored key, including alongside near-matches, an equal display name with a different registered item, or a same-item near-match with zero available amount does not produce a false warning. Removing the exact key restores the warning if a positive near-match remains. |
| V3 | Ordinary shortages, fluids, chemicals, successful plans, and plans without a positive missing amount retain their native presentation. |
| V4 | Multiple stored variants produce one line on the matching row; unrelated missing rows remain unchanged and exact item variants stay distinct. |
| V5 | Replan, menu replacement, sorting, cancellation, disconnect, another player, another network, and delayed, duplicate, or reordered live updates cannot carry the warning to a stale or different row. Storage observation ends with its menu/plan. |
| V6 | All four targets, all TTC sort modes, and no-sample plans render correctly; #320 recurrence coexists without either diagnosis erasing the other. English UI smoke and static English/Ukrainian resource/component checks follow the shared smoke policy. |
| V7 | Planning, submission, quantities, pattern data, storage contents, timing samples, saved data, and optional separate screens are unchanged. Missing or rejected diagnostic data leaves the native plan usable. |

See the [technical design](technical-design.md) and
[implementation plan](implementation-plan.md).
