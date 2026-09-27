# Minecraft 1.20.1 Guide Book Implementation Plan

Lifecycle: see the [scope status and evidence](spec.md).

Implement [#265](https://github.com/cTux/ae2-crafting-time/issues/265) from the
[specification](spec.md) and [technical design](technical-design.md).

## 1. Add one shared-content packaging path

- Keep the existing English/Ukrainian Markdown as the only authored content.
- Add target resource processing that emits the GuideME layout for Forge and the
  AE2 `ae2guide` layout for Fabric without changing modern NeoForge output.
- Extend build validation for page hashes, locale/navigation parity, relative
  links, guide definition, and JAR paths.

Gate: both 1.20.1 JARs contain byte-equivalent page sets in their platform
layouts, with no committed duplicate Markdown.

## 2. Implement the Forge 1.20.1 guide

- Add GuideME 20.1.15 as compile-only/optional development input and optional
  loader metadata.
- Add the conditional shapeless recipe producing one `guideme:guide` with the
  exact legacy `guideId` NBT.
- Reuse the existing guide definition, localized name, vanilla book model, and
  content. Keep production optional references behind installed-mod boundaries.
- Add unit/build checks for exact ingredients, result/NBT, condition, metadata,
  missing-dependency packaging, and item persistence.

Gate: the recipe/content work with GuideME 20.1.15, and Forge client/dedicated
server startup plus recipe lookup stay clean without GuideME.

## 3. Implement the Fabric 1.20.1 guide

- Produce a marked `minecraft:book` from the ordinary shapeless recipe without
  adding a Fabric-only item registry entry; open only marked books at
  `ae2craftingtime:index.md` through AE2 15.x.
- Keep the localized name and vanilla book model. Warn players to back up world
  and player data before upgrading: old saved `ae2craftingtime:guide` stacks may
  be lost when inventories load, so they must craft new books afterward.
- Test main/off-hand use, client/server class isolation, exact recipe inputs,
  invalid substitutes, inventory sync, and world reload.

Gate: the guide opens the AE2 bundled renderer at the correct page and Fabric
declares no GuideME dependency.

## 4. Align publishing metadata

- Add GuideME as an optional relation only to the 1.20.1 Forge row in
  `scripts/release-matrix.json`.
- Update both deploy implementations and dry-run tests with identical Forge-only
  relations. Update `docs/dependencies.md` and feature coverage.
- Verify modern NeoForge relations and guide artifacts remain unchanged.

Gate: dry runs show GuideME for Forge 1.20.1 and both modern NeoForge rows, never
Fabric 1.20.1; CurseForge and Modrinth mappings agree.

## 5. Prove the complete flow

- Run documentation/link checks, `git diff --check`, targeted tests, all four
  release-matrix builds, and player-JAR/test-driver isolation checks.
- Extend the prepared-client guide scenario, then run Forge with GuideME, Forge
  without GuideME, and Fabric with AE2 15.x. Cover 2x2/3x3 recipes, substitutes,
  both hands, English/Ukrainian pages/navigation, sync, and reload.
- Review screenshots and retain exact mod inventories, artifact hashes, logs,
  semantic assertions, process/world cleanup, and any failed attempts.

Completion gate: every acceptance criterion has retained passing evidence, both
1.20.1 loaders expose the same player behavior, optional metadata matches real
availability, and modern targets have no regression.
