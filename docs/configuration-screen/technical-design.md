# In-game Configuration Screen Technical Design

Lifecycle: see the [scope status and evidence](spec.md).

This design implements the [specification](spec.md) for
[#117](https://github.com/cTux/ae2-crafting-time/issues/117).

## Decisions

Use Minecraft's existing `Screen`, list, button, slider, and text-field widgets.
Do not add Cloth Config or another runtime dependency. Keep two explicit config
models:

- `ClientConfig`: local display, appearance, and default-sort values;
- `ServerConfig`: profiling, retention, filtering, notification/chat, and delay
  thresholds.

The models expose typed values, defaults, validation, reset, load, and save.
Shared UI code reads those models rather than loader config objects. The current
static `Ae2CraftingTimeConfig` accessors become the server-model facade so
profiling call sites do not gain loader branches.

The [feature-switch inventory](spec.md#feature-switches) is the setting
contract. Use one boolean per named behavior with the defaults in that inventory. Do not
derive independent switches from category flags. `enabled` is the only master:
when off, dependent server rows are unavailable without changing their stored
values. Storage sections follow the Client/Server tabs and their logical groups.

## Storage and migration

| Owner | Forge/NeoForge | Fabric |
| --- | --- | --- |
| Client | `config/ae2craftingtime-client.toml` through the shared typed parser/writer | same shared backend |
| Server | `<world>/serverconfig/ae2craftingtime-server.toml` through the shared typed parser/writer | same shared backend |

When the new owner file is absent, read known keys from the existing
`ae2craftingtime-common.toml` and validate them. Existing owner files take
precedence; missing keys in them use defaults. Do not delete or rewrite the legacy file. `showInTree` maps to the
client tree toggle; the other current keys map to `ServerConfig`.
`notifyOnDelayed` keeps its server-wide meaning, as does `showChatMessages`.
Missing keys use their model defaults, including recurrent detection off.
Existing values are never replaced by new switches.

### Preserve explicit values when copying (#592)

The [focused scope and status](spec.md#recurrent-detection-default-592) covers the
default-off follow-up. `FeatureOptions.reset()` owns the defaults;
`ServerConfigFile.load()` applies explicit values afterward. Before PR #597,
`ServerConfig.copy()` created a default model and copied only disabled switches,
so an explicit recurrence opt-in became disabled again. [PR #597](https://github.com/cTux/ae2-crafting-time/pull/597)
fixed the copy to preserve every switch and numeric value. All four targets
share this model and its callers: `OptionsSession.reload()` copies the received
snapshot, `OptionsSession.save()` copies the submitted draft, and
`ServerOptionsRuntime.accept()` copies the accepted effective values.

Copy every server-owned switch's effective value, following the existing
`ClientConfig.copy()` loop, and preserve the numeric fields. `FeatureOptions.copy()`
already clears defaults before copying; `ServerOptionsWire.decode()` already sets
each switch explicitly. Neither needs a change. Keep the fix in the shared model;
leave packets, persistence format, permissions, loader adapters and calculations
unchanged. Reset intentionally restores defaults; copying must never do so.

Both writers use a temporary sibling followed by atomic replacement. Server
writes remain on the logical server. Parsing and writing live in Minecraft-free
shared code on every loader. The focused [startup-generation scope](server-config-startup/spec.md)
adds creation of a missing world file without an in-game edit.

## Screen structure

`ConfigScreen` owns a working copy of both models. Section descriptors are fixed
data: translation key, control kind, default, range/choices, owner, apply timing,
and availability predicate. The screen creates native widgets directly from
those descriptors. There is no plugin/factory layer.

Render Client and Server tabs first, then the groups in the spec. The Server
tab always shows effective values; only authorized players can edit. Disabled
optional rows state the missing mod or version. The static
[mockup](options-mockup.svg) is a layout guide; native widgets determine size,
focus, narration, and scrolling.

- Boolean rows use cycle buttons.
- Bounded integral values and opacity use sliders plus their exact numeric value.
- Doubles use text fields so values such as `4.0` are not rounded by a slider.
- RGB values use a `#RRGGBB` text field and a live color swatch.
- Sort modes use the existing numeric meanings: `0` AE2 order, `1` shortest,
  `2` longest.

Reset changes the working copy only. Done validates all visible fields, saves
the values the current player may own, and returns to the parent screen. Cancel
returns without saving. Disabled rows retain their stored values.

Minecraft 1.20.1 and 1.21.1 share the existing `mc1201` client source seam;
26.1.2 uses `mc2612` only where screen/widget signatures differ. The screen's
state and setting descriptors stay in Minecraft-free shared code.

## Loader entry points

- Forge 1.20.1 registers the loader's config-screen extension from client setup.
- Both NeoForge targets register the matching `ConfigScreenFactory` extension on
  the client distribution.
- Fabric adds a `modmenu` entrypoint class that implements Mod Menu's config
  screen factory. Metadata uses `suggests`, and compile-time use is isolated so
  the class is never loaded without Mod Menu.

Every entry point passes the loader's parent screen to `ConfigScreen`. No client
class is referenced from the common mod initializer or dedicated-server path.

## Applying settings

UI mixins replace hard-coded visibility, sort defaults, `TtcColor` constants,
status/total colors, and `TtcBadge.BACKGROUND` reads with `ClientConfig` getters.
TTC calculation and server snapshots remain unchanged.

Each renderer checks its switch before reserving space, drawing a badge,
building a tooltip, or registering a click target. Status switches filter only
client presentation. Server diagnostic switches gate their collection or
classification path and clear only related runtime state, retaining throughput
samples and world saves. Disabled sort controls fall back to AE2 order for
rendering and hit testing.

Server-owned settings are read only on the logical server. Delay thresholds
replace the current 10-second and 2x constants at the single classification
seam. Retention/filter changes apply to new calculations without discarding
valid retained samples; shrinking `maxSamples` trims each queue once.

Add a bounded server-to-client config snapshot to the existing stats protocol.
It contains the effective values and an `editable` flag. Refresh it on login and
after a server config reload. A missing or invalid snapshot leaves the last valid
values/defaults and logs the rejection.

Add one matching client-to-server update packet containing the complete typed
server model plus the snapshot revision it edits. Accept it only from the
integrated-server owner or a player with permission level 4, on the server game
thread, with the expected protocol/context and current revision. Revalidate every
field, write through the server config backend, apply once, increment the
revision, and broadcast the new snapshot. Reject unauthorized, stale, malformed,
or oversized updates without partial application. This packet edits only the
fixed server-setting set; it is not a general remote file/config API.

Persist `receiveCraftWarnings` in client config and send that one boolean on
login and after Done. Default to true until received so older clients retain
current behavior. Hold it only in the player's server session and clear it on
disconnect. Both `DelayedNotificationServer` and `BlockReasonNotifier` check
the recipient preference at the final chat send point as well as the global
`notifyOnDelayed` switch. The preference packet cannot write server settings
or another player's preference. An absent client mod keeps the server default.

## Validation and failure behavior

- Validate at parse and before save. Reject NaN/infinity, invalid RGB, and ranges
  outside the spec.
- If a save fails, keep the working copy on screen, show one actionable error,
  and leave the last file intact.
- If Mod Menu or an optional UI integration is absent, disable only its row or
  entry point. Startup remains clean.
- Unknown sort values fall back to longest first. Invalid colors fall back per
  field, not for the whole appearance section.
- Translation checks require identical English/Ukrainian key and placeholder
  sets.

## Verification map

| Acceptance area | Proof |
| --- | --- |
| Ownership and packet boundary | unit tests for models/codecs plus authorized integrated/operator edits and forged, oversized, stale-revision/context, and unprivileged-write checks |
| Migration and persistence | parser tests for old/new/malformed files and reload tests on each loader |
| UI behavior | prepared-client scenarios for entry, save/cancel/reset, validation, colors, toggles, sorting, and restart messaging |
| Optional integrations | Fabric with/without Mod Menu and each optional display mod present/absent |
| Server safety | dedicated startup on all targets and two-client authority check |
| Packaging | release-matrix builds and JAR audit for client-only classes/dependencies |
