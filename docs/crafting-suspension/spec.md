# Crafting suspension on Forge 1.20.1

The finished standard-CPU backport below is a separate delivery scope from the
later [replacement addon CPU extension](#replacement-addon-cpus).

Status: finished

Scope: Initial Forge 1.20.1 backport and its focused verification.

Issue: [#631](https://github.com/cTux/ae2-crafting-time/issues/631)

Planning: [Reviewed implementation plan](implementation-plan.md)

Implementation: [PR #637](https://github.com/cTux/ae2-crafting-time/pull/637),
merged as `079c4be603b8f07e438fd55c49ea2911d10972a8`.

Verification: [PR #637's final evidence](https://github.com/cTux/ae2-crafting-time/pull/637)
records the production UI, accounting, diagnostics, accuracy, two-client restart,
permissions, file-disabled recovery and optional-peer checks. Final head
`e9c565619e93f9c60fe8719e55588380f1cb09ba` changes only planning documents from
tested `d5b10fe41013cd70db4a0a33d0f8f1fbe2366f7e`; unchanged source carries the
recorded runtime evidence, rather than claiming a new Minecraft run.
[Build](https://github.com/cTux/ae2-crafting-time/actions/runs/36927661822),
[tests](https://github.com/cTux/ae2-crafting-time/actions/runs/36927661466) and
the configured review passed on e9. All six English/Ukrainian
[wiki pages](https://github.com/cTux/ae2-crafting-time/wiki/Feature-Crafting-Suspension)
were published and read back at `733fd286d39231573daceeeb639f7b058bb6324b`.
Owned Java processes/tasks and the temporary token were cleared; CodexVM's
clean soft shutdown was verified at 2026-10-01 21:04:27.455 UTC.

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

The world-owned server option `craftingSuspension` defaults to `false` and appears
under **Server Options / General** on Forge 1.20.1. Existing operator permission,
revision validation and Done/save behavior apply. File edits load at world/server
startup. The option is independent of profiling's `enabled` setting.

Enable it to show Suspend/Resume. Existing files with an explicit value retain
that choice; missing values and Reset use the disabled default.

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

Planning review found no issue-body correction necessary. The initial scope is
delivered with the verification evidence linked above.

## Replacement addon CPUs

Status: ready-to-implement

Scope: Extend suspension to the project's supported replacement CPU engines on
Forge 1.20.1 only. Fabric and newer NeoForge targets are outside this extension.

Issue: [#647](https://github.com/cTux/ae2-crafting-time/issues/647)

Planning: [Addon implementation plan](implementation-plan.md#addon-cpu-extension)
and [addon technical design](technical-design.md#addon-cpu-extension).

The documents have been self-reviewed. The exact final issue text was approved
on 2026-10-05, applied to #647 and read back without differences. No addon
support is delivered yet.

### Player behavior and boundaries

Support AdvancedAE Quantum Computer CPUs, NeoEco ECO CPUs and LightningTech
time-wheel CPUs using their existing optional integrations. Ordinary addon
blocks that still produce the standard AE2 CPU and logic retain the existing
backport. Do not treat an arbitrary subclass as supported merely because it
inherits an interface; unrecognized replacement engines keep the control hidden.

Use the same Suspend/Resume control, job identity checks and world-owned
`craftingSuspension` option as the standard CPU. Suspension stops new work,
preserves reservations and progress, accepts already-dispatched outputs, permits
Cancel and completion, and survives a clean save/restart. It does not stop
external machines or release the CPU. All shared-provider and diagnostic rules
in the initial scope apply equally to addon jobs.

When an addon already has a persistent player-pause state, use that state rather
than keeping a competing boolean. NeoEco's player pause is distinct from its
internal suspension; Resume and option-disable recovery must never clear an
internal engine condition. The option governs the integrated player-pause state
on supported jobs, including one changed through the addon's own controls.
Turning it off clears that player pause on the next loaded logic tick, while
leaving internal engine conditions alone. This also works with TTC profiling off.

Observe native player-pause changes so menu state and TTC diagnostics agree.
Preserve existing addon controls and optional-peer behavior; show only one
Suspend/Resume control if an addon already provides one in the same screen.
TTC-originated requests retain the existing menu/context/UUID validation. Do not
replace an addon's scheduler, cancellation, soft-cancel or recovery behavior.

Suspension availability must not depend on enabling the addon's TTC profiling
option. Disabling a diagnostic integration must neither strand a paused job nor
remove access to Resume. A missing or incompatible suspension API hides only
that suspension adapter; it must not disable working profiling integration.

The LightningTech suspension capability floor is `2.1.0-beta.4-forge.1.20.1`,
the inspected artifact. Beta.2 and beta.3 retain their existing profiling
support but do not expose TTC suspension; test that capability boundary.
Later versions require a matching suspension contract before enabling it.
No broader dependency minimum or newest-release claim changes in this scope. Verify
the pinned compatible artifacts and retained supported API families. A changed
addon API needs a verified adapter before its control is enabled. LightningTech
source-discovery issue [#460](https://github.com/cTux/ae2-crafting-time/issues/460)
does not prevent inspecting the recorded cached artifact; it does prevent
claiming that artifact is the latest available release.

### Acceptance criteria

| ID | Completion gate |
| --- | --- |
| ACS-1 | AdvancedAE, both retained Forge NeoEco API families and contract-compatible LightningTech at or above the beta.4 suspension floor expose Suspend/Resume for the actual selected replacement CPU. Earlier LightningTech versions retain profiling only; unknown replacements and absent addons remain safe. |
| ACS-2 | Normal, batched, FastPath and time-wheel dispatch stop while paused; in-flight returns still count. A competing CPU finishes, then the same resumed UUID finishes with exact input/output conservation. |
| ACS-3 | Three pause/resume cycles, failed submission, completion, cancellation, soft cancellation and replacement-job/CPU-switch stale requests preserve native behavior. |
| ACS-4 | Two real clients, reopen and clean dedicated restart retain UUID/state/counts. Repeated and forged requests cannot mutate a different job. |
| ACS-5 | Live and startup-file disable recovery work with profiling and addon diagnostics off. Internal NeoEco suspension remains untouched, and native pause actions update TTC state. |
| ACS-6 | Paused rows, totals and CPU cards remain visible: the total says Suspended and the card estimate is unknown. Actual addon profiler scopes suppress estimates, false blocked diagnostics, warnings and automatic highlights on those surfaces without suppressing another job; resume starts a fresh delay interval and paused jobs contribute no accuracy sample. |
| ACS-7 | Contract selection, core coverage, transformed production hooks, focused runtime captures, English/Ukrainian GuideME/wiki and dependency documentation agree. Other target artifacts and optional-peer installs keep their existing behavior. |

Do not mark this scope finished until every row has source-bound verification
and the implementation has been merged with separate authorization.
