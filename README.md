![It's AE2 Crafting Time! Autocrafting adventure you might want to understand in details.](docs/images/readme-banner.png)

<p align="center">
  Time estimates, progress tracking, and bottleneck diagnostics for<br>
  Applied Energistics 2.
</p>

<p align="center">
  <a href="https://www.curseforge.com/minecraft/mc-mods/ae2-crafting-time"><img src="https://img.shields.io/badge/CurseForge-522d1d?style=for-the-badge&logo=curseforge&logoColor=f68440" alt="Download on CurseForge" height="32"></a>
  <a href="https://discord.gg/mqnRf6jfw"><img src="https://img.shields.io/badge/Discord-5865f2?style=for-the-badge&logo=discord&logoColor=white" alt="Join Discord" height="32"></a>
  <a href="https://github.com/cTux/ae2-crafting-time/releases"><img src="https://img.shields.io/badge/GitHub_Releases-30363d?style=for-the-badge&logo=github&logoColor=white" alt="Download from GitHub Releases" height="32"></a>
  <a href="https://modrinth.com/mod/ae2-crafting-time"><img src="https://img.shields.io/badge/Modrinth-173b27?style=for-the-badge&logo=modrinth&logoColor=1bd96a" alt="Download on Modrinth" height="32"></a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Unofficial_AE2_addon-30363d" alt="Unofficial AE2 addon">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-30363d" alt="MIT license"></a>
</p>

<p align="center">
  <img src="docs/images/crafting-status-overview.png" alt="AE2 crafting status showing remaining-time estimates and blocked inputs" width="705">
</p>

<p align="center">
  Remaining-time estimates and blocked inputs, directly in AE2.<br>
  <a href="docs/images/README.md">See the screenshot gallery</a>
</p>

## From planning to troubleshooting

| 01 Estimate your craft | 02 Track progress | 03 Diagnose delays |
| --- | --- | --- |
| See timing estimates before starting. | Follow remaining time as work runs. | Find stalled recipes and bottlenecks. |

<details>
<summary>More features and integrations</summary>

- Remembers how long your crafts really take, so its time guesses get better the
  more you play.
- Tells you how long a craft will take — for one item or a whole job — right on
  the AE2 screens, so you know when to come back.
- Marks scheduled work as waiting until its first pattern dispatch, so you can
  tell that it hasn't started yet.
- Alerts you when a craft has stalled, so you can fix it instead of waiting
  forever.
- Shows what's slowing you down — like a machine that's too slow, too few
  Pattern Providers, or not enough Crafting Co-Processors — and how to speed it
  up.
- Shows how close its time guesses were after a craft finishes, so you can trust
  the numbers.
- Adds time, colors, totals, and sort buttons to the AE2 crafting screens, so the
  info is easy to read at a glance.
- Lets you Ctrl-Alt-click an entry to erase old, wrong stats for that item.
- Works with Applied Mekanistics chemicals, and adds time hints to ME Requester
  rows and totals, when those mods are installed.
- Only shares summary stats with other players on the server — never your items
  or your base.
- Keeps what it learned inside your world, so it stays smart after you quit and
  come back.

</details>

## Downloads & compatibility

Choose your Minecraft version and loader on the download page.

Crafting Time can be installed on the client, server, or both. Install it on both
sides to see estimates, warnings, and controls while the server records crafts.
With only the server installed, crafting history is still collected and saved,
but clients without the mod see native AE2 screens. With only the client
installed, AE2 screens and crafting work normally; Crafting Time features
become available when you join a server that also has the mod. Singleplayer
uses the integrated server and has the full feature set.

[Supported versions, dependencies, and optional integrations](docs/dependencies.md)

## Documentation

[Player controls](docs/player-controls-and-integrations/spec.md) ·
[Screenshot gallery](docs/images/README.md) ·
[Building](docs/building.md) ·
[Contributing](docs/working-with-project.md)

[Report a bug](https://github.com/cTux/ae2-crafting-time/issues) ·
[Ask a question](https://github.com/cTux/ae2-crafting-time/discussions)

<details>
<summary>All technical documentation</summary>

- [Building It](docs/building.md)
- [Running a Development Client](docs/dev-client.md)
- [Automated UI testing](docs/automated-ui-testing/spec.md)
- [UI test-driver mod](docs/test-driver/spec.md)
- [Repo Layout](docs/repo-layout.md)
- [Working with this project](docs/working-with-project.md)
- [Codex Skills](docs/codex-skills.md)
- [Dependencies and integrations](docs/dependencies.md)
- [Client and modpack coverage](docs/mod-automation-coverage.md)
- [Known Issues](https://github.com/cTux/ae2-crafting-time/issues?q=is%3Aissue%20is%3Aopen%20label%3Abug)
- [Architecture](docs/architecture.md)
- [Feature documentation map](docs/feature-coverage.md)
- [Profiling and diagnostics](docs/profiling-and-diagnostics/spec.md)
- [Player controls and integrations](docs/player-controls-and-integrations/spec.md)
- [AE2 addon integration](docs/ae2-addon-integration/spec.md)
- [Release process](docs/release.md)
- [Server-owned stats design](docs/server-client-stats.md)
- [World save persistence](docs/world-save-persistence.md)
- [Time To Craft plan UI](docs/time-to-craft-plan.md)
- [TTC sorting](docs/ttc-sorting.md)
- [TTC colored text](docs/ttc-colored-text.md)
- [Waiting to start status](docs/waiting-to-start/spec.md)
- [Provider locate](docs/provider-locate/spec.md)

</details>

<details>
<summary>Project health and download statistics</summary>

[![Tests](https://github.com/cTux/ae2-crafting-time/actions/workflows/test.yml/badge.svg?branch=master)](https://github.com/cTux/ae2-crafting-time/actions/workflows/test.yml)
[![Coverage](https://codecov.io/gh/cTux/ae2-crafting-time/graph/badge.svg)](https://codecov.io/gh/cTux/ae2-crafting-time)
[![Modrinth downloads](https://img.shields.io/modrinth/dt/LyDZjvxd?logo=modrinth&label=Modrinth%20downloads)](https://modrinth.com/mod/ae2-crafting-time)
[![CurseForge downloads](https://img.shields.io/curseforge/dt/1591476?logo=curseforge&label=CurseForge%20downloads)](https://www.curseforge.com/minecraft/mc-mods/ae2-crafting-time)
[![GitHub downloads](https://img.shields.io/github/downloads/cTux/ae2-crafting-time/total?logo=github&label=GitHub%20downloads)](https://github.com/cTux/ae2-crafting-time/releases)
[![Issues fixed](https://img.shields.io/github/issues-closed/cTux/ae2-crafting-time?logo=github&label=issues%20fixed)](https://github.com/cTux/ae2-crafting-time/issues?q=is%3Aissue%20is%3Aclosed)
[![Open issues](https://img.shields.io/github/issues/cTux/ae2-crafting-time?logo=github)](https://github.com/cTux/ae2-crafting-time/issues)
[![Discussions](https://img.shields.io/badge/GitHub-Discussions-8250df?logo=github)](https://github.com/cTux/ae2-crafting-time/discussions)
[![Latest release](https://img.shields.io/github/v/release/cTux/ae2-crafting-time?logo=github)](https://github.com/cTux/ae2-crafting-time/releases/latest)
[![License](https://img.shields.io/github/license/cTux/ae2-crafting-time)](LICENSE)

</details>

<details>
<summary>Development disclosure</summary>

Yes, AI helps me write this mod. I'm not really a Java guy, if you know what I
mean, but I have enough engineering experience to keep it from turning into
vibe-coded spaghetti. I direct and review the work, and I expect the code to be
tested, maintainable, scalable, and reusable. I hope that makes it clear where
AI fits into the project.

The mod does not use generative AI while running and does not include
AI-generated in-game visual assets. The README branding is separate.

</details>

---

<p align="center">
  Unofficial addon. Not affiliated with or endorsed by the Applied Energistics 2 team.<br>
  Available under the <a href="LICENSE">MIT License</a>.
</p>
