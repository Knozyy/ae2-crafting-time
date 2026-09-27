# Explain generated configuration files

Status: ready-to-implement

Scope: Comments for every generated server and client setting, plus configuration ownership and editing guidance.

Issue: [#590](https://github.com/cTux/ae2-crafting-time/issues/590)

Planning: [Technical design](technical-design.md) and [reviewed implementation plan](implementation-plan.md).

## Problem

Server owners naturally look in `config`, but active server settings belong to a world. The generated file currently lists settings without explaining their effects. The older common file can add confusion because it remains after migration.

The startup generation fix is already implemented under [#535](https://github.com/cTux/ae2-crafting-time/issues/535). This scope explains the files and their settings; the separate in-game save investigation stays in [#536](https://github.com/cTux/ae2-crafting-time/issues/536).

## Behavior

Write a useful full-line comment block immediately before every setting saved in `ae2craftingtime-server.toml` and `ae2craftingtime-client.toml`. Explain what the option affects, what turning it off changes, its default, and any range, unit, dependency or reload detail that helps an owner edit it. Name optional addons explicitly. Include numeric settings and every serialized color.

Each file starts with its ownership and location. The server file is `<world>/serverconfig/ae2craftingtime-server.toml`; on a dedicated server, `<world>` is the directory selected by `level-name`. In singleplayer it is the selected save under `saves`. Starting a world creates a missing server file. Client settings are local to that client in `config/ae2craftingtime-client.toml`; saving Client Options creates or replaces that file. Client startup alone does not create it.

Explain that `config/ae2craftingtime-common.toml` supplies only known legacy settings when the corresponding active file is absent. The server migrates `enabled`, `notifyOnDelayed`, `showChatMessages`, `maxSamples` and `outlierMultiplier`; the client migrates `showInTree`. Active files take precedence, and migration leaves the common file intact. Editing the common file does not override an existing active file.

External edits take effect when the client restarts or the world/server reloads its options at startup. Owners should stop the relevant client or world/server before editing its file. Saving valid Options changes with Done applies them live after a successful save, including sample-window and outlier changes. Server edit permissions remain unchanged.

Newly generated files and later explicit saves contain the comments. Loading an existing file does not rewrite it to add documentation. Preserve effective values, defaults, validation, key order, keys, file creation timing, permissions, packets and world data.

## Details that must stay accurate

- `recurrentDetection` defaults off on the implementation base. Other server switches default on.
- Client `compactStatusAmounts`, `ttcColors`, `textShadow` and `badgeBackground` default off; other client switches default on.
- `waitingColor` remains serialized for compatibility but currently has no rendering effect. Say so rather than implying it changes Waiting text.
- Sort values are `0` for AE2 order, `1` for shortest first and `2` for longest first; both default to `2`.
- Colors use quoted six-digit RGB strings. Badge opacity is `0` through `255`, default `176`; it affects visible badge backgrounds when enabled.
- Disabled display switches do not disable the optional addon itself. Client status visibility still depends on the corresponding server diagnostic.

## Compatibility and scope

All four release targets consume the shared writers: Forge 1.20.1, Fabric 1.20.1, NeoForge 1.21.1 and NeoForge 26.1.2. Keep loader adapters and legacy migration unchanged. Use comment lines beginning with `#`; do not introduce inline value comments, another parser, another configuration library or another setting.

Update the English and Ukrainian GuideME configuration pages and their published wiki counterparts. Explain paths, creation timing, migration precedence and how to edit settings. Correct the stale claim that Done requires a restart for sample-window or outlier changes. Keep unrelated guide and wiki content.

## Acceptance criteria

| ID | Required result |
| --- | --- |
| A1 | Every saved server/client assignment has a meaningful preceding comment describing its effect and applicable off behavior, default, range, unit and dependency. Numeric options and all colors are covered. |
| A2 | Headers and guidance explain ownership, exact paths, server startup creation, client creation on save, common-file migration and active-file precedence. |
| A3 | Representative non-default values round-trip through documented output. Subsequent saves retain complete comments. Defaults, keys, values, order, permissions and wire format are unchanged. |
| A4 | Loading existing client/server files preserves their bytes. Missing server generation preserves migrated values and legacy bytes. Existing active files win over legacy input. |
| A5 | Both GuideME locales and the live English/Ukrainian wiki configuration pages give matching, accurate editing guidance, including live Done and external-edit reload behavior. |
| A6 | Shared changed code retains 100% line/branch coverage, all four target test/build paths pass, GuideME resources validate, and required current-head CI passes. |

Completion requires the reviewed implementation, verified publication of both wiki pages, and the checks above. This comment-only change does not require a Minecraft UI campaign: serialization is shared and no UI, loader lifecycle or runtime setting behavior changes. If implementation expands into such behavior, review the verification plan before proceeding.
