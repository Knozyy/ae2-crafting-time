---
navigation:
  title: Recurrent
  parent: statuses/index.md
  position: 12
---

# Recurrent

**Label:** `Recurrent: 1`

This diagnosis is off by default. Enable **Detect recurrent ingredients** in
Server → Diagnostics or set `recurrentDetection = true` in the server file.

Recurrent appears in Crafting Plan before you submit a job. Normal-weight red text on a
rounded dark badge means AE2 proved that a missing ingredient could not use a
recipe because that recipe eventually needs the same ingredient again. For
example, A may need B while B needs A. Direct self-dependencies and longer
loops work the same way.

The number is AE2's total missing amount for that row. A row that combines an
ordinary shortage with a recurrent one still shows that total; it is not a
recurrent-only count. An ordinary shortage stays Missing. One rejected circular
alternative is not proof when AE2 can use another recipe.

## What to check

1. Inspect the affected ingredient and hover it for the explanation.
2. Follow its patterns until you find the loop.
3. Supply a usable seed, or choose or correct a recipe that breaks the loop.
4. Calculate the plan again.

Recalculation replaces the diagnosis; old plans do not carry it to another
menu or network. A successful plan with a usable seed or alternative has no
Recurrent row, but having the requested output in storage does not guarantee
AE2 can use it as a seed. The mod explains the loop but does not repair it.
Timing samples, TTC colors, and running-job order do not control this label.
Crafting Tree and ME Requester use separate screens.

In an A -> B -> A recipe loop, AE2 may report a missing row as Recurrent.
Another item in the loop does not have to become a missing row.

[Previous: TTC estimate](estimated.md) | [Statuses](index.md) |
[Time estimates](../features/time-estimates.md)
