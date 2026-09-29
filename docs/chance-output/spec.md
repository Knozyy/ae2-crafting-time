# Chance-based output diagnostics

Status: experimental; implemented and runtime-qualified only for the 1.20.1 Forge Mekanism Precision Sawmill. Other machine support may be added after its live recipe chance can be verified.

Scope: Diagnose non-guaranteed processing outputs when a supported server integration proves the recipe chance.

Issue: [#471](https://github.com/cTux/ae2-crafting-time/issues/471).

Initial integration: Mekanism 10.4.16.80 Precision Sawmill on Minecraft
1.20.1 Forge. Its server recipe for an acacia hanging sign has a guaranteed
two-plank main output and a 50% sawdust secondary output. This is the first
supported detector, not a generic inference from partial returns. Runtime
qualification passed on the prepared Forge client at source commit
`55009dc5432fc710130bddd27a2b42839a82e600`.

## Problem and evidence limits

An encoded processing pattern promises a fixed output. A machine that returns
that output only by chance can leave AE2 waiting: 100 operations at 60% chance
produce 60 outputs on average, not a guaranteed 60 or a guaranteed completion.

The issue's pinned AE2/source research found no standard probability field in
`IPatternDetails`. Successful provider dispatch adds expected outputs; accepted
returns reduce the waiting amount. Neither observation proves the machine has
finished its attempts. Slow processing, missing fuel, blocked routing, and
unloaded machines can produce the same partial-return symptom.

`CraftProfiler` and `CraftingCpuLogicMixin` already observe outstanding output,
progress, and lifecycle. Reuse these boundaries; do not estimate recipe chance
from the ratio of observed returns to dispatched output or from network stock.

## Behavior

- **Chance output** applies only when a supported server integration
  proves that the job's encoded output is not guaranteed. Show a percentage only
  when its exact effective value is known.
- Explain that AE2 may wait for missing output. On a delayed row, include
  job-specific outstanding amount and idle time only when those values are known.
- Preserve concrete blocker statuses and retain applicable chance context in the
  tooltip. Unknown mappings stay DELAYED with a conditional suggestion to check
  for chance outputs, never a confirmed diagnosis.
- Reuse owner-targeted delayed alerts, server settings, and episode deduplication.
  Do not emit duplicate generic/chance messages merely because chance is present.
- Explain recovery: inspect routing and machines, remove optional random byproducts
  from promised outputs, or stock random outputs independently. Cancel and correct
  an unrecoverable job. Extra inputs and batching do not guarantee success.

No scheduler changes, automatic retries/cancellation, invented outputs, or
probability-based TTC correction are requested. The eventual status needs an
on/off option through the existing options model.

## Approved presentation

Use ⚠ Chance output: sentence case, with the label and leading warning
symbol in Minecraft red (`#FF5555`) and normal-weight text. All red status
labels and tooltip headings use normal weight, including Recurrent, Delayed,
and provider/dispatch blockers. Reuse the existing red warning symbol
(`TtcSymbols.Symbol.ERROR`, U+26A0) and shared status badge presentation.
The matching Ukrainian label is ⚠ Випадковий вихід.

Apply this presentation to the affected output row, its tooltip heading, and
any applicable delayed alert. Prefix the symbol exactly once at the client
presentation boundary. Respect Show emoji: Off removes only the symbol,
leaving the red label intact. Respect the existing badge background switch,
color, and opacity. Do not hard-code the symbol into translation values.

When the effective chance is verified, the tooltip heading may read
⚠ Chance output · 60%. Unknown mappings retain the existing generic
Delayed status and conditional hint. Red indicates the risk of missing
promised output; it does not prove that the machine has stopped or finished.

Acceptance: verify the exact English/Ukrainian labels, red label and symbol,
single prefix, emoji On/Off, shared badge settings, and unchanged generic Delayed
fallback. Carry this presentation into both GuideME translations and the Wiki
when the feature is implemented. The concept preview approves presentation;
it is not evidence of an implemented detector or Minecraft glyph rendering.

## Design and verification boundaries

Map evidence by job/CPU, pattern, and output; one global output ID is insufficient
for mixed producers. Verify upgrades, pack changes, ambiguous factory patterns,
and effective server recipes. A recipe-browser tooltip is not server authority.
Keep snapshots bounded and detection server-owned; avoid per-tick full recipe scans.
Define reset, cancellation, completion, unload/reload, and stale-evidence handling.

A deterministic fixture may dispatch 100 promised units and return exactly 60
to verify 40 outstanding. Test a real chance recipe separately without expecting
an exact random yield. Cover zero returns/no history, partial and late returns,
guaranteed main outputs with chance byproducts, multiple CPUs, mixed producers,
and deterministic slow/blocked negative controls.

Review both source variants, packet implementations, status composition,
`TtcText`, `ProfilerBridge`, snapshot caches/codecs, and notifications. Qualify
Forge/Fabric 1.20.1 and both NeoForge targets, explicitly excluding unsupported
addon mappings. If no reliable mapping exists, retain that research result;
a conditional hint alone does not complete the requested status.

## Documentation and completion

After behavior is implemented and verified, update English/Ukrainian UI text,
GuideME status pages and indexes, DELAYED cross-links, and diagnostic guidance.
Publish and verify equivalent English/Ukrainian GitHub wiki pages and navigation.
Use reviewed runtime captures, not proposal images.

Explain the detection limits, recovery, and average-yield example. Reconcile the
issue's reported DELAYED documentation timing discrepancy against current
configuration/code rather than copying the old 30-second claim.
[#412](https://github.com/cTux/ae2-crafting-time/issues/412) is related guide work,
not an established blocker. Retain exact source, test, CI, and publication evidence
before closing the feature.

The prepared Forge client passed the native Mekanism sawmill scenario with 100
promised sawdust, 60 controlled returns, 40 outstanding, and the recipe's verified
50% chance. Captures with Badge background both off and on show a normal-weight
red Chance output row and tooltip heading. The shared red text paths for delayed
and blocker statuses were checked with current-head unit tests and focused UI
captures. The Recurrent plan fixture fails before rendering on the current graph;
that separate test failure is tracked in #602. Optional chance detection is
limited to the direct Mekanism Sawmill mapping on 1.20.1 Forge; all other
supported targets retain generic delayed behavior for unsupported mappings.
