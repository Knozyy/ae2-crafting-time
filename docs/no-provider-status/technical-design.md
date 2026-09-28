# No Provider Status Technical Design

Lifecycle: see the [scope status and evidence](spec.md).

## Verified dispatch seam

`CraftingCpuLogic.executeCrafting` calls
`CraftingService.getProviders(IPatternDetails): Iterable` for each remaining
pattern task, even when its inputs are unavailable. Observe that result without
changing it. A nonempty list includes busy providers, so busy machines do not
become missing providers.

Source and bytecode inspection on 2026-09-03 verified this descriptor in AE2
15.0.10 (Forge/Fabric), 19.0.24, and 26.1.10-beta. AdvancedAE's
`AdvCraftingCPULogic` has the same call in 1.3.6-1.20.1, CurseForge file 7849217,
and 26.1.7. Extend the existing standard and optional pseudo-mixins. No new
dependency, mixin registration, or support range is needed. Custom CPU engines
that bypass these methods remain outside this status integration.

## Server state and recovery

Use a covered, Minecraft-free `MissingProviderTracker<P>` owned by `CraftProfiler`.
Both API variants of `ProfilerBridge` delegate to it. It keeps an identity map of CPU objects, then a
map of exact pattern tokens to their positive, network-scoped output keys.
The bridge supplies `IPatternDetails` tokens and converts output stacks to
amounts. The core owns positive-output filtering, replacement, and cleanup.

Only an actual empty dispatch lookup can add a pattern. A nonempty lookup
removes that exact pattern. When collecting a status snapshot, revalidate only
previously observed missing patterns against the selected CPU's current grid.
Remove restored patterns before returning the union of their output keys.
This read never creates a warning or enumerates unrelated network patterns.
Filter that union to requested keys before sending it.

This replaces the earlier draft's 20-tick output-level expiry: an arbitrary
expiry could hide a still-blocked pattern when the CPU uses its dispatch budget
elsewhere, and could retain a restored provider for an extra refresh. Exact
pattern revalidation also prevents one healthy recipe from clearing a missing
recipe that shares its output. It works while another batch is active.

Clear a CPU's records on accepted job replacement, finish, and cancellation.
Clear all records when profiling is disabled or samples are loaded. They never
enter SavedData. Retained state is bounded by patterns in outstanding jobs;
restored patterns and empty CPU maps are removed.

## Transport and client

The status snapshot now carries one bounded `blockReasons` map, shared with
NO POWER. The server returns at most `PacketLimits.MAX_KEYS`, validates output
ids, rejects unrequested keys and unknown reason values, and gives NO PROVIDER
priority when both causes affect one row. This replaces the set originally
shipped for NO PROVIDER, as approved during issue #121 implementation.

Current wire boundaries are Forge `9`, Fabric `stats_snapshot_v7`, and both
NeoForge registrars `8`. Saved data is unchanged. Fabric retains its existing
capability check.

`ClientStatsCache` replaces the blocker map for requested keys even
when no learned stats exist. Clearing selected-CPU state also clears this map
when opening the status screen or selecting another CPU. A snapshot also carries a `long cpuContext`, packing the menu container id and
selected CPU serial. The cache only exposes flags when that context matches the
current menu. Late replies and automatic CPU reselection cannot show another
CPU's warning. This tag applies only to the new diagnostic.

`CraftingRowState.blockReason` requires a positive pending amount and cached
server evidence. The status table checks it after stored-only NO SPACE and
before Waiting, DELAYED, or TTC. Both API variants of the status screen use
the same predicate to exclude blocked rows from TTC sorting and coloring.
Craft plan, Crafting Tree, and ME Requester do not consult this state.

`TtcText` supplies the normal-weight red label and the two exact issue tooltip sentences.
Add matching English and Ukrainian keys and recognize the label in the existing
compact-badge renderer on both API variants.

## Acceptance and checks

| Requirement | Proof |
| --- | --- |
| Only actual empty dispatch attempts create warnings | Tracker tests plus verified lookup hook |
| Second provider prevents warning; restoration clears it | Nonempty lookup and snapshot revalidation tests |
| Mixed active/pending rows and shared outputs | Tracker union and row predicate tests |
| Selected CPU, network, finish, disable, reload | Identity, key scope, clear and profiler lifecycle tests |
| No stats, stale replacement, CPU switch | Cache and packet round-trip tests |
| Bounded packets | Empty, maximum, excessive, malformed and unrequested-key decode tests |
| Every target and both locales | Shared boundary tests in all four modules; translated label/style checks |

GitHub's required test/JaCoCo workflow is the first test run after the hook
creates the PR. Runtime rendering is a separate evidence boundary; automated
logic and packet checks do not claim a live client smoke result.
