# Discord release announcements

Use a small GitHub Actions workflow when the Discord announcement must include
direct links to every `.jar`. Discord's built-in GitHub webhook can announce
repository events, but its message format is fixed and does not guarantee that
release assets appear in the message.

The workflow runs when a GitHub Release is published. In exactly one message, it
posts the release page, the complete release-body prose, and one row per JAR in the form
`[filename.jar](GitHub URL) ([CF](CurseForge file URL), [MR](Modrinth version URL))`.
When the release says Modrinth was skipped, omit its `MR` links.
It uploads the approved release image in that same webhook request so Discord
shows the file inline instead of relying on its remote Markdown URL. An
over-limit announcement fails before posting anything.

## Full release description requirement

Requirements updated 2026-09-11:

- **DR-01:** The Discord announcement must contain the same complete description
  as the published GitHub Release, as well as its GitHub link and all JAR links.
  Use that release's `body` verbatim as the source; do not generate a summary,
  reconstruct notes from commits, or repeat common notes per target/JAR.
- **DR-02:** Preserve the description's wording, order, headings, lists, and
  links. Replace only the standalone approved image Markdown with that exact
  image uploaded as an inline attachment. Discord rendering may differ from
  GitHub.
- **DR-03:** Send exactly one Discord message. If the complete announcement
  exceeds Discord's 2,000-character content limit, fail before the webhook post
  so the release body can be revised and reapproved. Never split, truncate, or
  replace the remaining text with a link or an attachment-only description.
- **DR-04:** Keep the existing complete-JAR-set check. Report success only after
  the single message is confirmed sent. Do not turn release text into unintended
  mentions.

## Implementation and acceptance

`scripts/announce-discord-release.sh` and its
`scripts/test-discord-release-announcement.sh` regression checks reuse the
already-fetched release JSON's `body` field. A null/empty GitHub body has no
description to mirror; keep the title and links without invented notes.

Assemble title/link, complete body prose, then JAR links in that order. Remove
the standalone approved image Markdown from the content and upload that exact
file in the same multipart webhook request. Reject multiple images, unsupported
image filenames, or content over Discord's 2,000 UTF-16-unit limit before the
webhook post. Disable allowed mentions and suppress remote link previews; neither
setting may hide the uploaded attachment.
Use webhook server confirmation (`wait=true`), record the returned message ID,
and report success only for that one confirmed message.
These limits and confirmation behavior come from the
[Discord webhook API](https://docs.discord.com/developers/resources/webhook#execute-webhook);
the release text comes from [GitHub's release response](https://docs.github.com/en/rest/releases/releases#get-a-release).

Acceptance: existing shell tests capture requests with no live posting and prove
short, multiline, image, empty, and exact-limit bodies produce one message; the
approved image is downloaded and included as that message's file attachment;
each JAR URL appears once; and mentions and remote link previews stay disabled.
Over-limit and multi-image bodies must fail before any webhook request. Compare
the sent description text with the release `body`, allowing only removal of the
standalone image Markdown that the attachment replaces. Verify actual Discord
content after the next approved real release, not by publishing a throwaway
release.

## Create the Discord webhook

You need permission to manage webhooks in the Discord server.

1. Open **Server Settings**, then **Integrations** and **Webhooks**.
2. Create a webhook named `AE2 Crafting Time Releases`.
3. Choose the announcements channel and copy the webhook URL.

Treat that URL like a password. Do not put it in this repository, a pull
request, an issue, or a chat message. If it leaks, delete the webhook and create
a new one.

Do not add `/github` to this URL. That suffix is only for Discord's built-in
GitHub message formatter; this workflow sends a normal Discord message.

## Save the URL in GitHub

In the GitHub repository, open **Settings**, **Secrets and variables**,
**Actions**, then create a repository secret:

```text
Name: DISCORD_RELEASE_WEBHOOK_URL
Value: <the copied Discord webhook URL>
```

Repository secrets are not available to pull requests from forks. That does
not affect this workflow because it only runs for a published release.

## How it runs

The tracked `.github/workflows/discord-release.yml` workflow runs on GitHub's
`release: published` event. It checks out the trusted default branch and runs
`scripts/announce-discord-release.sh` with GitHub's read-only job token. The
script uses each asset's public `browser_download_url` value for the direct
download link, resolves the matching CurseForge file URL via the public
cfwidget API using the matrix `curseProjectId`, and builds the matching
Modrinth version URL from the matrix `modrinthProjectId` plus the
`<modVersion>-<loader>-<minecraftVersion>` version number. If the CurseForge
lookup fails, the announcement still sends with the available links. A JAR
that matches no matrix entry is posted with its GitHub link only and logged
as a warning so filename/matrix drift stays visible.

GitHub may start the workflow while `gh release create` is still uploading
assets. The script reads the expected JAR count from
`scripts/release-matrix.json`, waits for the complete set, and fails instead of
sending a partial announcement.

## Verify the next release

Do not publish a throwaway release just to test Discord. After the next approved
real release:

1. Open the **Announce release in Discord** run in the GitHub **Actions** tab and
   confirm it passed.
2. Confirm the announcements channel contains one complete announcement for the
   release and its image appears inline as an uploaded attachment.
3. Compare its JAR links with the assets on the GitHub Release. Every asset whose
   name ends in `.jar` should appear once, kept linked to GitHub, followed by
   its own `CF` and `MR` links for the same mod version, Minecraft version,
   and loader.
4. Open one GitHub link and confirm it downloads the named JAR; open its `CF`
   and `MR` links and confirm they show the matching file/version.
5. Compare the complete Discord description with the GitHub Release body;
   every section and change must appear in the same order, without truncation.

If the job fails, first check that the secret name is exact and that the Discord
webhook still exists and still points to the announcements channel.

## Native webhook limitation

Discord's native GitHub webhook is not an accepted replacement for the full-body
and direct-JAR requirements above. Its existing setup uses:

1. Copy the Discord webhook URL and append `/github`.
2. Add it under GitHub **Settings**, **Webhooks**, **Add webhook**.
3. Select `application/json`, choose individual events, and enable **Releases**.

Do not configure both options for release events, or each release will be
announced twice.

## References

- [Discord: Intro to Webhooks](https://support.discord.com/hc/en-us/articles/228383668-Intro-to-Webhooks)
- [GitHub: Events that trigger workflows](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#release)
- [GitHub: REST API endpoints for release assets](https://docs.github.com/en/rest/releases/assets)
- [GitHub: Creating webhooks](https://docs.github.com/en/webhooks/using-webhooks/creating-webhooks)
