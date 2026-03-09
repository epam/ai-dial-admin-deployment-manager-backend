---
name: create-pr
description: Create a GitHub pull request with Conventional Commits title and structured description. Use when creating PRs, opening pull requests, or submitting code for review.
argument-hint: [base branch (optional)]
---

# PR Creation Skill

Create a pull request for the current branch.

If `$ARGUMENTS` is provided, use it as the base branch. Otherwise, default to the repository's development branch.

## Workflow

1. **Gather context** — run these in parallel:
   - `git status` (never use `-uall`)
   - `git diff` to see staged and unstaged changes
   - `git log --oneline` for recent commit style
   - Check if the current branch tracks a remote and is up to date
   - `git log <base-branch>..HEAD --oneline` and `git diff <base-branch>...HEAD` to see all commits and changes since diverging from the base branch

2. **Determine the issue number** — look at branch name and commit messages for issue references (e.g. `#123`, `issue-123`). If no issue number can be found, **ask the user** for the related GitHub issue number before proceeding.

3. **Draft the PR title** — use [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) format:
   - `feat: ...` for new features
   - `fix: ...` for bug fixes
   - `chore: ...` for maintenance
   - `docs: ...` for documentation
   - `refactor: ...` for refactoring
   - `test: ...` for tests
   - Add `!` after the type for breaking changes (e.g. `feat!: ...`)
   - Add a scope if appropriate (e.g. `feat(auth): ...`)
   - Keep the title under 70 characters

4. **Draft the PR description** using this exact template:

```
### Applicable issues

<!-- Please link the GitHub issues related to this PR (You can reference an issue using # then number, e.g. #123) -->
- fixes #<ISSUE_NUMBER>

### Description of changes

<!-- Please explain the changes you made right below this line. -->
<SUMMARY_OF_CHANGES>

### Checklist

<!-- [Place an '[X]' (no spaces) in all applicable fields. Please remove unrelated fields.] -->

- [X] Title of the pull request follows [Conventional Commits specification](https://www.conventionalcommits.org/en/v1.0.0/)

By submitting this pull request, I confirm that my contribution is made under the terms of the Apache 2.0 license.
```

5. **Show the draft to the user** — present the title and description for review. Ask if they want to adjust anything before creating.

6. **Create the PR**:
   - Push the branch to remote with `-u` if needed
   - Create the PR using `gh pr create` with the title and body. Use a HEREDOC for the body.

7. **Return the PR URL** to the user.

## Important rules

- ALWAYS ask the user for the issue number if it cannot be determined from the branch name or commits.
- ALWAYS show the draft title and description to the user before creating the PR.
- ALWAYS use Conventional Commits format for the title.
- Fill in `<SUMMARY_OF_CHANGES>` with a concise description of what the PR does, based on the commits and diff.
- The checklist item for Conventional Commits title should be pre-checked `[X]`.
- Do NOT push or create the PR without user confirmation.
