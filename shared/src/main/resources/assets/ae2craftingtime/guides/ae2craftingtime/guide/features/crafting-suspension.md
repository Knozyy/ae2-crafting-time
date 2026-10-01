---
navigation:
  title: Crafting suspension
  parent: features/index.md
  position: 9
---

# Crafting suspension

On Minecraft 1.20.1 Forge, open a standard AE2 crafting CPU or select one in a
terminal's Crafting Status screen. Press **Suspend** beside **Cancel** to stop
that job from sending more patterns. Press **Resume** to continue the same job.
You can still cancel it while it's suspended.

Work already inside machines keeps running, and its outputs can return to the
CPU. They can even finish the job while it's suspended. The CPU keeps its
reserved ingredients and completed work. Suspending a large job can give
smaller jobs on other CPUs a turn at shared providers once those machines clear;
it does not free a machine immediately or release the CPU.

The server remembers suspension across a world restart. A suspended job shows
**Suspended** instead of a finish estimate, and its intentional pause does not
produce delay warnings. On resume, a new delay interval begins.

The world-owned Server Options → General switch **Allow crafting suspension**
(`craftingSuspension = true`) starts on. Turning it off resumes every loaded
suspended CPU on its next logic tick; unloaded CPUs resume when they load.
This switch works even if profiling is off. Only standard AE2 CPUs support this
Forge backport; addon replacement CPUs keep their normal behavior.

![Crafting status while a job is running](images/crafting-status-running.png)

*The running job shows a time estimate; a suspended job shows Suspended instead.*

[Previous: Configuration](configuration.md) | [Features](index.md) |
[Next: AE2: Crafting Tree](crafting-tree.md)
