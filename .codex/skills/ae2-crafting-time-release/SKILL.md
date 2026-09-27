---
name: ae2-crafting-time-release
description: Build or release AE2 Crafting Time distributions. Use for release-matrix rows, artifact naming, deploy scripts, uploads, and release checks.
---

# AE2 Crafting Time Release

## Workflow

Build all production and test-driver JARs on the host, never in CodexVM. Use
`JAVA_HOME_17`, `JAVA_HOME_21`, and `JAVA_HOME_25` for the installed host JDKs;
follow [host build and VM staging](../../../docs/dev-client.md#host-build-and-vm-staging)
when preparing artifacts for a client or modpack smoke test.

1. Read `AGENTS.md` and `docs/release.md`; they own the complete release order.
2. Treat the GitHub issue list as the source of truth. Find the matching issue
   for release work before starting, or create one when none exists. Remove
   secrets, personal data, private paths, and private server details before
   creating or updating an issue.
   Follow the [issue-label policy](../../../AGENTS.md#issue-labels).
3. Read [the writing skill](../ae2-crafting-time-writing/SKILL.md) before
   changing changelogs, release notes, or text published to CurseForge,
   Modrinth, or GitHub.
4. Treat `scripts/release-matrix.json` as the source of truth.
5. Keep jar names loader-explicit. Do not add a matrix row until its module
   builds a real jar.
6. Run the release self-check after script or matrix edits.
7. Before retrying an upload, inspect the platform error and confirm whether it
   created a partial version, GitHub release, local state, or version bump.
8. Write multipart JSON only through the script's BOM-free `Write-Json` helper.
   Keep the no-BOM regression check in `test-deploy-changed.ps1`.
   Send JSON-valued multipart fields with `curl --form-string`, never `-F`;
   semicolons in JSON are otherwise parsed as multipart attributes. Keep file
   attachments on `-F`.
9. In a managed environment, check GitHub authentication and user-scoped tokens
   with the same elevated permissions as the real deploy. Treat a sandbox-only
   failure as non-authoritative and never print token values.
10. Before the dry run, read the relevant commit subjects as player copy. If
    automatic conversion would still sound like a commit log, prepare a manual
    categorized changelog in the project voice. Use `-Changelog` only when every
    affected row has the same notes. Otherwise use `-ChangelogPath` with common
    notes under `all` and row-specific notes under exact release-matrix ids;
    never send a loader- or Minecraft-specific note to an unrelated JAR. Use a
    manual changelog whenever automatic conversion lacks full source-issue URLs.
    Keep a generated changelog file outside the worktree so the real deploy's
    clean-tree gate accepts it, and use that unchanged file for dry run and deploy.
11. End every GitHub and Discord release-note item with its linked source GitHub
    issue, for example `([#111](https://github.com/cTux/ae2-crafting-time/issues/111))`.
    Use one source link per bullet; do not combine several issues into one item.
    Include one image when reviewed smoke evidence exists for a player-visible
    change; otherwise include none. Discord must receive exactly one message;
    upload that image in the same webhook request so it appears as an inline
    attachment, never only as a remote Markdown link. Reject an announcement
    that exceeds Discord's content limit before posting instead of splitting or
    truncating it. Take release screenshots only from reviewed
    UI smoke-test results; never generate or reconstruct them. Select the release's
    highest-effort player-visible feature or fix and use a focused crop from that
    evidence, not a full screenshot. Inspect the crop before publishing and
    exclude account data,
    tokens, chat, server addresses, coordinates, and unrelated worlds. Include
    the exact image in the release-body approval preview.
    Before approval, budget the complete Discord message with production-length
    release, JAR, and selected platform links, not only the release body. Leave
    enough margin for generated file ids.
12. Modrinth upload is optional. When the user excludes it, pass `-SkipModrinth`
    to both dry run and deploy; require only the CurseForge token and project ids.
    After the dry run, get explicit user approval for the exact GitHub Release
    title and body and for every affected versioned JAR's changelog on each
    selected platform. Do not upload until all text is approved; rerun the
    preview and approval if the text or affected JAR set changes.
13. In the final report, immediately after the completed-work list, list every
    versioned JAR deployed to CurseForge or Modrinth with its changelog. When
    both services received the same JAR and changelog, list the file once, name
    both services, and show the changelog once. Split entries only when the
    services received different changelogs.

## Checks

After changing the deploy script or matrix:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-deploy-changed.ps1
```

After changing Gradle release tasks or artifact naming:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-all-versions.ps1
```

For upload work, require selected platform tokens only for a real `-Deploy`. Follow
the secure Windows token-loading path in `docs/release.md` without printing
values.
