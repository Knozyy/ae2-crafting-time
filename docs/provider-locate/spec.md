# Provider Locate Spec

Status: finished

Scope: Original delayed-only provider highlights and cleanup.

Implementation: [PR #232](https://github.com/cTux/ae2-crafting-time/pull/232), [PR #245](https://github.com/cTux/ae2-crafting-time/pull/245), [PR #246](https://github.com/cTux/ae2-crafting-time/pull/246).
Verification: [prepared-client results](../automated-ui-testing/prepared-clients-2026-09-08.md).
Later icon and red-warning beam scopes are tracked separately below.

The sections before [Planned red sky beam](#planned-red-sky-beam) describe the
existing delayed-only plate behavior and manual rainbow outlines. Issue #488
extends automatic plates to every red warning and adds a red sky beam only to
a successful chat-link locate, sharing that locate's rainbow expiry.

## Goal

Extend the private delayed-output warning (issue #118) with two independent
world highlights that share positions but never share lifetimes.

Red plates also clear when releasing a machine's held final output finishes
the craft immediately. This does not require another progress update or an
open terminal screen.

- Red background + item icon (plate): appears automatically when a craft
  becomes delayed, blinks, and lasts while the craft stays delayed.
- Rainbow edges: appear only after a chat-link click or crafting-item
  double-click, blink for 15 seconds, then expire on their own.

The word "delayed" renders in red. Plates are server-authoritative and return
after leaving and re-entering the world while the craft is still delayed.
Rainbow timers are never saved and never restored.

The same private warning shape, with the same clickable provider link, also
fires for `NO SPACE` and `NO POWER` rows so the owner learns about a blocked
craft without watching the status screen. Blocked warnings never create or
clear red plates.

This covers [issue #231](https://github.com/cTux/ae2-crafting-time/issues/231).

Highlight style follows
[issue #237](https://github.com/cTux/ae2-crafting-time/issues/237)
as refined by
[issue #239](https://github.com/cTux/ae2-crafting-time/issues/239):
manual locates draw thick (2-3x) rainbow-cycling edges only, while the
stuck output's item icon stays centered on each camera-facing block face on
a red background for as long as the output is delayed.
(Supersedes the diagonal-rod style from
[issue #234](https://github.com/cTux/ae2-crafting-time/issues/234), which
read as messy rectangles in-game.)

## Player behavior

- Red plates appear and blink automatically when a craft becomes delayed. No
  click is needed and no window needs to be open. The server sends them
  separately from chat.
- `notifyOnDelayed` controls chat only. Turning it off stops the private
  delayed message but never stops red plates, plate clears, or login resync.
- Double-click applies to any resolvable active crafting item, including
  normal TTC rows. The server checks the player's open CPU scope, job
  ownership, and live provider positions. Unresolvable rows answer with the
  same expiry notice as a stale chat link and draw nothing.
- Chat links and double-clicks draw rainbow edges only. They blink for 15
  seconds and never create, extend, or clear red plates.
- Lifetimes are independent:

  | Event | Red background + item icon | Rainbow edges |
  | Craft becomes delayed | Appear and blink automatically | Unchanged |
  | Chat link or crafting-item double-click | Unchanged | Appear and blink for 15s; close originating screen |
  | TTC returns to normal | Disappear | Continue until expiry |
  | Craft finishes / cancelled | Disappear | Continue until expiry |
  | Provider breaks | Remove that provider's plate | Remove that provider's outline |
  | Leave and re-enter world | Restore for crafts still delayed | Never restore |

- The delayed warning keeps its #118 wording, timing context, owner-only
  delivery, and once-per-stall rules.
- The output name renders underlined with a hover hint that invites the click.
- A delayed row's tooltip shows a "Double-Click for highlighting Pattern
  Provider in a world" hint above the details line, so the locate action is
  discoverable without the chat message.
  (See [issue #241](https://github.com/cTux/ae2-crafting-time/issues/241).)
- Every locate answers with a private system message
  ("Highlighting <provider> at <coords> in <dimension>"), whatever triggered
  it. The message names the provider block (falling back to a generic
  "Pattern Provider" name), and every coordinate is a clickable shortcut
  that teleports the clicker to that position.
  (See [issue #241](https://github.com/cTux/ae2-crafting-time/issues/241).)
- Clicking the chat link closes the chat; a double-click locate closes the
  CPU screen, so the player immediately sees the highlight.
  (See [issue #240](https://github.com/cTux/ae2-crafting-time/issues/240).)
- Plates are server-authoritative, not UI cache. Opening another CPU, the
  planning screen, or closing all windows never hides a still-delayed plate.
  Plates vanish only on an explicit server clear (recovery, finish, cancel)
  or when that provider target breaks.
- Leaving the world clears all client highlights. Red plates return only
  through server login resync for crafts still delayed. Rainbow timers are
  never serialized and never return.
- A broken provider drops only its own positions from plates and edges, in
  that dimension only. "Broken" means the actual provider target is gone:
  air, missing block entity, replacement non-provider block, or a surviving
  host without a provider service. Unloaded chunks and unreadable grid state
  are unknown, not broken, and keep the highlight. This check is shared on
  all loaders.
- Blocked reasons (`NO POWER`, `NO SPACE`) never drive red. They send chat
  with a clickable record for a manual edge locate, but no plate and no
  fallback update.
- Chat links resolve against the active job plus still-valid targets. Active
  links survive reload through persisted records and per-output fallbacks.
  Links invalidate (expiry notice, no highlight) on finish, cancel, broken
  targets, or foreign owner. Finished links never recreate red or target a
  replacement block.
- When no provider position resolves for a delayed output, the name renders
  as plain text with no click action.
- The word "delayed" renders in red in English and Ukrainian.
- Clicking a link that belongs to another player, or whose record expired or
  was lost, highlights nothing and answers with a short
  expiry notice visible only to the clicker.
- A `NO POWER` output warns its owner once while dispatch keeps failing for
  lack of energy, with a red status word and a clickable provider name when
  the provider resolves.
- A `NO SPACE` output warns its owner once while its crafting CPU cannot
  store finished items, with a red status word and a clickable provider name
  when the provider resolves.
- A reason that clears (power restored, storage freed, output unblocked)
  re-arms that reason, so a later genuine stall warns again.
- A row that is both delayed and blocked warns once per reason, since each
  warning names a different problem.

## State rules

- `Provider link` means all of these are true:
  1. A pattern producing the output was dispatched by a crafting CPU during
     its current job.
  2. AE2's crafting service still offers that pattern through at least one
     provider at notify time.
  3. At least one offering provider resolves to a world position through its
     grid node location.
- Identity is job + network + dimension + output + provider. Identical
  outputs on different CPUs or networks track independently. Independent
  rainbow targets never replace each other. Active plates and edges are never
  silently evicted to fit a cap.
- Positions resolve at notify time, not at dispatch time, so a provider that
  moved or unloaded between dispatch and stall does not produce a stale box.
- Links are per crafting CPU and output. A new job replaces the previous
  job's links for that CPU. Finishing, cancelling, disabling the profiler,
  or clearing the scope drops its links, plates, and click records so stale
  chat links expire.
- `NO SPACE` mirrors the client row predicate on the server: the CPU reports
  it cannot store items, and the output has stored items with nothing still
  outstanding.
- `NO POWER` reuses the existing per-output dispatch-power diagnostics with
  their 20-tick freshness window.
- The persisted copy (network, output, owner, dimension, provider positions,
  display name) is a fallback, not the authority: live dispatch data wins
  whenever the job ran during this session. The stored dimension travels with
  the fallback so resync never re-derives it from the network id alone.
- Rainbow edges are never persisted. Only plates and click records persist,
  and only for active crafts.
- Persisted links never grant visibility: a locate click only serves records
  owned by the clicking player.

## Compatibility

- Support 1.20.1 Forge, 1.20.1 Fabric, 1.21.1 NeoForge, and 26.1.2 NeoForge.
- Use the same server-owned path in singleplayer and on a dedicated server.
- No new config option. Two meanings stay explicit: double-click means "any
  active crafting item", while `notifyOnDelayed` means "chat only". Locating
  and blocked warnings follow the existing `notifyOnDelayed` server setting
  for chat; plates, plate clears, and login resync ignore it.
- Update English and Ukrainian text together; the split status word keeps
  matching placeholders.
- Keep command arguments, packet fields, collection sizes, and decoded
  positions bounded and validated.
- Wire version is Forge channel protocol `14`, Fabric
  `provider_highlight_v4` (plus `provider_locate_v1`), NeoForge registrars
  `13` on 1.21.1 and 26.1.2. The highlight codec adds `networkId` and stored
  `dimension` additively with tolerant reads for older packets and saves.
- Bump every affected loader compatibility boundary in the same change.
- Old world saves without the new section load normally with empty provider
  state.

## Not included

- Locating the external processing machine behind a provider; the provider
  block itself is the highlight target.
- Choosing one provider when several ran the craft's patterns; all resolved
  positions highlight together (bounded).
- Clickable links for NeoEco and AE2 Lightning Tech CPU jobs: their dispatch
  paths do not expose patterns yet, so provider positions never resolve
  there and names render plain. `NO SPACE` detection still works on every
  CPU type; `NO POWER` warnings cover the standard and AdvancedAE dispatch
  paths that observe power.
- `NO PROVIDER` warnings: missing providers flap during normal play, so only
  `NO SPACE` and `NO POWER` warn.
- A locate action outside the delayed warning and CPU double-click (no
  hotkey, no screen button).
- Changing the 15-second rainbow duration in-game.

## Acceptance criteria

- The warned player sees an underlined output name with a hover hint and a
  red "delayed" word.
- When a craft becomes delayed, the owner sees blinking item-on-red plates
  with no click and no open screen; other players see nothing.
- Clicking the name or double-clicking any resolvable active crafting item
  draws thick (2-3x) rainbow-cycling edge boxes on the correct provider
  block(s) for 15 seconds without changing red plates.
- TTC recovery, finish, or cancel clears red but leaves rainbow until its
  own expiry. Provider break removes only that provider's plate and outline.
- Leave and re-enter restores red for still-delayed crafts and never
  restores rainbow.
- The clicker also gets a private "Highlighting <provider> at <coords> in
  <dimension>" message naming the provider block, with clickable coordinates
  that teleport to each position, and the chat (or CPU screen, for
  double-click) closes so the highlight is visible.
- A delayed row's tooltip carries the double-click locate hint above the
  details line.
- Clicking another player's, finished, cancelled, or broken link shows the
  expiry notice and draws nothing.
- Leaving and re-entering the world keeps the link working for an active
  craft; a still-delayed craft warns again when the owner is online.
- A craft with no resolvable provider position renders a plain name.
- Blocked warnings send chat with a clickable edge-only link and never touch
  red.
- A `NO POWER` warning arrives once per power-failure episode and returns
  after power is restored and fails again.
- A `NO SPACE` warning arrives once per storage-blocked episode and returns
  after space is freed and blocks again.
- World saves from before the feature load with empty provider state and no
  errors.
- English and Ukrainian messages keep matching placeholders.
- Packet, NBT, and message-component round trips have full test coverage.

## Planned shared-provider icon correction

Status: in-progress

Scope: One displayed icon per shared provider (#443).

Implementation: [PR #445](https://github.com/cTux/ae2-crafting-time/pull/445) is merged and CI passed.
Verification: complete runtime acceptance is not recorded in that PR.

[Issue #443](https://github.com/cTux/ae2-crafting-time/issues/443) reports two
delayed item icons overlapping on one provider's red plate. This correction is
planned, not implemented. It applies to all four supported targets.

Render one red plate and at most one output icon on each camera-facing provider
face. The first retained delayed output at that dimension and block position
wins; every visible face uses that output. Updating that identity does not move
it behind another output. The choice stays stable while the retained candidates
and their positions stay unchanged; no timer or frame counter rotates icons.

Keep every delayed identity in client state. If the winner recovers, finishes,
is cancelled, or loses that position, the next retained candidate becomes
visible. Clearing another candidate does not hide or replace the winner.
Disconnect clears the state as before; login resync may establish a new order.
The chosen output still uses the current centered transform and item lookup.
An unknown or non-item winner keeps the plate-only fallback; this does not add
fluid or chemical icons or search later candidates for a different icon.

The change is display selection only. Server timing, ownership, packet and save
formats, rainbow edges, the one-second pulse, face culling, scale, lighting and
depth offsets retain their current behavior. No setting or dependency is added.

### Acceptance for #443

| ID | Observable result |
| --- | --- |
| P443-1 | Two or more delayed outputs sharing a provider draw one red plate and at most one centered icon per visible face. Duplicate positions do not add another draw. |
| P443-2 | Repeated frames and updates to an existing identity keep the first retained winner. A later candidate cannot replace it while it still covers that position. |
| P443-3 | Clearing the winner reveals the next candidate without losing its delayed state. Clearing an unselected candidate keeps the winner. An empty candidate set draws nothing. |
| P443-4 | Partially overlapping position lists preserve all unique providers. Equal coordinates in different dimensions remain independent. Different network identities sharing one physical position still produce only one display there. |
| P443-5 | Provider removal and session cleanup retain their existing rules. Rainbow edges and their 15-second expiry, delayed timing and plate pulse remain unchanged. Unknown/non-item winners remain plate-only. |
| P443-6 | All four loader adapters use the same selection rule, including both plate rendering and item submission on 26.1.2. Covered selection and lifecycle checks plus reviewed in-game evidence prove the change; compilation alone does not. |

## Planned fluid and gas icon correction

[Issue #376](https://github.com/cTux/ae2-crafting-time/issues/376) tracks the
item-only world icon limitation. Its [resource icon spec](resource-icons/spec.md),
[research and design](resource-icons/technical-design.md), and
[implementation plan](resource-icons/implementation-plan.md) extend this feature
to fluid and supported gas/chemical icons. These documents describe a planned
fix; the existing red plate may currently appear without a non-item icon.

## Planned red sky beam

Status: ready-to-implement

Scope: automatic plates for all eight red warnings and temporary chat-locate beams ([#488](https://github.com/cTux/ae2-crafting-time/issues/488)).

Planning: [PR #489](https://github.com/cTux/ae2-crafting-time/pull/489), [PR #491](https://github.com/cTux/ae2-crafting-time/pull/491). No implementation is recorded for this extension.

[Issue #488](https://github.com/cTux/ae2-crafting-time/issues/488) extends automatic
red backgrounds with output icons to every red warning status. Clicking a valid
provider link in chat adds a red sky beam alongside the rainbow borders for the
same 15 seconds. This is planned, not shipped.
See the [design](technical-design.md#red-sky-beam-design) and
[implementation plan](implementation-plan.md#red-sky-beam-implementation).

The beam rises from the center of the provider's top face into the sky, passing
through every block above it. Solid blocks, glass, fluids and dimension roofs
neither stop it nor change its red color. It remains visible through intervening
world geometry within the normal client view distance, including underground
and in roofed dimensions. No sky access or beacon structure is required.

The beam starts only when a successful chat-link locate highlights a valid
provider. It uses that rainbow highlight's positions, expiry and one-second
pulse. There is no separate beam timer. A red plate or resolved resource icon
is not required. Automatic warnings and login resync never create a beam.
Crafting-row double-clicks keep their rainbow-only behavior. No new setting is
added, and existing chat notification rules remain unchanged.

Each locate identity keeps its own 15-second expiry. Repeating a chat locate
refreshes its rainbow and beam together. A later row double-click for the same
identity replaces that locate with rainbow-only state; it cannot prolong a
beam. Different outputs and providers remain independent. At a shared provider,
draw one beam while any live chat highlight still covers it.

Warning recovery, job completion or cancellation clears the appropriate plate
without shortening a previously started rainbow/beam pair. The pair disappears
at its common expiry, when its provider is removed, or on session exit.
Reconnect never restores rainbow borders or beams. Invalid, expired and foreign
chat links show no new world effect.

Automatic red backgrounds with output icons apply to `DELAYED`,
`NO SPACE`, `NO PROVIDER`, `NO POWER`, `NO CHANNEL`, `NO TARGET`, `INPUT BLOCKED`
and `LOCKED`. Ordinary `Waiting`, `No data yet`, TTC values colored red by
relative ranking and pre-craft `Recurrent` text are not warning statuses.
Existing status detection, priority, freshness and chat rules do not change.

Treat eligibility as the union of current warning reasons, not just DELAYED.
Changing from one red reason to another never clears or restarts its plate.
Resolving one reason or one job keeps the plate while another eligible reason/job
owned by the same recipient still needs that provider. Remove the plate only when
the last qualifying warning clears, the target breaks, or the session ends.

All statuses need a valid associated provider position. `NO PROVIDER` still
qualifies, but an absent provider cannot be marked: use only an existing,
validated associated target, never invented coordinates or a replacement block.
No resolvable target means no world marker; the red status remains visible.
Reconnect resynchronizes current warnings without an open screen. After server
restart, transient dispatch reasons require fresh evidence; never persist or
restore them as DELAYED or another remembered reason.

### Beam acceptance criteria

| ID | Observable result |
| --- | --- |
| B1 | Each of the eight red warning statuses creates a red plate with output icon for valid targets without a click or open screen, even with `notifyOnDelayed` disabled. Automatic warnings and login resync create no beam. A successful chat-link locate creates rainbow borders and a red beam together for 15 seconds; row double-clicks create only rainbow borders. Non-warning text creates no automatic plate. |
| B2 | After a successful chat locate, an underground provider under opaque blocks, glass, fluids or a dimension roof emits an uninterrupted red beam above the roof for the rainbow lifetime. Looking through covering blocks does not hide it. |
| B3 | Final warning recovery, finish (including immediate final-output completion) and cancellation clear the plate without truncating an existing chat rainbow/beam pair. Both transient effects expire together. Provider removal trims all effects; session exit clears them. Login restores only valid current plates, never rainbow or beam state. Transient warning reasons require fresh evidence after restart. |
| B4 | Shared-provider warning outputs keep one selected plate while any eligible owner-bound contribution remains; red-to-red transitions do not flicker or reset it. Independently, overlapping chat locates draw one beam per position until the last covering chat highlight expires. Other owners, providers and dimensions retain independent state. |
| B5 | A successful chat locate creates a beam even without a red plate or resolved icon. A repeated chat locate refreshes the pair's common expiry. A row double-click for the same identity replaces it with rainbow-only state and cannot extend the beam. Invalid, expired or foreign links create neither transient effect. |
| B6 | Only the authorized recipient sees effects, only in the matching dimension. Missing icons still permit plates and chat-locate beams. NO PROVIDER with no valid target draws no world marker; no stale or replacement target is marked. Existing appearance and unrelated rendering remain intact. |
| B7 | All four supported targets pass lifecycle checks and reviewed runtime captures for roof penetration, pulse and cleanup. |

This adds no world blocks, light sources, crafting behavior, cross-dimension
visibility or persisted transient statuses. A versioned packet field distinguishes
chat locates from row locates. Resource-icon support in #376 is independent and
does not block the beam.
