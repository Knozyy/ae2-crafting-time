# Repository Workflow

- Work on a branch. Complete each fix or feature as one conventional commit.
- Rebase only when the user explicitly requests it. A rebase does not require
  a full smoke run; choose verification from the actual changes and task scope.
- Run `scripts/setup-git.ps1` once per clone. Its post-commit hook pushes each
  commit and creates or updates the branch PR.
- Do not run local tests before the hook creates the PR. Afterward, run only the
  checks required by the applicable skill and report GitHub CI separately.
- Optional client presentation mixins must preserve vanilla behavior if their
  injection point changes: use `require = 0`, and verify both the remapped
  production target and a client startup when adding or changing one. Do not
  use this fallback for required gameplay or data hooks.
- When work reveals another issue, handle it based on its impact:
  - Fix it as part of the current task when it is related.
  - Fix it when it is unrelated but blocks the current task.
  - When it is unrelated and non-blocking, leave it out of the current change
    and create a detailed GitHub issue for it.
- Let the discovered repository skills own development, writing, release, issue,
  and Prism workflows. Do not duplicate their detailed rules here.

## Issue Labels

- Give each new issue exactly one `priority/<low|medium|high>` label and one
  `effort/<low|medium|high>` label. Reuse existing labels.
- Use `backlog` for deferred work. It is an implementation hold, even when its
  documentation is ready. Remove the hold only when the user authorizes starting it.
- Keep `in/progress` for active work, `recurrent` for recurring audits, and
  `display` for an addon UI that needs a TTC hook, when applicable.
- Do not create or apply area, issue-type, or LLM-model labels. Do not prefix
  issue titles with a type such as `[Bug]` or `[Feature]`.
- Preserve unrelated labels when updating an issue. Apply the same naming rules
  to open and closed issues.
