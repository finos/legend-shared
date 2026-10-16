# legend-release

Claude Code skills for releasing the Legend stack. They work from a checkout of
any of the five release repos — `legend-shared`, `legend-pure`, `legend-engine`,
`legend-sdlc` and `legend-depot` — and live here so there is one copy to
maintain rather than five.

## Install

From any Claude Code session:

```
/plugin marketplace add finos/legend-shared
/plugin install legend-release@legend
```

Run `/plugin marketplace update legend` to pick up changes merged here.

## Skills

Skills are namespaced by the plugin, e.g. `/legend-release:release-preflight`.
They also trigger on their own from requests like "can I release 4.143.1" or
"why did the release fail".

| Skill | Purpose |
|---|---|
| `release-preflight` | Verify a branch is releasable before spending hours of CI — tag free locally and on origin, version not already on Central, pom version correct, no stray release-plugin commits |
| `release-dispatch` | Preflight, confirm, dispatch `release.yml`, then watch the run and hand off on failure |
| `release-triage` | Pull the failed jobs of a run and separate real code failures from environmental ones |
| `release-cleanup` | Inspect what a failed release left behind, then undo it surgically |
| `release-backport` | Backport a fix to a release branch without dragging maven-release-plugin commits with it |
| `release-train` | Sequence a stack release: shared → pure → engine → sdlc → depot |
| `post-release-verify` | Confirm a release actually landed: readable on Central, tag correct, branch at the right next SNAPSHOT |

## Reference files

- `skills/release-preflight/references/repo-matrix.md` — the per-repo release
  mechanisms, naming conventions, version properties and workflows to avoid.
  The five repos use four different release mechanisms; every skill reads this
  first.
- `skills/release-triage/references/known-failures.md` — failure signatures seen
  in real releases, with a verdict (real or environmental) and an action for
  each. Add new ones here by PR as they are diagnosed; the copy in an installed
  plugin is overwritten on update.
