# Minecraft 1.20.1 Guide Book Specification

Status: in-progress

Scope: GuideME support and full port acceptance on both 1.20.1 loaders.

Implementation: [PR #333](https://github.com/cTux/ae2-crafting-time/pull/333).
The Fabric item identity below was revised by [#537](https://github.com/cTux/ae2-crafting-time/issues/537)
so a Crafting Time server accepts clients with only AE2 installed.
Verification: that PR records blocked Fabric runtime acceptance.
Later [NO CHANNEL delivery](https://github.com/cTux/ae2-crafting-time/pull/423) includes Forge/Fabric book QA,
but does not explicitly close every port acceptance criterion.

Issue: [#265](https://github.com/cTux/ae2-crafting-time/issues/265)

This extends the implemented [modern guide specification](../spec.md) to both
Minecraft 1.20.1 loaders without changing its content or modern targets.

## Player behavior

- Forge and Fabric players can craft **AE2 Crafting Time Guide** from one book,
  one uncharged Certus Quartz Crystal, and one clock in any arrangement.
- The recipe consumes all three ingredients and produces one guide. The crafted
  stack uses the vanilla book model and keeps its identity through inventory
  synchronization and world save/reload.
- Using the guide in either hand opens the introduction. English and Ukrainian
  navigation contains the introduction and **Chapter 1: Your first estimate**,
  matching the content already shipped on modern NeoForge.
- Opening never consumes or damages the guide. Missing optional Forge GuideME
  support never exposes a recipe whose result cannot open.

## Platform rules

### Forge 1.20.1

- GuideME `20.1.15` is optional on both client and server.
- The recipe exists only when `guideme` is loaded and produces `guideme:guide`
  with string NBT `guideId: "ae2craftingtime:guide"`.
- AE2 Crafting Time starts without GuideME and does not resolve GuideME classes,
  register guide-only integrations, or expose the guide recipe.
- Published Modrinth/CurseForge relations list GuideME as optional only for this
  Forge row.

### Fabric 1.20.1

- Use AE2 15.x's bundled `ae2guide` renderer and addon resource namespace. Do
  not declare or publish a GuideME dependency.
- The recipe produces `minecraft:book` with a persistent Crafting Time guide
  marker and localized name. Only a marked book opens
  `ae2craftingtime:index.md` through AE2's guide API. No Fabric-only item is
  registered, so clients without Crafting Time can join a server with it.
- Package the same source Markdown in AE2's
  `assets/ae2craftingtime/ae2guide/` layout for this target.

The different stack implementations are an internal loader boundary. Their
name, model, recipe ingredients, content, navigation, and use behavior match
when Crafting Time is installed on the client.

## Compatibility and non-goals

- Support the current AE2 15.x compatibility range on both 1.20.1 rows. Forge
  also supports GuideME from `20.1.15` within its compatible 20.1 line.
- Keep the guide page ID and shared English/Ukrainian Markdown source. Fabric's
  former item ID `ae2craftingtime:guide` is removed: existing stacks with that
  ID in saved inventories cannot be migrated automatically once the registry
  entry is absent. Back up worlds and player data before upgrading: old books
  may be lost when those inventories load under the new registry. Use the old
  books while running the old version, then craft replacements after upgrading.
- Do not add GuideME to Fabric, replace AE2's bundled guidebook, fork the content,
  change modern NeoForge recipes, add new chapters, or change AE2 Crafting Time
  gameplay/profiling behavior.
- No custom renderer, model texture, screen, packet, persistence format, or
  server-side guide state.

## Acceptance criteria

- Forge with GuideME 20.1.15 shows the recipe, crafts the legacy-NBT guide, opens
  both pages in English and Ukrainian, and preserves identity after sync/reload.
- Forge without GuideME starts client and dedicated server cleanly and has no
  guide recipe or premature optional-class loading.
- Fabric crafts and opens only the marked vanilla book through AE2 15.x,
  renders both locales/navigation, and preserves its marker after sync/reload.
- Both loaders accept every ingredient order in 2x2 and 3x3 grids, consume the
  three inputs, produce one guide, and reject charged Certus/dust/substitutes.
- Metadata and release relations match actual platform availability; modern
  NeoForge guide behavior does not regress.
- Focused prepared-client smoke covers crafting, both hands, navigation,
  localization, inventory synchronization, world reload, and the Forge-without-
  GuideME case. Player JARs contain no test driver.
