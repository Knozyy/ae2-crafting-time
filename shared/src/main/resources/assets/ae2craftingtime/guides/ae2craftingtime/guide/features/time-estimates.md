---
navigation:
  title: Time estimates
  parent: features/index.md
  position: 0
---

# Time estimates

With **Show emoji** on, each displayed time starts with an aqua ⏱. It appears
on row and total estimates, CPU cards, tooltips, samples, delayed durations,
accuracy timings, chat, and supported addon displays. Approximate and uncertain
marks remain after the symbol: `~12s?` appears as `⏱ ~12s?`. Rates and counts
stay plain. The symbol uses the game's available font; its appearance can vary
with fonts, scale, and resource packs.

Crafting Plan shows TTC beside every row that has learned timing data. The row
time covers the full **To Craft** amount, not one pattern operation. The total
below the CPU details adds the known plan rows; if some rows have no data, it is
only the known part of the job.

With **Compact crafting amounts** on, the plan shows available and to-craft
amounts on one line: `4/10`. With only one amount, `A4` means available and
`C10` means to craft. Missing and
recurrent warnings stay separate. Hover for AE2's full labeled amounts.

Crafting Status uses the same row estimates while a job runs. Its total follows
the longest remaining dependency path, so parallel branches are not added as if
they ran one after another. For example, two independent ten-second branches
can still finish in about ten seconds, while two dependent steps take about
twenty. Estimates are not deadlines: machine speed, other work, and server load
can change the result.

With the same option on, Crafting Status puts available, crafting, and scheduled
amounts on one line, in that order: `4/10/200`. A missing amount is `-`; when only one amount is
present, its `A`, `C`, or `S` prefix identifies it. Hover for AE2's full labeled
amounts. **Compact crafting amounts** starts off under **Options > Client >
Displays**. Turn it on for the compact line in both windows. This setting works
independently of row TTC.

![Crafting Plan with row and total TTC](images/crafting-plan-estimate.png)

*Seeded test values show two learned recipe estimates and their known total.*

[Features](index.md) | [Next: Learning throughput](learning-throughput.md) |
[Make your first estimate](../getting-started.md)
