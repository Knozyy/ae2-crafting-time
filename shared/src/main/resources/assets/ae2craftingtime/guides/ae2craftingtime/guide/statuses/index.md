---
navigation:
  title: "Chapter 3: Statuses"
  parent: index.md
  icon: minecraft:comparator
  position: 2
---

# Chapter 3: Statuses

Crafting Status labels explain what the selected CPU is doing or what currently
blocks it. Start at the top and use the first label you see:

With **Show emoji** on, the full labels keep their usual colors and gain these
symbols: red ⚠ for NO SPACE, NO PROVIDER, NO CHANNEL, LOCKED, INPUT BLOCKED,
and NO TARGET; red ⚡ for NO POWER; yellow ⌛ for Waiting; gold ⚠ for DELAYED;
and aqua ℹ for No data yet. Numeric estimates use aqua ⏱. Crafting Plan uses
red ↻ for Recurrent and gold ⚠ for Stored variant. Details use ℹ, successful
stats resets use green ✓, locate hints and throughput use aqua →, and the
suggestions heading uses gold ⚙. Turn Show emoji off for text-only labels.
Gold ⚠ also marks low confidence and an expired provider link. The confidence
wording appears only when the estimate is unreliable.

1. [NO SPACE](no-space.md)
2. [NO PROVIDER](no-provider.md)
3. [NO POWER](no-power.md)
4. [NO CHANNEL](no-channel.md)
5. [LOCKED](locked.md)
6. [INPUT BLOCKED](input-blocked.md)
7. [NO TARGET](no-target.md)
8. [Waiting](waiting.md)
9. [DELAYED](delayed.md)
10. [No data yet](no-data-yet.md)
11. [A TTC estimate such as ~12s](estimated.md)

Crafting Plan has one separate diagnosis before you submit a job:
[Recurrent](recurrent.md). It reports a proven recipe self-dependency, not a
running-job priority shared with the labels above.

Crafting Plan can also show a gold **Stored variant** label when the network has
the missing item with different saved data. It keeps AE2's Missing amount and
doesn't change whether you can start the job. Like the other mod status labels,
it uses your Badge background color and opacity. Turning Badge background off
hides the rounded fill while keeping the text visible.

The order matters. `NO SPACE` applies to stored output. The other blocking
labels apply to scheduled batches; a row can still have an active batch that
finishes while its next batch is blocked. The mod reports observed conditions,
not a diagnosis of a particular machine.

[Introduction](../index.md) | [Chapter 1](../getting-started.md) |
[Chapter 2: Features](../features/index.md) | [Delay diagnostics](../features/delay-diagnostics.md)
