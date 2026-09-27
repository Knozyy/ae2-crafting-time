# Configuration comments implementation plan

Lifecycle: see the [canonical scope status](spec.md).

## 1. Deliver documentation before implementation

Use the reviewed investigation base `8ddcc38dc15829242c0a915c91a92f9b3f20b16b` or its verified descendant containing these documents. In Step 5, deliver this spec, design and plan together with the English/Ukrainian GuideME configuration guidance. Follow the repository branch, one conventional commit, hook-created PR and test-order rules. Use `Refs #590` for the documentation PR.

Explain current paths, ownership, server-file creation on world startup, client-file creation on Done, known legacy-key migration and precedence, and live Done versus external-edit reload behavior. Fix the stale claim that sample-window/outlier changes are only applied when a profiler is created. Preserve the newly shipped recurrent-detection default-off guidance. Do not describe generated comments as already delivered.

Refresh the separate wiki checkout, review matching changes to `Feature-Configuration.md` and `Ukrainian-Feature-Configuration.md`, then publish under the parent-granted Step 5 authority. Preserve unrelated wiki work. Read back both live pages and record wiki commit, canonical guide revision and URLs. The docs PR must merge and the issue must receive verified document links before implementation starts. No issue-body revision is needed.

## 2. Document shared serialized output

On the fresh implementation branch from the delivered documentation base:

1. Read both config writers, models and runtime callers again, including any changes since investigation.
2. Add file headers and a meaningful full-line comment block before every existing setting. Keep assignment keys, order and formatting unchanged.
3. Explain defaults from the current model, boolean off behavior, numeric ranges/units, optional integration scope and conditional color behavior. Document `waitingColor` as retained without a current rendering effect.
4. Preserve load/save timing, parser behavior, atomic replacement, defaults, permissions, packets and migration.
5. Review every comment against its runtime consumer; ensure recurrent detection is documented as off by default and external editing is distinguished from live Done.

Do not edit loader adapters, introduce inline comments after values, or add a configuration library. Keep any shared comment helper limited to what both existing writers need.

## 3. Strengthen existing regression checks

Extend `ClientConfigFileTest` and `ServerConfigFileTest` to verify:

- Every assignment has its own nonempty explanatory comment block, including numeric and color keys; the assignment set has no missing or extra keys.
- Default output matches model defaults, including recurrent detection off.
- Non-default booleans, numeric values, sort modes, opacity and all colors survive save/load, and another save still emits complete comments.
- Missing server generation retains known migrated values and preserves legacy bytes.
- Active files override common-file values; existing client/server bytes stay unchanged on load.
- Comment lines are ignored by the existing reader and malformed-field recovery remains covered.

Reuse existing tests for invalid input and I/O recovery. Change the server default test's physical-line count into a setting count. Avoid tests that merely assert the exact wording of prose.

## 4. Review, create the PR, then verify

Review the complete implementation against A1-A6 before its single conventional commit. The hook creates or updates the implementation PR with `Closes #590`. Do not run local tests before that PR exists.

After PR creation, run `./gradlew.bat test jacocoTestReport checkGuideResources` and `./scripts/build-all-versions.ps1 -JavaHome $env:JAVA_HOME_21`. Verify Forge 1.20.1 and Fabric 1.20.1 with Java 17, NeoForge 1.21.1 with Java 21, and NeoForge 26.1.2 with Java 25; Gradle runs with Java 21. Keep local outcomes separate from GitHub CI, and bind results to the final PR head.

Inspect representative generated files in the test artifacts or temporary test output and review both locale guides plus published wiki text. No Minecraft UI campaign is required while the diff remains generated comments and explanatory documentation. If implementation changes runtime or loader behavior, stop and revise the documented verification scope before running it.

## Criteria mapping

| Criterion | Change | Check |
| --- | --- | --- |
| A1 | Shared writer comments for all feature/numeric/color keys | Complete-assignment regression checks and semantic review against consumers |
| A2 | Headers and Step 5 GuideME/wiki guidance | Generated-header inspection; locale/path/legacy review |
| A3 | Preserve serialization and save behavior | Non-default round trips and repeated saves; full diff review of boundaries |
| A4 | Preserve load/migration behavior | Existing-byte, legacy-byte and precedence tests |
| A5 | Step 5 English/Ukrainian GuideME and wiki delivery | `checkGuideResources`, meaning review, wiki revision and live readback |
| A6 | Shared tests and all-target consumption | All-module tests, 100% shared coverage, all-target build and current-head required CI |

## Completion

Complete only after documentation delivery, implementation review, selected checks, current-head CI and verified implementation merge. Reconcile the canonical spec status with those evidence links, verify normal issue closure, and remove only this run's owned `in/progress` claim through the issue workflow. Keep unrelated findings out unless they block or belong to this scope.
