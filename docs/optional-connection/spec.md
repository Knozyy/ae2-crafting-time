# Optional client and server installation

Status: in-progress

Implementation: [PR #545](https://github.com/cTux/ae2-crafting-time/pull/545).
Verification remains open until current-head CI and the connected runtime matrix
meet the acceptance criteria below.

Scope: Optional Crafting Time installation on either side of a connection.

Issue: [#537](https://github.com/cTux/ae2-crafting-time/issues/537)

Planning: [Technical design](technical-design.md) and [implementation plan](implementation-plan.md).

Verification scope: Add a focused observation mode to the existing test driver
and connected runner, as defined in the design. Install production and driver only
on the sides marked installed; inspect absent clients through the VM framebuffer.
Complete and verify this prerequisite before the connected campaign.

## Behavior

Crafting Time must be optional on either side of an otherwise compatible AE2
connection. This does not relax Minecraft, loader, AE2, or other mods' requirements.

| Client | Server | Result |
| --- | --- | --- |
| Installed | Installed | Existing profiling, UI, packets, warnings and options work. |
| Absent | Installed | Login works; server collection and saved history remain usable; no Crafting Time payload goes to this client. |
| Installed | Absent | Login works; no Crafting Time payload goes to the server; AE2 displays and controls retain native behavior. |
| Absent | Absent | Native AE2 behavior remains unchanged. |

Client-only fallback covers totals, row estimates, CPU ordering, sort controls,
amount formatting, tooltips, status text, locate/reset/detail actions and optional
integration UI. Saved local preferences remain available through the loader's
configuration entry, but they cannot enable connection-dependent behavior.
Server settings are unavailable without a supporting server.

Singleplayer with the mod installed continues through the integrated server's
normal networking path. Connecting to a supported server re-enables features
without restarting the client or rewriting its preferences.

## Acceptance criteria

- OC-1: Both-installed, server-only, client-only and neither-installed logins
  succeed on Forge 1.20.1, Fabric 1.20.1, NeoForge 1.21.1 and NeoForge 26.1.2.
- OC-2: Every custom outbound payload checks peer support before transmission;
  an unsupported peer receives none, including login and unsolicited updates.
- OC-3: Unsupported-server UI matches native AE2, including the amount-formatting
  paths and optional integrations, with no stale estimate or actionable mod control.
- OC-4: Supported to unsupported to supported transitions in one client process,
  plus disconnect/reconnect, reset capability, stats, network amounts, CPU state,
  highlights and request cooldowns. Late work from an old connection cannot populate
  the new connection's state.
- OC-5: Both-installed and integrated singleplayer preserve existing behavior;
  server-only crafts still populate and retain history across a server restart.
- OC-6: Existing incompatible-present protocol handling, bounded codecs, request
  authorization and persisted data remain intact. Absence is not version compatibility.
- OC-7: Installation guidance, the GuideME book and GitHub wiki explain these
  combinations and when Crafting Time features are available.
- OC-8: Evidence for all sixteen connected cells records exact artifacts, login,
  payload observation, UI/profiling results, timestamps and cleanup. Source inspection
  or a successful login alone does not prove a cell passed.

## Boundaries

No new gameplay feature, packet format or dependency is required. Fabric 1.20.1
must replace its custom guide item with a marked vanilla book to avoid a registry
sync disconnect for native clients; saved stacks using the old custom item ID
cannot be migrated after that ID is removed. Back up worlds and player data
before upgrading because those books may be lost when inventories load.
Capability is a connection fact, not a user preference: users cannot
override an absent peer into accepting packets. Existing feature switches remain.
Do not weaken tests, coverage or handshake protection to make absence work.
