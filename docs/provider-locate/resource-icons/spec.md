# Delayed resource icons

Status: in-progress

Scope: Typed fluid and gas/chemical provider icons.

Implementation: [PR #499](https://github.com/cTux/ae2-crafting-time/pull/499); [#376](https://github.com/cTux/ae2-crafting-time/issues/376) remains open.
Fixture qualification does not satisfy production-icon acceptance.

The [#482 fixture prerequisite](../../test-driver/connected-resource-fixtures/spec.md)
merged in [#484](https://github.com/cTux/ae2-crafting-time/pull/484). Its
fixture-only qualification leaves production icon acceptance NOT_RUN; this
specification still requires the production fix and reviewed icon captures.

## Expected behavior

A delayed fluid or supported gas/chemical output shows its recognizable resource
icon centered on each camera-facing red provider plate, just as an item does.
Use the actual resource appearance and tint, not an arbitrary bucket, tank, or
same-named item. Fluids without bucket items are included.

This extends the [provider locate specification](../spec.md). Red plates appear
automatically, even with chat notifications disabled and no terminal open.
Crafting-row locates still add only the independent 15-second rainbow outline.
Successful chat-link locates add the red sky beam for the same rainbow lifetime
as specified in [#488](../spec.md#planned-red-sky-beam).
The icon follows the plate through recovery, finish, cancel, provider removal,
and server-approved reconnect resync. The server remains the authority.

## Compatibility and limits

| Target | Required coverage |
| --- | --- |
| 1.20.1 Forge | Items, fluids, Applied Mekanistics gases and supported chemical key types |
| 1.20.1 Fabric | Items and fluids; no new chemical dependency |
| 1.21.1 NeoForge | Items, fluids, Applied Mekanistics unified chemicals, including gases |
| 26.1.2 NeoForge | Items and fluids; no new chemical dependency |

An absent integration, unknown key type, or unreadable legacy display key keeps
the warning plate usable without inventing an icon or crashing. Existing saves
load normally. Live typed output data replaces an incomplete legacy fallback
when available. The exact original fluid and game/mod versions remain unknown;
the reported fluid symptom is not a recorded reproduction on every target.

## Acceptance criteria

1. A real delayed fluid craft shows the matching icon and tint, including a
   fluid without a bucket item. Confirm more than one fluid to catch wrong tint.
2. A real delayed Applied Mekanistics gas/chemical craft shows the matching
   icon on both integration targets above. Include more than one chemical.
3. Item rendering stays intact. Display selection uses resource type and key
   data rather than registry lookup order; an equal item/fluid ID cannot choose
   the unrelated item's icon. Ambiguous legacy data has no guessed icon.
4. Automatic plates and reconnect resync display icons without a terminal open;
   chat disabled, manual locate, completion and cancellation retain their rules.
5. Missing integrations, invalid keys and bounded decode failures cannot crash
   the client or change another output's plate. Old saved entries remain readable.
6. Automated boundary tests and reviewed in-game captures cover these cases;
   compilation or fixture setup alone is not evidence of a visible chemical icon.

These criteria preserve the current one-plate-per-provider selection. When
several delayed outputs share a provider, its icon belongs to the selected
plate; recovery promotes the next plate with that plate's own resource key.
Position trimming and reconnect retain that key. If fresh live candidates are
ambiguous, they clear any older display fallback instead of keeping a stale icon.
The red plate and its existing lifetime remain intact.

## Out of scope

No new status, setting, message, hard Mekanism dependency, processing-machine
locate target, or profiling identity migration. Existing profile-key collisions
are not redesigned here: conflicting typed display candidates must fail closed
instead of selecting an arbitrary icon. No claim of a shipped fix or release
changelog is part of this documentation change.
