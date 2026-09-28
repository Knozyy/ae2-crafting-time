# Player Controls And Integrations Spec

Status: finished

Scope: Shipped controls, common configuration and optional UI baseline.

Verification: [prepared-client coverage](../automated-ui-testing/prepared-clients-2026-09-08.md)
and [feature inventory](../feature-coverage.md).
The proposed configuration screen and later addon updates have separate scopes.

## Goal

Put TTC information where players already inspect AE2 crafts, make the learned
data understandable, and keep optional-mod support invisible when those mods are
absent.

## Presentation

- Show eligible craft-plan and crafting-status rows as TTC text on a
  translucent black rounded badge with two pixels of padding and text shadow.
- Color known row estimates from green through yellow to red relative to the
  current list. Show delayed rows as normal-weight red `DELAYED`.
- Show the craft-plan total below the CPU details. Show the running-job total in
  the status title only when it fits without overlapping the title.
- Start both standard AE2 screens in `TTC: longest first` order. The toolbar
  button cycles through AE2 order, shortest first, and longest first. Sort state
  lasts only for that open screen.
- Keep unknown rows after known rows and keep their original order.
- Tooltips may explain throughput, retained production windows, confidence,
  accuracy, delay evidence, and controls. They must not expose server internals
  beyond aggregate statistics.

## Details And Reset

- Ctrl-click an eligible row or Crafting Tree node to publish a compact TTC
  summary and sample details to server chat.
- Ctrl-Alt-click the same target to clear retained stats for that output on the
  player's current AE2 network.
- The configurable mouse binding defaults to the left button. Ctrl and Alt are
  modifiers, not separate configurable actions.
- Reset works even when chat messages are hidden. In that mode no reset notice
  is published.
- Ignore clicks outside an eligible crafted row or node.
- The server must resolve the network, validate the structured action, apply a
  per-player two-second message cooldown, and refuse a reset when no retained
  sample exists.

## Configuration

The common file is `ae2craftingtime-common.toml` on every loader.

| Key | Default | Range | Behavior |
| --- | --- | --- | --- |
| `enabled` | `true` | boolean | Enables profiling and server-owned stats. |
| `showInTree` | `true` | boolean | Enables AE2: Crafting Tree badges, tooltips, spacing, and clicks on pre-26 targets. |
| `showChatMessages` | `true` | boolean | Enables public Ctrl-click details and the private reset notice. Reset still happens when false. |
| `maxSamples` | `10` | 1-100 | Sets the retained throughput and runtime accuracy window per output. |
| `outlierMultiplier` | `4.0` | 1.0-1000.0 | Sets the median-relative throughput outlier boundary. |

Invalid Fabric values keep the current/default value; numeric values outside a
range are clamped. Forge and NeoForge use their native config specifications.
Sample-window and outlier settings are applied when the profiler is created, so
changing them requires a game/server restart.

## Optional Integrations

### AE2: Crafting Tree

- Available on the 1.20.1 and 1.21.1 targets, not the 26.1.2 target.
- Show each node's recursive known TTC below the node and add vertical spacing
  so badges do not cover nodes or connectors.
- Color nodes relative to the current tree and append TTC plus details/reset
  hints to the node tooltip.
- Support the two Crafting Tree widget layouts currently listed in the mixin
  configuration.

### ME Requester

- Available on the 1.20.1 and 1.21.1 targets, not the 26.1.2 target.
- Estimate only the current network shortfall:

```text
max(0, requested amount - stored network amount)
```

- Show no badge when the requested amount is already stored. Show `No data yet`
  when a shortfall exists but throughput history is missing.
- Color visible row badges relative to one another and sum known visible rows
  into the header total.

## Compatibility And Failure Rules

- Core AE2 screens work on every supported target.
- Missing optional mods must not stop startup or change core behavior.
- A missing optional mod leaves its string-target mixin inactive. A missing
  reflected field or method omits that optional UI result.
- English and Ukrainian keys and placeholders stay matched.
- UI additions must not cover AE2 buttons, titles, item icons, table cells, or
  optional-mod content at supported GUI scales.

## Acceptance Checks

- Both standard screens open in longest-first mode and cycle all three modes.
- Badges, totals, tooltips, colors, and delayed text stay inside their owned
  screen areas.
- Details and reset select the same row the player sees after sorting and
  scrolling.
- The server ignores malformed, excessive, cooldown-blocked, and context-free
  requests.
- Every config key has identical defaults and ranges across loaders.
- Each optional integration disappears cleanly when its mod is absent, and
  26.1.2 contains no pre-26 optional UI mixins.

See [technical-design.md](technical-design.md) for the screen and packet paths.
