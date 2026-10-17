# Contributing

## Branch naming

```
feature/<short-kebab-name>          one user story       feature/create-view-offer
bugfix/<issue>-<short-kebab-name>   one defect           bugfix/12-send-without-approval
chore/<short-kebab-name>            tooling, docs, CI    chore/jenkins-pipeline
```

- Branch from `develop`, never from `main`.
- One backlog item per branch. If the branch grows a second concern, split it.
- Delete the branch after the pull request is merged.

## Commit messages

```
type(scope): subject

[optional body — why, not what]
```

| Type | Use for |
|---|---|
| `feat` | A new capability visible to a user |
| `fix` | A defect correction |
| `test` | Adding or correcting tests |
| `docs` | Documentation only |
| `chore` | Build, tooling, CI configuration |
| `refactor` | Behaviour-preserving restructuring |

Rules:
- Subject in the imperative mood, lower case, no trailing full stop, 72 characters or fewer.
- Scope is the area touched: `domain`, `security`, `ui`, `workflow`, `search`, `dashboard`,
  `build`, `ci`, `docker`, `ansible`, `repo`.
- One logical change per commit.

Examples:

```
feat(workflow): enforce manager approval before an offer can be sent
fix(search): keep active filters when moving between pages
test(selenium): cover the approve-and-send journey
chore(ci): pin JAVA_HOME to JDK 17 in the pipeline
```

## Pull requests

1. Open the PR against `develop`.
2. Fill in the template: what changed, which backlog item, how it was tested.
3. Link the issue with `Closes #n`.
4. The PR must carry at least one substantive review comment before merge.
5. The Jenkins build for the branch must be green.
6. Squash only when the branch has noise commits; otherwise keep the history.

## Definition of Done

A change is done when every item in
[docs/TASK-02-agile-planning-and-devops-workflow.md §7](docs/TASK-02-agile-planning-and-devops-workflow.md)
is satisfied — code, review, build, test, deployment and documentation.
