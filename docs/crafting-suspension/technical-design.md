# Crafting suspension technical design

Lifecycle and acceptance: [specification](spec.md).

## Evidence and existing flow

Investigation base: `2f315e2683bcd52a74a691d5095620f0ca30dc67`.
The native reference is AE2 revision
[`db17504a86128fdf3dae31f5fb7a112a646e0b93`](https://github.com/AppliedEnergistics/Applied-Energistics-2/tree/db17504a86128fdf3dae31f5fb7a112a646e0b93).
Its `ExecutingCraftingJob` persists `suspended`; `CraftingCpuLogic` stops dispatch
after cancellation checks; `insert` still accepts outputs. Its CPU screen places
Suspend at left 103, bottom 25, width 50, height 20; Cancel starts at left 163.

Both Forge AE2 15.0.10 and 15.4.10 expose the required public `executeCrafting`,
`hasJob`, `getLastLink`, `readFromNBT`, `writeToNBT` and cluster `markDirty` seams.
`ICraftingLink.getCraftingID()` supplies a stable job UUID. The logic tick checks
activity, drains idle inventory and handles cancellation before calling
`executeCrafting`. `insert` and `finishJob` remain untouched for item movement.

Existing common CPU mixins call `ProfilerBridge` for submission, dispatch,
returns, completion and capacity/notification ticks. `StatsRequestHandler`
collects selected rows and totals; `CpuTtcRequestHandler` collects listed totals.
`CraftProfiler` owns scoped pending/waiting/diagnostic state but shares completion
intervals by network and output. `DelayedNotificationServer` and
`BlockReasonNotifier` also run with screens closed. All these consumers must
respect suspension; masking screen text alone is insufficient.

## Forge state and hooks

Add one small Minecraft-free `CraftingSuspension` state/policy class under
`shared/src/main/java/.../core`, with full branch/line tests. It owns a boolean,
validates desired-state requests and defines default/disable/reset transitions.
A narrow interface implemented by the Forge logic mixin exposes that state to
adapters. No registry of jobs, replacement scheduler or copied AE2 class.

Register a separate Forge-only required mixin configuration in its build and
loader metadata. The logic mixin uses these exact seams with `remap = false`:

- `executeCrafting` HEAD: return zero only for supported suspended jobs. This
  common dispatch entrance covers direct calls too; normal tick cancellation and
  used-operation bookkeeping still run. Require this gameplay injection.
- `tickCraftingLogic` HEAD: reconcile disabled config and loaded state, without
  cancelling the tick. Clear/dirty a suspended flag when disabled, even when the
  cluster is offline or profiling is disabled. Refresh profiler suspension state.
- `trySubmitJob` RETURN: reset only after a successful submission; rejected
  submissions must not change an existing job.
- `finishJob` HEAD: clear suspension and scoped diagnostics before normal finish;
  never alter the success argument or inventory. Completion during pause is legal.
- `readFromNBT` RETURN and `writeToNBT` RETURN: read/write boolean
  `ae2craftingtime:suspended` inside the existing `job` compound only. Missing,
  wrong-type or absent-job state reads false. Native NBT is otherwise unchanged.

Mutations dirty the cluster. Support is exact standard cluster and logic runtime
classes; subclasses/replacements fail closed. This keeps independent AdvancedAE,
NeoEco and Lightning Tech engines outside the new behavior. Do not suppress
required-hook failures with `require = 0`.

## Actions and synchronization

Append `CraftingSuspensionC2S` to the existing Forge `StatsNetwork` channel with
`PLAY_TO_SERVER`, following `CpuTtcRequestC2S`: enqueue on the server thread and
resolve the sender's current `containerMenu`. Its fixed 29-byte payload is
`int containerId`, `long cpuContext`, UUID (two longs), and one desired-state
byte, accepting only 0 or 1. Reject wrong lengths, trailing bytes, invalid
context, zero UUID and malformed state before mutation. Put binary validation
in the covered pure-core codec; the Forge adapter delegates to it.

`cpuContext` uses the complete value from `StatsRequestContext.cpuContext`:
the container ID in the high 32 bits and the unsigned selected serial in the
low 32 bits. A direct CPU menu uses unsigned -1 only for its serial component.
Require the sender's current
`CraftingCPUMenu`, matching container/context, `stillValid(sender)`, standard
CPU/logic, a live job with matching native crafting-link UUID, channel support
and enabled server feature. Resolve the CPU through the existing
`CraftingCPUMenuAccessor`, never from an arbitrary client-supplied location.
Rejected input changes nothing; repeated desired-state requests are no-ops.
Ordinary players can control a job under native menu access; only server-option
editing requires operator permission. Guard outbound packets with existing
`StatsNetwork.canSend()`. Do not add native action registration, JSON parsing or
an `AEBaseMenu.receiveClientAction` injection.

Do not add `@GuiSync` fields. AE2 15.0.10 `DataSynchronization.readUpdate` cannot
skip an unknown field's typed payload, so extra fields would break server-only
installation. There are therefore no new native sync IDs or collision exposure.
Append a companion Forge S2C packet to the existing `StatsNetwork` channel:
`CraftingSuspensionS2C`, with fixed fields `int containerId`, `long cpuContext`,
one state byte and UUID (two longs), exactly 29 bytes. Bits encode supported,
enabled, has-job and suspended; reject unknown bits, inconsistent combinations,
invalid context and trailing/truncated bytes. A live job must have a nonzero UUID. The zero UUID is used only without
a job. Encode/decode/validation live in covered pure core; Forge buffer and
client-thread handling are thin adapters.

After native `broadcastChanges` sends its selected-CPU sync, send a changed snapshot, including an initial
snapshot, to that menu's player only when `StatsNetwork.canSend(player)` is true.
Clear state when no CPU/job is selected. The client accepts it only for its
current CPU menu and matching container/context; store state on that menu, never
globally across screens. Network order handles snapshots; a changed selection
invalidates old state immediately. Job UUID validates later clicks. No new
packet travels to absent peers. Without a confirmed supported snapshot the
button is hidden. Menu close discards state; reopen receives a fresh snapshot.

## UI and configuration

A Forge-only optional screen mixin adds a normal Minecraft button anchored to
Cancel's actual bounds: x = cancel.x - 60, same y, width 50 and height 20.
Refresh after init/resize and render; preserve native Cancel and other widgets.
Use `require = 0` for presentation injections with both mapped/SRG targets as
needed. Runtime assertions must prove the button appears; a silent skipped hook
is not passing evidence. Use a shared badge for the Suspended total label under
the existing background preference. Row suppression is keyed to the current
menu state, so a cached old stats snapshot cannot override suspension.

Append `CRAFTING_SUSPENSION(SERVER, GENERAL, "craftingSuspension")` to
`OptionFeature`; default true under `FeatureOptions`. Add ServerConfigFile's
required description. Hide this feature from `ServerOptionsScreen` unless
`IntegrationPlatform.TARGET` is `1.20.1-forge`; key storage elsewhere is inert.
Use existing world config, permission checks and save-before-apply transaction.

Append the enum entry to preserve old bit indices. Keep ServerOptionsWire's
38-byte shape but advance VERSION 2 to 3. Coordinate Forge channel 25 to 26,
NeoForge registrar 24 to 25 in both targets, and Fabric server-options channel
suffixes v2 to v3. Do not change unrelated Fabric channel IDs. Absent peers remain
allowed; old installed incompatible option payloads are rejected, never silently
misread. Cover these coordinated boundaries without adding suspension hooks or
visible options on unsupported targets.

## Diagnostic lifecycle

`CraftProfiler` owns a scoped suspended set and exposes transition/query methods.
Only Forge adapters activate it. On suspension, preserve pending amounts,
waiting-key membership, estimates, owner and learned history; clear that scope's
capacity, dispatch-power/provider blocker observations and delayed episode state.
Remove its automatic highlight contribution through existing `clearScope`;
shared provider plates retain other scopes. Invalidate only its pending
`TtcAccuracyTracker` job with the existing unsuccessful-finish path.

While suspended, suppress scoped waiting/stall/blocker answers and estimates,
including remembered-status fallbacks. Every notification entry point and
no-space probe bypasses suspended scopes, including direct request-triggered
paths. Reconciliation must clear existing contributions rather than just return
and leave stale highlights. Returning outputs still complete normal profiling
and native accounting. Never shift/reset global completion intervals: in-flight
work is actual work, and unrelated CPUs can return the same output.

On resume, rebase each retained last-progress tick and waiting start to current
tick, clear stale block observations/capacity and start a fresh delayed episode.
For the remainder of this job, ignore remembered global diagnostic fallbacks for
this scope; live observations remain authoritative and other scopes keep their
history. Fresh dispatch can rediscover genuine blockers. Keep membership and remaining
amounts. Clear suspension on finish/reset/reload of profiler state; each loaded
Forge logic tick re-establishes its actual persisted state. Unknown post-reload
runtime estimates stay unknown. Selected rows/totals override stale client cache
immediately; CPU cards receive unknown via the existing bounded refresh protocol,
with immediate masking for the selected suspended CPU.

## Documentation and verification boundaries

Add `features/crafting-suspension.md` and its `_uk_ua` counterpart under the
existing GuideME root, link both feature indexes and configuration pages, and
update both locale JSONs. Publish equivalent wiki feature pages plus
`Feature-Configuration.md` / `Ukrainian-Feature-Configuration.md` in the separate
wiki repository, preserving existing links and unrelated content. Update
`docs/dependencies.md` and the test-driver scope docs for the new coverage.

Verification uses existing real grid/furnace/multi-CPU fixtures and packaged
production/driver artifacts. The [plan](implementation-plan.md) explicitly owns
the missing suspension cases, two-client coordination and server restart phase;
no new generic harness or installer is required.



## Supplemental runtime checks after the automated leaf

The leaf's PASS is not all of CS-6. On the same tested production bundle,
retain the completed singleplayer fixture with the existing `-Interactive`
mode and use the VM's normal input plus `minecraft_get_ui_snapshot` and
`minecraft_take_screenshot`. The two standard CPUs and furnace providers remain
in that disposable world. Stock raw iron and fuel only; every returned ingot
must come from those furnaces. With the automatic leaf finished, collect real
furnace outputs and insert them through the ME terminal to drive returns.

Use the existing native read-only command `/data get block <x> <y> <z>` at each
observed CPU root for authoritative job facts. A cheats-authorized singleplayer
observer can run it in normal chat; dedicated checks use the existing operator
console/RCON or operator observer without granting the tested non-operator
editing permission. Capture the native command output in chat screenshots and
the corresponding CPU UI before and after each return. Narrow reads of
`job.link.craftId`, `job.waitingFor`, `job.tasks` and `job.remainingAmount` avoid
truncated output: retain all four integer UUID components, waiting item amounts
under `#`, task amounts under `#craftingProgress`, remaining amount and the
`ae2craftingtime:suspended` byte. These are the native facts already read by the
driver's `SuspensionState`; do not write NBT. The UI snapshot's `jobId` is an
output key, not the native UUID, and completed-leaf fixture observations are
stale. No new MCP field or observation harness is needed.

1. Keep profiling, accuracy and delay warnings on. Read the configured
   `maxSamples` and current iron accuracy count before the baseline and again
   before the job pair. Leave spare capacity for every successful untouched job
   through the final count; if needed, enlarge the existing Server Options
   value through Done, up to its supported maximum of 100, preserving history.
   An unchanged full-window count proves neither sample addition nor omission.
   Keep this baseline, pair and final count in the same loaded world session:
   `ProfilerBridge.load` replaces runtime accuracy and pending-job tracking;
   persisted throughput history does not preserve that accuracy experiment.
   Finish one normal small iron
   job to establish a real learned estimate. Record the iron accuracy sample
   count through the existing TTC details/chat before and after each later job.
   Submit two estimated jobs on separate CPUs. Pause/resume only one of them;
   after both finish, the untouched job must add one accuracy sample and the
   paused job must add none. Retained throughput history must remain available.
2. To prove overlapping warnings, withhold the real furnace outputs from AE
   storage after each CPU has dispatched. For the controlled setup below, pause
   the large job while it still has undispatched work; withhold furnace fuel as
   needed to keep the native
   machine capacity and queued work observable. Collect genuine furnace outputs
   into the player inventory, let the second CPU dispatch, then resume the first
   for the warning baseline. Keep the real items for later return. Record both
   native job UUIDs and each CPU's undispatched, waiting and remaining amounts.
   Wait for both jobs to show Delayed and the shared provider highlights.
   Suspend the large job: its selected rows/title lose diagnostics immediately while
   the other CPU stays Delayed and its provider highlight remains.

   Shared-storage iron returns have no CPU identity. Before and after each
   terminal return, record both UUIDs and per-CPU amounts to identify the actual
   recipient; never assume which job consumes an ingot or finishes first.
   One controlled setup is to keep the large job suspended with undispatched
   work greater than zero and drain its finite in-flight waiting outputs using
   the retained real items. Record waiting = 0 and remaining > 0 for that same
   paused job, with the other job still active; further genuine returns cannot
   fill the paused job's undispatched work. This setup is optional: direct
   before/after per-CPU observations that prove the other UUID completed while
   the paused UUID stayed active with remaining > 0 also satisfy the check.
   In either case, observe the other job actually complete: the completed
   job's highlight contribution must disappear without the paused job restoring
   it. Record both selected CPU screens, warning chat and world highlights,
   not only a global output-level boolean. If return ownership, other-job
   completion or the paused job's continued activity cannot be established,
   record an incomplete check and diagnose
   the fixture state; do not claim PASS or repeat the same sequence blindly.
3. In the terminal Crafting Status screen, select each CPU and switch back,
   including automatic selection after cancellation. Verify the suspended
   selected card immediately has no numeric estimate; other cards retain their
   own estimates. Close/reopen the menu and resize once. Record context, job
   identity, title and button geometry from observed UI snapshots and PNGs.
4. With a suspended job selected, switch Badge background off, then on with a
   non-default color/opacity through Client Options and Done. In both states
   record the actual Suspended title text and bounds; the enclosing fill must
   appear only when enabled and use the configured appearance. Inspect row
   tooltips too: no Waiting, Delayed or blocker text from cached data survives.
5. Resume while still withholding outputs. Record the configured delay threshold
   and observe no immediate warning, followed by a fresh genuine delayed episode.
   Return the retained real outputs and record normal completion/conservation.

Also exercise a non-operator connected client: it can Suspend/Resume an
accessible native job, cannot edit Server Options, and a denied edit leaves the
server revision/config unchanged. Read the saved server file after Done.
Prove file-based recovery in a separate disposable-world campaign on the same
tested bundle: save a genuinely suspended job, stop the world cleanly, preserve
the original stopped-world/config backup, set `craftingSuspension = false`,
and restart that recovery world. Record its job identity/counts and the loaded
config, then verify next-tick unstranding and normal completion. Do not edit job
NBT or reuse the connected persistence campaign's world or backup. That campaign
keeps exactly two phases and verifies suspension survives its enabled restart;
file-based recovery adds no phase to it. These checks remain mandatory and
pending until
steps 3 and 4 of the [verification ladder](implementation-plan.md#verification-ladder-and-commands)
retain their current-head evidence; no automated marker substitutes for them.
Optional-install observation and unsupported-loader artifact checks
remain separate gates in the implementation plan.
