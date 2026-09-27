# Release

Each release packages the same AE2 crafting-time, delay, and bottleneck tools
for every supported loader.

Each supported Minecraft/mod-loader combination is one row in `scripts/release-matrix.json`.
Do not add a row until the matching Gradle module builds a real jar.

Current rows:

```text
1.20.1-forge -> :mc_1_20_1_forge:distMod -> dist/ae2-crafting-time-<version>-forge-1.20.1.jar
1.20.1-fabric -> :fabric_1_20_1:distMod -> dist/ae2-crafting-time-<version>-fabric-1.20.1.jar
1.21.1-neoforge -> :mc_1_21_1_neoforge:distMod -> dist/ae2-crafting-time-<version>-neoforge-1.21.1.jar
26.1.2-neoforge -> :mc_26_1_2_neoforge:distMod -> dist/ae2-crafting-time-<version>-neoforge-26.1.2.jar
```

Every uploaded file follows `<mod-name>-<loader-specific-mod-version>-<mod-loader>-<minecraft-version>.jar`.
The loader id is lowercase and matches the release matrix.

Build every matrix row:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-all-versions.ps1
```

Normal builds use the tracked `modVersion` in `gradle.properties`. That is the
next release version, so every commit and every loader-explicit jar uses the
same development version.

Build only changed jars at the current development version:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\deploy-changed.ps1
```

## Release And Deploy

Start from a clean branch based on `origin/master`. The real deploy builds every
JAR needed by the GitHub Release, so do not run `build-all-versions.ps1` first.

Windows user environment variables are not automatically added to an already
running Codex process. Load the existing user-scoped secrets into the current
PowerShell process without printing them, run the cheap dry run, then deploy:

```powershell
$env:CURSEFORGE_TOKEN = [Environment]::GetEnvironmentVariable("CURSEFORGE_TOKEN", "User")
if ([string]::IsNullOrWhiteSpace($env:CURSEFORGE_TOKEN)) { throw "CURSEFORGE_TOKEN is missing at user scope" }
$env:RELEASE_TYPE = "release"
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\deploy-changed.ps1 -Deploy -DryRun -SkipModrinth
```

To include Modrinth, load and check `MODRINTH_TOKEN` at user scope and omit
`-SkipModrinth` from both commands. The dry run checks the plan without building
or uploading anything. Before the
real deploy, show the user its exact GitHub Release title and body and each
affected versioned JAR's exact changelog for each selected platform. Get
explicit approval for both groups of text. Do not upload before approval. If
the text or affected JAR set changes, rerun the dry run and get approval again.

