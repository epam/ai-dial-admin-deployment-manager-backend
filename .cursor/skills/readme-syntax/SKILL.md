---
name: readme-syntax
description: Answers questions about README and Markdown syntax. Covers basic Markdown (headings, bold, italic, lists, links, images, code blocks), GitHub-Flavored Markdown (tables, task lists, footnotes, alerts), README structure best practices, and inline HTML (collapsible sections, alignment). Use when the user asks about README formatting, Markdown syntax, how to write or improve a README, shields/badges, or any .md file content.
---

# README Syntax

## README Structure — Recommended Section Order

A well-structured README typically follows this order:

1. **Project title + badge row** (build, version, license)
2. **Short description** — one sentence, what + why
3. **Table of contents** (optional, for long READMEs)
4. **Features** (bullet list of highlights)
5. **Prerequisites / Requirements**
6. **Installation**
7. **Usage** (with code examples)
8. **Configuration** (env vars, config files)
9. **API reference** (or link to docs)
10. **Contributing**
11. **License**

---

## Basic Markdown

### Headings
```markdown
# H1
## H2
### H3
```

### Emphasis
```markdown
**bold**   _italic_   ~~strikethrough~~   `inline code`
```

### Lists
```markdown
- Unordered item          1. Ordered item
  - Nested item              2. Second item
```

### Links & Images
```markdown
[Link text](https://example.com)
![Alt text](path/to/image.png)
[![Badge](image-url)](link-url)   ← clickable badge
```

### Code
````markdown
`inline`

```python
# fenced block with language
print("hello")
```
````

### Blockquote
```markdown
> This is a blockquote.
```

### Horizontal rule
```markdown
---
```

---

## GitHub-Flavored Markdown (GFM)

### Tables
```markdown
| Column A | Column B | Column C |
|----------|:--------:|---------:|
| left     | center   |    right |
```
Alignment: `:---` left, `:---:` center, `---:` right.

### Task lists
```markdown
- [x] Completed task
- [ ] Pending task
```

### Footnotes
```markdown
Text with a footnote.[^1]

[^1]: Footnote content here.
```

### GitHub Alerts (callout blocks)
```markdown
> [!NOTE]
> Useful information.

> [!TIP]
> Helpful advice.

> [!IMPORTANT]
> Key information users must know.

> [!WARNING]
> Dangerous action ahead.

> [!CAUTION]
> Negative consequences to watch for.
```

### Auto-linked references
- `#123` → links to issue/PR
- `@username` → mentions a user
- Full commit SHA → links to commit

---

## Inline HTML

### Collapsible section
```html
<details>
<summary>Click to expand</summary>

Content goes here. Leave a blank line after `<summary>` for Markdown to render inside.

</details>
```

### Center-align content
```html
<div align="center">

![Logo](logo.png)

# Project Title

</div>
```

### HTML comments (hidden in rendered output)
```html
<!-- This text is invisible in the rendered README -->
```

### Line break
```html
First line.<br>Second line on new line.
```

---

## Shields / Badges

Use [shields.io](https://shields.io) for dynamic badges:

```markdown
![License](https://img.shields.io/badge/license-MIT-blue)
![Build](https://img.shields.io/github/actions/workflow/status/owner/repo/ci.yml)
![Version](https://img.shields.io/github/v/release/owner/repo)
```

Static badge pattern:
```
https://img.shields.io/badge/<LABEL>-<MESSAGE>-<COLOR>
```
Colors: `blue`, `green`, `red`, `yellow`, `orange`, `brightgreen`, `lightgrey`, hex (`ff69b4`).

---

## Tips

- Leave a **blank line** before and after fenced code blocks, lists, and HTML blocks for reliable rendering.
- GitHub renders `README.md` in the repository root automatically.
- Prefer relative paths for local images so they work on any fork.
- Avoid very deep heading nesting (H4+) — it reduces readability.
- Test rendering locally with tools like `grip` (`pip install grip`) or VS Code's Markdown Preview.
