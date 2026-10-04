# AE2 Crafting Time Test Driver Spec

Status: finished

Scope: Shipped driver baseline, excluding separately tracked extensions.

Implementation: [PR #128](https://github.com/cTux/ae2-crafting-time/pull/128), [PR #208](https://github.com/cTux/ae2-crafting-time/pull/208).
Verification: [prepared-client results](../automated-ui-testing/prepared-clients-2026-09-08.md).
Stored-variant and resource-icon sections inherit their linked feature status.

## Native options fault checks (#378)

Status: in-progress.

Implementation: [draft PR #643](https://github.com/cTux/ae2-crafting-time/pull/643).
At `2a03582e`, seventeen native assertion groups pass unattended in CodexVM;
the native JVM exits successfully and eight original screenshots are reviewed.
Cancel-file integrity, page recovery and the save deadline pass. The full
reported-code 100% gate remains incomplete.

The test-only Forge fixture uses the initialized Minecraft client and real
Options screen to check rejected saves, incorrect reset values and missing
controls, including sort values and changes leaking across reset groups.
Check Cancel against the physical saved-file hash. Reject compact restoration
after exhausting the wrong settings group's pages, then recover through Displays
and Done. A stuck native save must remain pending for 100 completed callbacks and
fail on callback 101. Each fault must assert the exact rejection and preserve the saved
configuration. Successful recovery must pass after restoring valid values.
Keep its JAR out of production and normal driver launches. Run it only in the
disposable coverage client, with bounded execution and original screenshots.

At `5a2fde81`, all nineteen native assertion groups pass with JVM exit 0.
The ten original captures are reviewed; the two new navigation originals are
also reviewed individually. Exhaust
the actual wrong group's pages without saving, then recover in Displays by
turning compact amounts Off and pressing Done. Assert the saved Off value and
pending persistence state; this Options-only check does not claim a relaunch.

Observation clipping and widget visibility checks pass at `4dee9921`. Use real
registered item keys in native plan DTOs to assert the exact fifteen visible
cells before and after scrolling. Hide and restore an actual title-screen
widget and verify its observation follows visibility. Closing the actual world menu after ordinary scenario completion
must not publish another snapshot; use the world fixture because closing a title
returns another title screen. The initial title-close assertion failed and its
run is preserved without contributing coverage. These assertions check the observation model,
without claiming a rendered crafting menu for the DTO-only clipping checks.
At `4dee9921`, both corrected observation groups pass along with the nineteen
Options groups, JVM exit 0 and ten reviewed captures. The separate real world
also passes its close-menu assertion, seven faults and thirteen ordinary checks,
with JVM exit 0; all 36 normal and seven fault captures are reviewed.

## Native crafting fault checks (#378)

Status: in-progress in [draft PR #643](https://github.com/cTux/ae2-crafting-time/pull/643).
At `82711af0`, all three row/Cancel fault assertions and all thirteen ordinary
checks pass in the native disposable client, with JVM exit 0. Original captures
were reviewed with tiny-text and tooltip-occlusion qualifications. The broader
100% coverage gate remains unfinished. At `358341d3`, the missing-font-pack
extension also passes, followed by all thirteen ordinary checks and JVM exit 0.

Use the ordinary runtime and standard-status-controls flow in a marked disposable
world. Remove a native status payload during amount and scale checks, and assert
that the driver restores its expected row without advancing the case or changing
saved options. Close actual Options with Cancel before saving and assert the exact
rejection; reopen Options and let the normal flow recover. Temporarily move the
owned uniform-font fixture out of the native pack directory, require the exact
missing-pack rejection, restore the same files, and retry the interrupted scale
case. Keep saved options and ordinary checks unchanged during each fault.
Also reject status relaunch while compact amounts are still enabled, and reject
it when the ordinary check map is incomplete. Use the actual status screen and
native option values; restore the original configuration before continuing.
At `b1a3522b`, both persistence guards pass along with all six native faults,
all thirteen ordinary checks and JVM exit 0. The corrected run checks each
rejection before intentionally saving the next test setup; the initial setup
failure is preserved separately.
In the addon graph, also remove an actual Applied Botanics status row and require
the same exact native key/amount restoration without advancing the case. This
extension passes at `60293867`: seven fault checks, thirteen ordinary checks,
JVM exit 0 and all 36 ordinary plus seven fault captures reviewed. Codecov
confirms 93.74%, so the broader 100% gate is still unfinished. Require all six
base faults, the seventh addon fault when
requested, and the ordinary scenario's full PASS result. Preserve original native
captures and keep deliberate fault assertions separate from the normal result.

At `2c56fb18`, all nine faults and the real world closure assertion pass,
followed by thirteen ordinary checks and JVM exit 0. All 36 normal captures and
nine fault originals are reviewed. Saved-Off persistence waits for a missing native status
payload before recovering its row, and badge persistence must reject the
original incomplete ordinary check map after validating saved custom Off
appearance. Preserve the saved baseline and continuation absence at each guard,
restore the original payload and configuration, then require the normal PASS.

At `57312de1`, the Start retry guard passes along with ten faults, world closure,
thirteen ordinary checks and JVM exit 0. All 36 ordinary and ten fault captures
are reviewed. Codecov confirms 94.07%; the overall gate remains incomplete.
The guard checks the actual inactive widget, but its original capture shows the
normal native appearance. At `4e487a48`, the corrected post-render capture shows
the same real widget inactive. All eleven faults, real menu closure, thirteen
ordinary checks and JVM exit 0 pass; all 36 normal and eleven fault originals
are reviewed.
Deliberately hold the actual Crafting Plan Start widget
inactive while its real stocked plan remains open. Require three native replans
with the real ten-second waits, then the exact bounded retry rejection. Preserve
ordinary checks and saved options, restore the widget, and require normal recovery.

At `4e487a48`, render actual compact status text, then restore the saved Off
setting before the driver consumes the frame. Require persistence to wait while
the drawn compact description is stale, without capture or continuation. Restore
ordinary rendering and require the existing missing-row and incomplete-map guards.
The stale-description and missing-row guards both pass after their actual eight
stable frames, preserving saved bytes, ordinary checks and continuation absence.
The separate connected CPU scenario passes all 41 checks across two native Java
17 launches with exit 0. All 32 original captures are reviewed. Codecov confirms
94.17%, with 29 misses and 310 partials; the overall 100% gate remains unfinished.

The unknown suspension-stage boundary must remain pending without changing the
stage or check map, capturing success, moving the mouse or starting server work.
At `3d539dae`, the focused test passes locally and against the packaged Forge
bytecode in CodexVM, without skips or failures. Codecov confirms 94.21%; the
overall coverage gate remains unfinished.

At `a33773d4`, all 24 native Options assertion groups pass with JVM exit 0,
twelve original captures reviewed, local checks and both current-head CI checks
green. Codecov confirms 94.26%; the overall coverage gate remains unfinished.
Wrong native screens must preserve pending badge,
scale and status restoration. Before the required redraw callbacks, badge edits
and both relaunch flows must wait without capture, configuration writes or
changed checks. A pending badge save must also block repeated edits.

At `ca770773`, thirteen native faults, menu closure and all thirteen ordinary
checks pass with JVM exit 0. The 36 normal and thirteen fault originals are
reviewed in contact sheets; the stale profiling-Off checkpoint is also reviewed
individually. Local checks and both current-head CI checks pass. Codecov confirms
94.33%; the overall coverage gate remains unfinished.
For profiling-Off readiness, retain eight unmodified, stable compact frames
from actual native rendering while profiling is On. After actual server metadata
reports Off, replay those older observations into a separate status flow and
require the missing native category descriptions to keep it pending. Restore
the current observation, remove the actual status payload, and require the
ordinary Off flow to wait for its row after eight stable frames. Preserve saved
bytes and checks, restore the payload, then require the full ordinary PASS.
The replay frames are historical native observations; its checkpoint image shows
the current Off screen, not a newly rendered stale compact screen.

At `81a2870e`, fourteen native faults, menu closure and all thirteen ordinary
checks pass with JVM exit 0. The 36 normal and fourteen fault originals are
reviewed in contact sheets; the badge observation checkpoint is also reviewed
individually. Local checks and both current-head CI checks pass. Codecov confirms
94.34%; the overall coverage gate remains unfinished.
With the actual two-row profiling-Off
screen, a separate badge flow must reject native row text when its pre-toggle
text observation is absent or contains only a genuine CPU header. Reach the
actual stable-frame threshold in both cases without capture, changed checks,
configuration writes or server work. Retain the native source frames, then
require the ordinary scenario to pass after the check.

Matching remembered native row text must still reject an
incorrect saved custom appearance after eight stable frames. Verify the actual
default Off/black/176 settings before expecting the custom-appearance rejection.
Also reject
invalid recurrence rendezvous inputs against the actual native player: another
player, an incorrect turn and an empty turn for Alpha. Reject Beta as an
unsupported recurrence role; do not extend the production role contract.
Preserve screen, player, menu, checks and saved bytes, write no command, and
restore the original rendezvous properties before ordinary scenario execution.
This is invalid-input boundary coverage, not a two-client server scenario.
The first `04e1cccc` launch failed before these assertions because the ordinary
fixture had not yet created its saved client configuration. Preserve that failed
launch and exclude its execution data from passing coverage evidence. Run the
participant checks only after the actual config file exists; no replacement
configuration or invented saved bytes may satisfy that prerequisite.
The `1bd6cca0` retry exposed the fixture's incorrect assumption that recurrence
accepted Beta. Preserve and exclude that failed launch too. The corrected test
requires the existing unsupported-role exception and retains the three invalid
Alpha-state assertions; this failed attempt does not verify them.
At `47e0dcb6`, all four participant assertions passed, but the matching-text
check failed on its incorrect On-background prerequisite. Preserve and exclude
that launch. The actual saved default is Off, black and opacity 176; use that
verified incorrect custom appearance without changing saved bytes. Runtime
verification of the corrected expectation is recorded below.

Driver highlight queries must require
both the requested output and the fixture's provider position. Plate/output
collections must stay bound to that position; beams additionally require a chat
locate. Verify empty state, wrong output, wrong position, matching data and
session cleanup through the actual packet-data APIs and coordinate values.
Enforce 100% line and branch coverage for these five queries and their predicates.
Run the assertions against the unchanged packaged native class too; these data
boundaries do not claim rendered world highlights.

At `89ad1a55`, all fifteen native fault groups, menu closure and thirteen ordinary
checks pass with JVM exit 0. The actual matching-text and participant boundaries
pass. All 51 originals are reviewed in contact sheets; the badge checkpoint is
also viewed individually. Four packaged-native highlight assertions and local
coverage gates pass without skips. Both current-head CI checks pass. Codecov
confirms 94.46%; the overall 100% target remains unfinished. The three failed
attempts above remain preserved and excluded from the passing coverage reports.

Missing snapshots, wrong screen identity,
empty rows, one positive row and an actual panel rectangle used as an invalid
badge must leave both badge states and scale readiness pending. Verify all fifteen
combinations against the actual native status screen, preserving stage, zero
stable-frame count, checks, screen, menu, GUI scale and saved bytes, with no capture
or server operation. These deliberately invalid observation DTOs are guard inputs,
not rendered native frames. Restore the original observation in `finally`, keep
their evidence separate from screenshots, then require the ordinary scenario PASS.

At `2df7a1cc`, all fifteen invalid-observation combinations pass, alongside sixteen
native fault groups, menu closure and all thirteen ordinary checks, JVM exit 0.
All 52 original captures are reviewed in contact sheets; the new unchanged-screen
checkpoint is also viewed individually. Local checks and both current-head CI
checks pass. Codecov confirms 94.55%; the requested 100% target remains unfinished.

Render scheduled-only, stored-only and
all-zero status payloads through the actual native screen. Positive scheduled or
stored quantities must reach the saved-Off checkpoint after genuine stable frames,
then reject the incomplete check map before writing any continuation. All-zero
quantities must remain pending after the same threshold, without capture. Preserve
checks, menu, screen and saved bytes; restore the original status payload on success
or failure. Keep one actual screenshot and original observations per quantity case.
These deliberate client payloads test native rendering and guard behavior, not
actual server job counts. Require the ordinary scenario to pass after restoration.

At `ed8b1560`, the fresh recovery run passes nineteen fault groups, menu closure
and all thirteen ordinary checks, JVM exit 0. Each quantity case uses eight
consecutive original native frames. All 55 captures are reviewed in contact
sheets, with the three quantity captures also reviewed individually. Local checks
and both current-head CI checks pass. Codecov confirms 94.58%; the 100% target
remains unfinished. The interrupted earlier run has no final receipt and a
zero-byte execution file; preserve it separately and exclude it from coverage.

Plan-observation extension: reuse the invalid observation guards in the
actual stocked Crafting Plan screen. Check missing snapshots, wrong identity,
empty/one-positive-row inputs, a panel rectangle used as a badge, and the original
stocked plan without a missing row. Both badge states must stay pending before
stable readiness advances, preserving stage, checks, native screen/menu, GUI scale
and saved bytes. Retain the original input separately from the five altered DTOs;
the screenshot shows the actual screen. Require all twelve plan combinations,
the existing fifteen status/scale combinations and ordinary recovery to pass.

At `526c6246`, all twelve plan combinations pass with twenty fault groups,
menu closure and all thirteen ordinary checks, JVM exit 0. All 56 captures are
reviewed in contact sheets and the plan checkpoint also individually. Local and
both current-head CI checks pass; processed Codecov remains 94.58%. A OneDrive
focus interruption was dismissed and the same native run resumed. The launch
interval includes that pause. All 115 archived file hashes match.

Quantity-observation boundary: use the actual first Stone quantity frame
and separate copies with wrong output, stored, active or pending metadata. Each
flow must reach its real stable-frame threshold before restoring the expected
native payload without advancing the quantity case, capturing success or moving
the mouse. Preserve checks, settings and the actual screen/menu; restore the
original observation and payload on success or failure. Record original native
frames separately from deliberately invalid DTO inputs. Require normal recovery.

At `9be70e52`, all four quantity cases reject on eight consecutive native source
frames. Twenty-one fault groups, menu closure and all thirteen ordinary checks
pass, JVM exit 0. All 57 captures are reviewed in contact sheets and the quantity
checkpoint individually. Local and both current-head CI checks pass. Processed
Codecov confirms 94.65%; all four quantity predicates have full branch coverage.
The 100% target remains unfinished.

Addon extension: reuse the quantity-observation boundary against the
actual first addon key and its declared quantities, preserving the same rejection,
restoration and ordinary recovery contract. Both installed addon fixtures and
their normal mana/chemical captures remain required.

At `17b62a8a`, four addon quantity cases reject on eight consecutive native
source frames. Twenty-two fault groups, menu closure and all thirteen ordinary
checks pass, JVM exit 0. All 58 captures are reviewed in contact sheets and the
addon checkpoint individually. Local and both current-head CI checks pass;
processed Codecov confirms 94.70%, with full addon quantity predicate coverage.
All 119 archived file hashes match. The 100% target remains unfinished.

Menu mismatch boundaries: require six status stages to wait on the
actual Crafting Plan screen, and recurrence Plan sort to wait on the actual
Crafting Status screen. Reach each real stable-frame threshold without advancing
stage, opening options, issuing server work, moving the mouse or capturing success.
Both directions also test the three badge observation steps; the plan screen
additionally tests the status scale guard. Keep original native frames and one
checkpoint per direction. Preserve checks, native menu/screen and saved bytes,
then require the ordinary scenario to pass.

At `5678a671`, twenty-four fault groups, menu closure and all thirteen ordinary
checks pass, JVM exit 0. All 60 captures are reviewed in eight contact sheets and
both new menu checkpoints individually. Local and both current-head CI checks
pass. Processed Codecov confirms 94.84%, 25 misses and 275 partials. All 123
archived file hashes match. The 100% target remains unfinished.

Pending recurrence readiness boundaries: require rejection of the actual server
inventory menu before ordinary preparation. At the actual Crafting Plan, remove
the client summary, server result and server summary separately and require
pending readiness. Restore the exact original payloads in `finally` before any
render or ordinary action. Preserve actual menu/screen, checks and saved settings
throughout asynchronous polling. Record input checks separately from screenshots;
the checkpoint shows the restored native plan, not missing payloads. Require the
original scenario to pass afterward.

At `e1ece13c`, twenty-six fault groups, menu closure and all thirteen ordinary
checks pass, JVM exit 0. All 62 captures are reviewed in nine contact sheets and
the new recurrence checkpoints individually. The inventory checkpoint shows
Loading terrain; server menu identity is established by assertions and its JSON
receipt. Local and both CI checks pass. Processed Codecov confirms 94.89%, 25
misses and 272 partials. All 128 archived file hashes match. The failed public
null-installer attempt is excluded. The requested 100% target remains unfinished.

Pending quantity readiness extension: after matching the actual quantity row,
require item and addon flows to remain pending on an empty tooltip. The addon
capture-delay guard must wait before hover or capture. At the real billion-scale
row, separately reject incorrect stored, active and pending DTO quantities, a
zero observed scale and a scale that differs from the requested native scale.
Reach real stability on distinct original frames, preserve case, checks, saved
bytes and native menu/screen/scale, and restore original observations and payloads
before normal recovery. Altered DTO inputs are not native rendered frames.

At `3d1c0f5d`, twenty-seven fault groups, menu closure and all thirteen ordinary
checks pass, JVM exit 0. The item, addon and scale cases each use eight consecutive
original frames. All 63 captures are reviewed in nine contact sheets and the
new scale checkpoint individually. Local and both CI checks pass. Processed
Codecov confirms 95.00%, 25 misses and 266 partials. All 130 archived file hashes
match. The 100% target remains unfinished.

Pending recurrence grid readiness: create a separate real managed node in the
disposable world. Require the existing fixture's readiness predicate to reject
its unpowered grid. Connect it to a real isolated creative energy cell, await
native activation and require rejection while the powered grid has no craftable
patterns. Remove the owned node and temporary cell, restore the original air
block and feature flags, and require normal scenario recovery. Prove grid state
through native assertions and JSON receipts; a menu screenshot is not grid proof.
The first attempt at `4fea30b7` failed while its test setup queried client-owned
features through the server feature set. It created no grid and remains excluded
from coverage. The corrected setup snapshots the server's disabled set, lets the
existing recurrence fixture temporarily enable detection, and verifies exact
restoration when that fixture closes.
At `0d96a020`, both real grid rejection states and exact cleanup pass, followed
by all thirteen ordinary checks, JVM exit 0. All 64 original captures are reviewed
in nine contact sheets and the grid checkpoint individually. Local and both CI
checks pass. Codecov confirms 95.01%, 25 misses and 265 partials. All 132 archived
file hashes match; the 100% target remains unfinished.

Pending recurrence fixture boundaries: exercise its registry filter with the
actual registered air, smooth stone and stone items. Begin real AE2 calculations
on the isolated native grid and call validation again in the same server
operation, before the next simulation tick. Require pending validation to wait,
then poll genuine calculation completion and clean up before ordinary recovery.
Do not substitute future implementations or claim these server-only checks from
the held-plan screenshot.
At `eab08c5c`, those native registry and pending-plan boundaries pass with normal
recovery and JVM exit 0. All 64 originals are reviewed in nine contact sheets and
the grid checkpoint individually; all 132 archived hashes match. Local and both
CI checks pass. Codecov confirms 95.05%, 25 misses and 263 partials; the reported
recurrence fixture is now at 100%. The remaining gaps are in the standard driver.

Pending native badge-text boundary: temporarily enable the actual client-owned
background switch in memory without saving, await genuine native badge renders,
and derive a separately recorded DTO input that relabels text inside those real
bounds as native status text. Reach the existing stable-frame threshold and
require incorrect custom appearance to reject before success capture or stage
advancement. Restore the background switch and original observation on success
or failure, preserve saved bytes and native menu identity, and require ordinary
scenario recovery. Altered inputs are not native rendered frames.

At `8312c3e3`, the badge-text check and ordinary recovery pass with JVM exit 0.
All 65 originals are reviewed in nine contact sheets and the new badge checkpoint
individually; all 134 archived hashes match. Both CI checks pass. Codecov confirms
95.07%, 24 misses and 263 partials. The requested 100% target remains unfinished.

Pending native stored-variant guards: hold the actual clear and diagnosed plan
checkpoints and test 28 rejected or pending inputs against separate scenario
state. Require eight distinct native source frames for every case. Keep altered
observation DTOs separate from actual rendered frames. Temporary native payload
and row-flag changes must restore before another render. Missing tooltips,
recurrent labels, drawn text and fresh revisions must wait without capture or
case advancement. Incorrect retained summaries, unrelated diagnoses and an
impossible scale must reject. Restore a temporary scale reduction immediately.
Preserve the real menu, plan, ordinary checks and saved client bytes, then require
the complete ordinary stored-variant scenario to recover successfully.
The first `aead530a` native attempt failed on the harness assumption that defaults
already had a saved client config. It is retained and excluded from coverage.
The guard must preserve both existing saved bytes and a previously absent file.
At `e1f9d3df`, all 28 guard cases and 17 ordinary checks pass with JVM exit 0.
Both CI jobs pass. Codecov confirms 95.56%, 11 misses and 247 partials. All 13
originals are reviewed in three contact sheets and both guard images individually;
all 34 archived hashes match. The clear guard image is black after native resize
cleared the framebuffer, so its visual checkpoint remains unverified. The guard
capture must wait for a subsequent actual native frame after scale restoration.
Pending recurrence-tooltip extension: reuse the actual diagnosed pickaxe tooltip
and seven separate DTO inputs to verify retained variant-text rejection, hover
and hint disagreement, absent recurrence labels, bold labels, incorrect color
and absent color. Independent recurrence flows must wait at their real stable
threshold without modifying native state, capturing success or scheduling server
work. Keep the original stored-variant observation enabled and require ordinary
recovery after all 35 cases. New guard images must show actual frames after redraw.

At `6d178701`, all 35 guards and 17 ordinary checks pass with JVM exit 0. Both
CI jobs pass. Both guard captures show actual redraws after restoration. All 13
originals are reviewed in three contact sheets and both guard images individually;
all 34 archived hashes match. Codecov confirms 95.67%, 10 misses and 242 partials.

Pending native suspension waits: reuse the ordinary singleplayer suspension
scenario. At the four-input and final one-input waits, temporarily withdraw the
two actual provider patterns and real furnace inputs on the server thread. Require
the original scenario's own server predicate to complete false with the stage
unchanged. Restore those exact native stacks and patterns, verify input counts
and job UUID, then continue. Close the actual replacement CPU menu once so the
original scenario must open it again. At disabled recovery, hold the actual client
Options screen for eight real frames, cancel without saving, and return to the
same native CPU screen. Record each checkpoint and native server receipts; images
do not prove server-only inventory counts. Require every ordinary suspension
check and exact output conservation to pass before reporting the combined run.
After cancelling Options, hold the actual CPU screen at disabled recovery and
provide separate absent, wrong-screen and stale Suspended-title observation DTOs.
For each input, require eight actual source frames where the original native
server predicate is consumed and the stage remains pending. Restore the original
observation before the next render. Saved config, native screen and menu remain
unchanged. Do not present altered DTOs as rendered pixels.

At `9701cc45`, all five supplemental groups and 23 ordinary suspension checks
pass with JVM exit 0, native interval 528.344802 seconds. The actual four-input
and one-input server predicates wait false, preserving job UUIDs and restoring
inputs. Three invalid observation inputs each consume eight real predicate frames.
All 11 captures reviewed in three contact sheets and four supplemental originals
individually; all 33 archived hashes match. Both CI jobs pass. Codecov confirms
95.82%, five misses and 238 partials. The two failed harness attempts remain
preserved and excluded. Screenshots retain tooltip/chat occlusion and implausible
fallback time headers; those do not establish prediction accuracy.

Pending badge bounds extension: in addition to actual text inside real badges,
relabel one actual text entry outside all badge bounds in the independent DTO
input. Retain its original content and geometry. Require both inside and outside
native-status entries in that input, while the native source stays unchanged.
The incorrect saved appearance must still reject at the existing real threshold,
then restore the original switch, bytes and observation before normal recovery.

At `6c70e8f3`, the outside badge text case and all 30 supplemental checks plus
13 ordinary checks pass with JVM exit 0, native interval 279.071105 seconds.
Source badge frames 2200-2207 are actual consecutive frames; the selected outside
entry is native CPU #1 text at its original content and geometry. JaCoCo confirms
both bounds predicate branches covered. All 65 captures reviewed in nine contact
sheets and the new badge checkpoint individually; all 134 archived hashes match.
Both CI jobs pass. Codecov confirms 95.84%, five misses and 237 partials.

Pending native NeoForge suspension API boundary: use the prepared 1.21.1 native
client and original standard status scenario. After its real fixture prepares,
hold the actual terminal stage and open its actual standard CPU through AE2’s
server-side MenuOpener and real block-entity locator. Await eight
native screen render callbacks. The genuine CPU menu must lack the Forge-only
suspension method; invoking the existing selected-snapshot helper must report
that missing API with the retained error and NoSuchMethodException cause. Preserve
the menu identity while checking. Capture the real menu, close its actual container,
and let the original standard scenario open the terminal and complete normally.
No substitute menus, fake Minecraft objects or job state. This is missing API
boundary evidence on NeoForge, not evidence of a broken required Forge mixin.
The first `6bdcf5e3` launch failed during loader initialization because its fresh
runtime lacked the prepared FML config. The second used that template but exposed
an extra quote in the optional artifact metadata. Both attempts are preserved and
their execution data is excluded. Correct the packaged TOML and require an explicit
8 GiB client heap before retrying. No native menu success is claimed yet.
At `2811732c`, the actual fixture prepares, but its one CPU interaction does not
produce a rendered CPU menu within thirty seconds. Preserve and exclude that run.
Wait for the actual client CPU block entity to be formed and active before sending
the interaction; keep a bounded wait and record native failure context and pixels.
At `c40c3ee2`, the genuine client CPU is ready and its block interaction returns
SUCCESS, but no native menu opens. Preserve and exclude this failed run as well.
Use AE2’s actual server-side menu-opening API with the real fixture block entity
and real player; require its successful return and eight client CPU renders. This
creates native menus through AE2, never replacement menu objects or fake players.

At `3f881b94`, the genuine AE2 menu opener, missing Forge API assertion and all
thirteen ordinary NeoForge status checks pass, JVM exit 0. Eight actual CPU render
callbacks precede the assertion. Native interval: 188.577893 seconds; local checks
and packaging: eight seconds; reports: one second each. All 33 originals reviewed
and all 80 archived hashes match. Both CI jobs pass and both uploads are MERGED.
Codecov confirms 95.87%, three misses and 237 partials. The supplemental matching
NeoForge unit-class report includes existing unsupported-target and absent transport
checks; it does not relabel native execution data or change coverage rules. Visual
gates remain REVIEW_REQUIRED; tiny scales, chat/toast occlusion and implausible
fallback headers retain their limitations.

Pending native terminal and premature-release boundaries: hold the actual standard
ME terminal before the stored-variant flow advances. An independent driver flow
expects the wireless route and must wait for eight actual screen-render callbacks on that
wrong real screen without actions, captures, config changes or ordinary check
changes. Then submit the original final-output release guard against the actual
prepared idle CPU, which has no held output. Require the real server future to
reject with the retained error before mutation. Read native CPU state before and
after and require equality. No fake jobs, players, fixtures or futures. Release the
original stored-variant scenario and require all seventeen ordinary checks.
The `2fa18c63` attempt completes the ordinary scenario and prior variant probes
but fails its final completeness assertion because the two new probes never run.
ME storage screens do not publish observation snapshots. Count genuine terminal
ScreenEvent.Render.Post callbacks instead; preserve the existing observation store
without inventing a snapshot. Preserve and exclude the failed run.

At `13e9682b`, all 37 native guard cases and seventeen ordinary checks pass,
JVM exit 0, native interval 188.956217 seconds. Eight actual terminal render
callbacks exercise the wrong-route wait. The actual idle CPU rejects premature
release without changed server state. All fourteen captures reviewed and all
37 archived hashes match. Both CI jobs and both Codecov uploads pass. Codecov
confirms 95.92%, one miss and 236 partials. Visual gates remain REVIEW_REQUIRED.

Pending completed-job chat guard: run the original `craft-lifecycle` with both
real furnace jobs. Wait for each genuine full or partial coverage chat response
while the actual completed job passes the original server accuracy predicate.
Temporarily replace only the client response's coverage suffix, invoking the
original guard from an independent flow with the actual fixture and interaction.
Require its retained error after real stable frames, then restore the original
native message objects and interaction deadline before rendering. Preserve native
menu, screen, observation, config and ordinary checks. Capture only the restored
chat/plan and require all twelve ordinary lifecycle checks afterward. Invalid
inputs are client payload evidence, not rendered chat or invented server history.

At `117ca4cb`, both full/partial chat guards and all twelve ordinary lifecycle
checks pass, JVM exit 0, native interval 235.688974 seconds. All fifteen originals
reviewed; all 39 archived hashes match. A copied helper left an incorrect SHA
suffix in the local archive receipt; it was corrected from the authentic archived
process receipt and every hash reverified. Native receipts and uploads were
unchanged. Both CI jobs and both Codecov uploads pass: 95.96%, zero misses and
235 partials. Restored plans obscure faded chat; visual gates remain REVIEW_REQUIRED.

Pending native Options extension: open actual Appearance controls with Badge
background Off and On in the isolated test config. Verify pending Off values,
incorrect relaunch/Cancel/Done values, and pending saves on the real Options
screen without advancing checks or changing saved bytes. Temporarily relabel
the actual badge/shadow widgets to exercise missing-control guards, restoring
their original Component objects before drawing. Require retained errors or
unchanged pending stages and capture the restored Off/On controls. Do not claim
these isolated driver expectations prove a real process relaunch.

## Plan readiness with optional backgrounds (#585)

Status: planned; implementation and runtime verification pending.

Issue: [#585](https://github.com/cTux/ae2-crafting-time/issues/585). See the [design](technical-design.md#plan-readiness-with-optional-backgrounds-585) and [plan](implementation-plan.md#plan-readiness-with-optional-backgrounds-585).

Restore visible Crafting Plan smoke completion with Badge background off. This driver fix changes no production option, renderer, recipe, result schema or timeout policy.

- **PS585-1/2:** Plan readiness uses rendered total TTC and the expected output row, independent of background fills. Preserve fresh-frame deduplication, three stable ordered rows, row TTC, sorting, tooltip, Tree node draw, requester assertions, reset/details and optional-read recovery. Missing frames, target, total or drawn Tree TTC must fail.
- **PS585-3:** Validate required text geometry and any fills actually drawn. Empty fills are valid with the option off; invalid geometry or missing TTC fails in either mode.
- **PS585-4:** Report screen, target and unmet rendered-content/stability condition through existing diagnostics. Deadlines and truthful checkpoint progress remain unchanged.
- **PS585-5:** Cover shared and native 26.1.2 code with regression, compile and focused smoke checks; retain results and captures, separating watchdog failures from UI assertions.
## Recurrent fixture option ownership (#602)

Status: in-progress

Scope: Restore deterministic `recurrent-plan` setup after detection became opt-in.

Issue: [#602](https://github.com/cTux/ae2-crafting-time/issues/602).

Planning: [design](technical-design.md#recurrent-fixture-option-ownership-602)
and [implementation plan](implementation-plan.md#recurrent-fixture-option-ownership-602).

The reported Forge 1.20.1 `two` case expects stone recurrence but gets an empty
diagnosis before rendering. Fresh servers now correctly default detection off.
This repair makes the fixture own its temporary opt-in; it changes no production
default, recurrence rule, pattern, packet or saved configuration.

- **RF602-1:** Before calculating a recurrence case, the shared fixture enables
  `RECURRENT_DETECTION` on the logical server and synchronizes the client through
  the existing server-options snapshot. Integrated and marked dedicated callers
  use the same setup and retain real calculations and all negative controls.
- **RF602-2:** Save the original switch once per fixture lifetime. Repeated
  preparation and case changes must not overwrite it. Restore either original
  value on close and synchronize connected clients. Close before dedicated grid
  replacement and on success/failure finalization. Unused and repeated close are
  harmless. Do not persist the override or change sibling settings. An aborted
  integrated run stops its disposable server instead of advancing the suite.
- **RF602-3:** The current-head Forge 1.20.1 compatible scenario passes existing
  semantic cases and retains an inspected Recurrent row and tooltip. Following
  `standard-plan-controls` in the same world sees the restored option. A compatible
  Fabric 1.20.1 dedicated recurrence run covers grid replacement, replan and
  reconnect through the other shared caller. The row's badge background follows
  the client's Badge background switch, which defaults off.
- **RF602-4:** Regression checks cover initial false/true, repeated setup, case
  transitions, restoration and unchanged siblings. Keep all four target adapters
  compatible and production artifacts independent of the test driver.

These checks qualify this fixture repair, not the broader recurrence feature or
addon matrices. Implementation review, current-head checks, and integrated and
dedicated runtime verification remain pending.

## Optional connection observation extension

Issue [#537](https://github.com/cTux/ae2-crafting-time/issues/537) adds an
opt-in `ae2craftingtime.test.observeConnection` mode. It records outbound wrapper
attempts and loader send invocations for six payloads in each direction, per
connection. Unsupported peers receive bounded probes; the required result is six
attempts and zero loader sends on the installed side. Both-installed control
traffic must yield a nonzero loader send. The absent side loads neither the mod
nor its test driver. Manual plan/status screenshots and a real craft complete the
native UI evidence. See the [connection plan](../optional-connection/implementation-plan.md)
for the sixteen-cell acceptance contract. This extension is pending runtime
qualification and does not alter the shipped scenario result.

The saved Forge fixture starts within reach of its marked terminal and contains
no retained CPU jobs. Optional scenarios keep its grid, patterns, and samples.
Native CPU setup waits for the formed cluster to become active before selection.

## Compact status amounts extension

Lifecycle and acceptance: [compact status amounts](../crafting-status-amounts/spec.md).
This planned extension is outside the shipped driver baseline. Extend
`standard-status-controls` with the eight quantity combinations, item/fluid
boundaries, native tooltip comparison, independent compact/TTC controls,
Done/Cancel/resets and saved-option relaunch checks. Exercise default and wider
fonts at scales 1, 2 and Auto on all four targets. Existing status leaves retain
their real waiting, running and warning checks and also inspect the new badge.
The [feature plan](../crafting-status-amounts/implementation-plan.md#verification-prerequisites)
defines fixture ownership, synthetic-row limits, capture requirements and
environment gates; none of these new checkpoints is claimed as implemented.

## CPU-list total TTC scenario

`cpu-list-total-ttc` opens the real Crafting Status screen on eight native CPUs:
five busy jobs include distinct totals, an equal-total tie, one unknown total,
and an off-screen shortest job; three CPUs are idle. Actual rendered-card
observations retain raw AE2 serial order separately. The driver captures the
initial mode and full button cycle, verifies stable CPU groups and item sorting,
then compares selection, the native TTC title, tooltip, cancellation, and badges by serial.

A focused second fixture creates 33 server-known busy CPUs. Without selecting
or scrolling first, captured outgoing request batches must cover every raw
serial, contain no duplicates, and stay at or below 32 entries. Deterministic
cache tests cover 0, 1, 25, 26, 32, 33, 100, and 1,000 busy rows, fairness,
timeouts, and adaptive expiry without inflating the runtime fixture further.

The scenario scrolls both directions, uses long English and Cyrillic fixture
names for static layout checks, and exercises reorder, removal, completion,
cancellation through the native button, and same-output replacement. It also
checks hit suppression before the first draw and for a removed prior-frame row,
wheel input before the next draw, server-selected identity, channel fallback,
late replies after mode switches, and AE2-mode expiry. It closes and reopens the menu,
switches to a second grid with overlapping names and serials, expires a delayed
reply, and reconnects without accepting stale totals. Runtime screenshots stay
English (`en_us`) and cover selected/unselected cards at the smallest and
largest GUI scales where the screen fits. Every card keeps its icon, amount,
progress bar, click area, tooltip, and scrollbar clear.

Run the same checks against integrated and connected disposable dedicated
servers on all four compatible targets. Record production/driver artifacts,
dependencies, selected adapters, server estimates, and matching client card
snapshots. Retained checkpoints include raw and displayed serial order, draw
scroll, server state, and timestamped outgoing request batches. A headless
server check is not UI evidence.

`recurrent-plan` is the bounded native Crafting Plan recurrence case. It uses
real self, two-key and three-key loops; ordinary, seeded, eligible-alternative,
successful-alternative, mixed, exact-variant, fuzzy-substitute and emitter
controls; concurrent and cancelled calculations; and CRAFT_LESS retries.
Large item quantities and fluid units exercise native amount formatting. Every
integrated case traverses all three TTC sorts without profiling samples, checks
the native quantity and cell bounds, and captures its row and tooltip. The
NeoForge 1.21.1 case also creates a real plan with more than 256 entries.
Client-thread observations record native setPlan before diagnostic chunks and
reject malformed/stale diagnostics without changing the plan. Its dedicated
counterpart is restricted to the marked loopback fixture; this does not enable
general multiplayer or arbitrary-server automation.
Every target runs one 8 GiB client with the fixed offline fixture identity.
The client visits grids with opposite results, swaps patterns/replans, and
reconnects without retaining an old diagnosis. Server acknowledgements bind
the observed player UUID, menu and revision to the captured client frame.
Unit/packet-boundary tests cover different recipients and reject wrong or stale
identity. Separate identity sessions, if used, run only after the prior client
exits. No simultaneous-player proof is required or claimed. Keep PID/start-time
and scheduled-task cleanup for the single client. Run this leaf plus
`standard-plan-controls` on all four targets in English; check Ukrainian
resources/components statically. These are required checks, not recorded passes.

All Minecraft smoke clients run sequentially, including connected scenarios;
confirm the previous client exited before launching another. A disposable
dedicated server may remain running for the client's reconnect. Remove obsolete
dual-client scheduling and resource requirements before running the revised gate.

The connected path is `scripts/run-connected-dedicated-ui-smoke.ps1`. It takes
one prepared server and the matching prepared client launch, stages only the
matching production and driver artifacts into the server, and retains separate
server/client logs, hashes, authoritative estimates, and semantic screenshots.
The prepared server is an immutable source marked by
`.ae2-crafting-time-dedicated-fixture.json`; the runner validates its target,
launch files, profile, and exactly one production/driver artifact pair before
creating a separately marked disposable copy below the report directory.

The [#482 provisioning expansion](connected-resource-fixtures/spec.md) adds a
bounded preparer for the four compatible connected targets and their supported
AppMek graphs. It seals new sources and requires explicit EULA consent for
disposable execution. Resource-only prewarm proves readiness before fixture
activation, not scenario success; older connected leaves keep their contracts.

## Stored-variant plan scenario (planned)

Lifecycle: see [stored-variant implementation and remaining verification](../exact-ingredient-mismatch/spec.md).
The heading retains its historical link anchor; the scenario is implemented.

`stored-variant-plan` verifies [#327](../exact-ingredient-mismatch/spec.md) using
real processing patterns and two damaged stacks of one item with different
exact NBT/components. Keep one native confirmation menu and summary open while
storage moves through none, near-match, none, near-match plus exact key, and
near-match again. Require automatic warning appearance, clearing and recovery
at the next ordinary synchronization after AE2's notification, with unchanged
native quantities, summary revision and Start behavior. Never seed diagnostics.

Capture each transition and the neutral normal-weight row and unchanged tooltip in English.
Cover ordinary, exact-only, different-item, fluid and successful controls,
multiple variants, unrelated rows, all TTC sorts without samples, narrow layout
and coexistence with recurrence. Record server storage facts and notification,
refresh, send and receive ordering; screenshots alone do not prove those facts.
Check English/Ukrainian resources and components statically.

Run the leaf and native plan/recurrence regressions on all four compatible
targets. Its connected counterpart adds only this named leaf to the marked
loopback exception, retaining one 8 GiB client at a time. Verify live changes,
replan, network switch, cancellation, reconnect and watcher cleanup through
server UUID/menu/revision acknowledgements. Pure and packet tests cover other
recipients, reordered/duplicate updates and zero-mask clearing; no simultaneous
player claim is made. Matching immutable dedicated sources and prepared clients
must be verified before execution; missing provisioning remains a prerequisite.
This is required future coverage, not an existing runnable leaf or a pass.

## Delayed resource icon scenarios (planned)

Lifecycle: [fixture scope](connected-resource-fixtures/spec.md);
[production icon scope](../provider-locate/resource-icons/spec.md).

The fixture prerequisite [#482](connected-resource-fixtures/spec.md) merged in
[#484](https://github.com/cTux/ae2-crafting-time/pull/484).
Its explicit fixture-only runs qualify real jobs and control/lifecycle evidence;
they report production icon acceptance as NOT_RUN and cannot satisfy #376.
The following remains the final production-icon acceptance contract.

For [#376](https://github.com/cTux/ae2-crafting-time/issues/376), implement the
[resource-icon acceptance matrix](../provider-locate/resource-icons/implementation-plan.md#runtime-acceptance-matrix)
through these existing bounded cases. Their ordinary production acceptance mode
is required future coverage; the existing fixture-only mode is not an icon pass.

| Scenario | Targets and real fixture outputs |
| --- | --- |
| `delayed-resource-icons` | Forge/Fabric 1.20.1, NeoForge 1.21.1 and 26.1.2: item, water, lava and overlap; Forge also has the bucketless test fluid |
| `appmek-resource-icons` | Forge 1.20.1 and NeoForge 1.21.1 with Applied Mekanistics: oxygen, hydrogen and overlap |

Submit real processing jobs, observe provider dispatch, withhold outputs until
DELAYED, then return them through normal ME crafting insertion. Include a named
bucketless fluid on Forge as specified in the linked plan. Do not seed
production delayed state, client highlights or renderer results. Existing
`appmek-cpu` oxygen storage setup is not a chemical-output rendering check.

Capture the recognizable resource texture and tint on visible provider faces
with chat disabled and the terminal closed. Cover shared-provider winner
promotion, manual rainbow expiry independent of red, recovery, immediate
completion, cancellation, provider removal/unload and resource reload. Base-only
profiles prove items/fluids work without AppMek. Observe retained and selected
plate keys alongside screenshots; key equality alone is not visual proof.

Run fluid send/reconnect on all four connected dedicated targets and chemical
send/reconnect on both AppMek targets. The same single client reconnects to the
still-running marked disposable server; require server-approved typed plate
restoration and no restored rainbow. This adds only these two named scenarios
to the bounded connected exception below. Retain normal safety checks, sequential
8 GiB clients and exact-process cleanup; do not enable arbitrary multiplayer.

Ordinary runs of these leaves must check production acceptance; explicit
`-ResourceFixtureOnly` preserves the prerequisite contract. Keep fixture evidence
at NOT_RUN and require additional `resource-icon-evidence.json` schema 1 for
ordinary runs, as defined in the
[production design](../provider-locate/resource-icons/technical-design.md#production-acceptance-on-the-existing-fixtures).
Missing or mismatched evidence fails. Typed-key assertions cannot replace world
capture review; visual acceptance remains REVIEW_REQUIRED until reviewed.

For #488, the item case places an opaque stone roof over its real provider.
The row double-click must leave beam state empty. A chat locate with a record
owned by another player must also leave it empty; the owner's command must
create a beam over the roof with the rainbow. The integrated case releases the
output while that pair is live, captures `item-recovery-pair.png` after the
plate clears, and then observes the pair's shared expiry. The connected case
captures the live beam before reconnect and
then confirms neither temporary effect returns. The capture sidecar records
the live beam positions and each rainbow's chat provenance.

Record resource key/type, authoritative job state, target/profile, dependency
and production/driver hashes, and unique English PNG/sidecar checkpoints for
each observed transition. Require all scenario checks, clean client exit and
reviewed world captures. Missing fixtures, unsupported APIs or absent required
integrations fail the required case rather than silently reducing its scope.

## Optional screen read recovery

`crafting-tree-read-recovery` and `merequester-read-recovery` use isolated addon
JARs with renamed read contracts. They require visible host content and no TTC
overlay. Tree also retains its original tooltip and ignores TTC SHOW/RESET
modifier clicks. Each negative case must be followed by `craft-lifecycle` in
the same process, with one original-cause WARN across both worlds. Positive
screen scenarios retain their normal TTC assertions.

## Optional selected-CPU read recovery

`advancedae-read-recovery` runs on an isolated dedicated server with a deliberately
renamed AdvancedAE menu field. It constructs a real transformed AE2 crafting
status menu on the fixture grid, reads the normal server stats context twice,
and requires the grid to survive with no selected CPU. It then submits a real
native AE2 job and requires fresh samples and successful completion. The runner
must retain exactly one WARN and the original missing-field cause. Altered addon
JARs remain disposable test inputs and never enter production artifacts.

All future smoke follows the [shared policy](../automated-ui-testing/spec.md#smoke-policy):
newest adapter per dependency/target and English (`en_us`) only. The English-only
scenario requirements below are the required next state; removing existing
bilingual driver states is follow-up implementation work. Keep Ukrainian
product translations and static resource checks.

## Chance-output status scenario

`chance-output-status` is a focused Forge 1.20.1 leaf requiring the compatible
Mekanism graph. Its isolated native AE2 CPU dispatches a processing pattern for
100 sawdust from acacia hanging signs into one directionally targeted Precision
Sawmill. The pattern also promises the recipe's guaranteed two planks. The pinned
Mekanism recipe declares a 50% secondary sawdust chance. The fixture clears
accepted inputs in the unpowered sawmill until all 100 operations dispatch.
The fixture leaves the machine unpowered for a controlled return: zero output,
then 200 planks and 60 sawdust through the provider return inventory, with
40 sawdust still promised.
It must observe the real CPU job, authoritative recipe evidence, red Chance
output label, 50% tooltip, contained badge, and cancellation clearing in
English. Retain screenshots for zero, partial, tooltip, and cancellation.
After cancellation, leave one previously dispatched sign in the sawmill, power
the machine, and observe one actual recipe operation. Require two planks and
record whether zero or one sawdust appeared; do not require either random
outcome. The controlled return proves status arithmetic separately.

## No-provider status scenario

`no-provider-status` submits a real 64-output processing job to an isolated
native AE2 CPU. Blocking mode and a chest hold the first batch active while
later batches remain scheduled. Removing its pattern must show NO PROVIDER in
that combined row. Check the rendered badge and both tooltip sentences in
English (`en_us`). Restore the pattern and require recovery without
reopening the menu. Install an equivalent pattern in a second provider, remove
the first pattern, and require no warning for two refresh cycles. Remove that
second provider block, observe the warning again, then replace/reconnect it and
require recovery. Cancel the job and confirm its diagnostic clears.

The scenario uses real provider inventories, grid lookups, job submission,
menu synchronization, and final frame observations. It never seeds the warning
or invokes a production reporting hook to make a UI assertion pass. Capture
before, mixed-row warning, English tooltips, both recoveries, redundant
provider, provider removal, and cancellation checkpoints. Keep the scenario
available across the shared 1.20.1/1.21.1 driver and the 26.1.2 API counterpart;
a passing smoke result applies only to the target actually launched.

## Provider-dispatch status scenarios

`no-target-status`, `input-blocked-status`, and `locked-status` reuse the same
real 64-output processing job. NO TARGET removes and restores the provider's
adjacent inventory. INPUT BLOCKED exercises both blocking mode and a completely
full target, then proves recovery through disabled blocking and partial target
capacity. LOCKED exercises while-low, while-high, until-pulse, and until-result
provider modes through redstone changes and an item returned through the
provider return inventory. Every warning is observed in a mixed active/scheduled
row with its English label, explanation, suggestion, qualifier, and contained
badge; every recovery happens in the open screen.

All four native targets run the three leaves. The Forge 1.20.1, NeoForge 1.21.1,
and NeoForge 26.1.2 AdvancedAE graphs repeat them with a job submitted directly
to an AdvancedAE CPU. Driver code detects that graph from the loaded addon and
does not link AdvancedAE classes in native or Fabric runs.

## No-space status scenario

`no-space-status` uses an isolated native AE2 CPU and a full item cell in the
disposable world. A full external furnace first leaves the warning absent.
The fixture seeds retained CPU contents through AE2's inventory API; AE2's
normal tick and menu synchronization must report rejected storage. Observe the
rendered warning, badge, and tooltip in English (`en_us`). Replace the full
cell with writable capacity and require the warning to clear in the same screen.
Keep before, English tooltip, and recovered screenshots. Pure row tests
cover active and scheduled exclusions; the fixture does not simulate a craft.

## Standard AE2 acceptance scenario

`standard-ae2` is a host-expanded group of ten independently runnable leaves:
`standard-plan-controls`, `badge-background`, `recurrent-plan`,
`stored-variant-plan`, `standard-status-controls`, `waiting-status`,
`running-status`, `delayed-status`, `craft-lifecycle`, and `cpu-list-total-ttc`. Each has a fresh
native grid and its own seeded estimates; no case depends on an earlier reset,
world, job, or cached observation. Standalone standard leaves copy only world metadata and
the marker (including separate native world-generation settings when present),
then generate fresh chunks; importing saved chunks could restore
incompatible CPU jobs before the driver starts. Other scenarios keep their
tracked layouts. Shared-world suites restore pristine fixture state between
leaves instead of copying a world per leaf. The host keeps one process for the group.

Plan controls also checks highlight item resolution against the loaded registry:
known stone resolves correctly; null, malformed, and unknown IDs return empty stacks.
Before checking plan sort order, wait for both seeded row estimates to render;
a partial stats reply must restart the stable-frame gate.
Its plan intentionally lacks one input and checks that the missing row stays ahead
of the two craftable rows in AE2 order and both TTC directions.

The [leaf contracts](../automated-ui-testing/technical-design.md#groups-and-independent-standard-flow)
retain every original assertion. Waiting and running require real dependency
progress. Delayed checks the active row, normal-weight red label, diagnostic tooltip and
recovery after actual output. Its two intermediate outputs wait on the same
provider, where it requires one stable first-retained render icon. A real row
double-click adds a rainbow edge; releasing that selected output must reveal the
surviving icon while red and rainbow remain. It then withholds the final output
until its own delayed plate arrives and imports that output with all menus closed.
Require CPU completion, fresh samples, stored output, and client plate
clearing without an intermediate progressing craft. Capture held-output status,
overlap, winner recovery, final highlight, world completion, and reopened idle
status. The world camera
shows an AE2 terminal directly in front of the final provider to review red
background and item-icon occlusion. Client plate state proves packet lifecycle;
inspect world screenshots to verify rendering. Lifecycle follows terminal, amount, plan, Start,
status, real furnace output and new samples, then reopens the idle CPU screen.
The raw JVM property accepts leaf IDs or `suite`; use the host alias for the group.

## Goal

Provide a development-only companion mod that drives and observes a real
Minecraft client for repeatable AE2 Crafting Time UI smoke tests. It runs beside
the production mod and never replaces or ships inside it.

The driver covers the standard AE2 Crafting Plan screen and add-on crafting CPU
scenarios on Minecraft 1.20.1 Forge/Fabric and 1.21.1 NeoForge. New optional-mod scenarios must plug into
the shared add-on fixture flow without adding another branch to the UI state
machine.

Wireless-terminal addons are the exception because they add terminals, not
crafting CPUs. Their scenarios open the real wireless terminal, check the
craftable-entry TTC tooltip, and follow the normal AE2 Crafting Plan flow.

ME Requester has its own screen scenario. It places and configures a real
requester on the disposable grid, opens that screen normally, and checks its
rendered TTC row, header total, badge layout, and screenshot.

This covers [issue #126](https://github.com/cTux/ae2-crafting-time/issues/126).

## Fabric full-client suite

Run `scripts/invoke-ui-smoke-codexvm.ps1 -Target 1.20.1-fabric -Scenario suite`.
The full pinned compatible graph runs in one maximized 8 GiB client. The expanded
suite manifest owns the case list, including Crafting Plan, ExtendedAE, Applied Botanics, AE2 Things DISK storage,
MEGA Cells, AE2 Wireless Terminals, ME Requester, and NO SPACE. Each case gets a
pristine fixture reset in the shared world, screenshots, and checked semantic results.
JEI and transitive libraries load with the graph but have no dedicated assertions.
Crafting Tree and Network Analyser are not pinned in this Fabric graph.

The same `-Target` works for single scenarios, latest profiles, and interactive
runs. Full suites also run as separate latest diagnostics. Fabric uses its own version-matched
`ae2-crafting-time-<mod-version>-fabric-1.20.1-test-driver.jar`, remapped by Loom,
installed into `run/mods` or `run-latest/mods`. Player JARs stay independent.

## NeoForge 1.21.1 full-client suite

Run `scripts/invoke-ui-smoke-codexvm.ps1 -Target 1.21.1-neoforge -Scenario suite`.
Use JDK 21 and the complete pinned compatible graph in one maximized 8 GiB
client. The expanded cases in `scripts/ui-smoke-neoforge-suite.json` cover the base
plan, Crafting Tree, all pinned CPU/provider fixtures, four wireless-terminal
flows, ME Requester, and NO SPACE. Each case uses a pristine fixture reset in the shared native 1.21.1 world and retains
its own semantic results and screenshots. JEI, GuideME, and transitive libraries
load with the graph but have no dedicated UI assertions. Expanded AE remains
excluded from the compatible graph because of its recorded OmniSequence conflict.
Applied Botanics, AE2 Things, and Network Analyser are not pinned in this graph.

Compatible and latest launchers install the exact
`ae2-crafting-time-<mod-version>-neoforge-1.21.1-test-driver.jar` in their managed
`mods` directory. Single scenarios and interactive diagnosis use the same target;
full suites also run as separate latest diagnostics. The companion stays inert without explicit
scenario options and never enters the production JAR or `dist`.

## NeoForge 26.1.2 full-client suite

Run `scripts/invoke-ui-smoke-codexvm.ps1 -Target 26.1.2-neoforge -Scenario suite`.
The pinned compatible graph runs its expanded suite in one maximized 8 GiB client, including
Crafting Plan, AdvancedAE, ExtendedAE, BM Addon, Lightning Tech, OMNI Cells,
Applied Flux, AE2 Wireless Terminals, Import Export Card, Infinity Booster,
and NO SPACE. Neo Vitae supports the BM Addon recipe; GuideME, JEI, and
transitive libraries load with the graph without dedicated assertions.

Each case uses a pristine fixture reset in one disposable native 26.1.2 world. The driver
builds a native AE2 grid in that copy and checks real crafting, new profiling
samples, final UI observations, and checkpoint screenshots. The Gradle launcher
uses JDK 21 and selects the JDK 25 client toolchain. The exact companion JAR
is installed by both compatible and latest launchers and stays out of player
artifacts and `dist`. Full suites also run as separate latest diagnostics.

## Artifact contract

The driver artifact name is derived from the project version:

```text
ae2-crafting-time-<mod-version>-forge-1.20.1-test-driver.jar
```

It tests exactly:

```text
ae2-crafting-time-<mod-version>-forge-1.20.1.jar
```

The driver has its own mod ID, `ae2craftingtime_test_driver`, and refuses to
load with a different AE2 Crafting Time version. It accepts every AE2 version
allowed by the matching production mod so the same driver can run the
compatible and latest development profiles.

The driver artifact is written under `build/test-driver`, never `dist`. It does
not embed production AE2 Crafting Time classes. Player JARs and published
artifacts must not contain driver classes, resources, metadata, or dependencies.

### Diagnostic relaunch fast path

`cpu-list-total-ttc` may resume phase 2 from a captured phase-1 bundle only for
diagnosis. The bundle binds the marked disposable world, continuation and
screenshots, campaign/world identity, Git head, exact artifact-tree hash,
dependency mode, and an ordinal JAR-name/hash catalogue. Phase 1 writes that
dependency identity into the disposable world marker. Capture and restore reject
a marker from any other dependency graph before scheduling Java;
any mismatch is rejected. A resume-only result is explicitly non-final. Final
approval still runs both client processes and proves their distinct PID/start
identities. The runner records launch counts and per-phase timings and may fail
early when callbacks stop advancing. While the loader, world, or fixture is
still preparing, the absolute startup deadline remains authoritative; the
60-second no-checkpoint deadline begins only after the driver reports
`state=WORLD_READY phase=ACTIVE`.

Changed selection maps this feature directly to `cpu-list-total-ttc` on all
four required targets, one primary graph each. A sealed bundle is built once per
immutable head/selection fingerprint and hash-verified on every reuse.

## Development client installation

`scripts/run-client.ps1 -Target 1.20.1-forge` builds and installs the matching
driver JAR in its development client's managed mod directory before starting Minecraft:

```text
versions/1.20.1-forge/run/resolved-mods/
  ae2-crafting-time-<mod-version>-forge-1.20.1-test-driver.jar
```

Add `-Latest` to use `run-latest`; the matching `scripts/run-client.sh` command
has the same behavior. Each launch replaces a stale
driver copy and fails before Minecraft starts if the matching driver cannot be
built or installed.

The installed driver remains inert during an ordinary development-client run.
Only the explicit test-driver launch option starts a scenario or MCP endpoint.
Installation in these managed development clients does not copy it to `dist`
or make it a published mod artifact.

See [Automated UI Testing](../automated-ui-testing/spec.md) for the eventual
cross-target and optional-dependency suite.

## Automatic Crafting Plan scenario

The runner copies the tracked Forge 1.20.1 fixture world, starts the client with
the driver explicitly enabled, and runs this sequence:

1. Enter the disposable fixture world.
2. Open its known AE2 terminal.
3. Select its known craftable output.
4. Wait for `CraftConfirmScreen` and stable plan data.
5. Check TTC rows, total TTC, badge geometry, and the sort button.
6. Cycle AE2 order, shortest first, and longest first through the real button.
7. Hover the known row and check its final tooltip.
8. Save base, each sort-mode, and tooltip screenshots and an atomic semantic result.
9. Close the exact client cleanly.

The fixture supplies retained samples so this scenario checks rendered TTC,
not the separate `No data yet` state. A fixed resolution, GUI scale,
language, fixture, and cursor position make the screenshots comparable.

The driver observes the final AE2 screen, renderer, widget, tooltip, and
framebuffer state after normal production hooks have run. A production method
reporting that it executed is not evidence that its output reached the screen.
Translation keys and output IDs are semantic identity; rendered English text is
report evidence, not the assertion key.

The `appbot-cpu` fixture mounts a real Applied Botanics mana cell, verifies
native mana insertion and extraction through the grid, and then runs the shared
craft/sample/TTC flow on a normal AE2 CPU. Separate amount, packet, and saved-data
tests cover raw mana precision and unit migration; the fixture does not claim
to automate a Botania mana-generation recipe.
The `appbot-fork-cpu` scenario reuses that fixture against the separately pinned
fork artifact. Original and fork must never be loaded together.

The `advancedperipherals-cpu` fixture connects a real ME Bridge and CC:Tweaked
computer to the grid. It submits the selected output through the bridge's
`craftItem` API using the attached computer, then observes a new server profile
sample and TTC in the normal AE2 Crafting Plan. This tests the peripheral API,
not an automated Lua editor or a separate ComputerCraft TTC display.

The `ae2things-cpu` fixture mounts the loader's real DISK inventory, removes
pre-existing cobblestone from the disposable grid, and supplies the craft
ingredients through that DISK. A native CPU craft must produce a fresh profile
sample and visible TTC. Removed Forge machines are not part of this scenario.

The `expandedae-cpu` fixture joins Expanded AE's real two-thread accelerator
to native crafting storage, verifies the CPU reports two co-processors, and
requires a new profile sample and TTC after crafting. Run it as a focused
latest-profile scenario: the full compatible graph excludes Expanded AE
because of its existing conflict with OmniSequence.

The `lightningtech-cpu` fixture submits a smooth-stone smelting job to a real
Tianshu pool, using a pattern provider, furnace, hopper, and ME interface.
It checks that the pool receives the job and requires a fresh
profiling sample for the final output and visible TTC on the next Crafting Plan.
This is a regression check for standalone outputs that go directly to ME storage.

## Single-launch suites

A named-pack or prepared-client campaign runs all selected scenarios in one Minecraft process.
The normal suite also keeps one disposable world loaded. Before each case, restore
the pristine fixture, player and profiler state, and clear client observations.
Save each case's screenshots and result before resetting. No case may consume a
previous case's blocks, inventory, job, sample or cached response. Schema-1 world
reload suites remain available for explicit isolation diagnostics.

Use `scenario=suite` with the usual profile, output directory, and first world
properties. The output directory contains `suite-plan.json`: schema 2 and a
`cases` array of `{scenario, world}` entries (1–64 unique scenarios) sharing one
marked disposable world. Schema 1 requires distinct worlds instead. All referenced
worlds must be pre-created, marked disposable copies. Reject unknown schemas and
mixed-world schema-2 plans. Validate the whole plan before acting.
Interactive mode remains single-case only.

Each case writes under `<output>/<scenario>/`. The root `result.json` records
one JVM process ID, ordered case outcomes and timings, and the overall result.
Missing cases never count as passes. Stop on the first failure, retain its
screenshot/result, mark later cases `NOT_RUN`, and close the exact client.
Only a complete suite with every case passing can report `PASS`.

The existing single-case option and result schema remain unchanged. Selecting a
suite does not silently add mods or skip missing integrations.

Prepare a suite with `scripts/prepare-ui-smoke-suite.ps1 -RuntimeDirectory <game>
-OutputDirectory <new-evidence-directory> -Scenarios <ordered-names>`. It returns
the first world and launch properties. Pass `scenario=suite`, that world, profile,
and output through the existing JVM properties, with the first world as Prism's
quick-play world. The helper never changes the pack's mod graph.

For the full prepared Forge compatible graph, run
`scripts/invoke-ui-smoke-codexvm.ps1 -Scenario suite`. The ordered cases live in
`scripts/ui-smoke-forge-suite.json`; each uses a reset fixture in one loaded world.
The wrapper validates every per-case result and screenshot plus the overall
suite result, retains shared logs, and cleans all disposable worlds. The suite
allows 40 minutes including dependency resolution/build; single cases keep their
8-minute limit. Archive the evidence before another invocation.

This suite rejects `-Latest`, `-ProjectId`, and `-Interactive`: its case list
matches the full compatible graph. The graph includes the Applied Botanics fork;
the colliding original and incompatible Expanded AE require separate graphs.
Documentation/recipe viewers and transitive libraries are loaded
but have no dedicated UI assertions. Do not report those as scenario passes.

## Optional add-on CPU fixtures

`advancedae-cpu` builds a valid Quantum Computer enclosure containing its core,
an Accelerator, and a Data Entangler. CPU selection verifies the added threads
and multiplied storage. Submission must create a job in that exact cluster
before a fresh profile sample and the resulting plan TTC can pass.

## Crafting Tree scenario

`crafting-tree-screen` opens the real tree toolbar button from a populated
Crafting Plan. It supports the original and Refreshed widget packages. Check
the rendered node badges, their bounds against node icons, and a hovered
crafted node's TTC and details/reset hints. Capture the tree and its tooltip
separately. A missing tree, badge, or tooltip fails the scenario; opening a
normal plan alone cannot pass it. Run against the prepared compatible graph
and the original Crafting Tree artifact in Project Infinity 0.1.

## Optional fixture contract

An add-on CPU scenario uses the same disposable world, UI flow, profiler checks,
result schema, and runner. Its fixture owns only the add-on-specific placement,
formation, and CPU selection. Scenario names end in `-cpu`; the runner derives
the standard add-on checks and screenshot name from that convention.

Adding an optional mod requires one fixture implementation, one registry entry,
and a driver-only compile dependency. It must not add a production dependency,
make the optional mod mandatory, or require a new runner branch.

## Wireless terminal scenarios

The `ae2wcwt-terminal` scenario links a charged Wireless Comprehensive Wireless
Terminal to a real wireless access point on the fixture grid. It opens the
terminal through normal item use, hovers the known craftable output, checks the
TTC tooltip, then clicks the entry and verifies TTC on the standard Crafting
Plan screen. The required checks are `screen`, `ttc-tooltip`, and `plan-ttc`.

The `ae2wtlib-terminal` scenario uses the same flow with AE2 Wireless
Terminals' charged wireless crafting terminal.

The `ae2importexportcard-terminal` scenario links a charged standard AE2
wireless terminal, installs a real export card, and verifies the TTC tooltip
and Crafting Plan on the addon's modified terminal screen using a deterministic
profile sample.

The `aeinfinitybooster-terminal` scenario installs a real Infinity Card in the
linked access point, moves the player beyond its normal range, and opens the
standard wireless terminal. It checks `screen` and `plan-ttc`, with terminal and
plan screenshots. The range-only addon adds no terminal TTC tooltip surface.

## ME Requester scenario

The `merequester-screen` scenario places a real requester on the fixture grid,
configures a deterministic out-of-stock diamond request and profiler sample, opens its
screen through block use, and checks `screen`, `ttc-row`, `total-ttc`, and
`layout`. The layout check includes active menu item slots, so a badge drawn
under an item cannot pass just because its text draw call was observed.
Its screenshot is `merequester-screen.png`.

## AE2 Network Analyser scenario

The `ae2networkanalyser-screen` scenario equips the real network analyser,
opens its configuration screen through normal item use, and verifies the
expected screen/menu identity and that its GUI remains inside the viewport.
It does not assert TTC text because the addon visualizes network topology and
does not expose crafting status.

## Interactive diagnosis

Interactive mode runs the same scenario but pauses at the failed or completed
step and exposes a loopback MCP endpoint. The first artifact provides only:

```text
minecraft_get_state
minecraft_get_screen
minecraft_get_ui_snapshot
minecraft_take_screenshot
minecraft_get_logs
minecraft_quit
```

The first five tools observe the launched client without changing its world.
`minecraft_quit` performs only a normal client shutdown. Synthetic click,
hover, key, command, and world-editing tools are deferred until a later
scenario proves they are needed.

The endpoint:

- listens only on `127.0.0.1` and only in explicit interactive mode;
- requires a fresh per-run secret that is not placed in command-line logs,
  result files, or screenshots;
- accepts one controller at a time;
- uses a fixed tool allowlist and bounded arguments, requests, responses, and
  timeouts;
- stops when Minecraft exits; and
- never exposes arbitrary Java calls, shell execution, Minecraft commands, or
  filesystem access.

Client state is read only on the Minecraft client thread. Integrated-server
state is read only on the server thread. Endpoint work is queued to the owning
thread and fails on a bounded timeout.

## Embedded server class loading (#353)

The interactive endpoint must resolve its shaded server classes through the
test driver's defining classloader. Forge must not report missing parent
classes for relocated Tomcat classes during endpoint startup or requests.
The reported Project Infinity run completed its focused checks despite these
errors; it does not establish a crash or a broken endpoint.

Acceptance criteria for this driver-only correction:

- **CL1:** Both the shared driver and native 26.1.2 implementation bind their
  embedded context to the driver loader before startup. An isolated-loader
  regression fails without the binding and passes with it.
- **CL2:** A prepared Minecraft 1.20.1 Forge compatible client on Java 17 runs
  `craft-lifecycle` interactively without the reported relocated-Tomcat parent
  errors. MCP initialization, tool listing, state inspection, screenshot capture,
  and normal quit succeed. Existing lifecycle checks and required screenshots
  remain complete and visually reviewed.
- **CL3:** Both endpoint implementations compile and package with the existing
  dependencies. Production artifacts remain isolated; authorization, loopback
  binding, limits, and game-thread scheduling remain intact.

Do not suppress errors globally or change dependencies without new evidence.
Prepared Forge verification is not an exact Project Infinity 0.0.52.0 /
Forge 47.4.20 pack pass.

## Fixture safety

The driver may act only when all of these are true:

- the explicit test-driver launch option is present;
- the connection is singleplayer, or an explicitly selected bounded connected
  scenario (`cpu-list-total-ttc`, `recurrent-plan`, or the planned
  `stored-variant-plan`, `delayed-resource-icons` / `appmek-resource-icons`) uses the runner-created
  loopback dedicated server and control directory;
- the opened world is the runner-created disposable copy; and
- the world contains the expected test-fixture marker and scenario data.

It refuses other multiplayer sessions, an unmarked world, the tracked source fixture, or a
world opened without test-driver mode. After a timeout it records failure and
stops taking scenario actions. The runner owns copying and deleting the
disposable world; the driver changes only the running copy.

## Layout checks

Semantic observations include screen-relative rectangles for visible rows,
text, badges, item cells, AE2 buttons, and the test target widget. A required
rectangle fails when it is outside the GUI or overlaps an owned control or item
cell. Screenshots remain the human-readable evidence for clipping, spacing,
color, and unexpected visual changes.

The first slice does not use full-frame golden-image comparison. Add a cropped
golden comparison only after a stable region has a demonstrated regression that
semantic bounds do not catch.

## Result contract

The driver writes `result.json` atomically in the scenario output directory. A
temporary or incomplete file is never a pass.

```json
{
  "schema": 1,
  "complete": true,
  "driver": "ae2-crafting-time-1.1.0-forge-1.20.1-test-driver.jar",
  "target": "1.20.1-forge",
  "profile": "compatible",
  "scenario": "craft-plan",
  "result": "PASS",
  "checks": {
    "screen": true,
    "ttc-row": true,
    "total-ttc": true,
    "sort-cycle": true,
    "tooltip": true,
    "layout": true
  },
  "screenshots": ["craft-plan.png", "craft-plan-sort-1.png", "craft-plan-sort-2.png", "craft-plan-sort-3.png", "craft-plan-tooltip.png"]
}
```

The runner independently checks the schema, completion flag, exact driver name,
target, profile, scenario, required check set, screenshot existence, clean
client exit, and fatal log entries. Missing or invalid output is a failure.

## Compatibility

- First artifact: Minecraft 1.20.1, Forge, Java 17, standard AE2 Crafting Plan.
- Optional add-on CPU fixtures currently cover AdvancedAE, Applied Flux,
  Applied Mekanistics, BM Addon, Crazy AE2 Addons, AppliedE, ExtendedAE,
  ExtendedAE-Plus, MEGA Cells, Modern AE2 Additions, NeoEco AE, OMNI Cells,
  OmniSequence: Transfinite, LightningTech's Tianshu multidimensional CPU pool, and ProjectCell.
  ExtendedAE and ExtendedAE-Plus replace the fixture's molecular assemblers
  with ExtendedAE assemblers. BM Addon installs a real Blood Pattern and its
  inputs. Crazy AE2 Addons places a native AE2 1K crafting storage CPU and
  selects that recorded cluster. Modern AE2 Additions builds a native AE2 CPU
  with its 4x co-processor and selects that recorded cluster. ProjectCell
  replaces the fixture's normal cobblestone supply with a bound EMC Storage
  Cell. AppliedE replaces that
  craft path with a player-owned Transmutation Module, furnace knowledge,
  ProjectE EMC, and a native AE2 CPU. Applied Mekanistics mounts a chemical
  storage cell containing oxygen and places a native AE2 256K CPU. The other
  provider scenarios use an existing idle CPU because those addons do not add
  one.
- AE2 WCWT and AE2 Wireless Terminals have separate Forge 1.20.1 terminal
  scenarios because they add no crafting CPU.
- ME Requester has dedicated Forge and Fabric 1.20.1 screen scenarios.
- Run it against both the compatible and latest AE2 profiles already owned by
  `scripts/run-client-versions.json`.
- Share identical 1.20.1 driver code. Keep loader entrypoints in their modules.
- The driver may compile against production classes but may not alter the
  production packet protocol, saved-data format, runtime behavior, or JAR.

## Not included

- Optional-addon behavior outside the documented scenario contracts.
- General dedicated-server or multiplayer support outside the bounded connected
  scenarios and registered server fixtures.
- General-purpose UI automation, arbitrary world setup, or remote control.
- Pixel-perfect full-frame comparisons.
- Publishing the driver on GitHub, CurseForge, or Modrinth.
- A new production config option, packet, API, or test hook.

## Acceptance criteria

- The dedicated build task creates only the version-matched driver under
  `build/test-driver`.
- The Forge 1.20.1 compatible and latest development launchers install that
  exact driver in their selected `resolved-mods` directory, remove stale driver
  versions, and stop if installation fails.
- Both loaders refuse a driver paired with the wrong AE2 Crafting Time version.
- The driver remains inactive without the explicit test option and refuses
  other multiplayer sessions, the tracked fixture, and unmarked worlds. The
  bounded connected exception requires the marked disposable server and explicit
  control-directory launch options described above.
- One command copies the fixture, runs the compatible Crafting Plan scenario,
  validates the result and logs, saves five screenshots, closes the exact
  client, and returns zero only on a complete pass.
- The same command can select the latest profile without weakening a compatible
  profile failure.
- The scenario proves the real screen, TTC row, total, three sort modes,
  tooltip, badge bounds, and non-overlap rules from final UI observations.
- A registered add-on CPU scenario reuses the common setup, selection, sample,
  result, and screenshot flow without changing the runner's scenario allowlist.
- Interactive mode exposes only the six bounded tools above and rejects missing
  authentication, a second controller, oversized input, and unknown tools.
- Client and server access stays on the owning game thread and times out rather
  than blocking indefinitely.
- `dist` and every production JAR remain free of driver artifacts and classes.
- Automated tests cover result validation, state transitions, safety refusals,
  endpoint limits, and artifact isolation.

## No-power status scenario

`no-power-status` reuses the real processing-job fixture with 64 cobblestone
per dispatch and an unfuelled furnace. Verify no warning while network energy
is sufficient. Replace the creative source with a real energy cell and keep
only enough energy for idle demand, below the next dispatch cost. Observe an
active CPU, one active output, and scheduled work, then the rendered NO POWER
badge and complete tooltip in English (`en_us`). Restore energy, require
another real dispatch and warning recovery in the same menu, cancel, and check
that an inactive CPU alone produces no warning. Retain each distinct English checkpoint.
Every full compatible suite includes this scenario and the NO PROVIDER
regression. Driver checks observe final frames and real AE2 state, never seed
production diagnostics. Shared pure tests cover threshold, expiry, priority,
CPU switching and lifecycle; packet tests cover the shared transport boundary.

## Badge background checkpoints (#532)

The `badge-background` standard AE2 leaf uses the native Client → Appearance
screen to save a nondefault badge RGB and opacity, then saves Badge background
Off and On. Capture the Crafting Plan and Crafting Status with the same live rows
in both states, including a tinted Plan row and unchanged text. Supply the
missing Plan input and replan before Start. Retain the saved client option and
selected RGB/opacity through assertions at each capture. A screenshot requires
human review to confirm the rounded fill disappears and returns while AE2's own
background remains. Missing rows, labels, or native controls fail the leaf.

When launched as a focused leaf, retain the six Plan/Status captures, then save
Off and stop the first client with a continuation bound to the campaign, world,
and saved client config hash. The runner must start a distinct second Java
process, verify Off from the saved config and native Appearance control, and
capture Off before restoring On. In that second process, exercise native Cancel,
Reset Appearance, and Reset all controls; Cancel must leave the saved Off state
and custom RGB/opacity intact, while both reset controls must show their default
draft values. Restore On with Done, reopen Appearance, and capture the saved On
control. Include small and Auto scale
status captures while Off, with contained native GUI and unchanged text. The
single-launch suite retains its existing six-check leaf without a continuation.

## Screenshot refresh checkpoints

For issue #343, `craft-lifecycle` also captures an unprofiled two-row plan,
then a plan with only the stone dependency profiled. It seeds both rows before
the first real two-furnace job. After completion it requests the final output's
actual details through Ctrl-click and captures the open chat with full job
coverage. It removes the completed output, clears only smooth-stone history,
and completes a second real job with partial prediction coverage. A second
Ctrl-click captures the resulting partial-coverage chat. Accuracy must come
from normal CPU submission and completion, never seeded accuracy samples.
Keep the original lifecycle checkpoints and add stable PNG/sidecar pairs for
each new context. Chat captures preserve the original response; published crops
exclude the player attribution. Single-case interactive mode may retain this
world for subsequent manual book checks; suites remain non-interactive.

## No-channel status scenario

For [#405](https://github.com/cTux/ae2-crafting-time/issues/405), add
`no-channel-status` with the complete [NO CHANNEL criteria](../provider-dispatch-statuses/no-channel/spec.md).
It submits a real processing job through a powered, booted provider starved by
an actual cable bottleneck. CPU, terminal and storage retain healthy channels.
Require zero learned samples, positive scheduled work, a failed dispatch and a
visible English NO CHANNEL badge/tooltip. Restore the route, observe the badge
clear, return actual output and require job completion. Verify a healthy
same-pattern alternative suppresses the warning and cover the specified
negative-state, mixed-row and lifecycle cases without seeding diagnostic maps.

Run direct status/recovery on all four native targets and all three applicable
AdvancedAE targets, retaining actual CPU/adapter identity. Missing AdvancedAE
must fail its required case rather than silently selecting native. Keep older
adapter contract/packaging checks. Record real node predicates, server ticks,
job state, result checks, English screenshots/sidecars and dependency manifest;
review badge/tooltip layout. The screenshot also supplies the canonical book
crop after visual review. No new runner, production dependency or persistent
channel diagnostic is introduced.

## Crafting suspension (#631)

The Forge 1.20.1 `crafting-suspension` leaf uses two standard CPUs, two native
providers in native blocking mode with the same processing pattern, and fueled furnaces. It drives the
real Suspend, Resume, Cancel and Server Options controls. The large 64-output
job pauses three times without changing UUID; a competing two-output job must
finish while the large job remains paused. Exact output and raw-input counts,
in-flight completion after suspension, cancellation refunds, disabled-action
rejection and profiling-off recovery are required checks. Furnace products are
transferred into native AE storage only after real machine ticks.

The connected leaf runs two distinct offline profiles at once. Both must see
matching server job state; Beta sends a retained stale action after changing
CPUs. Phase 1 saves a paused job and stops both clients and server. Phase 2
restarts the same disposable world under a new epoch, verifies the original
UUID, resumes through Alpha's menu, and finishes the native job. The runner
records artifact hashes, phase/PID ledgers, server checkpoints, client
screenshots and sidecars outside the world. Other connected leaves retain their
one-client launch path.
