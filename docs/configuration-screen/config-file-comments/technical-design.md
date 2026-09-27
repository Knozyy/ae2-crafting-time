# Configuration comments technical design

Lifecycle: see the [canonical scope status](spec.md).

## Current evidence

Investigation base: `8ddcc38dc15829242c0a915c91a92f9b3f20b16b` on `master`.

`shared/src/main/java/com/ctux/ae2craftingtime/core/ServerConfigFile.java` and `ClientConfigFile.java` are the shared serialization boundary. Each save method writes a title followed by settings without descriptions. `OptionFeature` defines boolean keys and ownership; `FeatureOptions` defines their defaults. `ServerConfig` and `ClientConfig` define numeric validation, color keys and defaults.

All four loader entrypoints call the `mcCommon` runtimes. `ServerOptionsRuntime.initialize` resolves the world-root server file and delegates to `ServerConfigFile.load`. Loading prefers the world file, otherwise reads known legacy keys, then saves only if the world file is absent. `ClientOptionsRuntime.initialize` loads the client file or legacy `showInTree` without saving.

Options Done reaches `ClientOptionsRuntime.apply` or `ServerOptionsRuntime.accept`. Both use the same save methods as generated output. Server acceptance validates permission and revision, saves, updates the effective model, calls `ProfilerBridge.configure`, and broadcasts the accepted snapshot. Both `mc1201` and `mc2612` bridges configure the profiler and accuracy tracker live. `CraftProfiler.configure` updates sample limits and outlier/delay thresholds and trims stored sample queues. No lifecycle or permission changes are needed.

Both writers use `ClientConfigFile.saveLines` for directory creation and atomic file replacement. Readers split lines at the first `=` and ignore unrecognized keys. Full-line `#` comments are safe; inline comments after values are not supported.

## Change

Add English comment text at the shared writer boundary, using existing loops for features and colors. Keep the assignment strings and their order intact. Use the existing option models as the source of defaults and validation facts; avoid a second configuration model or loader-specific copies. A small shared description helper is acceptable only if it reduces duplication between these two writers.

Every emitted assignment gets its own comment block. File headers carry ownership, path, generation, migration and reload guidance so individual blocks can focus on the setting. Explain each boolean's local or server-wide effect and off behavior. State conditional effects rather than promising that an absent or unsupported addon starts working when enabled.

## Semantic sources and boundaries

| Settings | Evidence and documentation boundary |
| --- | --- |
| Boolean defaults | `FeatureOptions.reset`: recurrent detection off on this base; four client appearance/display defaults off. Do not use the previous revision's all-on server assumption. |
| Samples and outliers | `ServerConfig` validates `maxSamples` 1-100/default 10 and finite `outlierMultiplier` 1.0-1000.0/default 4.0. `CraftProfiler.filteredSamples` filters median-relative duration per unit when at least five samples exist. |
| Delay thresholds | `minimumNoProgressSeconds` is 1-3600/default 10; `typicalDurationMultiplier` is finite 1.0-1000.0/default 2.0. `CraftProfiler` converts seconds at 20 ticks/second and uses the greater minimum/learned threshold. |
| Sort and appearance | `ClientConfig` validates sort modes 0-2, opacity 0-255 and RGB 0-FFFFFF. `RowTextColor` and `ClientOptionsRuntime.ttcColor` govern native/neutral/TTC color behavior. |
| Waiting color | `ClientConfig.Color.WAITING` remains serialized, but has no production rendering consumer and is excluded from appearance rows. `TtcText.waiting()` inherits styling. Describe it as retained with no current visual effect. |
| Diagnostics and client visibility | `ClientOptionsRuntime.enabled` combines client switches with server diagnostic availability; `ProfilerBridge`, `CraftingCalculationMixin` and provider observers consume server switches. |
| Warnings and chat | `DelayedNotificationServer` and `BlockReasonNotifier` gate global warning delivery; client warning preference is independent. `StatsChatServer` allows a reset even when its chat notice is disabled. |
| Optional integrations | `ServerOptionsRuntime.scopeEnabled` gates AdvancedAE, NeoEco and AE2 Lightning Tech CPU adapters; `keyEnabled` gates Applied Mekanistics chemical statistics. Client Crafting Tree and ME Requester switches control their TTC surfaces. |
| History | Both `ProfilerBridge` families gate learned-history writes through `saveHistory`; disabling it does not delete existing saved history. |

Keep `OptionFeature` names/order, packet encoding, defaults, validation and client/server runtime behavior unchanged. Preserve the currently inert waiting color rather than expanding this issue into a display change.

## Documentation delivery

Before implementation, Step 5 delivers the reviewed scope documents and the English/Ukrainian GuideME configuration guidance, and publishes equivalent wiki guidance. Describe existing ownership/editing behavior accurately; do not claim new generated comments have shipped before implementation. The implementation can subsequently mention the delivered comment behavior when appropriate.

Canonical pages are `shared/src/main/resources/assets/ae2craftingtime/guides/ae2craftingtime/guide/features/configuration.md` and its `_uk_ua/features/configuration.md` counterpart. Wiki pages are `Feature-Configuration.md` and `Ukrainian-Feature-Configuration.md` in the separate `ae2-crafting-time.wiki.git` repository. Strip GuideME frontmatter and preserve existing wiki links and unrelated prose. Refresh the wiki before editing; the investigated revision was `72d4e7b6b071b6133761a1cb17b21e939896d4fe`. Record the published revision and read back both live pages.

No issue-body update is required: #590 already describes this scope and excludes the separate startup and edit defects.

## Verification and feasibility

Extend the existing `ClientConfigFileTest` and `ServerConfigFileTest`; do not add a new test framework. Check complete documented assignments, representative non-default values, subsequent saves, migration/precedence, and unchanged existing bytes. Replace the server test's physical-line count with an assignment count so comments are allowed while missing/extra settings remain detectable. Human review checks the accuracy of prose; tests check emitted structure and preserved values.

The shared implementation is identical across all targets. Run the normal all-module tests/coverage, all-target compilation/build, and `checkGuideResources` after the hook-created PR exists. Host Java 17, 21 and 25 executable paths were verified during investigation. Existing Gradle tests and GuideME validation require no new fixtures or tooling.

No Minecraft launch is needed for a generated-comment change. The smoke impact planner has no narrow config-writer rule and falls back to full suites; that fallback does not establish a technical need for a campaign. Do not change smoke policy or launch a full suite to verify prose. If executable scope changes, reassess representative scenarios and verify guest native manifests, Java versions, disposable fixtures and VM lifecycle before recommending runtime checks. Guest prerequisites were not inspected for this scope.
