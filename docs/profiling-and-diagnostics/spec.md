# Profiling And Diagnostics Spec

Status: finished

Scope: Original profiling, accuracy and delayed-diagnostic baseline.

Verification: [prepared-client results](../automated-ui-testing/prepared-clients-2026-09-08.md).
The #114 extension below supersedes historical collection/window rules.

The sections above the #114 extension retain the earlier runtime baseline.
The implemented extension supersedes their collection/window rules.

## Goal

Learn how quickly each AE2 network produces an output, then use that history to
estimate work and explain unusually slow jobs. The result should get more useful
as the world runs without pretending that an estimate is exact.

## Learned Throughput

- Identify history by AE2 network and output id. Two separate networks must not
  share samples.
- Measure a continuous production window from the first dispatched batch until
  every matching batch on that network becomes idle.
- Combine concurrent batches for the same output. Do not add parallel durations
  as if the work ran one batch at a time.
- Keep at most `maxSamples` recent completed windows per output. The default is
  10 and the accepted range is 1 through 100.
- Give newer usable samples more weight than older samples.
- Starting with five samples, exclude duration-per-unit values outside the
  median divided or multiplied by `outlierMultiplier`. The default is 4.0 and
  the accepted range is 1.0 through 1000.0.
- Mark an estimate reliable only when at least three samples exist and none was
  excluded. Low-confidence estimates remain visible with a `?`.
- Keep items in item units. Normalize fluids and chemical-style keys to
  millibuckets before profiling and estimating.

## Prediction Accuracy

- Freeze the displayed whole-plan TTC only after AE2 accepts the job.
- Record the predicted seconds, known plan rows, total plan rows, start tick,
  and monotonic start time for that crafting CPU.
- Add an accuracy sample only when the job finishes successfully.
- Exclude cancelled jobs, restored jobs, invalid clocks, and jobs with no usable
  prediction.
- Calculate aggregate error metrics only from fully covered plans. Partial plans
  still contribute coverage and latest-job context.
- Keep accuracy runtime-only and bounded by `maxSamples` per final output.
- Never feed accuracy results back into throughput or TTC calculations.

## Delayed Output Diagnostics

- Diagnose only outputs that still have pending work and retained throughput
  history on the selected crafting CPU.
- Mark an output delayed after both 30 seconds without progress and twice its
  learned average production-window duration.
- Treat partial accepted output as progress and restart the idle timer.
- Show active and scheduled amounts plus recently used parallel slots when that
  capacity sample is no more than 20 ticks old.
- Suggest more parallel Pattern Providers or machines when work is waiting and
  slots are free. Suggest Crafting Co-Processors when all recent slots were used.
  Always suggest speeding up the active machine.
- Keep pending work, capacity, and delay state runtime-only.

## Lifecycle And Reset Rules

- Starting with profiling disabled hides stats and ignores new profiling,
  accuracy, and capacity events. Retained throughput samples stay saved.
- Finishing or cancelling a crafting CPU job clears unmatched work for that CPU
  so it cannot contaminate a later sample.
- Clearing an output removes its retained samples, pending work, busy window,
  and prediction-accuracy history for that network/output key.
- Persist only completed throughput samples through Minecraft `SavedData`.

## Acceptance Checks

- Sequential and concurrent batches produce the correct network-wide rate.
- A same-tick completion lasts at least one tick.
- Invalid, unmatched, disabled, or nonpositive events create no sample.
- The configured sample limit keeps the newest windows.
- Outlier boundaries, recency weighting, reliability, and normalized units are
  deterministic.
- Only successful accepted jobs create accuracy samples, and partial plans do
  not change aggregate error metrics.
- Delay state starts at the combined threshold, resets on progress, and uses
  only fresh parallel-capacity evidence.
- Reset, cancellation, and load clear their documented runtime state. Saved
  output contains only completed throughput samples.

See [technical-design.md](technical-design.md) for ownership and code flow.

## Planned: normalized sample details (#114)

