---
navigation:
  title: Waiting
  parent: statuses/index.md
  position: 7
---

# Waiting

**Label:** `Waiting`

This appears in Crafting Status when an output has scheduled work but its first
pattern has not dispatched. It is the normal fallback after the mod has no
specific provider, power, lock, input, or target reason to show.

## What to check

1. Check earlier ingredients and dependencies in the job.
2. Check whether providers and machines are busy with other work.
3. Wait for an upstream batch to finish before changing the network.

The label clears when the first batch dispatches, a more specific blocker is
observed, or the scheduled work ends. It is not proof that a machine is broken,
and it has no learned timing threshold.

![Waiting row behind an active ingredient](images/crafting-status-waiting.png)

*The output is scheduled behind work that has not produced its required input yet.*

[Previous: No target](no-target.md) | [Statuses](index.md) |
[Next: Chance output](chance-output.md) | [Delay diagnostics](../features/delay-diagnostics.md)
