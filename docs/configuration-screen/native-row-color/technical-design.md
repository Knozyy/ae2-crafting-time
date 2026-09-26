# Native row color technical design

Lifecycle and acceptance: [specification](spec.md).

## Current path and cause

Investigation baseline: `39aaaaf3dda14f7cbe788ff381db0e156d7972a7`.
Paths below are relative to `shared/src/`.

| Source | Role |
| --- | --- |
| `mcCommon/java/com/ctux/ae2craftingtime/mc1201/ClientOptionsRuntime.java` | `ttcColor` selects Total when TTC colors are disabled. `enabled` also applies server profiling gates. |
| `mc1201/java/com/ctux/ae2craftingtime/mc1201/mixin/AbstractTableRendererMixin.java` and its `mc2612` counterpart | Build the row color context and intercept native text draws, including the scaled branch. |
| `mcCommon/java/com/ctux/ae2craftingtime/mc1201/mixin/CraftConfirmTableRendererMixin.java` | Adds Plan TTC and copies its foreground, or Total without a status, to compact amounts. |
| `mcCommon/java/com/ctux/ae2craftingtime/mc1201/mixin/CraftingStatusTableRendererMixin.java` | Adds Status TTC/warnings and copies their foreground, or Total, to compact amounts. |
| `mcCommon/java/com/ctux/ae2craftingtime/mc1201/TtcText.java` | Creates ordinary and special components; also serves surfaces outside these rows. |

AE2's `AbstractTableRenderer.render` reads
`screen.getStyle().getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB()` and
passes it to each description draw. Verified against cached AE2 15.4.10 source
and 26.1.10-beta bytecode. The mc1201 adapter receives that value in
`GuiGraphics.drawString`; mc2612 receives it in `GuiGraphicsExtractor.text`.
Both normal and width-scaled branches already forward it. Explicit component
foregrounds take precedence, so passing the native draw color alone cannot fix
the current Total-colored components.

## Resolved change

Put the off/off decision and semantic ordinary/special classification in
Minecraft-free shared code, with complete branch coverage. Use the two client
feature values directly, not the server-gated `ClientOptionsRuntime.enabled`:
compact amounts intentionally remain available without profiling.

At the Plan/Status component boundary, leave ordinary numeric TTC and ordinary
or status-free compact amounts without an explicit foreground in off/off.
That lets the existing native draw argument supply the actual screen palette.
Apply this before copying a status color to compact amounts. Keep special
foregrounds and all non-color styles. Do not mutate a component shared with
another surface. Reuse one row-specific component adapter for the two callers
if needed; keep Minecraft API conversion thin.

Do not globally change `ttcColor` or the general `TtcText` factories. All
`ttcColor` callers are the two table adapters, `mc1201/.../CraftingTreeTtc.java`,
and `mc1201/.../mixin/MERequesterScreenMixin.java`; the latter two are outside
scope. The two row mixins are the only consumers of `TtcColorContext.get`.

## Special status precedence

Status rows currently choose no-space first, then require a remaining amount,
then choose block reason, waiting, delayed, and finally estimate/collecting.
Keep that ordering and each feature gate. No-space and block reasons use
Delayed color; block reasons and delayed TTC are bold. Waiting uses Waiting
color. Collecting uses Collecting color, although its outer translation key is
the same `text.ae2craftingtime.ttc` as a numeric estimate: inspect its semantic
content, not only that outer key. Plan recurrent remains red/bold and stored
variant remains gold. Compact amounts copy special foreground, not boldness.

Removing every `status.amounts` color at draw time would hide warning colors.
Comparing RGB with Total is also wrong when custom colors coincide. Preserve
special meaning during component creation, before a compact line loses the
identity of the status it copied.

## Compatibility and failure boundaries

The existing background switch already gates both versions of `TtcBadge`.
No config, packet, cache identity, or world-save changes are required.
The `mc1201` adapter serves both 1.20.1 loaders and NeoForge 1.21.1; `mc2612`
serves NeoForge 26.1.2. Keep both drawing branches intact and verify compilation
of both API families. Native and foreign components must bypass the policy.
Unknown or absent statuses must not be mistaken for a known warning by color.

The [plan](implementation-plan.md) maps regression checks to these boundaries.
Runtime theme/scale readability remains a visual check, explicitly omitted
for the current user run rather than inferred from component tests.
