# Stored variant background design

Lifecycle and requirements: [specification](spec.md).

## Existing path and cause

`CraftConfirmTableRendererMixin` in `shared/src/mcCommon` appends
`TtcText.storedVariant()` when the live diagnosis applies. That component uses
`text.ae2craftingtime.plan.stored_variant`. The row copy now takes the neutral
foreground; the factory's original style stays available outside the row.

The `AbstractTableRendererMixin` counterparts in `shared/src/mc1201` and
`shared/src/mc2612` both call `CraftingRowState.isBadge`. Its shared `BADGE_KEYS`
set omits this key, so neither renderer calls `TtcBadge.fillRoundedRect` for it.
Both renderers already use `isWidthLimited` and `badgeTextScale` to constrain
long labels to `BADGE_TEXT_WIDTH` (90 font pixels, 45 screen pixels at AE2's
half scale), keeping the existing right edge.

## Smallest shared correction

Add the Stored variant key to badge membership and to `isWidthLimited` in
`shared/src/main/java/com/ctux/ae2craftingtime/core/CraftingRowState.java`.
Reuse the renderer geometry, padding, rounded corners, and width limiting.
No per-loader drawing path or new renderer helper is needed.

`TtcBadge.fillRoundedRect` already honors `badgeBackground()` in both renderer
families. `ClientOptionsRuntime` supplies the configured background color and
opacity through `TtcBadge.BACKGROUND`. Keep those paths as the single source of
appearance behavior, including transparent opacity and background Off. Width
limiting still applies when the fill is disabled.

Keep `TtcText.storedVariant()` and its tooltip components intact. Do not give
the explanation or suggestion translation keys a row badge. Diagnosis removal
already removes the component; there is no separate badge state to clear.

## Verification ownership

Extend `CraftingRowStateTest` for membership and width limiting, retaining its
negative tooltip/non-status cases and width boundary checks. Extend the existing
`TtcTextTest` component checks where needed to connect both localized strings
to the recognized key and preserve normal-weight styling. The row foreground
follows the neutral badge/native rule; tooltip colors remain unchanged.

Use the existing `stored-variant-plan` scenario for live label creation/removal
and row placement. Reuse the appearance controls and evidence patterns from
`badge-background` for On/Off/custom appearance checks; do not create another
launcher or storage fixture. Follow the [implementation plan](implementation-plan.md)
for required evidence and the [smoke policy](../../../.codex/skills/run-ae2-client-smoke/SKILL.md)
for English-only runtime checks.
