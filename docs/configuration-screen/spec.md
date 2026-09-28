# In-game Configuration Screen Specification

Status: implementation in review

Scope: In-game configuration screen.

Planning: [original plan, PR #280](https://github.com/cTux/ae2-crafting-time/pull/280); [revised plan, PR #509](https://github.com/cTux/ae2-crafting-time/pull/509).
Implementation: [draft PR #510](https://github.com/cTux/ae2-crafting-time/pull/510). The screen, storage, protocol, and runtime switches are implemented; full cross-loader UI verification and dedicated-server permission testing remain open.

Issue: [#117](https://github.com/cTux/ae2-crafting-time/issues/117)

## Goal

Let players and server owners turn off each player-facing runtime feature without
turning off unrelated features. Local display and personal notification choices
belong to the client; profiling and shared behavior remain server-authoritative.

## Entry points and layout

- Forge and NeoForge expose **Configure** from this mod's loader details page.
- Fabric exposes the same screen through optional Mod Menu integration. The mod
  still loads and its config files still work when Mod Menu is absent.
- The root has **Client** and **Server** tabs. Client groups are Displays,
  Warnings, Appearance, and Controls. Server groups are General, Diagnostics,
  Notifications, Integrations, and Advanced. Each group has its own page or scroll region.
  Every row shows its current value, default, valid range or choices, and a
  short English or Ukrainian explanation. The Server tab is read-only for
  players without edit permission, with the effective server value still shown.
- The [options mockup](options-mockup.svg) shows the proposed grouping and the
  personal warning mute. It is a design preview, not an implemented screen.
- **Done** saves valid changes. **Cancel** discards unsaved changes. Each section
  has **Reset section**, and the root screen has **Reset all**; reset changes are
  reviewable before **Done** writes them.
- Client-only changes apply as soon as **Done** is pressed. Server-owned changes
  made by an authorized local/server operator are validated, saved, and applied
  live by the server. The server sends the effective values back to players.

## Settings

### Server-owned

| Setting | Default | Valid values | Behavior |
| --- | --- | --- | --- |
| Profiling and TTC (`enabled`) | on | on/off | Enables server profiling and the data used by TTC surfaces. |
| Retained samples (`maxSamples`) | `10` | `1`-`100` | Limits recent throughput and accuracy samples per output. |
| Outlier multiplier (`outlierMultiplier`) | `4.0` | `1.0`-`1000.0` | Sets the median-relative throughput filter. |
| Delayed and blocked notifications (`notifyOnDelayed`) | on | on/off | Global server permission to send private craft warning chat. |
| Chat details (`showChatMessages`) | on | on/off | Controls server-sent TTC details and reset notices. |
| Minimum no-progress time | `10 s` | `1`-`3600 s` | Earliest time at which an output can become delayed. |
| Typical-duration multiplier | `2.0` | `1.0`-`1000.0` | Required learned-duration multiple before an output becomes delayed. |

### Feature switches

Switches default **on** except recurrent ingredient detection, fast-to-slow TTC
colors, compact crafting amounts, text shadow, and badge background, which default
**off**. Turning one off removes only the named feature; it does not erase learned samples, change active
crafts, or silently turn off sibling features. The server profiling master
(`enabled`) is the exception: turning it off suspends all server-derived TTC and
diagnostics. It is clearly labelled as the master switch.

### Accurate switch default tooltips (#607)

Status: finished

Scope: Correct client and server switch tooltip defaults without changing settings behavior.

Issue: [#607](https://github.com/cTux/ae2-crafting-time/issues/607)

Planning: [Design](technical-design.md#accurate-switch-default-tooltips-607) and
[reviewed plan](implementation-plan.md#focused-follow-up-accurate-switch-default-tooltips-607).

Implementation: [PR #610](https://github.com/cTux/ae2-crafting-time/pull/610) merged to `master` at [9855381756ccd14526a385f6db079620a7f9534d](https://github.com/cTux/ae2-crafting-time/commit/9855381756ccd14526a385f6db079620a7f9534d).

Verification: [Issue completion evidence](https://github.com/cTux/ae2-crafting-time/issues/607#issuecomment-5877065808) confirms [PR #610](https://github.com/cTux/ae2-crafting-time/pull/610) merged after [tested head 36296cf1b20fcf61b7a6440582286df8ce7d8509](https://github.com/cTux/ae2-crafting-time/commit/36296cf1b20fcf61b7a6440582286df8ce7d8509) passed [Gradle tests/coverage](https://github.com/cTux/ae2-crafting-time/actions/runs/36469909276), [all four builds](https://github.com/cTux/ae2-crafting-time/actions/runs/36469909142), and local validation of all 201 locale keys/placeholders, both templates' two arguments, and fresh owner-specific model usage. No Minecraft launch was required for this tooltip-only scope.

Every switch tooltip must describe its fresh model default in English and
Ukrainian. Compact crafting amounts, TTC colors, text shadow, and badge background
default Off on the client; recurrent detection defaults Off on the server. Other
switches default On. The displayed default stays the same when the saved or draft
value changes. Keep the existing explanation, apply timing, and ownership text.

| ID | Acceptance criterion |
| --- | --- |
| T1 | Both screens derive each advertised default from the appropriate fresh `FeatureOptions` model, independently of the edited value. |
| T2 | English and Ukrainian help strings have matching keys/placeholders and display localized On/Off values, including all default-Off exceptions. |
| T3 | Existing defaults, reset actions, save/cancel, permissions, persistence, layout, and rendering remain unchanged on all four supported targets. |
| T4 | Locale validation, existing model tests, target compilation, and required current-head CI pass before merge. |

This scope needs no Minecraft launch: it changes tooltip arguments and copy,
not widget layout or interaction. Static locale/argument checks and compilation
cover the changed boundary; existing tests cover the source defaults. It does
not claim new visual evidence or complete older rendering verification scopes.
Client Reset section currently enables every switch; its separate behavior correction
is outside this issue. Reset All continues to restore model defaults.

### Recurrent detection default (#592)

Status: finished.

[PR #593](https://github.com/cTux/ae2-crafting-time/pull/593) shipped the
default-off change and English/Ukrainian player guidance. The follow-up
[PR #597](https://github.com/cTux/ae2-crafting-time/pull/597) preserves explicit
server values when copying `ServerConfig`. Its reviewed and tested head was
`737652991753865e65fc66bf510ac4374026a825`; it merged as
`54662c06fe44a518b7287182bf2f6b931ec5ccc5`.
Issue [#592](https://github.com/cTux/ae2-crafting-time/issues/592) closed when
the fix merged on 2026-09-28 at 08:02:02 UTC.

The shared regression passed with 100% line coverage (2295/2295) and branch
coverage (1537/1537). The regression failed against the old copy implementation
as expected, then passed on the complete candidate. Current-head GitHub CI also
passed: [tests](https://github.com/cTux/ae2-crafting-time/actions/runs/36394293763)
and [build](https://github.com/cTux/ae2-crafting-time/actions/runs/36394293765).

Fresh server files and Reset use `recurrentDetection = false` on all four targets.
An explicit `true` must survive loading, independent model copies, editing and
Done. Players can opt in through Server → Diagnostics → Detect recurrent
ingredients or the world server file. Copying must preserve every server switch
and numeric value without changing the source. Other defaults and crafting
calculations remain unchanged. This precautionary default does not establish a
cause for the unverified performance/crash report in the issue.

### Switch inventory

| Owner and group | Independent switches | Off behavior |
| --- | --- | --- |
| Client / Displays | Crafting Plan row TTC, Crafting Plan total, Crafting Status row TTC, Crafting Status total, CPU-card total, Crafting Tree TTC, ME Requester TTC | Hide the named surface only; unsupported integrations are shown disabled with a reason. |
| Client / Displays | Fast-to-slow TTC coloring, prediction accuracy, detailed tooltips, control hints | Hide the named detail or use neutral text color; estimates stay available. Fast-to-slow colors default off. |
| Client / Displays | [Compact crafting amounts](../crafting-plan-amounts/spec.md) (`compactStatusAmounts`, #438, #525, and #529) | Show compact amounts in Crafting Plan and Crafting Status when on; keep row TTC independent. Defaults off and remains effective with server profiling off. |
| Client / Warnings | Waiting-to-start, collecting-data, delayed, recurrent ingredient, and each blocked reason (`NO PROVIDER`, `NO POWER`, `NO SPACE`, `NO CHANNEL`, `NO TARGET`, `INPUT BLOCKED`) | Hide that status and its badge/tooltip on this client; other statuses and calculations remain. |
| Client / Warnings | Private craft warning chat | Mute delayed and blocked warning messages for this player, including the screenshot's repeated `is delayed` lines. Other players keep their own choice. |
| Client / Controls | Crafting Plan sort control, Crafting Status sort control, CPU-list TTC sort, TTC details click, reset-history click, provider-locate click | Hide or disable only the named control. A disabled sort control uses AE2 order; no server-side history is removed. |
| Server / Diagnostics | Prediction-accuracy recording, waiting-to-start tracking, delayed detection, recurrent ingredient detection, and each blocked reason (`NO PROVIDER`, `NO POWER`, `NO SPACE`, `NO CHANNEL`, `NO TARGET`, `INPUT BLOCKED`) | Stop collecting or classifying that named diagnostic for all clients; existing TTC estimation remains when profiling is on. |
| Server / General | World-save history | Stop future writes of learned throughput history; keep the existing world-save file so re-enabling can resume from it. |
| Server / Notifications | Private craft warning chat (`notifyOnDelayed`), Ctrl-click details/reset notices (`showChatMessages`) | Suppress the named server message for everyone without disabling diagnostics, clicks, or reset actions. |
| Server / Integrations | AdvancedAE, NeoEco, AE2 Lightning Tech CPU profiling, and Applied Mekanistics chemical statistics | Stop only the named optional adapter; base AE2 profiling continues. Unsupported targets show the switch disabled with a reason. |

The current `showInTree` value becomes the Crafting Tree display switch.
`notifyOnDelayed` remains the global server switch. A client's private-chat mute
is separate: a warning is delivered only when both switches are on. A local
mute must work on a dedicated server without operator permission.

The server sends the effective values needed for display and explanations after
login and when they change. Only the integrated-server owner or a player with
server operator permission level 4 can submit changed server values. The server
revalidates and saves them; dedicated-server files and permissions remain
authoritative.

### Client-owned

| Group | Settings and defaults |
| --- | --- |
| Displays | The independent on/off values in the feature-switch table. Fast-to-slow colors and compact crafting amounts default off; the other supported switches default on. |
| Sorting | Separate Crafting Plan and Crafting Status defaults. Both start at longest first, matching the current `2` mode. Choices are AE2 order, shortest first, and longest first. |
| TTC scale | Fast `#55FF55`, middle `#FFFF55`, slow `#FF5555`. |
| Status text | Neutral Plan/Status rows use AE2's text color without a badge and Total `#E0E0E0` with a badge. Delayed/blocked warnings stay red; fast-to-slow TTC colors and tooltip colors stay as configured. |
| Badges | Background `#000000` with `176/255` opacity, matching `0xB0000000`. |
| Appearance | [Text shadow](text-shadow/spec.md) (`textShadow`, #531), default off; controls mod-drawn text independently of badge backgrounds and TTC colors. |
| Appearance | [Badge background](badge-background/spec.md) (`badgeBackground`, #532), default off; hides only mod-owned badge backgrounds while preserving saved color and opacity. |

Options for a feature unavailable on the current target stay visible but disabled
and explain the missing mod or unsupported target. Guide book content,
translations, and packet safety limits are not independent runtime behaviors
and are not presented as off switches.

## Validation and recovery

- Text fields reject non-numeric, non-finite, and out-of-range values before
  saving. Color values use six-digit RGB; opacity uses `0`-`255`.
- A missing key uses its default. Existing valid values survive the upgrade.
  Existing `enabled`, `showInTree`, `showChatMessages`, `notifyOnDelayed`,
  `maxSamples`, and `outlierMultiplier` values migrate without changing meaning.
- A malformed client or server value is logged and only that value falls back
  to its default. It does not crash a client or dedicated server.
- Unknown keys are preserved when the backing loader supports preservation and
  otherwise ignored; they never appear as invented UI options.

## Compatibility

The screen and matching behavior cover all four release-matrix targets:
Minecraft 1.20.1 Forge, Minecraft 1.20.1 Fabric, Minecraft 1.21.1 NeoForge, and
Minecraft 26.1.2 NeoForge. English and Ukrainian keys, defaults, validation, and
ownership remain aligned.

## Non-goals

- A new configuration library, web UI, command-only editor, or custom widget
  framework.
- Client authority over server profiling, persistence, chat, or diagnostics.
- Exposing packet limits, protocol constants, cache sizes, or other
  implementation-only values.
- Duplicating the TTC-details mouse binding from Minecraft's Controls screen.
- Changing crafting behavior, learned samples, or existing defaults merely by
  opening the screen.

## Acceptance criteria

- Every current config key and every setting above can be viewed and changed at
  its correct authority boundary on all supported targets.
- Loader and Mod Menu entry points open the same categorized screen, and Fabric
  works normally without Mod Menu.
- Save, cancel, section reset, full reset, invalid input, migration, reconnect,
  and restart-required states behave as specified.
- Display toggles, colors, opacity, default sorting, status visibility, and
  accuracy/detail visibility affect every listed UI surface without changing TTC
  calculations.
- A remote unprivileged client cannot change server-owned values; effective
  server values remain consistent between clients.
- Every feature switch listed above has a checked off/on case. A client can mute
  the screenshot's warning chat for itself on a dedicated server while another
  player continues receiving it; the server global switch suppresses it for all.
- English and Ukrainian labels have matching keys/placeholders, dedicated-server
  startup stays client-class-free, and all four release-matrix builds pass.
- Prepared-client smoke covers the loader entry point, editing, reset/cancel,
  immediate client updates, server-authority messaging, and persistence on every
  target.