Status: in-progress

Scope: Live throughput learning and normalized samples (#114).

Implementation: [PR #287](https://github.com/cTux/ae2-crafting-time/pull/287).
Verification: that PR records three passing compatible targets and a blocked
NeoForge 1.21.1 run; full acceptance reconciliation remains outstanding.

[Issue #114](https://github.com/cTux/ae2-crafting-time/issues/114) includes live
completion-interval learning and normalized details, implemented in PR #287.
These requirements supersede the baseline collection/window rules above.
See the [implementation plan](implementation-plan.md). The heading retains its
historical link anchor.

### Confirmed meaning

The maintainer confirmed `9 items / 90 ticks` should display as `1 item / 10 ticks`,
with one observation rather than nine invented samples. They also requested
that a large order update its learned speed and confidence while items return,
without waiting for the whole order to finish. This replaces the earlier
presentation-only scope and the original per-provider-insertion sample rule.

A new sample represents an observed completion interval. Same-tick returns for
the same network/output form one observation, even across CPUs and callbacks.
It measures effective throughput, including queueing and return transport, not
one machine slot's processing time. A furnace may queue inputs or process them
in parallel; no eight- or nine-slot Unobtainium capacity is assumed.

### Requirements

- **N1 — Meaning:** Display each observation as `⏱ M ticks` under `Samples (per unit)`, with M equal
  to its elapsed ticks divided by completed output amount. Never call this
  isolated machine processing time or equate one output item with one recipe.
- **N2 — Precision:** Use up to three decimals, round half up, and omit trailing
  zeros. Positive values below 0.001 tick display `<0.001`, never zero. The
  interval has a one-tick minimum; its per-item quotient does not. Tick-based
  seconds mean ticks/20, not wall-clock duration.
- **N3 — Evidence:** Retain one positive observation per network/output return
  tick. Group same-tick returns before counting or filtering them. Preserve raw
  integer amount/duration pairs and the newest `maxSamples` observations. Do not
  duplicate an interval when the job or continuous production window finishes.
- **N4 — Live learning:** Every finalized interval immediately participates in
  the existing recency-weighted rate and outlier calculation, even if old history
  exists and the order still has pending work. Remaining row TTC and the server
  remaining-job total use the latest rate. TTC may increase or decrease; it is
  not a fixed countdown. Frozen job-accuracy predictions remain unchanged.
- **N5 — Details:** Standard plan/status tooltips and client/server compact
  details use per-unit values. Compact average is the arithmetic mean of retained
  per-unit durations; latest is the latest ratio. Label both per unit. The
  weighted rate keeps its own meaning and need not equal the inverse average.
- **N6 — Units:** Use singular `item`, or the existing normalized `mB`/`mana`.
  Multiple-output recipes and fluid/chemical normalization keep their meanings.
- **N7 — Compatibility:** Preserve existing saved raw samples as older, broader
  observations. New intervals age them out under the same retention limit. No
  forced history reset or NBT/wire layout change. Cover all four supported targets
  and English/Ukrainian. Optional mods remain optional.
- **N8 — Missing evidence:** Ignore unmatched, simulated, nonpositive, or invalid
  timing events. Missing/invalid detail pairs cannot produce fabricated ratios,
  NaN, or infinity. A silent interval adds no sample. A later real return includes
  that active waiting time, so silence is not mistaken for fast production.
- **N9 — Confidence:** Recompute the existing reliability rule after each retained
  interval: at least three observations and none excluded. Ten is a retention
  limit, not the required count. One or two remain `?`; three clean observations
  can remove it before completion. Outliers can keep or restore `?`. Adjacent
  intervals are observations, not proof of statistical independence or certainty.
- **N10 — Refresh:** Learn on the logical server even with every GUI closed.
  Publish a return tick by the next server-tick boundary. An open visible row
  receives the changed count, rate, and confidence on its next normal one-second
  stats refresh plus network/server scheduling; no reopen or hover is required.
  Preserve existing request limits rather than sending a packet per item.
- **N11 — Lifecycle:** Already accepted output remains valid evidence after
  cancellation. Drop unfinished work, not finalized observations. Reset removes
  the selected history and its unfinalized state. Save/reload preserves finalized
  intervals and clears pending state. Never count idle time between separate
  production episodes, and never reuse an old episode's output after reset.
- **N12 — Delay:** Keep the 200-tick minimum and last-progress reset behavior.
  The learned delay baseline becomes raw completion-interval duration, not
  normalized ticks/item. This intentionally replaces whole-order duration;
  a nine-item batch arriving every 900 ticks must not look stalled after 200
  ticks because its normalized per-item time is only 100 ticks.

### Acceptance examples

A1–A10 describe raw observations and their normalized view; L1–L10 cover live
collection. Default retention is ten unless stated otherwise.

| ID | Observation | Expected |
| --- | --- | --- |
| A1 | 1 item in 200 ticks | `⏱ 200 ticks` |
| A2 | 9 items in 90 ticks | `⏱ 10 ticks`; one sample |
| A3 | 8 items in 90 ticks | `⏱ 11.25 ticks` |
| A4 | 10 items in 411 ticks | `⏱ 41.1 ticks` |
| A5 | 9 items in 1 tick | `⏱ 0.111 ticks` |
| A6 | 10,000 items in 1 tick | `⏱ <0.001 ticks` |
| A7 | One recipe yields 4 items in 80 ticks | `⏱ 20 ticks`; one observation |
| A8 | Nine distinct 1-item/200-tick intervals | Nine observations, not one whole-order sample |
| A9 | 1,000 mB/20 ticks; 100 mana/20 ticks | `1 mB / 0.02 ticks`; `1 mana / 0.2 ticks` |
| A10 | (1 item, 100 ticks), (9 items, 90 ticks) | Average 55 ticks/unit; latest 10; weighted rate unchanged for these pairs |
| A11 | Reset/reload/cancel/concurrent CPUs | Follow N11 and L5–L9; no duplicate output |
| A12 | Empty, unequal, zero/negative detail pairs | Omit invalid normalized details; keep valid aggregate rate |
| L1 | Start 1,000 items at tick 0; return one at 200, 400, 600 | Three `(1,200)` samples by end tick 600 while 997 remain; `?` removed on next refresh |
| L2 | Existing two `(1,200)` samples, one new interval during order | Count becomes three and clean confidence becomes reliable before finish |
| L3 | One CPU returns 4 then 5 items at tick 90 | One `(9,90)` sample, never two or nine |
| L4 | Bulk output with no later returns | One sample remains one; waiting/rendering cannot promote confidence |
| L5 | Two CPUs dispatched at tick 0 return 4 and 5 at tick 90 | One network `(9,90)` sample; other networks stay separate |
| L6 | Returns at 100 and 300 while work remains pending | Intervals end at each return: `(amount1,100)`, `(amount2,200)` |
| L7 | Last return at 100; next dispatch 1,000, return 1,100 | New interval lasts 100 ticks, not 1,000 |
| L8 | Final return triggers finish in same callback/tick | Final interval retained once; no additional full-order sample |
| L9 | Cancel after partial return; reset before tick finalization; reload mid-order | Cancel keeps accepted evidence; reset suppresses its pending bucket; reload keeps only finalized history |
| L10 | New outlier, full retention window, or `maxSamples < 3` | Existing filter/eviction rules apply; `?` may persist or return; low configured limit cannot become reliable |

A 3/10 history without excluded observations is already reliable. For the user's
1,000-item example, test low confidence with two samples (L2), and test retained
outliers separately (L10); do not invent a three-clean-sample `?` precondition.

### Not included

No per-item fake samples, provider insertion counts, machine-slot introspection,
new machine dependencies, CPU-specific profile keys, new confidence threshold,
new push protocol, or automatic accuracy calibration. Normalization alone is a
presentation change; new completion intervals enable live learning.
