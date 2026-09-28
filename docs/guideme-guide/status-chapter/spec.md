# Status Chapter Specification

Status: finished

Scope: Original status chapter and NO CHANNEL delivery.

Implementation: [PR #339](https://github.com/cTux/ae2-crafting-time/pull/339), [PR #423](https://github.com/cTux/ae2-crafting-time/pull/423).
Verification: resource/CI checks and manual book QA recorded in those PRs.
The later Recurrent book/wiki scope is tracked below.

Issue: [#305](https://github.com/cTux/ae2-crafting-time/issues/305)

## Goal

Add **Chapter 3: Statuses** so every state currently displayed by AE2 Crafting
Time has a detailed page explaining meaning, location, recovery, clearing, and
limits, with a real screenshot and useful cross-links.

## Pages and display priority

`statuses/index.md` keeps these eleven pages in their existing order and appends Recurrent.
The landing page separates Crafting Plan diagnostics from running-job priority:

| Page | Visible state | Required explanation |
| --- | --- | --- |
| `no-space.md` | `NO SPACE` | Stored output cannot return to ME storage; free or add writable storage. |
| `no-provider.md` | `NO PROVIDER` | Scheduled patterns have no connected provider; restore provider/pattern. |
| `no-power.md` | `NO POWER` | Network cannot power next dispatch; increase generation or stored energy. |
| `no-channel.md` | `NO CHANNEL` | The provider has no usable channel; check its network connection and channel capacity. |
| `locked.md` | `LOCKED` | Provider locks/redstone prevent the next batch; satisfy or disable the lock. |
| `input-blocked.md` | `INPUT BLOCKED` | Destination refuses inputs; inspect space, sides, filters, and blocking mode. |
| `no-target.md` | `NO TARGET` | Provider has no usable destination; connect/configure a compatible target. |
| `waiting.md` | `Waiting` | Output is scheduled but its first pattern has not dispatched. |
| `delayed.md` | `DELAYED` | Active work stopped producing output beyond its learned threshold. |
| `no-data-yet.md` | `No data yet` | No usable retained timing sample exists yet. |
| `estimated.md` | TTC such as `~12s` | A usable estimate exists; explain amount, total, confidence, and uncertainty. |
| `recurrent.md` | `Recurrent` | Crafting Plan has a proven recipe self-dependency; check seed ingredients and alternative recipes. |

Stored-only NO SPACE wins first. For scheduled work, current reason precedence is
NO PROVIDER, NO POWER, NO CHANNEL, LOCKED, INPUT BLOCKED, NO TARGET, then Waiting, DELAYED,
missing history, and estimate behavior. Pages explain mixed active/scheduled
rows without implying active batches share the scheduled blocking reason.

Features is Chapter 2; Statuses is Chapter 3.

## Page requirements

Every status page includes the exact English/Ukrainian label, affected rows and
precedence, observed condition, ordered recovery checklist, clearing boundary,
false-positive/retention limits, an exact screenshot with alt text/caption, and
links to the landing page, relevant feature, and adjacent/related status.

The landing page provides a compact label-to-page decision path without
inventing machine diagnoses that the mod has not verified.

## Screenshots

Reuse reviewed gallery images for NO SPACE, NO PROVIDER, NO POWER, Waiting,
DELAYED, No data yet, and TTC. Capture focused NO TARGET, INPUT BLOCKED, and
LOCKED images through the existing prepared scenario. Package images locally,
keep native pixels, show the exact label/tooltip, and exclude accounts,
addresses, chat, coordinates, unrelated worlds, and test controls.

## Languages and compatibility

English and Ukrainian use identical paths, positions, image references, and
links; Ukrainian is a complete natural translation. Both themes remain readable.
All twelve statuses and the chapter cover all four supported targets. Reconcile the
approved scope first if implementation finds target drift.

This changes guide resources and existing transformations only: no detection,
precedence, thresholds, packets, persistence, config, recipes, or dependencies.

## Non-goals

- Adding/changing statuses, tooltips, recovery actions, or notifications.
- AE2-native-only statuses or machine-specific guesses.
- Video, animation, remote media, custom widgets, or another guide system.

## Acceptance criteria

| ID | Observable result |
| --- | --- |
| S1 | Chapter 3 links to exactly the twelve listed states, preserving the first eleven positions, including NO CHANNEL, and separating Recurrent from running-job priority. |
| S2 | Every state emitted by the mod maps once; no planned/native-only state is presented as shipped. |
| S3 | Every page satisfies meaning, scope, recovery, clearing, limits, screenshot, and link rules. |
| S4 | Precedence and mixed active/scheduled explanations match current renderer/data. |
| S5 | Introduction, Chapter 1, Features, Statuses, and children have no orphan/dead end. |
| S6 | English/Ukrainian paths, metadata, images, labels, and links match. |
| S7 | Images are packaged, readable, exact, and private-data-free. |
| S8 | All four distributions contain target-correct resources with no guide regressions. |
| S9 | Validation rejects missing page, translation, image, return link, stale label, or wrong position. |
| S10 | Docs/link checks pass and reviewed clients cover both renderer paths. |

See the [technical design](technical-design.md) and
[implementation plan](implementation-plan.md).

## Recurrent addition (#412)

Status: in-progress

Scope: Recurrent book and wiki delivery (#412).

Implementation: [PR #479](https://github.com/cTux/ae2-crafting-time/pull/479) is merged and CI passed.
Verification: complete book/runtime and wiki acceptance is not recorded in that PR.

Follow the [existing recurrence contract](../../recurrent-crafting-status/spec.md)
for detection and quantities. This addition documents that behavior; it does not
change it. The page uses `Recurrent` / `Циклічне`, explains the normal-weight red
`Recurrent: <amount>` label and matching hover explanation, and places it in
Crafting Plan before submission, not a running CPU's Crafting Status.

That normal-weight description matches the shipped presentation. The planned
[#496 warning style](../../recurrent-crafting-status/spec.md#planned-warning-style-496)
replaces it with normal-weight red text and the existing compact dark badge. Update both
locale pages, wiki source, and the shared screenshot when that UI change has
reviewed runtime evidence; a planning-docs merge must not claim it is shipped.

The regression reported in [#408](https://github.com/cTux/ae2-crafting-time/issues/408)
was fixed by merged [#410](https://github.com/cTux/ae2-crafting-time/pull/410).
Older investigation notes in the recurrence documents are historical, not an
open prerequisite or a request to change detection in this issue.

Use a simple A -> B -> A example and mention direct and longer loops. Explain
that an ordinary shortage stays Missing, and a rejected circular alternative
alone is not proof. The number remains AE2's total missing amount, even when a
row combines ordinary and recurrent shortages. A successful plan with a usable
seed or alternative has no Recurrent row; having the requested output in storage
is not a guarantee that AE2 can use it as a seed.

Order the recovery checklist: inspect the affected ingredient and hover help;
follow its patterns to find the loop; supply a usable seed or choose/correct a
recipe that breaks it; calculate the plan again. Do not promise an automatic
repair. Explain that recalculation replaces the diagnosis and old plans do not
carry it across menus or networks. Timing samples are not required, and TTC
colors/order do not control this label. Separate Crafting Tree and ME Requester
screens are outside this native-plan diagnostic.

| ID | Observable result |
| --- | --- |
| S11 | Both locales explain Recurrent's location, label, loops, quantities, recovery, clearing, and limits above. |
| S12 | Recurrent has position 11 after TTC estimate at position 10, is reachable from the landing and TTC estimate pages, and is explicitly outside running-job priority. |
| S13 | A reviewed, packaged PNG shows a real Recurrent row; caption/alt text describe the fixture without claiming every loop item becomes a missing row. |

S3 and S5-S10 also apply to the new page. This planning merge leaves #412 open;
book resources and their runtime verification belong to its implementation.

## GitHub wiki parity (#412)

The same change must reach the published GitHub wiki in English and Ukrainian.
The initial wiki publication from [#411](https://github.com/cTux/ae2-crafting-time/issues/411)
is complete; updating it is part of #412, not a new publication project. Keep the
GuideME Markdown as the canonical content. Mirror the Recurrent explanation,
reviewed screenshot, captions, and changed status navigation, adapting only the
format and links needed by GitHub wiki Markdown.

| ID | Observable result |
| --- | --- |
| S14 | Published English and Ukrainian Recurrent wiki pages match the book's meaning and reviewed image, are reachable from their status landing and estimate pages, and have working return and related links. |

Verify both published wiki pages and their navigation/images on GitHub before
completing #412. A local wiki checkout or book-only merge does not satisfy S14.
