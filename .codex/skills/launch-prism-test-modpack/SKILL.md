---
name: launch-prism-test-modpack
description: Install or smoke-test a named AE2 modpack through Prism in CodexVM. Use when the user asks to test a modpack; do not use for prepared repository clients such as 1.20.1-forge.
---

# Test A Named Modpack In Prism

Treat the GitHub issue list as the source of truth. Find the matching issue
before starting a modpack test, or create one when none exists. Remove secrets,
personal data, private paths, and private server details before creating or
updating an issue.
Follow the [issue-label policy](../../../AGENTS.md#issue-labels).

## Always use CodexVM

- Build production and test-driver JARs only on the host. Follow
  [host build and VM staging](../../../docs/dev-client.md#host-build-and-vm-staging)
  for Java selection, the session worktree share, and artifact replacement.
- Load `use-codex-vm` before every Minecraft launch or visual check. Reuse the
  running VM and share the session worktree; reuse an exact existing share.
- Maximize the exact Minecraft window through VNC before visual inspection. Run
  clients sequentially and confirm the tested client stopped; never kill Java
  processes broadly.
- Before persisting any guest filesystem path in Prism JVM arguments, resolve it
  to an absolute Windows path and serialize it with forward slashes (`C:/...`).
  Reject `?` and control characters, then read back the persisted Prism field and
  require the exact serialized value before launch. Never inject raw `\` path
  separators or try to reconstruct a corrupted path inside the test driver.

## Route

- For a named modpack, resolve the exact pack release and inspect the managed
  instance inventory. Read
  [install-modpack.md](references/install-modpack.md) only when an exact eligible
  instance is missing. Then read
  [launch-and-verify.md](references/launch-and-verify.md).
- Do not use this skill for a prepared repository target. A request such as
  `smoke UI test for 1.20.1-forge` uses `run-ae2-client-smoke` and must not open
  Prism.

## Named Modpack Contract

For multiple scenarios on one installed mod graph, use the
[single-launch suite](../../../docs/test-driver/spec.md#single-launch-suites).
Prepare one marked disposable world, launch Prism once, restore pristine fixture,
player, profiler and client-cache state between cases, and collect each case's
screenshots before advancing. Do not relaunch the pack for every integration.

1. Resolve the canonical CurseForge or Modrinth project and an exact release
   supported by `scripts/release-matrix.json`. Report ambiguity instead of
   silently choosing a similarly named pack.
2. Inspect only instances in Prism's **Codex** group. Reuse one only when
   group membership, `instance.cfg`, and `mmc-pack.json` prove the same managed
   pack release, Minecraft version, and loader. If no matching instance exists
   in that group, download and install it through Prism's UI into **Codex**.
   Never launch, copy, move, or modify a modpack outside that group, even when
   its release matches.
3. Inspect enabled JAR metadata, including nested JARs, for mod ID `ae2`. If AE2
   is absent, do not copy or launch this mod; remove the test instance and report
   it as ineligible rather than failed.
4. Select the exact release-matrix row. Unless the user supplied a JAR, build
   that row on the host once per campaign and reuse its loader- and
   version-specific artifact.
   Stop as unsupported when no row matches.
5. Launch through Prism, verify the mod and startup completion from current logs
   plus the guest display, collect crash evidence when needed, and close only the
   exact tested client.
6. Report the requested and resolved pack names, release, instance ID, Minecraft
   version, loader, copied JAR, result, and failure reason.

## Timing Report

For every UI smoke run, read and follow
[the screenshot archive contract](../../../docs/ui-smoke-evidence.md). Keep
evidence per mod and integration checkpoint, including failed attempts.

Before every screenshot-bearing launch, set `guiScale:0` only in the exact
disposable staged instance and verify its readback as described in
[launch-and-verify.md](references/launch-and-verify.md). Never change the
managed Prism source instance or global settings.

Start a wall-clock timer before the first smoke-test action. Record each material
phase from actual timestamps, including setup, installation or staging, launch,
UI verification, retries, evidence collection, and cleanup. End every smoke-test
report with this table and a total row:

| Part of the smoke UI testing task | Time | Why it took that long |
|---|---:|---|

Use concrete causes from the run, such as VM boot, pack download, mod loading,
world startup, UI assertions, or a failed attempt. Keep a successful retry's
runtime separate from total task time. Mark an unavailable duration as
`not measured`; do not estimate it.

## Boundaries

- Do not place a live Minecraft runtime on VMware's shared filesystem; use the
  guest-local staging described in the references.
- Do not install a JAR, select an account, join a world or server, or perform
  gameplay unless the request authorizes it.
- Never substitute another loader or Minecraft version, infer mod presence from
  filenames, start duplicate clients, or kill Java processes broadly.
- Retry a repeated provider metadata failure once. If it repeats or stalls at
  completion, record the install as blocked and continue the batch.

For a standard group in an explicitly requested pack, pass `standard-ae2`
through `prepare-ui-smoke-suite.ps1` so it expands into the leaves listed in
`scripts/ui-smoke-groups.json`, sharing one loaded disposable world before the
one Prism launch. Use schema-1 world reloads
only for explicit isolation diagnostics. Do not pass that alias to the raw JVM
scenario property. This does not change the inspected pack graph or authorize
automatic pack selection from a source diff.
