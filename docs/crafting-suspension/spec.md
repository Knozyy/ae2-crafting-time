# Crafting suspension on Forge 1.20.1

Status: in-progress

Scope: Initial Forge 1.20.1 backport and its focused verification.

Issue: [#631](https://github.com/cTux/ae2-crafting-time/issues/631)

Planning: [Reviewed implementation plan](implementation-plan.md)

Implementation: [PR #637](https://github.com/cTux/ae2-crafting-time/pull/637)

Remaining gates: retain the supplemental manual diagnostics, accuracy and UI
evidence; non-operator permission checks; stopped-file recovery with
`craftingSuspension = false`; optional-install peer checks; published wiki
readback; current-head CI and review; and the implementation merge. Complete
the [verification ladder](implementation-plan.md#verification-ladder-and-commands),
including dedicated restart evidence and verified VM shutdown, before finishing.

## Player behavior

Suspend a large craft so smaller jobs on other CPUs can use the same providers
as work already inside machines clears. Resume continues the same job, with its
reserved ingredients, completed work and remaining work intact.

On Minecraft 1.20.1 Forge, native AE2 crafting CPU and terminal status screens
show **Suspend** immediately left of **Cancel**, matching AE2 1.21.1's 50-by-20
button and ten-pixel gap. It becomes **Resume** while the selected job is
suspended. Empty, unsupported or unavailable jobs cannot receive an action.
Cancel remains usable while suspended. Switching CPUs never applies an old
button action to the newly selected CPU or a replacement job.

Suspension stops new pattern dispatch. Outputs already in flight still enter
the CPU, update its progress and may finish the job. It does not stop external
machines, release the CPU, release reserved ingredients, change priorities or
promise that all machines become available immediately.

The job remains suspended across menu reopen and world/server reload. Every
connected supported client sees the server's state. An unsupported client or
server keeps normal AE2 menus and receives no unsupported custom packets.

## Configuration and diagnostics

The world-owned server option `craftingSuspension` defaults to `true` and appears
under **Server Options / General** on Forge 1.20.1. Existing operator permission,
revision validation and Done/save behavior apply. File edits load at world/server
startup. The option is independent of profiling's `enabled` setting.

Turning the option off hides its control and rejects suspension requests. Each
loaded suspended CPU clears its flag on its next logic tick; unloaded CPUs do
the same when they load. Jobs therefore resume when their normal AE2 conditions
allow. Re-enabling the option does not suspend anything automatically.

The selected suspended job shows **Suspended** in its total/status presentation
instead of a numeric finish estimate. Its rows do not claim Waiting, Delayed,
No provider, No power, No space or another blocked condition caused by intentional
suspension. Its CPU-card estimate becomes unknown on the next normal refresh;
selecting it suppresses a cached numeric estimate immediately. Server warnings
and automatic provider highlights for that job stop, without clearing another
job's warning for the same output/provider. Resume starts a fresh delay interval
and clears stale blocker observations; genuine failures can then be detected
again. Retain waiting membership, pending work and learned samples.

Already-dispatched returns remain real throughput observations. A job suspended
at least once contributes no job-accuracy sample: its intentional pause is not a
prediction error. Other jobs and their accuracy/history remain unchanged. After
reload, use the existing unknown-estimate behavior where runtime profiling data
is unavailable; do not fabricate a completion time.

## Compatibility and exclusions

Only the standard `CraftingCPUCluster` with the standard `CraftingCpuLogic` on
Forge 1.20.1 gets this backport. Addon replacement CPU/logic classes fail closed:
no suspension control or scheduling interception for them. Ordinary machines or
providers used by a supported standard CPU are not excluded.

Support the existing minimum AE2 15.0.10 API and the prepared compatible runtime
AE2 15.4.10 / Forge 47.4.23 / Java 17. No Fabric suspension feature, replacement
addon-CPU backport, scheduler rewrite or change to native suspension on newer
Minecraft versions is included. Shared configuration serialization can advance
its protocol coherently across targets, but unsupported targets hide the option
and never consume it for gameplay or presentation.

English and Ukrainian GuideME and published wiki pages explain the feature,
option, safe disable behavior, addon limitation and limits of machine relief.

## Acceptance criteria

| ID | Required evidence |
|---|---|
| CS-1 | Correct native-screen button names, placement, selected job and empty/unsupported handling; Forge production startup and remapped hook proof. |
| CS-2 | A large and small job share real providers on separate CPUs; the small job finishes while the large job remains suspended, then the same large job completes with exact conserved counts. |
| CS-3 | In-flight returns, completion during suspension, repeated idempotent requests, another CPU, stale selection/job actions and cancellation are safe. |
| CS-4 | Two real clients agree; screen reopen and a clean dedicated-server save/restart/rejoin retain suspension and permit resume. |
| CS-5 | Default/file/UI configuration, server rejection while disabled, permission checks and automatic unstranding work with profiling on and off. |
| CS-6 | Suspended presentation, row/card/total suppression, warning/highlight cleanup, resume delay timing and per-job accuracy policy are correct without affecting another job. |
| CS-7 | Both locales and live wiki guidance agree; unsupported loaders/native versions and optional-install peers retain their behavior. |
| CS-8 | Covered core and boundary checks, focused singleplayer and dedicated production runs, current-head CI and exact VM shutdown evidence pass. |

Planning review found no issue-body correction necessary. This status records a
reviewed plan, not shipped behavior; implementation and runtime checks remain.
