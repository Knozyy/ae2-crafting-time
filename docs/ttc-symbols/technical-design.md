# TTC symbols technical design

Lifecycle and acceptance: [spec](spec.md). Work: [#450](https://github.com/cTux/ae2-crafting-time/issues/450).

## Current paths

Investigated base: `17c7b5e540bd8e8b3022c1528d4f8960c72c5895`.
The merged proposal has no implementation. Text is assembled in several places;
changing translation strings alone cannot preserve separate icon colors or a local toggle.

| Surface | Current owners and callers |
| --- | --- |
| Plan/status rows and tooltips | `mcCommon/TtcText`, `RowTextColor`, `CraftConfirmTableRendererMixin`, `CraftingStatusTableRendererMixin` |
| Totals and CPU cards | `CraftConfirmScreenMixin`, `CraftingCPUScreenMixin`, `CPUSelectionListMixin` in `mc1201` and `mc2612`; all call `TtcText` |
| Optional displays | `WirelessTerminalScreenMixin` in both version sets; `MERequesterScreenMixin`, both Crafting Tree widget mixins, and `CraftingTreeTtc.drawBadge` in `mc1201` |
| Samples and diagnostics | `TtcText.statsLines`, `windows`, `stallLines`, normalized details and accuracy helpers |
| Server chat | `StatsChatServer` builds summary, sample timings, accuracy, missing-history and confirmed-reset messages; `StatsChatMessages` only sends requests |
| Warnings and links | Both `DelayedChatText` variants, called by `DelayedNotificationServer`, `BlockReasonNotifier`, `ProviderLocateServer` and `ProviderLocateCommand` |

Java names above live below `shared/src/<source-set>/java/com/ctux/ae2craftingtime/`.
`CraftingTreeTtc.drawBadge` currently renders a raw string and must use a styled
component too. Accuracy includes signed error seconds and three latest-job times;
those are durations, whereas coverage, ratios, counts and throughput are not.
The unused string-returning `TtcText` chat helpers have no production callers;
do not introduce another live formatting route through them.

## Small shared presentation policy

Add `SHOW_EMOJI` (`showEmoji`, CLIENT, APPEARANCE) to `OptionFeature`.
`FeatureOptions` already defaults new client options to On. `ClientConfigFile`
and `OptionsScreen` enumerate features, so reuse their persistence, Done/Cancel,
reset and pagination behavior. No loader config or server preference is needed.

Keep symbol selection and translation-key/argument-position rules in pure Java
under `shared/src/main/java`, with complete line/branch coverage. Use a small
component adapter in `mcCommon` for styled symbol-plus-value composition and
known TTC translation traversal. Do not parse rendered English/Ukrainian prose.
Preserve translation keys, fallback, argument order/count, siblings and styles.
Each raw duration is decorated once, including each duration in compound lines;
never decorate outer TTC wrappers again when their inner value is decorated.

Use separate symbol components with explicit color and no click/hover event.
Keep original value and label styles, including inherited colors, bold and
interactive item/provider/coordinate components. Copy rather than mutate incoming
chat components. Unknown translations and ordinary chat text are unchanged.
Handle nested translation arguments and siblings, including vanilla chat wrappers.
For blocked chat inspect the existing reason-word key: `chat.blocked` alone does
not distinguish power from storage. Preserve the current low-confidence condition.

`RowColorPolicy.isNumericEstimate` currently recognizes a String argument under
`text.ae2craftingtime.ttc`. Decorating that argument with a Component requires
preserving equivalent numeric-versus-collecting classification in the adapter
and covered policy tests. Preserve TTC colors, native ordinary text colors,
badge backgrounds, opacity and independent text shadow. Measure decorated
components at each existing layout boundary; never remove the time prefix to fit.

## Client chat boundary

Do not read `ClientOptionsRuntime` from server message builders or add a packet.
Decorate the local copy passed to `ComponentRenderUtils.wrapComponents` during
chat line layout. Raw server messages, stored chat components and logs stay plain.
This supports different settings for recipients of the same broadcast.

Cached Minecraft sources/bytecode confirm these concrete boundaries:

- 1.20.1 Forge/Fabric: `ChatComponent` private five-argument `addMessage` calls
  `ComponentRenderUtils.wrapComponents(FormattedText, int, Font)`.
- 1.21.1: `ChatComponent.addMessageToDisplayQueue(GuiMessage)` calls the same helper.
- 26.1.2: `net.minecraft.client.multiplayer.chat.GuiMessage.splitLines(Font, int)`
  calls that helper; it moved out of `ChatComponent`.

Modify only the first wrapping argument and delegate to the common decorator.
Use the repository's existing remapped 1.20.1 / `remap = false` NeoForge mixin
pattern, as demonstrated by `ChatScreenMixinSrg` and its counterparts. Register
client-only mixins in each applicable manifest and exclude remapped twins from
1.21.1 as its build already does. Restrict decoration to known TTC components.
Rewrap existing chat after a successful option save using the native chat refresh
path so the visible history follows the current setting without editing history.
No new notification, permission, delivery, rate-limit or persistence behavior.

## Compatibility and verification limits

All four release targets share the policy and component adapter. The 26.1.2
chat adapter differs as described above; old optional Crafting Tree/ME Requester
surfaces remain limited to their existing supported targets. No dependency,
protocol, saved NBT, identifier, command, log or export format changes.

Use existing shared, Minecraft component, resource and all-target build checks.
Do not launch Minecraft or add a smoke runner: the user explicitly excluded smoke.
Font glyph presence, GUI scale/resource-pack appearance and visual overlap remain
unverified. Single Unicode code points use existing fonts; no bitmap font or
variation selector is added. The Off option is the supported text-only fallback.
