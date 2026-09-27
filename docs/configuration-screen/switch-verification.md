# Verify every options switch

Tracking: [#511](https://github.com/cTux/ae2-crafting-time/issues/511).

The base crafting-plan smoke for [PR #510](https://github.com/cTux/ae2-crafting-time/pull/510)
does not exercise every runtime switch. This is the campaign checklist for the
[configuration specification](spec.md), not evidence that the campaign passed.

## Build the matrix from the tested revision

Enumerate every value of `OptionFeature`, recording its stable key, owner,
group, default, affected behavior, applicable targets, and optional dependency.
Do not copy a fixed count into the test: later options must appear in the matrix.

Cover Forge 1.20.1, Fabric 1.20.1, NeoForge 1.21.1, and NeoForge 26.1.2.
For unavailable addon cases, record not-applicable with the missing contract or
unsupported target. An available but unrun case is unverified.

Most switches default on. Verify the four default-off exceptions explicitly:
fast-to-slow TTC colors, compact crafting amounts, text shadow, and badge
background. Test defaults from a clean client/world as well
as migrated configuration; do not reset a user's real files.

## Per-switch sequence

1. Prepare a visible fixture for the named behavior and record its initial state.
2. Turn the switch off, save with Done, and observe the specific effect.
3. Confirm an unrelated sibling feature remains active.
4. Reopen the screen and verify the saved value.
5. Turn it on, save, and confirm the behavior returns without losing history.
6. Exercise Cancel, section/all reset, and malformed-field recovery at the
   appropriate existing model/config boundary.

Client switches affect only that player's display or controls. Check server
profiling and a second client's choices remain intact. Server switches affect
world-owned behavior without erasing learned history or unrelated diagnostics.
The profiling master intentionally suppresses server-derived behavior; this is
not a sibling-isolation failure. Compact amounts and cosmetic choices require
their documented profiling-off checks.

Use the existing options session, client/server config parsers, and prepared
scenario checkpoints. Match old config round trips and migration to current
defaults; one malformed field must not reset unrelated valid values.

## Result record and gate

For each key/target/state, retain exact source commit, fixture/scenario, dependency
graph, expected and actual result, and screenshot/log references. Review visual
checkpoints with missing qualified baselines explicitly; semantic PASS alone
does not establish appearance.

Cross-link the [server authority campaign](server-options-verification.md) and
[two-player warning campaign](warning-mute-verification.md) instead of treating
an ordinary Plan smoke as their substitute. Link any fixes and current-head CI.
The issue remains open until every applicable switch has evidenced off/on and
persistence results, with unsupported and untested cells clearly distinguished.
