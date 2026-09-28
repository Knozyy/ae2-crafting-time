# Screenshot gallery

The original crops came from UI smoke testing on **Project Infinity 0.0.52.0**
(Minecraft 1.20.1, Forge 47.4.20, AE2 15.4.10). Red status crops were refreshed
from the prepared 1.20.1 Forge client at commit `55009dc5432fc710130bddd27a2b42839a82e600`
with AE2 15.4.10 and Forge 47.4.23. [sources.json](sources.json) records each
source capture's SHA-256, archived run, and physical crop. JPEGs focus on the
relevant rows or complete tooltip. Click an image to inspect its native size.
The book uses larger exports of the same crops to keep its text readable.

## Crafting plan

Before any timing history exists:

![Crafting plan with no timing data](crafting-plan-no-data.jpg)

With timing history available:

![Two-stage crafting plan with recipe and total estimates](crafting-plan-estimate.jpg)

The older Recurrent crop came from the real native Crafting Plan
`recurrent-plan` fixture. Its retained source is archive
`20260922T201206763Z-d068777e`, path
`1.20.1-forge/primary/run/evidence/recurrent-plan/recurrent-plan-tooltip.png`,
commit `9afdd6b0228c5c77eef2bae6ecb149cb799fde8d`, SHA-256
`ea72093d72a5eb66cccadfe2ea657da53f0a78509b43374e451fa5420ffd6c70`, and
physical crop `[740, 110, 1400, 360]`, exported at 1600 pixels wide. The
scenario passed its Recurrent row, badge, tooltip, quantity, and layout
checks. That historical image is no longer embedded in the book or Wiki because
red status text now has normal weight. A current Recurrent capture is blocked by
the separate [#602](https://github.com/cTux/ae2-crafting-time/issues/602) fixture failure.
The wider historical campaign stopped on the unrelated CrazyAE2Addons CPU
fixture failure tracked in [#507](https://github.com/cTux/ae2-crafting-time/issues/507);
it is not treated as a full-campaign pass.

When only part of the plan has history, the total covers the known work:

![Crafting plan with one known and one unknown recipe](crafting-plan-partial-estimate.jpg)

## Live crafting status

Chance output after a controlled 100 promised / 60 returned / 40 outstanding
Mekanism sawmill job, shown with Badge background off as in the reported view:

![Normal-weight red Chance output row](crafting-status-chance-output-row.jpg)

Hovering it shows the verified 50% recipe chance and the outstanding amount:

![Chance output diagnosis and recovery advice](crafting-status-chance-output.jpg)

Active recipes, scheduled work, and the remaining-time estimate:

![Running crafting job](crafting-status-running.jpg)

Scheduled work waits for its ingredient to finish:

![Active ingredient and waiting output](crafting-status-waiting.jpg)

The delayed state after a processing machine stops producing output:

![Delayed crafting job](crafting-status-delayed.jpg)

Hovering the delayed recipe shows its timing, recent activity, and advice:

![Delayed recipe diagnostics](crafting-status-ttc-bottleneck-diagnostics.jpg)

When the CPU can't return its stored items to the ME network, the row explains
the problem and suggests freeing or adding storage:

![NO SPACE warning and storage advice](crafting-status-no-space.jpg)

When scheduled work loses its connected Pattern Provider or encoded pattern,
the row shows NO PROVIDER and explains how to resume the job:

![NO PROVIDER warning and provider recovery advice](crafting-status-no-provider.jpg)

When Pattern Providers for a scheduled recipe lack a channel:

![NO CHANNEL warning and channel-route advice](crafting-status-no-channel.jpg)

When the ME network can't supply enough energy to dispatch the next pattern,
the row shows NO POWER and suggests increasing generation or stored energy:

![NO POWER warning and ME network energy advice](crafting-status-no-power.jpg)

When no enabled provider side has a compatible destination, the scheduled row
shows NO TARGET:

![NO TARGET warning and destination advice](crafting-status-no-target.jpg)

When the destination exists but refuses this pattern's inputs, the row shows
INPUT BLOCKED:

![INPUT BLOCKED warning and input advice](crafting-status-input-blocked.jpg)

When an active Pattern Provider crafting lock prevents dispatch, the row shows
LOCKED:

![LOCKED warning and crafting-lock advice](crafting-status-locked.jpg)

## Timing details

The first sample is marked as low confidence:

![Low-confidence timing tooltip](ttc-low-confidence.jpg)

The sample-history tooltip also shows throughput:

![Production samples and throughput](ttc-production-sample-details.jpg)

Ctrl-click expands timing details in chat. Ctrl-Alt-click clears that item's
history and confirms the reset:

![Expanded TTC details in chat](ttc-details-chat.jpg)

A completed job with partial timing coverage appears in chat as one tracked job
with no fully covered plan yet:

![Completed-job accuracy with partial coverage](ttc-job-accuracy-partial.jpg)

After a fully covered job completes, chat shows one of one jobs fully covered,
prediction error, and the actual-to-estimated duration ratio:

![Completed-job accuracy details with a fully covered result](ttc-job-accuracy.jpg)

## Sorting

The sort button cycles through these three modes:

![AE2 order](ttc-sort-ae2.jpg)

![Longest TTC first](ttc-sort-longest.jpg)

![Shortest TTC first](ttc-sort-shortest.jpg)

## Other windows

The crafting tree has its own estimate and tooltip:

![Crafting tree estimate](crafting-tree-estimate.jpg)

![Crafting tree tooltip](crafting-tree-tooltip.jpg)

ME Requester with a diamond request and TTC below the amount fields:

![ME Requester diamond request with row and total TTC](me-requester-estimate.jpg)

## Capture notes

The source images are unmodified PNG evidence from the exact pack. The initial
36-case smoke run passed its semantic checks; all 87 screenshots were manually
reviewed. Automatic visual comparison still requires reference images, so this
is not a claim of a golden-image pass. Source paths, hashes and crop rectangles
are recorded in [sources.json](sources.json).

The standard stone and smooth-stone examples use two real furnaces in a marked,
disposable fixture world. Seeded timing samples make UI states repeatable; they
are demonstrations, not performance measurements. The lifecycle accuracy
examples use actual submitted and completed jobs.

The furnace-item, crafting-tree, sorting, and ME Requester examples also use
seeded history. ME Requester shows a diamond request. Storage, energy and
provider-dispatch examples exercise their named failure and recovery conditions.
Crops retain the complete relevant tooltip, including this pack's additional
item text. The unrelated AE2 title-timer defect is tracked in
[issue 350](https://github.com/cTux/ae2-crafting-time/issues/350).

To refresh these images, use the repository's
[refresh-guide-images skill](../../.codex/skills/refresh-guide-images/SKILL.md)
and `scripts/export-guide-image.ps1`. Keep raw evidence in the smoke archive;
export gallery crops at native size and book copies at
`min(1600, cropWidth * 4)` pixels wide. Inspect the actual book at 1920x1080
before accepting a refresh. See the
[GuideME sizing notes](../guideme-guide/technical-design.md#screenshot-exports).

The five no-data, partial-plan, and chat contexts were captured in the focused
`20260908T120437Z-gallery-02` lifecycle run on the same pack (12 checks passed,
13 raw captures). Per-image run and commit fields in `sources.json` override
the initial run fields. Chat crops omit the account-name line and retain the
throughput and job-coverage details without altering the captured text.

The 24 gallery JPEGs total 1,191,066 bytes (previous PNGs: 1,270,916 bytes).
The 44 enlarged book PNGs total 2,671,574 bytes (previous JPEGs: 4,269,248
bytes). Book exports use lossless PNG for GuideME compatibility; gallery exports
keep JPEG quality 90.

The NO CHANNEL book image comes from the scenario-PASS capture in the real
1.20.1 Forge BaseOnly run for issue 405; the outer adapter-contract check failed
after the scenario completed. The retained client archive is
`20260914T195816885Z-083b2040`, source path
`1.20.1-forge/compatible/no-channel-status/evidence/no-channel-en-us.png`, and it has
SHA-256 `9f69ed7591faf4204c3a45899929c353ee3faaee9b67baa0b15ee5d2304494c`;
the physical crop is `[500, 185, 980, 320]`, exported at 1600 pixels wide.
Both locale copies are identical, SHA-256
`60573bdfe90bd0705e6c59d52e9e23ed78d43b08021c9fbbbfb5693e2c3a9dbf`
and 61,207 bytes each; the source is 249,255 bytes.
