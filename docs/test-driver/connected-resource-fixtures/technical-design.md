# Connected resource fixture design

Lifecycle: see the [scope status and evidence](spec.md).

Tracks [#482](https://github.com/cTux/ae2-crafting-time/issues/482).
Original source baseline: `b94d1a4264d0d299945178fb542d4afcc8ba2617`.
Expansion documents are based on `d7a867c`; implementation evidence below reaches
`5294f0d9e7123b3fda2e680d1284f71186973e9c` on #484. These are historical design
inputs; the specification links the later merged RF1-RF8 qualification.
The [specification](spec.md) defines the boundary; this design defines the new
driver-only control contract. Nothing below changes a production protocol.

## WATER unload/reload repair (#628)

See the [repair scope and criteria](spec.md#water-unloadreload-repair-628).
The investigation baseline is `3702b556496b4a1fc6e84270ef46599fc748b084`.

`ResourceFixtureClient.tick` schedules integrated server work before polling
the pending acknowledgment. Connected `DedicatedCpuScenario` calls the same
`ResourceFixtureServer.tick`. The server retains `operationInFlight`, runs
`apply`, and writes the state and receipt only after the operation returns true.
An unchanged acknowledgment therefore does not prove a missed command.

`ResourceFixtureServer.unloadReload` releases owned connected chunk tickets,
teleports away, polls `!hasChunkAt(provider)`, clears cached CPU identities,
teleports back, then waits for the provider, `grid.prepare` and
`processing.delayed`. The latter checks production delayed state for the
reconstructed grid's network identity. Shared server/client/processing classes
serve all four targets; 26.1.2 has its own `StandardCraftFixture` and native
platform adapters. Both fixture variants already move production grids 1024
blocks from the source origin. Integrated runs do not force resource chunks;
connected runs release only tickets they own, preserving preexisting tickets.

The four archived manifests match the hashes in #628, and all report timeout
with successful cleanup. None records the pending operation or unload phase.
Chunk retention, a blocked server callback, and grid/job/delayed recovery remain
hypotheses. Do not replace the acknowledgment contract or rebind estimates
merely because those changes look plausible.

First add a small bounded snapshot to the existing retained fixture evidence:
pending sequence/action/case, accepted tick and poll count, unload phase and
observed-unload flag, player/provider positions, and safe loaded/grid/job/delayed
readiness facts. Read native readiness only when the relevant chunks and nodes
exist; diagnostics must not load the chunk they observe. Retain this snapshot
before cleanup, alongside the original failure, using the current control and
evidence path. Record phase changes or the terminal snapshot instead of writing
unchanged state every poll. No second transport or early acknowledgment.

Use a focused Forge run to identify the first gate that stops progressing and
whether sequence 8 was accepted. Then make the smallest correction at that
shared cause and add its executable regression. If evidence points into a
production lifecycle boundary, document that proved boundary before changing
it and apply its existing compatibility checks; do not silently expand scope.
Keep the existing receipt and capture contracts, reset behavior, owned-ticket
cleanup and ITEM checks. Diagnostics must survive failed or partial operations
without masking their original error.

## Existing seams and ownership

`scripts/run-connected-dedicated-ui-smoke.ps1` validates source/launch/dependency
hashes, copies a schema-2 marked source, starts one loopback server and client,
archives evidence and owns process cleanup. It admits only CPU-list
and recurrence scenarios at the original baseline. #484 adds resource leaves;
extend that implementation without reverting its fixes or changing older leaves.

The shared `DedicatedCpuScenario` dispatches CPU-list and recurrence branches;
26.1.2 compiles that shared dispatcher with its own `StandardCraftFixture` and
`ServerDriverPlatform`. Delegate the new resource branch to a driver-only
resource lifecycle class rather than copying the whole dispatcher. The shared
client scenario owns observation, ordinary locate input, screenshots and normal
disconnect/reconnect. It uses the same fixture operations on the integrated
server thread; connected mode invokes them through the control below.

`CpuListTtcControl` supplies directory/epoch and disposable-marker validation;
`RecurrentPlanControl` demonstrates bounded properties files and recipient-aware
acknowledgements. Add a small resource-specific control class, reusing the atomic
move/retry utility, without changing either existing wire contract. Pure parsing
and transition decisions stay Minecraft-free in the driver and its existing
core-test source set. No client classes may initialize on the dedicated server.

## Provisioning evidence and ownership

Runtime records under `.omo/evidence/issue-482-runtime/` on the #484 worktree
separate preparation from qualification. `forge-47.4.23-provisioning-and-current-head.md`
records the official Forge installer (8,836,161 bytes, SHA-256
`75cfcb11f60cc641dc83757c1357c08c9bdba255ec529feb115c5e89a59cf75a`),
a 196,535,715-byte sealed source and successful plan validation, not a connected
pass. `forge-connected-handshake-diagnosis.md` records matching channel/artifact
identities and live Netty classloading during pre-join timeouts. This supports
a cold-start timing diagnosis, not proof that a fixed sleep will cure it.
`stage5-settled-diagnosis-5294f0d9.md` separately proves a post-command server
death from repeatedly replacing unchanged state; #484 removes that replay churn.
Its final connected qualification still exhausted three pre-fixture attempts.
A complete diagnostic trace lacked the adapter contract and is not qualification.

Add `scripts/prepare-dedicated-ui-smoke-server.ps1`, not a second smoke runner.
Inputs are `Target`, matching `BundleDirectory`, `PreparedLaunch`, explicit
`SourceRoot`, `CacheRoot`, new `ReportDirectory`, optional `JavaHome`, and
`PlanOnly`/`Offline`. Target is the exact four-row release-matrix allowlist;
graph is inferred and checked against the compatible bundle (base or focused
AppMek only). No URL, loader override, arbitrary installer argument or latest
switch. PlanOnly validates local inputs and reports required downloads, bytes
and roots without creating directories, downloading or starting Java. Offline
accepts only a fully sealed, verified source hit; it never runs an installer or
opens the network. Java must already exist and match the row.

| Target | Java | Exact loader / AE2 | Native graph additions | Chemical graph additions |
| --- | --- | --- | --- | --- |
| 1.20.1-forge | 17 | 47.4.23 / 15.4.10 | GuideME 20.1.15 | AppMek 1.4.3, Mekanism 10.4.16.80 and pinned required transitives |
| 1.20.1-fabric | 17 | 0.19.5 / 15.1.0 | Fabric API 0.92.11+1.20.1 | Unsupported |
| 1.21.1-neoforge | 21 | 21.1.251 / 19.2.17 | GuideME 21.1.19 | AppMek 1.6.3, Mekanism 10.7.19.85 and pinned required transitives |
| 26.1.2-neoforge | 25 | 26.1.2.109 / 26.1.10-beta | GuideME 26.1.12-beta | Unsupported |

The release matrix owns target IDs, `run-client-versions.json` owns compatible
pins, and the already resolved compatible bundle owns exact mod filenames,
versions and hashes (including Fabric API and required transitives). Compare
all three before installing; do not resolve a second, possibly different graph.
Copy exactly the bundle's non-product/driver mod set and verify every byte.
Reject unknown/additional/client-only mods rather than silently dropping them.
The source contains no product/driver JAR; the runner stages the current pair.
No source build runs in CodexVM; installers run with row Java on local NTFS.

### Official inputs and integrity

Pin Fabric installer 1.1.2 independently of loader 0.19.5. Official input URLs:

- Forge: `https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-installer.jar`.
- NeoForge: `https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.251/neoforge-21.1.251-installer.jar`
  and `https://maven.neoforged.net/releases/net/neoforged/neoforge/26.1.2.109/neoforge-26.1.2.109-installer.jar`.
- Fabric: `https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.2/fabric-installer-1.1.2.jar`.
- Mojang metadata: `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`;
  follow only the selected version's official metadata and server download.

Use Forge/NeoForge `java -jar <installer> --installServer <staging>` and Fabric
`java -jar <installer> server -mcversion 1.20.1 -loader 0.19.5 -downloadMinecraft -dir <staging>`.
These follow the [NeoForge installation contract](https://docs.neoforged.net/user/docs/server/)
and [Fabric installer contract](https://fabricmc.net/use/installer/).
Invoke executable/argument arrays, never downloaded shell/batch scripts.

Before executing an installer, verify its official Maven checksum sidecar
(SHA-256/512 preferred; retain SHA-1 if that is the published checksum) and
compute SHA-256 locally. Store URL, upstream digest/algorithm, size and SHA-256
in a bounded provenance receipt. The recorded Forge SHA-256 above must also
match. A self-computed hash alone proves identity, not upstream authenticity.
All four sidecars were reachable during planning (2026-09-21); this is input
availability evidence, not proof of installer execution on the other targets.
No missing-checksum fallback. Verify Minecraft/libraries against upstream
version/install metadata and installer validation; retain metadata hashes and
the final installed tree. Official installers may fetch their declared
libraries; no injected repositories or user-supplied install profiles.

Repository HTTP requests use HTTPS without userinfo, auth headers, cookies or
default credentials. Allow only the named Maven hosts, `meta.fabricmc.net`,
`piston-meta.mojang.com`, `piston-data.mojang.com`, `launchermeta.mojang.com`,
`launcher.mojang.com` and `libraries.minecraft.net`; validate each of at most
three redirects. Mod downloads remain the existing prepared-bundle resolver's
responsibility. Run official installers without inherited token/proxy-auth
variables, with task-local Java user.home/TEMP, and no launcher account files.
This is not a sandbox guarantee for arbitrary installers: only verified fixed
official artifacts are executable. Unsupported library origins fail review.

### Sealing, limits and rollback

Resolve roots before writes; require distinct, non-overlapping dedicated roots
outside checkout, prepared clients, existing sources and report evidence.
Reject filesystem roots, reparse points in every ancestor/descendant, hard-linked
files, device/UNC paths and path traversal. Staging is a fresh marked UUID child
of SourceRoot on the same volume as the final source. Installer working/temp
directories are inside that child. Recheck links and containment before each
copy, seal, publish or cleanup; never copy links from a cache or source.

Repository fetch caps per invocation: 2,048 files, 512 MiB each, 2 GiB aggregate,
10 MiB metadata per response, 30-second connection and 120-second transfer
timeouts, at most two transfer attempts per file. Retain incomplete downloads
only as report-owned diagnostic metadata, never cache hits. Allow 15 minutes
for installation, at most 4 GiB staging and 4 GiB new cache data, and require
12 GiB free before starting. Check installer output files at one-second intervals
against the same file count/per-file limits and 4 GiB staging limit; terminate
owned installer/process descendants on a cap breach. Installer-internal network
retries are bounded by its 15-minute process deadline, not falsely claimed as
repository per-transfer enforcement. This monitored installer disk bound may
overshoot by one polling interval; it is not an OS/network quota. Repository
downloads enforce streaming byte limits and count retry bytes toward 2 GiB.
No automatic cap escalation or unrelated cache eviction.

Cache downloaded artifacts by SHA-256 with their verified provenance; reuse
recomputes hashes, never trusts filenames/mtime. A source key includes target,
Java major, installer and metadata identities, loader and sorted dependency
name/hash set. Build in staging, require installer exit 0 and expected launch
files (`fabric-server-launch.jar` or the row's `libraries/.../win_args.txt`),
and validate all referenced relative library paths. No user JVM argument file
may inject agents, classpaths or outside paths. Reject unexpected worlds,
credentials, product/driver artifacts and executable launch overrides.

Publish a schema-2 source marker with existing target/role/Java/loader/launcher/
dependency fields plus `provisioning` identity and seal references. The seal is
a sorted relative-path/size/SHA-256 inventory of every installed file except
the marker and seal itself; the marker hashes the seal and the report hashes
the marker. Bound inventories to 20,000 files, 4 GiB and 10 MiB JSON. Publish
the validated staging directory by same-volume rename to a new key directory,
then apply read-only sealing. Never overwrite an existing source: exact cache
hits must revalidate the full tree, and damaged hits fail with a quarantine
recommendation, not automatic deletion. The runner verifies the full seal
before/after copying; legacy schema-2 sources retain their old validation but
cannot claim RF7 until re-provisioned. Graph changes produce a new source.

Provisioning never boots the sealed source or writes EULA acceptance. Connected
execution requires explicit `-AcceptMinecraftEula` (or an already recorded
explicit campaign consent), copies to its marked runtime, and writes acceptance
only there. Do not infer consent from fixture approval. On failure retain bounded
logs/receipt, remove only marked owned staging after exact-process exit, and
preserve prior source/cache objects. Interrupted staging is never reusable;
cleanup on rerun requires its ownership marker and no live recorded process.

## Cold readiness before activation

For resource leaves, add `-Prewarm` through existing host/guest dispatch and
connected runner. It is required for RF8 qualification, not enabled for older
leaves. Use one 4 GiB server and one maximized 8 GiB client sequentially in
CodexVM, matching prepared launch, bundle, expected-adapters.json and full SHA.
No extra client/server is started merely to warm an operating-system cache.

Before resource fixture construction, enter driver-only PREWARM. Normal world
generation/login is allowed in the new disposable world; no grid/pattern/job,
retained sample, highlight, resource command or scenario result is created.
The resource dispatcher remains gated on both sides. Wait for server startup,
real native player join with expected UUID, 20 consecutive live server ticks,
and 40 completed client world frames with no menu/loading overlay. A listening
port or `Done` log alone is not readiness and no synthetic packet counts.

Use fixed `prewarm/server-ready.json`, `client-ready.json` and `arm.json` below
the existing control root, with the same 64 KiB, no-link, atomic-write rules.
Receipts carry schema 1, campaign epoch, full head, bundle digest, exact PID/
start time, expected player UUID, connection generation and readiness counters.
The runner publishes a one-shot arm binding hashes of both ready receipts.
The server rechecks the same live connection and empty fixture before accepting;
the client waits for server acceptance before entering the existing resource
flow. Record acceptance in `prewarm/armed.json`. Duplicate identical arm reads
do not rewrite state; wrong/stale identities, changed receipt or second activation
fail. Resource commands before arming fail, rather than queue for later execution.
Once armed, readiness files cannot restart, reset or extend the measured flow.

Prewarm has one absolute 600-second budget including server startup/client load
and at most three normal connection attempts in the same client process. Allow
another connection only after native disconnect completes, with no fixture
command/state/mutation and no arm; preserve native handshake timeout and stop
on fatal loader/mixin errors. This replaces, not multiplies, outer startup retries
for `-Prewarm`. Existing non-prewarm retry policy stays unchanged. Keep the
20-second callback watchdog once callbacks begin; do not invent checkpoint
progress or continually refresh the absolute prewarm deadline.

After one-shot arming, the existing 300-second setup, 20-second callback,
60-second active checkpoint/observation and 40-minute scenario limits apply.
No prewarm reconnect/retry is permitted after arm, even before the first resource
command. Normal measured reconnect cases retain their existing deadlines and
capture stability checks; prewarm cannot hide replay publication failures.

Write separate `prewarm-evidence.json` with phase timings, connection attempts,
receipt hashes, no-mutation assertions, transition and cleanup outcomes.
Record `readinessResult`, never a fixture PASS. Preserve cold installation/cache
miss and verified cache-hit timings separately, all source/download bytes and
identities, and fatal/disconnect diagnostics. Readiness must be fresh every run;
no cached successful receipt substitutes for current callbacks or world frames.
On failure use existing exact PID/start-time/task cleanup; keep the source
sealed and record whether live teardown ran. Never kill unrelated Java processes.

## Typed processing fixture

Extend the existing grid/CPU/provider setup seams with a resource fixture. Encode
one cobblestone input per output batch with `ServerDriverPlatform.processingPattern`;
overlap slot 1 uses one sand instead so accepted inputs identify each output.
An adjacent inventory accepts the real provider dispatch; record and consume
that input once before marking the matching output held. Release inserts the
retained `AEKey` through normal ME storage/crafting insertion, like the current
`StandardCraftFixture.pumpDelayed` path. Retain partial insertion remainder and
never synthesize a second output on a retried action. Fail on wrong key/count.

Each batch produces one item or one resource unit from `AEKey.getAmountPerUnit()`;
record the raw amount, which differs between Forge and Fabric fluids. Use an
item cell plus the appropriate fluid/chemical cell, and two native 256K CPUs
for overlap. Ordinary cases use only one CPU. Warm up each recipe by completing
two real batches before the measured held job, returning each batch 20 server
ticks after its dispatch; clear prior case samples first.
This establishes throughput without seeding delay state or sample maps.

Use AEItemKey stone, AEFluidKey water/lava, and target-local AppMek MekanismKey
oxygen/hydrogen. Forge uses GasStack; NeoForge 1.21.1 uses ChemicalStack. Optional
fixture factories load only after the selected scenario and mod presence checks.
Fabric/26.1.2 contain no Mekanism references in common initialization. Existing
`appmek-cpu` remains unchanged.

For a deterministic bucketless control, register the driver-only source fluid
`ae2craftingtime_test_driver:resource_fixture_fluid` on Forge 1.20.1 on both sides,
with no bucket or placed block and an explicit cyan tint over the water sprite.
It enters no world generation or production artifact. Use the same processing
flow and record its key, no-bucket assertion and appearance metadata. This
resolves the previously unnamed fixture without adding a player dependency.

## Resource control protocol

Use fixed `resource/command.properties` and `resource/state.properties` below
the runner's fresh control directory; no command contains a path, registry ID,
quantity, code, address or arbitrary coordinates. Atomic temporary-file replace
publishes each file. Limit each file to 64 KiB on reads and writes, rejecting
links, non-regular files and paths outside that control child. Read at most the
limit plus one byte so a size race cannot bypass the bound. No raw file dump in
errors. Missing files mean pending only before publication; invalid files fail.

Command schema `1` has exactly: `schema`, `epoch`, `scenario`, `player`,
`fixture`, `revision`, `sequence`, `action`, `case`, `slot`. Epoch/fixture are
runner/server-generated UUIDs, player is the fixed offline fixture UUID,
revision and sequence are positive signed longs, slot is 0 or 1, and enum strings
are at most 64 ASCII characters. Reject duplicate/unknown properties, bad numbers
and missing fields. `case` is one of `item`, `water`, `lava`, `bucketless`,
`fluid-overlap`, `oxygen`, `hydrogen`, `chemical-overlap`, with the target/scenario
restrictions from the spec. For actions without an output slot, require slot 0.

The initial state publishes fixture identity and revision 1. All commands must
match epoch, scenario, expected player, fixture and current revision. Except
for `create`, case must match the current server-owned case. Accept exactly
`lastAck + 1`; an identical decoded repeat of the last accepted command returns
its recorded acknowledgement without repeating work, including a reset's old
revision. Check that exact replay before the current-revision check. Reject conflicting
reuse, older or skipped sequences and wrong phases. Keep one in-flight action;
retries while its asynchronous operation runs reuse that operation. Ack only
after its postcondition holds. No sequence/revision wraparound is permitted.

| Action | Allowed state | Postcondition / next state |
| --- | --- | --- |
| `create` | READY or CLEAN | Prepare the selected case and warmup; submit measured job(s), observe real input dispatch; hold output / HELD |
| `release` | HELD | Release the selected active slot once; wait for completion; HELD if another slot remains, otherwise SETTLED |
| `cancel` | HELD | Cancel selected native CPU, discard its held output; wait for idle; HELD or SETTLED |
| `rejoin-prepare` | HELD | Record current session and arm one disconnect/reconnect; keep job held / REJOINING |
| `reconnect` | REJOINING | Require observed leave and return of the same player on a new connection; verify job unchanged / HELD |
| `reset` | SETTLED | Remove case jobs/inputs/patterns/samples and restore settings; increment revision / CLEAN |
| `complete` | CLEAN | Validate all mandatory case receipts, cleanup and final capture acknowledgement / COMPLETE |

After reset the acknowledgement names the accepted old revision and state carries
the new revision. State properties are `schema`, `epoch`, `scenario`, `player`,
`fixture`, `revision`, `ack`, `ackRevision`, `action`, `case`, `slot`, `phase`,
`failure`, `terminal`, `providers`, `jobs`. Terminal is an integer x/y/z triple;
providers is a JSON array of at most two such triples. Jobs is a JSON array of
at most two records:
slot, resource type/ID, key fingerprint, raw amount/held/released amounts, CPU/job
identity, dispatch count, busy/delayed flags and server tick. Serialize each
fixture key once with the target's AE2 generic-key encoding and SHA-256 those
immutable bytes for its fingerprint; retain the encoding and hash in evidence.
Never accept key data from the client. Terminal
positions are observations, not client-selected mutation targets.

Use the existing Ae2ctAlpha offline identity for these leaves. Match its UUID
and scenario on the server before preparing anything. Unexpected recipients
cannot advance the protocol. Menus may be closed, so bind actions to fixture/job
revision rather than menu IDs. The server tracks the actual leave/rejoin event;
a client-written reconnect command alone never proves reconnect.

## Launch, result and failure contract

Add explicit `-ResourceFixtureOnly` to the existing integrated/connected runner
entry points and propagate `ae2craftingtime.test.resourceFixtureOnly=true` to
the client and server. Permit it only with the two resource leaves. #482 admits
these leaves only in that explicit mode; ordinary icon-acceptance runs remain
unavailable until #376 supplies its assertions. Existing scenarios reject the
flag. No normal suite silently uses fixture-only mode.

Write `resource-fixture-evidence.json` schema 1 on both sides with
`fixtureResult: PASS|FAIL` and `productionIconAcceptance: NOT_RUN`. Include
SHA/graph/artifact identities, command receipts, server job facts, actual client
plate/output observations and screenshot names/hashes. The runner requires this
evidence in addition to existing driver/server results and preserves
`fixture-only` scope in its summary/archive. A known absent icon is recorded;
missing captures, absent delayed plates, incorrect lifecycle or failed cleanup
still fail. No diagnostic mode writes a full icon-acceptance PASS.

Preserve 20-second callback and 60-second active-progress watchdogs; allow up to
300 seconds for fixture setup and 40 minutes overall, with real case/phase
progress. Warmup/dispatch delays cannot manufacture progress indefinitely.
No automatic retry once a resource command has been accepted. Preserve existing
bounded pre-world startup retries only when no resource fixture/command exists
and the previous exact client and tasks have exited.

On failure, record the original error, cancel outstanding fixture jobs and
calculations, discard held outputs, remove forced chunks/blocks, restore settings,
then report cleanup outcome. Integrated cleanup runs on its server thread;
dedicated cleanup precedes halt. The runner still owns exact PID/start-time and
scheduled-task cleanup when callbacks fail. Preserve evidence and the immutable
source; do not broaden deletion or process targets. Crash cleanup may rely on
disposing the marked copy, but must record that live teardown did not run.
