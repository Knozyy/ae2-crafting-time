---
navigation:
  title: Chance output
  parent: statuses/index.md
  position: 8
---

# Chance output

**Label:** `Chance output` (red `⚠ Chance output` with Show emoji on).

A processing pattern promises an output that the connected machine produces
only by chance. AE2 waits for the promised amount even if the machine has
already processed every input. For example, 100 attempts at 50% chance yield
about 50 items on average, but any particular job can yield fewer or more.

The tooltip shows the verified per-operation chance and the outstanding amount.
This diagnosis currently supports a direct, single-target Mekanism Precision
Sawmill on Minecraft 1.20.1 Forge. Other machines and ambiguous patterns stay
on the regular Delayed status; missing items alone do not prove chance output.

Check the machine, output path, and inputs first. For optional random byproducts,
remove them from promised pattern outputs or supply them independently. More
inputs improve the average yield but cannot guarantee the promised amount.
Cancel and correct a job that can no longer finish.

The client **Chance output status** and server **Detect chance outputs** options
can each turn this diagnosis off. Show emoji removes the symbol but keeps the
red label. Badge background controls the row's shared rounded background.

[Previous: Waiting](waiting.md) | [Statuses](index.md) | [Next: Delayed](delayed.md)
