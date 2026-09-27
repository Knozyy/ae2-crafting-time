---
navigation:
  title: No data yet
  parent: statuses/index.md
  position: 9
---

# No data yet

**Label:** `No data yet`

This appears in Crafting Plan or Crafting Status when the server has no usable
retained timing sample for that output. Blocking and active-delay states take
priority where they apply.

## What to check

1. Complete a representative craft while profiling is enabled.
2. Let the recipe produce output normally so throughput can be measured.
3. Check that history was not cleared, expired, or separated by network identity.

The label clears after a completed production interval creates usable history.
Samples are server- and world-owned, retained for a limited history window, and
keyed to the ME network and output. The first sample can still be low confidence.

![Crafting Plan before timing history exists](images/crafting-plan-no-data.png)

*These plan rows have no retained timing sample yet.*

[Previous: Delayed](delayed.md) | [Statuses](index.md) |
[Next: TTC estimate](estimated.md) | [Learning throughput](../features/learning-throughput.md)
