# Badge Background Switch

Status: ready-to-implement

Scope: An independent client switch for backgrounds drawn by AE2 Crafting Time.

Issue: [#532](https://github.com/cTux/ae2-crafting-time/issues/532)

Planning: [Technical design](technical-design.md) and [reviewed plan](implementation-plan.md).

Verification prerequisite: CodexVM starts, but its guest tools did not become
available during investigation. Verify guest runtimes and prepared native loader
manifests before approving the runtime campaign. No Minecraft checks have run.

## Current default

The On defaults below and in the companion design/plan describe the original
proposal. The current [configuration contract](../spec.md#feature-switches)
supersedes them: a fresh model, missing/invalid value, and Reset All use Off.
Reset section currently enables the switch; correcting that action is separate
from the [tooltip correction](../spec.md#accurate-switch-default-tooltips-607).
This note does not claim completion of the original visual verification scope.

## Behavior

Add **Badge background** to Client / Appearance with key `badgeBackground` and
default **On**. Off skips the mod's rounded background rectangles behind Crafting
Plan and Crafting Status row badges, totals, CPU-card totals, and supported
Crafting Tree and ME Requester labels. Text, its shadow, position, scaling,
foreground color, and interactions retain their existing behavior.

Keep the saved Badge opacity and Badge background color unchanged when switching
Off and back On. On draws with those saved values, including opacity zero. The
switch is independent of Text shadow, fast-to-slow TTC colors, compact amounts,
and server profiling. Native AE2 window, row, and tooltip backgrounds are outside
its scope.

Use the existing options session: Done saves and applies, Cancel discards draft
changes, and Reset section or Reset all restores On in the draft. Resets retain
their existing behavior of also restoring the relevant color and opacity defaults;
only toggling must preserve custom values. Save the switch in
`config/ae2craftingtime-client.toml`. A missing or malformed value defaults On.

## Compatibility

Cover all four release targets: 1.20.1 Forge, 1.20.1 Fabric, 1.21.1 NeoForge,
and 26.1.2 NeoForge. Optional surfaces retain their existing availability. Add
matching English and Ukrainian labels and explain the option in both GuideME
locales and the GitHub wiki. No server setting, packet change, or world-save
migration is needed.

## Acceptance criteria

| ID | Required result |
| --- | --- |
| A1 | Client / Appearance shows Badge background, defaults On, and keeps pagination and color/opacity input validation working. |
| A2 | Off skips every mod badge background, including normal and scaled Plan/Status rows; text and native AE2 backgrounds remain visible and unchanged. |
| A3 | Off/On preserves custom background color and opacity; shadow, TTC colors, compact amounts, and profiling remain independent. |
| A4 | Save/load and relaunch retain Off; missing/malformed values use On; copy, Cancel, section reset, and full reset follow the existing session behavior. |
| A5 | Both renderer families build across all four targets; shared changed behavior has 100% line/branch coverage, and English/Ukrainian keys and player documentation agree. |
| A6 | Both states are visually verified on every supported target, including readability on tinted Plan/Status rows and restoration of the selected background appearance. |

Completion requires actual automated and visual evidence for these criteria.
An unavailable prepared client is a verification blocker, not a visual pass.
