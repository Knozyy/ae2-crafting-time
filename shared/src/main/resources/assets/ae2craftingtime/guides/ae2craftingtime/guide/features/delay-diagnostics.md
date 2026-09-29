---
navigation:
  title: Delay diagnostics
  parent: features/index.md
  position: 4
---

# Delay diagnostics

Crafting Status replaces TTC with red **DELAYED** only when pending work has
learned history and no accepted output has arrived for both the fixed idle
threshold and at least twice its normal production-window duration. Any partial
output restarts the timer.

Hover the row to see its estimate, idle time, active and scheduled amounts, and
recent parallel-slot use. Advice follows the evidence: free recent slots can
suggest more Pattern Providers or machines, saturated slots can suggest
Crafting Co-Processors, and the active machine may need attention. When a
clickable provider hint appears, click it to highlight the relevant provider.
A warning points to where work stopped; it does not prove a machine is broken.

When a red warning has a valid provider target, its output icon appears on a
blinking red plate there automatically. This includes DELAYED, NO SPACE,
NO PROVIDER, NO POWER, NO CHANNEL, NO TARGET, INPUT BLOCKED and LOCKED. A
warning without a valid provider has no world marker. Click a provider link in
chat to see rainbow borders and a red sky beam for the same 15 seconds. A
Crafting Status row double-click shows only the rainbow borders. The beam can
be seen through blocks above an underground provider. Leaving the world clears
temporary highlights; current warning plates return after the server checks the craft again.

![Delayed output diagnostics tooltip](images/crafting-status-ttc-bottleneck-diagnostics.png)

*A deliberately unfuelled furnace demonstrates the delayed row and its bottleneck hints.*

[Previous: Job accuracy](job-accuracy.md) | [Features](index.md) |
[Next: Sorting and colors](sorting-and-colors.md) | [Statuses](../statuses/index.md)
