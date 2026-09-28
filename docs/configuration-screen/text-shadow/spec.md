# Mod Text Shadow

Status: ready-to-implement

Scope: A client option for shadows on text drawn by AE2 Crafting Time.

Issue: [#531](https://github.com/cTux/ae2-crafting-time/issues/531)

Planning: [Technical design](technical-design.md) and [reviewed plan](implementation-plan.md).

Verification limit: the user excluded Minecraft smoke from this run. Automated checks and builds are planned; readability at supported GUI scales remains unverified and the issue's visual criterion remains outstanding.

## Current default

The On defaults below and in the companion design/plan describe the original
proposal. The current [configuration contract](../spec.md#feature-switches)
supersedes them: a fresh model, missing/invalid value, and Reset All use Off.
Reset section currently enables the switch; correcting that action is separate
from the [tooltip correction](../spec.md#accurate-switch-default-tooltips-607).
This note does not claim completion of the original visual verification scope.

## Behavior

Add **Text shadow** to Client / Appearance with stable key `textShadow` and default **On**. Off removes the shadow from mod-drawn Crafting Plan and Crafting Status row text, compact amounts, status badges, totals, CPU-card totals, and supported Crafting Tree and ME Requester TTC labels. On restores their existing shadowed appearance, including horizontally scaled badges.

Use the existing edit session: Done saves and applies the choice, Cancel discards it, Reset section and Reset all restore On in the draft. Persist it in `ae2craftingtime-client.toml`; missing or invalid values use On. The choice is independent of colors, badge opacity, compact amounts, and server profiling.

AE2's own table text keeps its incoming shadow flag. Do not change AE2 settings or globally intercept Minecraft font rendering. Native widget, tooltip, and chat rendering remains owned by its existing renderer. Only the shadow flag changes: text, position, width, scaling, foreground colors, and badge backgrounds retain their existing behavior.

## Compatibility

Support 1.20.1 Forge, 1.20.1 Fabric, 1.21.1 NeoForge, and 26.1.2 NeoForge through their existing rendering adapters. Optional surfaces remain optional and retain their current target availability. Add matching English and Ukrainian labels. This setting needs no server option or protocol change.

## Acceptance criteria

| ID | Required result |
| --- | --- |
| A1 | Client / Appearance exposes Text shadow, defaults On, and retains working pagination and color/opacity validation. |
| A2 | Off/On controls every listed mod-drawn surface, including scaled badges and compact amounts; native text preserves both incoming shadow values. |
| A3 | Save/load preserves Off; missing and malformed values use On; copy, Cancel, section reset and full reset retain existing session semantics. |
| A4 | Shadow is independent of badge background, TTC coloring and server profiling, with no layout or text changes. |
| A5 | Both rendering families build on all four targets, with matching English/Ukrainian keys and focused automated regression checks. |
| A6 | Verify readability with both shadow settings at supported GUI scales on all four targets. This visual requirement is deferred by the no-smoke instruction for this run. |

Automated evidence can establish A1-A5 only to the boundaries exercised. Do not report A6 as passed without visual evidence; keep that evidence status explicit without making its deferral in a particular run a permanent issue-closure condition.
