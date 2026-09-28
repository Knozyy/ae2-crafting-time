# Provider dispatch statuses

Status: finished

Scope: Original NO TARGET, INPUT BLOCKED and LOCKED statuses.

Implementation: [PR #293](https://github.com/cTux/ae2-crafting-time/pull/293).
Verification: [prepared-client results](../automated-ui-testing/prepared-clients-2026-09-08.md).
[NO CHANNEL](no-channel/spec.md), tooltip controls below, and
[red-warning highlights](../provider-locate/spec.md#planned-red-sky-beam) have separate scopes.

Planned change: [#488](../provider-locate/spec.md#planned-red-sky-beam) adds
automatic red plates with output icons and sky beams to every red warning,
including these dispatch reasons. It supersedes this document's existing
no-red-plates boundary, while preserving detection, priority and chat policy.

Issue: [#216](https://github.com/cTux/ae2-crafting-time/issues/216).

## Goal

The original three statuses and the [NO CHANNEL extension](no-channel/spec.md)
are implemented. This document retains the original three-status scope; see
the [source research](technical-design.md#repository-changes-since-the-original-plan).

Explain why scheduled work cannot reach a machine with three crafting-status
labels: `NO TARGET`, `INPUT BLOCKED`, and `LOCKED`. Replace a vague waiting or
delayed label only when the server has direct evidence for the new status.

## Player behavior

| ID | Status | Meaning | English tooltip |
| --- | --- | --- | --- |
| PD-01 | NO TARGET | No eligible destination was found for an attempted pattern dispatch. | No usable destination was found for this pattern. / Connect a compatible machine or inventory to an enabled provider side. |
| PD-02 | INPUT BLOCKED | A destination exists, but blocking mode or an observed insertion rejection prevents dispatch. | The destination is not accepting this pattern's inputs. / Check blocking mode, input space, filters, and machine sides. |
| PD-03 | LOCKED | An actual provider crafting lock prevents dispatch. | Pattern Provider crafting locks are preventing the next batch. / Check redstone conditions or return the previous result to its provider. |

- **PD-04:** Require positive scheduled work on the row and evidence from a real
  attempted dispatch. Missing ingredients, no dispatch budget, an inactive
  provider, provider busy state, or a generic failed machine call alone prove
  none of these statuses. Configuring a lock without activating it is not LOCKED.
- **PD-05:** Evaluate alternatives for the exact pattern. Any successful
  provider or destination suppresses that attempt's warning. Any unobserved,
  unsupported, busy, or otherwise unexplained alternative also suppresses a
  specific new warning. All eligible providers must agree on the same new
  status; mixed causes keep the existing fallback display.
- **PD-06:** A row combining several patterns can show a warning for one
  blocked scheduled pattern while other batches are active. Tooltips append
  `This status applies to scheduled batches; active batches may still finish.`
  when both amounts are positive. A successful different pattern with the same
  output must not erase the blocked pattern's evidence.
- **PD-07:** Keep stored-only NO SPACE first. For pending rows use
  `NO PROVIDER > NO POWER > LOCKED > INPUT BLOCKED > NO TARGET`, then the
  existing Waiting, DELAYED, TTC, and No data yet rules. Priority combines
  independently proven patterns in one row; it must not select a cause from
  disagreeing alternatives for one pattern.
- **PD-08:** Render the three labels normal-weight red in the existing compact badge.
  They have no TTC color and sort as unknown time. Keep existing total-TTC
  behavior; this feature does not promise an ETA for a blocked job.
- **PD-09:** Clear an observation on the next successful, unknown, or changed
  evaluation of that pattern. Otherwise expire it after 20 server ticks and
  clear it on the next existing status refresh. Recovery without a new attempt
  therefore takes at most 20 server ticks plus one refresh, not a fixed
  wall-clock promise during server lag. Job replacement, finish, cancellation,
  disable, and runtime reload clear all related state. These three reasons
  remain runtime-only: saving/reopening a world must not restore them, including
  as a different status through the existing remembered-status fallback.

## Compatibility and boundaries

- **PD-10:** Cover 1.20.1 Forge, 1.20.1 Fabric, 1.21.1 NeoForge, and 26.1.2
  NeoForge from the [release matrix](../../scripts/release-matrix.json).
  Support native AE2 CPUs and the existing AdvancedAE CPU integration on its
  three applicable targets. Providers inheriting the observed AE2 method can
  participate only when execution reaches the verified checks. Overridden or
  custom provider/CPU paths without those checks remain unknown, not broken.
- **PD-11:** Keep logical-server ownership, selected-CPU/network isolation,
  bounded packets, existing request authorization/rate limits, and optional
  dependency behavior. Client/server protocol versions advance together;
  stored samples and dependency minimum versions do not change. Preserve live
  completion-interval learning, current total-TTC calculation, and recovery
  from remembered NO PROVIDER/NO POWER statuses.
- **PD-12:** Add English and Ukrainian together. Ukrainian labels are
  `Немає приймача`, `Вхід заблоковано`, and `Заблоковано`. Keep translations
  semantically equivalent. Runtime smoke is English only and exercises only
  the newest implemented adapter, following the
  [shared smoke policy](../automated-ui-testing/spec.md#smoke-policy).

## Not included

No automatic fixes, extra screens, settings, saved diagnostic history, block
coordinates, per-lock subtype labels, or new statuses beyond the chosen three.
Do not infer machine power, fuel, recipe validity, chunk loading, channels, or
output capacity. NO SPACE keeps its CPU-to-ME-storage meaning. Craft Plan,
Crafting Tree, and ME Requester gain no new status display. New adapters for
NeoEco, LightningTech, or custom provider implementations are outside this
feature; their existing TTC/profiling support must remain unchanged. The three
new reasons do not add chat notifications, provider-locate records, or red
provider plates. Existing delayed warnings, locate clicks, and plate recovery
keep their current behavior. Do not restore removed accuracy tooltip rows.

## Acceptance criteria

| Check | Observable result | Requirements |
| --- | --- | --- |
| AC-01 | An attempted processing dispatch with no eligible destination shows NO TARGET; attaching a usable destination clears it. A recognized machine rejecting a craft never becomes NO TARGET. | PD-01, PD-04, PD-09 |
| AC-02 | Blocking-mode rejection and simulated zero-input acceptance each show INPUT BLOCKED; clearing the condition removes it. Partial acceptance followed by a successful dispatch is not INPUT BLOCKED. | PD-02, PD-04, PD-09 |
| AC-03 | Active high/low redstone locks, pulse locks, and result-return locks show LOCKED; inactive configured locks do not. Unlocking through that AE2 version's actual pulse/result behavior clears it. | PD-03, PD-09 |
| AC-04 | A healthy alternate side/provider prevents each warning. Busy, unknown, unvisited, and mixed-cause alternatives produce no new warning. Missing ingredients and generic machine rejection do not become any new status. | PD-04, PD-05 |
| AC-05 | Shared-output and mixed active/pending rows retain exact-pattern evidence, show the scheduling qualifier, and follow documented priority and sorting. Existing statuses, live learning, total TTC, locate/plate behavior, and compact tooltips do not regress. | PD-06, PD-07, PD-08, PD-11 |
| AC-06 | CPU/network switches, late previous-CPU replies, lifecycle cleanup, no learned samples, expiry, and backwards game ticks cannot leak or retain warnings. Save/reopen cannot restore these reasons or convert them to NO PROVIDER; existing remembered-status recovery stays fixed. | PD-09, PD-11 |
| AC-07 | Every target passes changed logic/packet/contract coverage and focused live status/recovery smoke. AdvancedAE cases cover its three targets; unsupported paths remain unchanged. | PD-10, PD-12 |
| AC-08 | Both locales have matching keys/placeholders, the visible English badges/tooltips fit, malformed payloads are rejected, and player JARs exclude the driver. | PD-08, PD-11, PD-12 |

See the [technical design](technical-design.md) and
[implementation plan](implementation-plan.md).

## Planned warning-tooltip controls correction (#437)

Status: in-progress

Scope: Warning tooltip controls (#437).

Implementation: [PR #447](https://github.com/cTux/ae2-crafting-time/pull/447) is merged and CI passed.
Verification: full runtime acceptance is not recorded in that PR.

[Issue #437](https://github.com/cTux/ae2-crafting-time/issues/437) restores the
control hints skipped by warning tooltips. This correction is planned, not
implemented. It applies to all four supported targets and extends the original
tooltip requirements above; it does not reopen the dispatch-status feature.

Every `DELAYED`, `NO SPACE`, `NO PROVIDER`, `NO POWER`, `NO CHANNEL`, `NO TARGET`,
`INPUT BLOCKED`, and `LOCKED` tooltip ends with these three gray hints, once each:

1. `Double-Click for highlighting Pattern Provider in a world`
2. `Ctrl-Click for TTC details`
3. `Ctrl-Alt-Click to clear TTC stats`

Use the existing `locate_hint`, `details_hint`, and `reset_hint` translations in
English and Ukrainian. Keep the status explanation, suggestion, delayed advice,
and any scheduled-batch qualifier unchanged and above the hints. Ordinary TTC
text colored red by relative ranking is not a warning status.

Hints describe the existing controls; they do not grant new action eligibility.
Details/reset still require positive active or pending work, so stored-only
`NO SPACE` rows still ignore those clicks. Locate still requires server-resolved
provider positions and job ownership; an unresolvable row keeps its expiry
notice. Preserve row selection after sorting/scrolling, server validation,
packets, saves, status priority, and provider-highlight behavior. Normal TTC,
Waiting, collecting-data, empty rows, Craft Plan, and Crafting Tree keep their
current tooltip behavior. No setting or dependency is added.

### Acceptance for #437

| ID | Observable result |
| --- | --- |
| W437-1 | Every warning listed above ends with exactly the three hints in the stated order, including warnings with no learned samples. |
| W437-2 | Existing tooltip body components keep their text, style, and order. The mixed-row qualifier stays before the controls; DELAYED does not duplicate its locate hint. |
| W437-3 | Existing English/Ukrainian hint keys and gray styling are reused. Non-warning rows and other UI surfaces retain their current behavior. |
| W437-4 | Automated final-tooltip checks cover INPUT BLOCKED and NO SPACE, all block-reason enum values, final hint order, and duplicate prevention. |
| W437-5 | Reviewed English client evidence shows complete, readable INPUT BLOCKED and NO SPACE tooltips. Real locate/details/reset clicks on an eligible warning row target the displayed output; stored-only NO SPACE retains its current no-op details/reset boundary. |
| W437-6 | All four targets compile the shared correction and pass their required changed-scope checks. No protocol, save, dependency, action-eligibility, or status-detection change is introduced. |
