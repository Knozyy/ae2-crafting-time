# TTC symbols implementation plan

Lifecycle and acceptance: [spec](spec.md). Architecture: [technical design](technical-design.md).

1. Add the default-On `showEmoji` client Appearance feature. Add matching English
   and Ukrainian config labels. Reuse the existing config reader/writer and screen.
   Cover defaults, old files without the key, false/true round trips, invalid values,
   copied options and reset behavior with `OptionsModelTest`/`ClientConfigFileTest`.
2. Add the covered pure-Java palette and exact translation/argument policy, then
   the shared component adapter. Cover every status in the spec, unknown keys,
   toggle Off, multiple durations, uncertainty, missing data and idempotence.
   Decorate client `TtcText` paths and raw Crafting Tree badges. Preserve numeric
   row classification and native/explicit color behavior with `RowColorPolicyTest`
   and `RowTextColorTest`. Preserve semantic translation roots used by callers.
3. Add thin client chat wrapping adapters at the verified version-specific seams
   in the design. Preserve raw log/history content, nested translation arguments,
   fallback text, click/hover actions and siblings. Refresh visible chat on option
   save. Add component tests beside `TtcTextTest`, `StatsChatServerTest` and
   `ProviderLocateTest` covering server-shaped messages through the decorator,
   all time argument positions, reason-specific warnings and two local settings.
4. Audit every `TimeEstimate.format*` display caller and the complete status-key
   list against the acceptance table. Keep rates, counts, identifiers and item names
   plain. Check decorated widths at row, total, CPU-card and optional-addon sites.
   Preserve badge and text-shadow options and existing status visibility switches.
5. Update both GuideME locales' `features/configuration.md`,
   `features/time-estimates.md` and `statuses/index.md` with the full palette,
   default-On toggle and text-only fallback. Mirror the reviewed text into existing
   English/Ukrainian GitHub wiki configuration/status pages, preserving wiki edits;
   verify published page content and links. Do not replace old screenshots or
   describe them as symbol verification. Review locale keys and placeholder parity.
6. Review the complete diff, then create the implementation PR through the normal
   conventional commit hook. Run no local tests before that PR exists. Afterward
   run `./gradlew test jacocoTestReport checkGuideResources` and the existing
   all-target compile/build checks needed for mixin registrations and API changes.
   Require 100% line/branch coverage for changed pure logic, retain component
   boundary tests, and report GitHub CI separately for the exact reviewed head.
   Do not run Minecraft smoke or claim runtime glyph/layout verification.
7. Reconcile spec status with merged implementation, exact test/CI SHA and wiki
   readback. Completion requires code, default-On config, both guide locales,
   published wiki, review and current-head required CI; visual qualification is
   explicitly outside this user-authorized run.

## Acceptance mapping

| Criterion | Change and evidence |
| --- | --- |
| A1: every duration once | Steps 2-4; tooltip, sample, accuracy, chat and addon component tests; caller audit |
| A2: every status | Step 2; full palette table and each reason/status key tested |
| A3: default-On local toggle | Steps 1/3; config persistence/reset and local chat/UI Off tests |
| A4: preserve behavior/styles | Steps 2-4; click/hover, nested components, row policy, raw-log boundary and layout source review |
| A5: supported targets/docs | Steps 3/5/6; registered adapters, all-target build, locales, GuideME resources and wiki readback |
| A6: verification limits | Steps 6/7; no smoke; explicit unverified glyph/visual appearance statement |