After approval, run the real deploy without changing the release inputs:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\deploy-changed.ps1 -Deploy -SkipModrinth
```

The real deploy builds changed rows at `modVersion`, rebuilds unchanged rows at
their latest released versions for the complete GitHub asset set, uploads
changed rows, creates the GitHub Release, bumps `modVersion`, commits the
release state, and pushes the branch.

## Release notes and names

- Read [the project writing skill](../.codex/skills/ae2-crafting-time-writing/SKILL.md)
  before writing anything that will appear on GitHub, Modrinth, or CurseForge.
- Write changelogs for players, not as raw commit logs. Use natural sentences
  grouped under `ADDED`, `FIXED`, `IMPROVED`, `DELETED`, or `CHANGED`; skip empty
  categories.
- End every GitHub and Discord release-note item with its linked source GitHub
  issue, for example `- Something was fixed ([#111](https://github.com/cTux/ae2-crafting-time/issues/111))`.
  Use a manual changelog when automatic commit conversion does not produce those links.
- Include one image when reviewed smoke evidence exists for a player-visible
  change; otherwise include none. Choose the
  release's highest-effort player-visible feature or fix and crop its reviewed
  smoke-test evidence to the relevant area instead of using a full screenshot.
  Inspect the crop before publishing and exclude account data, tokens, chat,
  server addresses, coordinates, and unrelated worlds. The exact image is part
  of the release-body approval preview. Discord must upload it as an inline
  attachment in the release's single message, not leave it as a remote image
  link or send it separately.
- Conventional commit subjects are converted into those categories and stripped
  of commit types, scopes, and hashes. A manual `-Changelog` must already use the
  same `### CATEGORY` Markdown headings.
- Use `-Changelog` only when every affected matrix row has the same notes. If any
  note applies to only some rows, pass `-ChangelogPath` with a JSON object whose
  `all` value contains common Markdown and whose matrix-row keys contain only
  their specific Markdown. The dry run prints each JAR's final platform
  changelog and keeps row-specific notes out of unrelated uploads.
- Read the relevant commit subjects before the dry run. If automatic conversion
  would still sound like a commit log, prepare a manual `-Changelog` in the
  project voice.
- Keep platform descriptions casual and useful. Explain what the mod helps with,
  mention compatibility that matters, and leave out hype or generic AI wording.
- Name the GitHub Release with only the mod version, for example `1.0.5`. Put
  loader, Minecraft version, artifact, and categorized changelog details in the
  release body. Use the deterministic `release-<mod-version>` tag so a repeated
  deploy cannot publish the same version twice.

After it succeeds:

1. Verify the GitHub Release contains one loader-explicit JAR for every matrix row.
2. If uploading to Modrinth, verify each new version is listed with the expected loader and game version.
3. Confirm CurseForge accepted every upload. Public visibility can lag, and the author upload token may not be authorized for the public files read endpoint.
4. Merge the hook-created release PR and verify `origin/master` contains the next patch `modVersion`.

To mirror each published GitHub Release into Discord, include its same full
release description, GitHub link, and direct links to every JAR. Follow
[Discord release announcements](discord-release-announcements.md), including
the one-message and attached-image requirements; a link-only post is
insufficient. If the complete announcement exceeds Discord's content limit,
revise and reapprove the release body before publishing it instead of splitting
or truncating the Discord copy.

If an upload fails, start with the response body printed by the script. Before
retrying, check the platform, GitHub Releases, `.release-state.json`, and the
working tree for partial completion. Retry only when the rejected version was
not created.

`deploy-changed.ps1` fingerprints only jar inputs: root build files, shared main code, and the matrix row's version main code. Test-only edits do not build or deploy.

For every affected row, `-Deploy` publishes the current `modVersion`, builds the loader-explicit jar, generates that jar's changelog from commits since its previous release, and uploads the jar plus changelog to CurseForge and, unless `-SkipModrinth` is set, Modrinth. A scoped manual changelog adds `all` notes to every affected row and adds each matrix-row entry only to that row; the GitHub body prints the common block once and each row-specific block under its loader and Minecraft version. Modrinth version numbers use `<mod-version>-<loader>-<minecraft-version>`, for example `1.1.0-forge-1.20.1`, so they stay unique across the project while putting the mod version first. CurseForge uploads include the project's Client and Server environment versions. It also rebuilds the unchanged rows at their current released versions so the GitHub Release always attaches the complete latest supported JAR set. The release title and body list only affected versions and their per-jar changelogs. After every successful deploy, it bumps `modVersion` to the next patch and commits `gradle.properties` together with `.release-state.json`; every later commit and normal build then belongs to that new version. The repository's post-commit hook pushes that commit and creates the branch PR when needed.

Run `scripts/setup-git.ps1` once after cloning. It installs the tracked post-commit hook, which automatically pushes every fix, feature, and release commit and creates one PR per branch. Existing PRs are reused, so later commits update them without duplicates. Work on a branch: the hook intentionally refuses to push a detached HEAD. GitHub CLI must be installed and authenticated for PR creation.

Release metadata can come from the matrix row or from environment overrides. `RELEASE_TYPE`, `MODRINTH_PROJECT_ID`, and `CURSEFORGE_PROJECT_ID` override the row values for all entries in the current run. `CURSEFORGE_TOKEN` and, unless `-SkipModrinth` is set, `MODRINTH_TOKEN` are required only for a real `-Deploy`; GitHub CLI must also be authenticated.

Each matrix row also declares its required and optional Modrinth projects in
`modrinthDependencies`. The deploy script validates and uploads that list with
every new version; keep it consistent with `docs/dependencies.md` and the loader's
mod metadata.

`-Deploy` fails fast unless project ids for selected platforms resolve for every affected row. The current Modrinth and CurseForge project ids are stored per row in the release matrix.

Check the release script after changing `deploy-changed.ps1` or the release matrix:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-deploy-changed.ps1
```
