# Native row color implementation plan

Lifecycle and acceptance: [specification](spec.md).
Implementation decisions: [technical design](technical-design.md).

## 1. Implement the row policy

Add the smallest shared decision beside existing row/config policy. Cover the
four client switch combinations and ordinary versus special status semantics.
Use it from both Plan and Status row component paths before assigning/copying
foreground. Keep collecting distinct from numeric TTC despite their common
outer key. Leave global TTC color selection, general text factories, tooltip
paths, and version-specific drawing behavior unchanged (A1-A3).

Do not add a setting or change saved values. Read client appearance preferences
without the server profiling gate; include stored-only/status-free compact
amounts (A1, A4).

## 2. Add focused regression coverage and guidance

Extend the nearest shared row-policy tests for every changed line/branch.
At the existing `shared/src/mc1201Test` component boundary, check both Plan and
Status behavior with ordinary, absent, collecting, waiting, delayed, and blocked
statuses. Assert no explicit ordinary foreground in off/off, preserved special
foreground even with equal custom RGB values, intact text/non-color styles,
and unchanged input components. These shared Minecraft tests are included by
all four target builds (A1-A3).

Reuse existing config copy/save tests to verify switch changes retain custom
colors and opacity; extend only if the new code touches that boundary (A4).
Update configuration guidance in English and Ukrainian GuideME and the GitHub
wiki to explain native ordinary text plus retained special colors (A5).

## 3. Review, create the PR, then check

Review all callers and both `AbstractTableRendererMixin` variants, including
normal and width-scaled draws. Confirm native draw color and shadow arguments
are unchanged, with no effect on excluded surfaces. Review whitespace, links,
locale consistency, and the complete diff before the conventional commit.
Follow the repository hook ordering: no local tests before the hook creates
the implementation PR.

After PR creation, run the selected shared policy/component regression checks
and shared coverage verification. GitHub CI must build all four targets and
run `test jacocoTestReport` with the existing 100% shared line/branch gate.
Record local evidence separately from current-head CI. Java 17, 21, and 25
are the target toolchains; inspect their availability before execution (A5).

## 4. Record verification limits accurately

The issue's visual acceptance remains A6: both screens, all four targets,
supported GUI scales, light/dark/tinted rows, ordinary and compact text,
warnings, and all switch combinations. Include off/on and save/reopen checks
and reviewed captures comparing native text with mod text when that work runs.

For the current 2026-09-26 user run, smoke testing is explicitly excluded.
Do not launch Minecraft, provision a smoke environment, or manufacture
screenshots. Report A6 as unverified by this run and keep that limit in the
canonical scope status. Automated checks may support the implementation handoff
under this instruction; they do not establish visual completion. Record the
implementation PR, tested SHA, actual checks, and remaining evidence before
changing the status. Mark the full scope finished only with its required
evidence or an explicit subsequent change to that acceptance requirement.
