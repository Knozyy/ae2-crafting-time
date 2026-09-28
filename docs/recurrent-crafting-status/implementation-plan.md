# Recurrent crafting status: implementation plan

Lifecycle: see scope status and evidence for [detection baseline](spec.md) and [warning styling](spec.md#planned-warning-style-496).

Historical planning status: the original feature and #408 repair are implemented. The next change is
the warning styling requested in
[#496](https://github.com/cTux/ae2-crafting-time/issues/496), following the
[specification](spec.md#planned-warning-style-496) and
[rendering design](technical-design.md#planned-shared-warning-presentation-496).
This documentation merge leaves #496 open for implementation and verification.

## Planned #496 implementation

1. Change `TtcText.recurrent` to normal-weight Minecraft red and include its translation
   key in the existing `CraftingRowState.isBadge` set. Reuse both target-specific
   `AbstractTableRendererMixin` paths and `TtcBadge` geometry/background. Retain
   the existing recurrence guards, amount formatting, tooltip explanation, native
   row tint, and tooltip panel. Keep detection and provider highlights unchanged.
2. Update the existing localized component test's normal-weight/non-badge
   expectations. Update the `recurrent-plan` driver and smoke-group checkpoint
   from `red-normal` to `red-warning-style`, checking normal-weight red text and a rendered
   containing badge. Keep ordinary/seeded/alternative controls, all sort modes,
   no-sample cases, quantity/unit comparisons, replan/disable cleanup, and layout
   checks. Add coverage only for changed decisions.
3. Review and commit the implementation through the development/test-driver
   workflows. Let the post-commit hook create its PR before running local checks.
   Run the focused component/badge tests and applicable coverage checks, then use
   `scripts/run-ui-smoke.ps1 -Changed -BaseRef origin/master -PlanOnly` to select
   prepared-client checks. Verify all four target source sets; retain runtime
   row/tooltip evidence on both renderer paths, including 26.1.2. Compare the badge
   with an existing red warning. Reuse `recurrent-plan` and
   `standard-plan-controls`; runtime smoke is English, with both locales covered
   by resource/component and layout checks. Broaden only for a changed boundary
   or a failing control; the historical #408 addon investigation is not a new
   prerequisite for this styling-only change.
4. Once the new style has reviewed runtime evidence, update the English and
   Ukrainian Recurrent GuideME pages and their wiki source, remove the old
   normal-weight description, and refresh the shared screenshot through
   `refresh-guide-images`. Until then, keep shipped guide text and images truthful.
   Complete required current-head CI and review, merge the implementation, then
   close #496 with evidence for R8 and the preserved R1-R7 behavior.

## Historical #408 repair plan

The remaining sections record the repair merged in
[#410](https://github.com/cTux/ae2-crafting-time/pull/410). Investigation wording,
source pins, and launch budgets below describe that earlier task; they are not
current blockers or verification claims for #496.

## 1. Revalidate the existing verification environment

The investigation base is `01cd5d75862105d7af049f03c2e0d88d54f5b980`.
Host Java 21 was verified, but CodexVM was off. Earlier retained evidence names
a prepared NeoForge 1.21.1 client and a marked dedicated source server; their
current guest state must be rechecked after starting/reusing CodexVM through
`use-codex-vm`. Do not treat historical manifests as current verification.

Use the prepared manifest contract in [dev-client.md](../dev-client.md),
`prepared/1.21.1-neoforge/launch.json`, and the existing connected runner's
schema-2 source marker. Verify target, Java 21, native loader arguments,
dependency/launcher hashes, disposable-world marker, interactive session and
available memory. Preserve the source server. Missing preparation uses the
documented native-loader provisioning path, not guest Gradle or Prism.

Reread `scripts/run-client-versions.json` before freezing artifacts:

| Purpose | Minecraft/loader | AE2 | Addon boundary |
| --- | --- | --- | --- |
| First prepared reproduction | 1.21.1 / NeoForge 21.1.238, Java 21 | 19.2.17 | Diagnostic graph with installed Tree 1.21.1-1.1.1; compare without Tree |
| Reported route | Same prepared target initially | 19.2.17 | AdvancedAE 1.6.12 Quantum Computer and WCWT 1.3.9, with Tree installed |
| Exact-loader fallback | 1.21.1 / NeoForge 21.1.249, Java 21 | 19.2.17 | Use only if the prepared loader cannot reproduce the reported installation |

The compatible graph currently pins Tree 1.0.1 and WCWT 1.3.8. Keep the newer
observed versions in a named diagnostic graph with exact hashes and resolved
dependencies. Do not silently promote those pins or raise dependency minimums.
An exact-loader fallback needs its own matching native manifest before launch.
No new general runner is planned; surface an unexpectedly large infrastructure
requirement before expanding this issue.

## 2. Make the failure reproducible and choose the shared fix

Inspect all callers and target counterparts of the changed summary hook.
Extend the existing `RecurrentPlanFixture` and `StandardAe2Scenario` with
processing patterns for A from B, B from A, and C from A. Request 100 C
explicitly through the amount menu; also request 1 on the same graph.
Preserve existing recurrence cases and successful seed/alternative controls.

Use the existing driver-only observations to record final plan keys, positive
server summary flags, recipient/menu/revision, native client installation,
chunk application, and the actual row/tooltip. Compare the same scenario with
and without Tree in disposable runtimes. First use the block terminal to isolate
the summary callback; then exercise the WCWT native plan with a Quantum Computer
on that grid. Reuse existing addon/wireless fixture facilities and verify the
selected native menu. Direct cluster submission is not a route check.

The leading hypothesis is Tree's cancellable `fromJob` callback bypassing our
RETURN enrichment. Confirm a failing control before calling it the root cause.
If calculation keys are already missing, investigate rejection/eligible-child
and amount state instead. If flags exist on the server, inspect the actual
chunk identity and client menu before changing transport. Do not patch guesses.

For confirmed callback bypass, own the smallest change at
`shared/src/mcCommon/java/com/ctux/ae2craftingtime/mc1201/mixin/CraftingPlanSummaryMixin.java`.
Make enrichment operate on the final returned summary despite another callback's
return. Verify a composable MixinExtras wrapper against installed APIs; call the
original once and preserve its summary, entries, quantities and addon fields.
Do not use a load-order/priority workaround or add per-terminal adapters.

Keep Minecraft-free decisions in covered `shared/src/main/java` logic. Add the
smallest regression boundary that fails with an early-returning enrichment
callback and passes with the fix, alongside the real addon case. Preserve
exact-key and positive-missing classification, protocol bytes and saved data.
Update `docs/dependencies.md` with any confirmed change to integration behavior
and the actual verification limits during implementation.

## 3. Review, commit and run focused checks

Use the development and test-driver skills. Self-review and independent review
precede the conventional implementation commit. Follow AGENTS.md: verify the
one-time Git setup, let the post-commit hook create the PR, and only then run
tests. The failing-control test is written first but executed after that PR
exists, followed by the corrected behavior on the same frozen candidate head.
Keep GitHub CI separate from local verification.

After PR creation:

1. Run `scripts/run-ui-smoke.ps1 -Changed -BaseRef origin/master -PlanOnly` and
   retain selection reasons. Explicitly include the #408 diagnostic graph;
   ordinary compatible selection alone does not contain the observed versions.
2. Run focused shared/boundary tests and JaCoCo. New or changed executable
   decisions require 100% line and branch coverage. Check the summary hook and
   registrations against all four affected target artifacts, including Fabric
   remapping; compile the affected targets. Do not weaken coverage gates.
3. Run `recurrent-plan` and `standard-plan-controls` on prepared NeoForge 1.21.1,
   including the quantity-100 fixture and Tree toggle. Retain the red row,
   unchanged 100 quantity and tooltip explanation with matching server evidence.
4. Validate the WCWT 1.3.9 + Quantum Computer native-plan route with Tree installed.
   Preserve native craftability, CPU selection, quantities and Start behavior;
   never submit a simulated failed plan to create a sample.
5. Once the representative target passes, run the complete changed-target
   selection. All four targets remain supported; broaden runtime checks for
   materially different hook implementations or a detected mapping failure,
   not by replaying the original full-feature campaign automatically.
6. Run the existing NeoForge 1.21.1 connected recurrence gate last, with one
   client, for native/mod order, replan, another grid and reconnect. A base-only
   connected result proves that lifecycle, not the addon combination. The
   reported addon route must also have dedicated-server evidence with the same
   observed dependencies on both peers. Use a separately sealed matching graph
   and the existing marked runner; never bypass dependency equality checks.

Keep English runtime smoke and existing English/Ukrainian resource/component
checks. Retain TTC sort modes, no-sample behavior, ordinary/seed/alternative
negative controls, variants, malformed/stale chunks and quantity comparisons.
Reuse unchanged tests; add no duplicate suite merely to list these guarantees.

## Launch budget and evidence

Clients run sequentially, one 8 GiB client at a time. Before launching, record
the exact process count, graph, source/artifact identity and wall-time budget.
Provisionally budget two integrated client launches for the Tree toggle and
one dedicated-server/client pair for the combined route and connected lifecycle;
add selected target launches explicitly after plan-only review. Reuse a single
loaded fixture process for quantity and terminal comparisons when possible.

The earlier base-only NeoForge connected client took about 157 seconds; that
does not predict a new addon graph's cold start. Reserve ten minutes per initial
integrated launch and fifteen minutes for the connected pair, then revise from
observed phase timing. Keep existing bounded startup and active-checkpoint
timeouts. Diagnose a stalled stage without replaying proven targets. Any
temporary instrumentation or addon toggle is diagnostic-only; final evidence
must use the final frozen head and a clean complete scenario.

Archive logs, result files, PNGs/sidecars, actual recipient/menu/revision records,
production/driver/dependency hashes, adapter identity and exact process cleanup.
Review every non-PASS visual checkpoint. No screenshot alone proves negative
controls or state isolation, and no sequential session proves simultaneous
players. Keep private machine paths and server details out of public reports.

## Criterion-to-check mapping

| Criteria | Change or preserved boundary | Required evidence |
| --- | --- | --- |
| R1-R2 | Exact three-pattern graph and explicit 1/100 requests | Real calculation keys, summary flags, unchanged missing quantity, red row and hover explanation |
| R3-R4 | Existing rejection/eligible-child rule, final intersections, exact keys | Existing covered classification tests and seed/alternative/ordinary/mixed/variant controls; no false-positive flags after the hook change |
| R5 | Existing recipient/menu/revision and clear-on-setPlan | Ordered native/chunk observations, stale/malformed checks, dedicated replan/grid/reconnect lifecycle |
| R6 | Shared summary/renderer contract on four targets | All-target hook/API/compile checks, changed-target runtime selection, locale/component checks and standard-plan-controls |
| R7 | Composable final-summary enrichment and unchanged native/addon state | Failing early-return control, Tree toggle, WCWT + Quantum dedicated route, unchanged quantities/craftability/submission/packet and persistence formats |

## Completion gate

The callback hypothesis is confirmed or replaced by observed root-cause evidence,
the smallest shared correction has a failing-control/passing-fix regression,
and every mapped check has current-head evidence. Required GitHub CI and review
must pass separately. Record which exact addon graph and terminal route ran;
never transfer a base-only pass to the reported combination. Keep #408 open
until its verified implementation merge and closure workflow finish. This plan
does not authorize a release.
