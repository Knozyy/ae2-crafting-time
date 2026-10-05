---
navigation:
  title: Configuration
  parent: features/index.md
  position: 8
---

# Configuration

Server settings belong to the world and live in
`<world>/serverconfig/ae2craftingtime-server.toml`. Starting a world creates this
file if it's missing. In singleplayer, `<world>` is that save under `saves`; on
a dedicated server, it's the world selected by `level-name`, so the path is
`<level-name>/serverconfig/ae2craftingtime-server.toml` under the server
directory. This is the active server file; the top-level `config` directory is
not where you edit these settings.

Client settings belong to one client and live in
`config/ae2craftingtime-client.toml` inside that client's game directory. Client
startup does not create the file; saving Client Options with **Done** does.

Use **Options** to edit settings when possible. Client changes apply and save on
that client when you press **Done**. Server changes are submitted to the world
and apply when the server accepts them; you need permission to edit server
options. Each player has their own client settings, while server settings are
shared by everyone in that world.

You can also edit the files by hand. Stop the client before editing its client
file, and stop the world or server before editing its server file. External file
edits are read when the client or world/server starts again. Existing files are
not rewritten just to add comments.

`config/ae2craftingtime-common.toml` is a legacy migration source, not the active
server settings file. If a world's server file does not exist at startup, known
legacy server values are copied into the new world file and other settings use
their defaults. The common file stays in place. Once the world file exists, it
takes precedence; changing the common file does not override it.

**Detect recurrent ingredients** in Server → Diagnostics starts off. Turn it on
to mark proven recipe loops as Recurrent in Crafting Plan, or set
`recurrentDetection = true` in the server file. Existing files that explicitly
enable it keep that choice; Reset returns it to off.

**Allow crafting suspension** in Server → General starts off for Minecraft
1.20.1 Forge. Enable it to show Suspend/Resume. Existing files keep their explicit
choice; Reset turns it off. Turning it off resumes paused standard AE2 CPUs on their next
logic tick, including after an unloaded CPU loads. It works independently of
`enabled` profiling. See [Crafting suspension](crafting-suspension.md).

**Chance output status** in Client → Warnings and **Detect chance outputs** in
Server → Diagnostics are experimental and off by default. Turn on both to use
the diagnosis. It currently supports only the Mekanism Precision Sawmill on
Minecraft 1.20.1 Forge, with a direct Pattern Provider and a matching live
recipe. Other machines still show Delayed. Support may expand as more machine
recipes can be verified. Existing saved choices stay as set; Reset turns both
options off. See [Chance output](../statuses/chance-output.md) for details.

**Show emoji** in Client → Appearance starts on (`showEmoji = true`). It adds
colored symbols to TTC times, statuses, and local chat. Turn it off for text-only
labels and times. Saving with Done updates visible chat too; reset restores On.
Each player controls their own display. Values, colors, badges, and actions stay
the same.

In Client → Appearance, **Text shadow** controls shadows on text drawn by AE2
Crafting Time. It's off by default. Turn it on for shadowed text; AE2's own text
keeps its original shadow setting. You can also set `textShadow = true` in the
client file.

**Badge background** in the same Client → Appearance section is off by default.
Turn it on to show rounded backgrounds behind AE2 Crafting Time's badges
while keeping their text visible. It doesn't hide AE2's own window, row, or
tooltip backgrounds. Your badge color and opacity stay saved, so turning it
on restores them. You can also set `badgeBackground = true` in the client
file.

**Fast-to-slow colors** and **Compact crafting amounts** in Client → Displays
also start off. Turn them on for the TTC color scale or shortened row amounts.
Saved choices stay as you set them.

If **Badge background** and **Fast-to-slow colors** are both off, ordinary time
estimates and compact amounts in Crafting Plan and Crafting Status use the same
text color as AE2's nearby amounts. This follows the current screen colors, even
on tinted rows. Collecting, waiting, delayed, and blocked labels keep their
warning colors, and compact amounts beside them use that warning color too.
Your chosen colors and badge opacity stay saved when you switch either option
off. This row behavior also applies to compact amounts when profiling is off.

- `enabled` turns profiling and server-owned stats on or off.
- `showInTree` controls Crafting Tree badges, tooltips, spacing, and clicks on
  supported pre-26 targets.
- `showChatMessages` controls Ctrl-click details and reset notices. It does not
  block the reset itself.
- `maxSamples` keeps 1–100 recent throughput and runtime accuracy samples per
  output; the default is 10.
- `outlierMultiplier` accepts 1.0–1000.0 and defaults to 4.0.

For example, set `maxSamples = 20` to retain a longer recent history. Accepted
server changes made with **Done** apply to the running profiler immediately.
Changes made directly in the file take effect the next time that world/server
starts. Invalid fields keep their defaults; values outside the allowed ranges
are rejected.

![Crafting Tree with TTC display enabled](images/crafting-tree-tooltip.png)

*The seeded tooltip shows the Crafting Tree display controlled by `showInTree`.*

[Previous: Saved history](saved-history.md) | [Features](index.md) |
[Next: Crafting suspension](crafting-suspension.md) | [Confidence](confidence.md)
