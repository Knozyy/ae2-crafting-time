# In-game Configuration Screen Implementation Plan

Lifecycle: see the [scope status and evidence](spec.md).

Implement [#117](https://github.com/cTux/ae2-crafting-time/issues/117) from the
[specification](spec.md) and [technical design](technical-design.md).

## Focused follow-up: preserve recurrent detection opt-in (#592)

Follow the [scope and status](spec.md#recurrent-detection-default-592) without
reopening the broader configuration-screen campaign below.

1. Change only `ServerConfig.copy()` to copy every server-owned switch's value,
   reusing the `ClientConfig.copy()` pattern. Keep defaults and numeric fields.
2. Extend `OptionsModelTest.serverDefaultsEditsCopiesAndResets` to enable recurrence,
   copy it, and assert the copy retains it alongside unrelated switches and numeric
   values. Reset the copy and assert recurrence is off while the source stays on.
   Keep `FeatureOptionsTest` and `ServerConfigFileTest` coverage for fresh defaults,
   generated false, explicit true on load, and reset false.
3. After the hook creates the fix PR, run
   `./gradlew.bat :shared:test :shared:jacocoTestReport`; shared line and branch
   coverage must remain 100%. Confirm the new regression fails with the old copy
   implementation. Record required GitHub CI separately at the final PR head.
4. Review the shared source-set wiring for all four release targets and the
   existing English/Ukrainian GuideME and wiki default/opt-in guidance. No new
   fixture or Minecraft launch is needed for this model-copy-only correction.

Completion: default-off generation/reset and explicit opt-in preservation pass,
unrelated values remain intact, current-head CI passes, and the fix is merged
before verified issue closure. If the diff expands into adapters or UI behavior,
reassess verification before proceeding.

## 1. Lock the typed config contract

- Add Minecraft-free client/server setting models with the exact defaults,
  ranges, sort values, validation, reset, ownership, and independent feature
  switches from the spec.
- Route current config access through `ServerConfig`; add the client getters used
  by UI code.
- Add focused tests for defaults, every boundary, NaN/infinity, invalid RGB,
  per-field fallback, and shrinking retained-sample limits.

Gate: every setting has one owner and one tested default/range.

For every row in the [feature-switch inventory](spec.md#feature-switches),
record the existing collection or UI seam before editing. Include both chat
senders for the personal warning mute. Keep the switch list synchronized with
the shipped feature inventory in `docs/feature-coverage.md`.

## 2. Split storage and migrate existing files

- Use shared typed client/world-server parsers and atomic writers on every loader.
- Migrate known values from `ae2craftingtime-common.toml` when the new owner
  file is absent; keep the legacy file untouched. Existing owner files take
  precedence, with missing keys using defaults.
- Follow the focused [startup-generation plan](server-config-startup/implementation-plan.md)
  for generating the missing world server file without an edit (#535).
- Test missing, valid, malformed, partial, interrupted-save, and repeated-migration
  cases on all loader implementations.

Gate: existing values survive, invalid values fall back narrowly, and dedicated
servers never load client config classes.

## 3. Build the native screen

- Add the shared working-copy/descriptor state and the smallest `mc1201` and
  `mc2612` native widget adapters.
- Implement categories, help/default/range text, availability states, save,
  Client/Server tabs, read-only server values for non-operators,
  cancel, section reset, full reset, numeric/color validation, and save errors.
- Add matching English and Ukrainian labels, descriptions, errors, ownership,
  availability, and apply-timing strings.

Gate: keyboard, mouse, narration, scrolling, focus, and parent-screen return work
at supported GUI scales in both code seams.

## 4. Register loader entry points

- Register Forge and NeoForge config-screen factories from client-only setup.
- Add the optional Fabric Mod Menu entrypoint and metadata without making Mod
  Menu a runtime requirement.
- Add startup/packaging checks for client-only isolation and optional dependency
  behavior.

Gate: every loader details page opens the screen where supported; Fabric starts
cleanly with and without Mod Menu.

## 5. Apply client settings

- Replace hard-coded surface visibility, detail/status visibility, sort defaults,
  TTC/status/total colors, and badge color/opacity at their shared UI seams.
- Make each named feature switch hide its row, badge, tooltip, and click target
  together. Check every status and optional integration independently.
- Keep calculations, packets, current per-screen sort cycling, and the Controls
  binding unchanged.
- Cover Crafting Plan, Crafting Status, Crafting Tree, and ME Requester with
  focused rendering/behavior checks.

Gate: each option changes only its named surface and persists across restart.

## 6. Apply and expose server settings

- Replace the central delay constants with validated server values and preserve
  profiling/retention/filter/chat/notification behavior.
- Gate each named diagnostic at its collection/classification seam, retaining
  learned throughput and unrelated diagnostics. Gate world-save writes without
  deleting existing history.
- Send the personal warning preference on login and change. Check it for each
  recipient in both delayed and blocked chat senders; keep the server-wide
  `notifyOnDelayed` override. Verify one muted and one unmuted player together.
- Add the effective-server-config snapshot and fixed typed update packet to the
  existing protocol; bump client/server protocol versions together on all four
  targets.
- Send snapshots on login/reload. Require integrated-owner or permission-level-4
  authority for updates; validate direction, context, revision, lengths, enums,
  and ranges before one atomic save/apply/broadcast.

Gate: two clients observe the same effective server values and cannot override
them; valid config reloads do not corrupt samples or active crafts.

## 7. Complete cross-target verification

- Run unit and integration tests, translation parity, documentation/link checks,
  `git diff --check`, and all four release-matrix builds.
- Run prepared-client smoke on all targets for loader entry, save/cancel/reset,
  invalid input, immediate client changes, persistence, authority/restart text,
  and every feature switch. Capture both Client and Server tabs at usable GUI
  scales and compare them with the [mockup](options-mockup.svg).
- Start dedicated servers on every target; run Fabric with and without Mod Menu;
  audit player JARs for test-driver content and unintended dependencies.

Completion gate: every acceptance criterion has passing automated or retained
runtime evidence, screenshots are reviewed, all processes/worlds are cleaned up,
and no repository-owned warning remains.
