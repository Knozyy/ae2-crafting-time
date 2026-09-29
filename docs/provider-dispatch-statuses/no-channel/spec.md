# NO CHANNEL status

Status: finished

Scope: NO CHANNEL detection, display and original guide delivery.

Implementation: [PR #423](https://github.com/cTux/ae2-crafting-time/pull/423).
Verification: that PR records 12 passing graphs, 846 reviewed captures, and
manual book QA. Automatic visual classification remains REVIEW_REQUIRED.

Planned change: [#488](../../provider-locate/spec.md#planned-red-sky-beam)
includes NO CHANNEL in automatic red plates with output icons. Sky beams require
a successful chat-link locate and share its rainbow borders' 15-second lifetime.
This supersedes NC-06's no-highlight/no-plate boundary only; detection,
freshness, runtime-only evidence and chat policy remain unchanged.

Issue: [#405](https://github.com/cTux/ae2-crafting-time/issues/405).

## Goal

Explain why a craft can be listed in the ME terminal but never start when its
Pattern Provider has no channel. Show `NO CHANNEL` in Crafting Status only
when the server has direct evidence for that cause.

## Player behavior

- **NC-01:** Show normal-weight red `NO CHANNEL` for positive scheduled work after a
  real failed dispatch proves that the provider is powered, its network has
  finished booting, and its channel requirement is unmet. Before the first
  dispatch this replaces `Waiting`, including when no timing samples exist.
  A node being inactive alone is insufficient evidence.
- **NC-02:** Diagnose the exact encoded pattern and all candidates actually
  considered for it. Every candidate must be conclusively blocked by channels
  and the candidate iteration must finish. A successful, busy, unknown,
  unvisited, or differently blocked alternative prevents `NO CHANNEL` for that
  evaluation. An empty lookup remains `NO PROVIDER`.
- **NC-03:** A combined output row may show the warning while another pattern
  or already dispatched batch is active. A successful different pattern does
  not erase a blocked pattern. Across independently proven patterns in a row,
  priority is `NO PROVIDER > NO POWER > NO CHANNEL > LOCKED > INPUT BLOCKED >
  NO TARGET`, followed by existing Waiting/DELAYED/TTC rules. Stored-only
  `NO SPACE` keeps its existing predicate and priority.
- **NC-04:** Reuse the compact badge and tooltip; blocked rows have unknown
  time for sorting and no TTC color. Keep existing total-TTC behavior.
  Tooltip text is fixed below. When both active and scheduled amounts are
  positive, append the existing scheduled-only qualifier.
- **NC-05:** A new successful, unknown, or changed evaluation clears/replaces
  the exact pattern's observation. Otherwise it expires after 20 server ticks
  and disappears on the next normal status refresh. Channel restoration must
  allow normal dispatch/completion. No fixed wall-clock guarantee during lag.
  Job replacement, finish, cancellation, disable, and runtime reload clear it.
  It is runtime-only and must not reappear after save/reopen as any status.
- **NC-06:** Preserve selected-CPU/network isolation, requested-row bounds,
  late-reply protection, and existing remembered-status recovery. No new chat
  warning, provider highlight, locate record, red plate, or sound is created.

| Text | English | Ukrainian |
| --- | --- | --- |
| Label | NO CHANNEL | Немає каналу |
| Explanation | Pattern Providers for this pattern do not have an available channel. | Постачальники цього шаблону не мають доступного каналу. |
| Suggestion | Free a channel or fix the channel route to the providers. | Звільніть канал або виправте маршрут каналів до постачальників. |

Use `text.ae2craftingtime.no_channel` with `.explanation` and `.suggestion`.
Reuse `text.ae2craftingtime.dispatch_status.scheduled_only` unchanged.

## Compatibility and boundaries

- **NC-07:** Cover every [release-matrix](../../../scripts/release-matrix.json)
  target: 1.20.1 Forge/Fabric, 1.21.1 NeoForge, and 26.1.2 NeoForge. Native AE2
  CPUs and the existing AdvancedAE CPU integration are included. An inherited
  provider path participates only when it executes the verified AE2 guard;
  custom overrides without that evidence remain unknown.
- **NC-08:** Keep server ownership, current dependency minimums, sample
  learning, total TTC, saved-data format, and optional-addon behavior.
  Advance the affected client/server protocol boundaries together.
- Do not diagnose CPU or terminal channels. If the CPU cannot execute or the
  terminal cannot operate, this feature does not guarantee a visible badge.
  Missing ingredients or dispatch budget also cannot create a fresh warning.
- Do not interpret a disconnected/absent provider, power loss alone, a network
  reboot, a busy send queue, or a generic failed machine call as missing channels.
  Respect AE2's channel mode; infinite mode must not generate false warnings.
- No cable-count heuristic, topology analyser, automatic repair, new setting,
  saved diagnostic history, or new optional adapter. No additions to Craft
  Plan, Crafting Tree, or ME Requester. No release upload in this task.

## Acceptance criteria

| ID | Required result |
| --- | --- |
| AC-01 | On a powered, booted network, an actual channel-starved provider shows NO CHANNEL after a failed dispatch; freeing its channel route clears the badge and the real job completes. Cover no learned data. |
| AC-02 | Healthy, busy, unknown, and mixed-cause alternatives suppress the new warning for one pattern; empty lookup retains NO PROVIDER. A separate blocked pattern sharing the output keeps its own evidence. |
| AC-03 | Power loss alone, reboot, absent node, missing inputs, exhausted dispatch budget, busy send queue, inactive CPU, and infinite channel mode do not create a false warning. |
| AC-04 | Mixed active/scheduled tooltip, reason priority, sorting, color, bounds, and both translations agree with NC-03/04. Existing statuses, total TTC, learning, and locate/plate behavior remain intact. |
| AC-05 | Fresh recovery, 20-tick expiry, backwards ticks, cancellation/replacement/finish, disable/reload, save/reopen, CPU switches, separate networks, and late replies cannot leak the new reason or turn it into a saved NO PROVIDER/NO POWER. |
| AC-06 | All four native targets and all three applicable AdvancedAE targets have direct English status/recovery evidence; changed pure logic has 100% line/branch coverage and packet/mixin/packaging checks pass. |
| AC-07 | English and Ukrainian GuideME each have a dedicated NO CHANNEL page explaining the cause, differences from Waiting/NO PROVIDER/NO POWER, and channel recovery. Both status indexes and previous/next/return links work; the shared reviewed screenshot, locale parity and packaged pages pass existing guide checks and rendered navigation review. |
| AC-08 | Publish matching English and Ukrainian GitHub Wiki pages and status navigation from the canonical book content. Verify the live pages, internal links and image; retain the source commit, Wiki commit and page URLs. The feature is incomplete until publication is verified. |

See the [research and design](technical-design.md) and
[implementation plan](implementation-plan.md).
