# Recurrent crafting status: technical design

Lifecycle: see scope status and evidence for [detection baseline](spec.md) and [warning styling](spec.md#planned-warning-style-496).

Historical planning status: recurrence detection is implemented. The #496 rendering change below
is planned against the [specification](spec.md); it has no runtime pass yet.
The historical #408 investigation was resolved by
[#410](https://github.com/cTux/ae2-crafting-time/pull/410). Its source inspection is bound to
`01cd5d75862105d7af049f03c2e0d88d54f5b980`; it is not a runtime pass for a fix.

## Current calculation and summary flow

The common production hooks live under
`shared/src/mcCommon/java/com/ctux/ae2craftingtime/mc1201/`. They observe AE2's
calculation rather than building a second graph from the recipe catalogue.

1. `mixin/CraftingTreeNodeMixin` wraps the existing
   `CraftingTreeProcess.notRecursive` call in `buildChildPatterns`. It returns
   AE2's answer unchanged and remembers a rejected candidate on that node.
2. At the actual simulated `CraftingCalculation.addMissing` call, it records the
   key only when recurrence rejected a candidate, no eligible child remains,
   and the missing amount is positive. `RecurrentMissing.record` owns this rule
   in pure Java. Storage, emission and eligible alternatives retain AE2's behavior.
3. `mixin/CraftingCalculationMixin` clears observations at every
   `runCraftAttempt` entry. On a non-null result it attaches an immutable
   intersection with the final plan's positive missing keys; nonsimulated
   success has no recurrence keys. Nodes retain rejection metadata because AE2
   can reuse its tree across attempts, including CRAFT_LESS.
4. `mixin/CraftingPlanMixin` implements `PlanRecurrence` on the returned plan.
   It retains exact AEKey identity, including variant data. Future completion
   publishes the attachment; there is no process-global map, profiler or saved state.
5. `mixin/CraftingPlanSummaryMixin` currently injects at `fromJob` RETURN.
   It flags only existing summary entries with a positive missing amount and a
   key in the plan attachment. AE2 computes those displayed quantities from
   current storage and emitter state. Mixed shortages keep AE2's total amount.
6. Native `CraftConfirmMenu.broadcastChanges` obtains the future, creates the
   summary and sends `CraftConfirmPlanPacket`. Client `setPlan` installs it.

Inspection of the installed AE2 19.2.17 bytecode confirmed the recursion,
terminal addMissing, summary factory and native send seams. The shared hooks
also serve Forge/Fabric 1.20.1 and NeoForge 26.1.2; the supported-target source
of truth remains `scripts/release-matrix.json`. A changed hook must be checked
against their resolved artifacts, including Fabric remapping.

Common server hooks and carriers load on both physical sides; renderer hooks
stay client-only. Use version source sets only for actual signature differences.
A missing required hook fails verification. A plan without recurrence evidence
keeps native Missing text and cannot damage calculation or learned timing data.

## Transport and plan identity

`CraftConfirmMenuMixin` sends diagnostic chunks at broadcast tail after the
native summary. Forge 1.20.1 uses `CraftConfirmMenuMixinSrg` for its mapped
broadcast method. Each remembers the sent summary object and increments a long
revision for each new summary; client `setPlan` increments its revision and
clears the incoming row flags before installing it.

`PlanRecurrenceS2C` uses each target's existing `StatsNetwork`. Native and mod
payloads go to the same player connection. Handlers must install the native
summary before applying its diagnostic on the client thread. There is no
evidence in #408 that warrants adding a queue or changing that wire contract.

Each chunk contains containerId, positive revision, entryCount, offset,
rowCount (1..256), and a fixed 32-byte bit mask. It describes original summary
indices before sorting. Only chunks with set bits are sent; no evidence means
no chunk. `PlanRecurrenceChunk.validFor` requires the active menu's matching
container/revision/count, an aligned nonnegative offset, the exact remaining
row count and zero bits beyond rowCount. Bounds cannot overflow or allocate a
collection from an untrusted count. Application is idempotent and ignores rows
without a positive missing amount.

The row bound is `PacketLimits.MAX_KEYS` (256): require `offset % 256 == 0`
and `rowCount == min(256, entryCount - offset)` after checking bounds. Validate
against the installed native summary rather than allocating from entryCount.
At most `ceil(entryCount / 256)` diagnostic chunks describe one summary.

Flags stay on existing entry objects, so sorting and distinct resource variants
cannot move a warning to another row. Closed, replaced or disconnected menus
and early/late revisions reject diagnostics without queuing them. Packets go
only to the player already receiving that server-authorized plan; there is no
C2S diagnosis query or client-selected network access.

Forge and both NeoForge targets retain their versioned registrations. Fabric
checks capability before sending `plan_recurrence_v1`. Existing loader mismatch
rules remain intact; an otherwise permitted unsupported peer keeps native
Missing text. AE2 packet bytes and SavedData do not change. Extra work is linear
in the summary and payload size is constant per chunk.

## Rendering

`CraftingPlanSummaryEntryMixin` supplies the transient boolean.
`CraftConfirmTableRendererMixin` consults it together with positive missing
amount and enabled mod state. It wraps AE2's Missing component construction in
both description and tooltip, preserves AE2's formatted amount, and adds the
explanation before the TTC-only craftAmount guard. It does not replace strings
by translated-text matching or overwrite the renderer.

`TtcText` provides the English/Ukrainian `plan.recurrent` and
`plan.recurrent_hint` components. The current implementation uses Minecraft red
without bold or a compact badge. Missing-first sorting, TTC values,
stored/craft quantities and Start behavior remain unchanged. Disabling hides
the label; re-enabling may use only the still-current summary's evidence.

### Planned shared warning presentation (#496)

Implement [R8](spec.md#acceptance-criteria) through the existing text and badge
paths. `TtcText.recurrent` owns the normal-weight red component shared by the plan row
and tooltip. Add only `text.ae2craftingtime.plan.recurrent` to
`CraftingRowState.isBadge`'s existing key set. Keep the explanation key out.

Both `mc1201` and `mc2612` versions of `AbstractTableRendererMixin` already
consult that shared predicate when drawing table text. Reuse their measured
component width, two-pixel padding, shadow, and `TtcBadge.fillRoundedRect` with
`TtcBadge.BACKGROUND` (`0xB0000000`). These source sets cover the supported
1.20.1 Forge/Fabric, 1.21.1 NeoForge, and 26.1.2 NeoForge targets. Check the
resolved source-set wiring during implementation. No new renderer or per-target
badge policy is needed.

Badge drawing is a table-rendering concern. The tooltip receives the same normal-weight
red component through `CraftConfirmTableRendererMixin`, using its existing
panel background. Preserve the supplied amount argument and plain explanation.
The existing recurrence/enabled/positive-missing guards still control whether
that component exists, so the badge disappears with it after replan or disable.
Do not add cached visual state, protocol fields, or a profiling requirement.

Keep Recurrent outside TTC color interpolation and provider-warning highlight
eligibility. Its shared badge membership changes only presentation. Preserve
AE2's missing-row background and the behavior of other badge keys.

Update `TtcTextTest`'s normal-weight/non-badge expectations to normal-weight red/badge
membership while retaining both locales and formatted-amount assertions. The
existing `recurrent-plan` driver and `scripts/ui-smoke-groups.json` currently
call the style checkpoint `red-warning-style`; update them together to verify normal-weight red
text and an actual containing badge. Retain row/tooltip screenshots and compare
with an existing red warning, including the 26.1.2 renderer path. A component
assertion alone does not prove the background was drawn or the text fits.

## #408: overlapping summary callbacks

The reported native plan has the recurrence implementation installed on both
peers. Inspected recurrence class bytes match the current dist classes. This
rules out a pre-feature binary for those classes, not every environment cause.

Installed AE2: Crafting Tree 1.21.1-1.1.1 contains
`com.neuvillette.ae2ct.mixin.AE2CraftingPlanSummary.buildEX`. Its `fromJob` TAIL
injection is cancellable and calls `setReturnValue` with the same summary after
attaching its own RecipeHelper. The failing server's mixin log lists that
summary mixin before ours. Our independent RETURN callback could therefore be
bypassed by the earlier cancellation: calculation keys would exist, summary
flags would remain false, and the sender would produce no diagnostic chunks.

This is the leading hypothesis, not a confirmed root cause. The available
exports do not contain the transformed summary class. Confirm with a controlled
Tree toggle and stage observations, or inspect the transformed instructions
and reproduce the same callback behavior in a focused runtime check. Do not
infer execution order from log order alone.

Keep the other hypotheses distinguishable: request quantity 100 might reach a
different terminal shortage; a valid chunk might fail the active menu/revision
guard; or the wireless route might differ. Inspect their actual values before
changing classification or transport. AdvancedAE's inspected crafting-service
hooks affect CPU listing, ticking and submission, not the calculation entry.
The selected Quantum CPU is not evidence that it caused the failure.

If callback bypass is confirmed, make enrichment compose at the shared summary
factory boundary, including returns from another callback. A MixinExtras outer
method wrapper is a candidate: call the original once, enrich the final result,
and return that same object. Verify the installed MixinExtras API and transformed
behavior before choosing the mechanism. Extract any new Minecraft-free branch
into covered shared logic. Do not solve this by changing addon load order or
mixin priority, duplicating terminal-specific marking, altering native packets,
or requiring Tree. Preserve other addons' fields and all native row objects.

## Verification boundary

The existing `RecurrentPlanFixture` and `StandardAe2Scenario` provide real
processing patterns, calculation/summary assertions and row/tooltip evidence.
Their normal path opens a block terminal and accepts the amount-screen default.
The large-input amount case does not request 100 outputs. Add the exact #408
pattern graph and explicit 1/100 requests to this existing path.

`RecurrentPlanObservation` and its driver-only mixins already observe native
installation, chunks, quantities and stale/malformed inputs. Reuse them to
identify where evidence disappears; positive flags must still come from AE2's
calculation. `AdvancedAeFixture` can place a Quantum Computer, but its direct
cluster submission does not prove native menu routing. Reuse existing wireless
fixture facilities for the WCWT route; do not treat a CPU submission pass as
that route's recurrence proof.

The compatible NeoForge profile pins Tree 1.0.1 and WCWT 1.3.8, while the report
uses Tree 1.21.1-1.1.1 and WCWT 1.3.9. Use an explicitly identified diagnostic
graph for those installed versions without silently updating compatible pins
or supported minimums. Prior connected recurrence evidence used only AE2 and
GuideME, so it cannot certify the addon combination.

Reuse `run-ui-smoke.ps1`, `run-ui-smoke-matrix.ps1` and
`run-connected-dedicated-ui-smoke.ps1`. Keep CPU-list's default, marked disposable
loopback worlds, source/dependency/launcher hash validation, one 8 GiB client at
a time, exact process cleanup and recipient/menu/revision acknowledgements.
The connected leaf remains `recurrent-plan-connected`. Replan, another grid and
reconnect use the existing single-client lifecycle. Never add a general live-server
driver or claim simultaneous-player proof. Test-driver changes stay out of dist.

The [implementation plan](implementation-plan.md) maps the focused regression
and verification to R1-R7. Separate screens, recipe repair, CPU execution,
persistence, TTC calculations and releases remain outside this fix.
