---
navigation:
  title: TTC estimate
  parent: statuses/index.md
  position: 11
---

# TTC estimate

**Label:** `~12s`

A time such as `~12s` means the server has usable timing history. In Crafting
Plan it covers the row's full **To Craft** amount. In Crafting Status it estimates
remaining active and scheduled work; the total follows the longest dependency
path instead of adding parallel branches.

## What to check

1. Hover the time to see sample count, spread, and recent activity.
2. Treat a low-confidence first sample as an early approximation.
3. Compare completed-job accuracy before relying on repeated estimates.

The estimate disappears when history is cleared or no longer retained, and it
updates as new intervals are learned. It is not a deadline: machine upgrades,
shared load, recipe changes, and server performance can change the result.

![Running Crafting Status with a TTC estimate](images/crafting-status-running.png)

*The visible TTC uses learned throughput for the remaining recipe amount.*

[Previous: No data yet](no-data-yet.md) | [Next: Recurrent](recurrent.md) |
[Statuses](index.md) |
[Time estimates](../features/time-estimates.md)
