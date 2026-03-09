---
name: create-issue
description: Create a GitHub issue (Bug Report or Feature Request) for the current project. Use when filing bugs, reporting issues, or requesting features.
argument-hint: [bug|feature] <brief title>
---

# GitHub Issue Creation Skill

Create a GitHub issue for the repository `epam/ai-dial-admin-deployment-manager-backend`.

If `$ARGUMENTS` starts with `bug` or `feature`, use that as the issue type. Otherwise, **ask the user** whether this is a **Bug Report** or a **Feature Request**.

## Workflow

1. **Determine issue type** from `$ARGUMENTS` or by asking the user.

2. **Get the project version** — read `build.gradle` and extract the `version` field (e.g. `0.15.0-rc`).

3. **Gather context** — investigate the codebase thoroughly to understand the issue. Use multiple approaches:
   - Search for relevant code, configs, logs, tests, and error messages
   - Read related source files to understand current behavior
   - Check recent commits and changes that might be related
   - Look at existing tests to understand expected behavior
   - Consider the issue from different angles: user perspective, developer perspective, system perspective
   - If it's a bug, try to identify the root cause or narrow down the area of code involved
   - If it's a feature, understand current architecture to describe where the feature fits

4. **Draft the issue** using the appropriate template below.

5. **Determine labels and metadata**:
   - **Type label**: `bug` for bug reports, `enhancement` for feature requests.
   - **Priority label** (one of): `Priority-Low`, `Priority-Medium`, `Priority-High`. Guess the appropriate priority based on context, but **always confirm with the user**.
   - **Severity label** (one of): `Severity-Low`, `Severity-Minor`, `Severity-Major`, `Severity-Critical`. Guess the appropriate severity based on context, but **always confirm with the user**.
   - **Milestone**: Use `release-<MAJOR>.<MINOR>` format (version from `build.gradle` without the bugfix/patch part, e.g. version `0.15.0-rc` → milestone `release-0.15`). **Always confirm the milestone with the user**.
   - **Project**: AI DIAL Admin (project number 68, added via `gh project item-add` after issue creation)

6. **Show the draft to the user** — present the full issue title, body, labels, milestone, and project for review. Ask if they want to adjust anything before creating.

7. **Create the issue** using `gh issue create` on `epam/ai-dial-admin-deployment-manager-backend` with the title, body, labels, and milestone. Use a HEREDOC for the body. Example:
   ```
   gh issue create --repo epam/ai-dial-admin-deployment-manager-backend \
     --title "the title" \
     --label "bug" --label "Priority-Medium" --label "Severity-Minor" \
     --milestone "release-0.15" \
     --body "$(cat <<'EOF'
   issue body here
   EOF
   )"
   ```

8. **Add to project** — after creating the issue, add it to the **AI DIAL Admin** project (number 68):
   ```
   gh project item-add 68 --owner epam --url <ISSUE_URL>
   ```

9. **Set the issue type** — use the `mcp__github__issue_write` tool to set the GitHub issue type. The available types for the `epam` org are: `Bug`, `Feature`, `Task`. Set `Bug` for bug reports and `Feature` for feature requests. Example:
   ```
   mcp__github__issue_write(method="update", owner="epam", repo="ai-dial-admin-deployment-manager-backend", issue_number=<NUMBER>, type="Feature")
   ```

10. **Return the issue URL** to the user.

---

## Bug Report Template

**Title:** Short, descriptive summary of the bug

**Body:**

```
### Name and Version

[BE] <VERSION_FROM_BUILD_GRADLE>

### What steps will reproduce the bug?

<DETAILED_REPRODUCTION_STEPS>

### What is the expected behavior?

<EXPECTED_BEHAVIOR>

### What do you see instead?

<ACTUAL_BEHAVIOR>

### Additional information

<ANY_EXTRA_CONTEXT — e.g. relevant code snippets, stack traces, environment details, related issues, possible root cause>
```

---

## Feature Request Template

**Title:** Short, descriptive summary of the feature

**Body:**

```
### Name and Version

[BE] <VERSION_FROM_BUILD_GRADLE>

### What is the problem this feature will solve?

<CLEAR_DESCRIPTION_OF_THE_PROBLEM — pain points, use cases, limitations of the current behavior>

### What is the feature you are proposing to solve the problem?

<PROPOSED_FEATURE — what it does, how it works, which components are affected>

### What alternatives have you considered?

<OTHER_APPROACHES_CONSIDERED_AND_WHY_THEY_WERE_REJECTED>
```

---

## Important rules

- ALWAYS read `build.gradle` to get the current version for both bug reports and feature requests.
- ALWAYS gather as much context as possible from the codebase before drafting — search code, read files, check tests, review recent commits.
- ALWAYS approach the issue from multiple perspectives (user, developer, system) to write a thorough description.
- ALWAYS show the full draft (title, body, labels, milestone, project) to the user before creating the issue.
- ALWAYS confirm Priority, Severity, and Milestone with the user before creating.
- ALWAYS apply the correct type label: `bug` for bug reports, `enhancement` for feature requests.
- ALWAYS set the GitHub issue type after creation: `Bug` for bug reports, `Feature` for feature requests (using `mcp__github__issue_write`).
- ALWAYS add the issue to the AI DIAL Admin project (number 68) using `gh project item-add`.
- Do NOT create the issue without user confirmation.
- Keep titles concise but descriptive.
- For bug reports, include concrete reproduction steps — not vague descriptions.
- For feature requests, explain both the "what" and the "why".
