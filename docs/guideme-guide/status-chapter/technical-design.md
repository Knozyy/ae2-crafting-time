# Status Chapter Technical Design

Lifecycle: see scope status and evidence for [original chapter](spec.md) and [Recurrent book/wiki delivery](spec.md#recurrent-addition-412).

The Recurrent image instructions below describe the earlier bold-red version.
After the normal-weight red status rule in #471, that image was removed from the
book and Wiki. A fresh Recurrent capture awaits the separate fixture fix in #602.

## Decision

Extend the shared guide to one parent and twelve child Markdown pages. Derive
inventory/order from current renderers, translations, and status types; derive
meaning from matching status designs. Reuse seven retained screenshots and add
three provider-dispatch captures for the original chapter. Those pages and the
later NO CHANNEL page are shipped. The Recurrent extension was delivered in
PR #479; its remaining book/wiki verification is tracked in the linked scope.
The design below retains the original requirement for one reviewed native-plan capture.

This implements the [specification](spec.md) for
[#305](https://github.com/cTux/ae2-crafting-time/issues/305).

## Evidence and ownership

`CraftingStatusTableRendererMixin`, `CraftingRowState`, `CraftProfiler`, and
`ProviderDispatchTracker` define visible precedence. `TtcText` and both locale
files define labels/tooltips. Commit `8169fad2` / PR #293 shipped NO TARGET,
INPUT BLOCKED, and LOCKED after the older provider-dispatch planning snapshot;
current code/history supersede that stale status line. Existing status specs
define conditions and clearing. The screenshot gallery retains seven states.

```text
shared/src/main/resources/assets/ae2craftingtime/guides/ae2craftingtime/guide/
  index.md
  getting-started.md
  statuses/
    index.md
    no-space.md
    no-provider.md
    no-power.md
    no-channel.md
    locked.md
    input-blocked.md
    no-target.md
    waiting.md
    delayed.md
    no-data-yet.md
    estimated.md
    recurrent.md
    images/*.png
  _uk_ua/statuses/*.md
```

`statuses/index.md` uses parent `index.md`, position `2`, a renderer-safe icon,
and localized titles **Chapter 3: Statuses** / **Розділ 3: Стани**. Children use
positions `0..11` in spec order. Features owns position `1`; Chapter 1 remains `0`.

## Sources and screenshots

This table records the original chapter's gallery sources. Its captures and
the later NO CHANNEL image are already packaged as PNGs; #412 preserves them.

| Page | Source | Image |
| --- | --- | --- |
| NO SPACE | no-space docs | `crafting-status-no-space.jpg` |
| NO PROVIDER | no-provider docs | `crafting-status-no-provider.jpg` |
| NO POWER | no-power docs | `crafting-status-no-power.jpg` |
| LOCKED | provider-dispatch docs/current code | new focused capture |
| INPUT BLOCKED | provider-dispatch docs/current code | new focused capture |
| NO TARGET | provider-dispatch docs/current code | new focused capture |
| Waiting | waiting-to-start docs | `crafting-status-waiting.jpg` |
| DELAYED | profiling docs | delayed and diagnostics gallery images |
| No data yet | TTC/profiling docs | `crafting-plan-no-data.jpg` |
| Estimated | TTC docs | `crafting-status-running.jpg` |

Copy retained images without recompression. New captures follow the gallery
design, are visually reviewed, enter `docs/images/README.md`, and identify
fixture data in captions.

## Links, validation, and packaging

Every child links to landing, adjacent status, and the relevant `../features/`
page. Blocking pages cross-link the closest alternative. Locale targets match.

Extend `checkGuideResources` to verify exact manifest/positions, locale peers,
declared labels against translations, parents/icons, local images, internal
targets, reachability, and return links. Each child requires one image and three
internal links. Runtime UI tests remain authority for rendered status.

Use existing shared copy/Fabric transformation and extend four-JAR assertions.
The original chapter's manual QA triggers each status through prepared scenarios and compares page to
label, tooltip, precedence, mixed-batch behavior, and clearing. Add the three
missing captures to the existing provider-dispatch scenario. Missing parity,
broken links, unsupported Markdown, unreadable captures, or target mismatch
blocks completion.

For #412, follow the focused renderer checks below instead of repeating that
completed chapter campaign.

## Alternatives not chosen

One matrix is too shallow for evidence and recovery. Generated pages make prose
and translation review worse. Remote/repository-only images fail offline.

## Recurrent extension (#412)

Eleven pages, including NO CHANNEL, already exist. Implement only this extension for
[#412](https://github.com/cTux/ae2-crafting-time/issues/412), keeping their positions
0..10. Add `statuses/recurrent.md` and `_uk_ua/statuses/recurrent.md` at position 11,
with parent `statuses/index.md` and localized navigation titles.

Update both landing pages: retain the first eleven links, then put Recurrent in a
separate Crafting Plan paragraph. Explain that navigation order is not a shared
priority between plan and running-job states. Add a Next link from each
`estimated.md`. The new page links back to `estimated.md`, `index.md`, and
`../features/time-estimates.md`, preserving identical locale link targets.

Use the current `TtcText` keys `text.ae2craftingtime.plan.recurrent` and
`text.ae2craftingtime.plan.recurrent_hint`, plus the
[recurrence design](../../recurrent-crafting-status/technical-design.md), as the
text/behavior evidence. Show the formatted quantity example; do not copy `%s`
into player prose. Do not change Java, packets, persistence, or detection.

The implementation base inspected for #412 is
`8561ae116b9439f122a77b89b79216c1e12d65f4`. Its status inventory includes
`no-channel.md`, and `estimated.md` has position 10. The recurrence documents'
#408 investigation describes an older summary callback. Merged #410 fixes that
regression; current `CraftConfirmMenuMixin` and its Forge SRG counterpart enrich
the returned summary at the native menu's factory call. Use current source for
this boundary, not the historical independent RETURN-callback description.

Append `recurrent.md: text.ae2craftingtime.plan.recurrent` to `statusPages` in
root `build.gradle`'s `checkGuideResources`. Reuse its existing count/order,
previous/next, translation, image, and packaging checks. Keep the existing map
as the sole inventory. The current label check compares the complete translation
literally and does not handle `%s`. Format the translated label with the same
fixed example amount used in both pages, such as `1`, before comparison. Keep
exact checks for ordinary labels. Add a focused negative check that rejects a
stale formatted Recurrent label; do not require `%s` in player prose or weaken
label validation to a substring match.

Capture the real native-plan `recurrent-plan` fixture through the prepared
client workflow, or reuse an existing reviewed capture only after inspecting it.
Package one crop at `statuses/images/crafting-plan-recurrent.png` and an identical
copy under `_uk_ua/statuses/images/`, with the same relative image reference in
both locales. The existing validator checks both files and their byte equality.
Record its source in the existing gallery
records under `docs/images/`; preserve native pixels and exclude private data.
Missing usable evidence blocks implementation completion, not this planning PR.

Reuse the shared resource pipeline and Fabric 1.20.1 transformation. Verify
Forge/Fabric 1.20.1 and both NeoForge targets. No dependencies, recipe changes,
new guide system, or server migration are needed. S11 maps to localized prose,
S12 to navigation/manifest changes, and S13 to the inspected capture. Existing
S3 and S5-S10 cover links, translations, packaging, and guide rendering.

## Verification prerequisites for #412

Use the existing prepared-client workflow, not a new guide runner or a Prism
instance. Check both renderer paths with 1.20.1 Fabric and 1.21.1 NeoForge;
build and inspect packaged resources for all four supported targets. The exact
profiles and launch gates are listed in the implementation plan.

The September 20 investigation verified host Java 17, 21 and 25, the VMX,
localhost VNC configuration, SSH key presence and tracked source-only fixtures.
CodexVM was off, so guest Java, SSH access, installed loader libraries and
`launch.json` manifests remain unverified. The session worktree also needs its
own exact share. Host prerequisites and old runtime receipts do not certify
guest readiness.

The existing launch preparation consumes an installed native loader and rejects
a mismatch; it does not install missing loaders. Before implementation, complete
the bounded VM preflight in the plan. If it finds a missing installation,
profile or fixture, document and deliver that prerequisite first, or obtain
explicit authorization to include it. Do not silently add provisioning tooling.
Keep independently useful or substantial infrastructure separate from #412.

## GitHub wiki update (S14)

The wiki published under #411 uses `Status-Estimated.md` and
`Ukrainian-Status-Estimated.md`, locale-specific landing pages `Statuses.md` and
`Ukrainian-Statuses.md`, and shared images under `images/statuses/`. Preserve
those conventions in the separate `ae2-crafting-time.wiki.git` repository.

After the canonical book change is merged, add `Status-Recurrent.md` and
`Ukrainian-Status-Recurrent.md` from the corresponding guide pages. Strip GuideME
navigation frontmatter and translate page links to wiki names without `.md`.
The new pages link to the matching locale's status landing, estimate page, and
`Feature-Time-Estimates` / `Ukrainian-Feature-Time-Estimates` page. Update both
landing pages and both estimate pages to expose Recurrent, keeping the separate
Crafting Plan explanation. The existing sidebar already links both landings.

Copy the reviewed PNG unchanged to `images/statuses/crafting-plan-recurrent.png`
and use it from both wiki pages. Do not introduce separately authored behavior
or a new synchronization framework. Start from the latest wiki checkout and
preserve unrelated wiki edits. Commit and publish the bounded page/image changes;
record the canonical source commit, wiki commit, and live page URLs as evidence.
If publication or rendered links/images fail, leave #412 incomplete until fixed.
