---
name: review-pr
description: Review a GitHub pull request with inline comments on specific diff lines. Use when reviewing PRs, analyzing pull request changes, or providing code review feedback.
argument-hint: [PR URL]
---

# PR Review Skill

Review the pull request at $ARGUMENTS.

## Workflow

1. **Fetch PR details** using `mcp__github__pull_request_read` with methods `get` and `get_diff` in parallel to understand the full change.
2. **Explore relevant existing code** to understand patterns, conventions, and context around the changed files. Use the Explore agent or read files directly.
3. **Analyze the diff** thoroughly for:
   - Code quality, readability, and style consistency with the existing codebase
   - Bugs, potential NPEs, edge cases, and error handling issues
   - Code duplication or missed reuse opportunities
   - Test coverage and test style consistency
   - Security concerns
   - Naming, formatting, and minor issues (trailing newlines, etc.)
4. **Create a pending review** using `mcp__github__pull_request_review_write` with method `create` (no `event` parameter — this creates a pending review).
5. **Add inline comments** on specific diff lines using `mcp__github__add_comment_to_pending_review`. Place each comment on the most relevant line(s) of the diff. Use `startLine`/`line` for multi-line comments where appropriate.
6. **Ask the user** how they want to submit the review. Present options:
   - **COMMENT** — neutral feedback
   - **APPROVE** — approve the PR
   - **REQUEST_CHANGES** — request changes
   - **Keep pending** — leave it as pending for further editing in GitHub UI

## Important rules

- NEVER submit the review automatically. ALWAYS ask the user which event to use.
- Prefer inline comments on specific lines over a single summary comment.
- Keep comments concise and actionable. Include code suggestions where helpful.
- Match the tone to the severity: bugs and NPEs deserve strong language, style nits can be gentle.
