#!/bin/bash
# Auto-regenerate docs/db-schema.md when migration files are changed.
# Triggered by Claude Code PostToolUse hook on Edit|Write|Agent.

cd "$(git rev-parse --show-toplevel)" || exit 0

if git status --porcelain -- 'src/main/resources/db/migration' 'src/main/java/db/migration' 2>/dev/null | grep -q .; then
    echo "Regenerating docs/db-schema.md..." >&2
    ./gradlew generateDbSchema --quiet 2>&1 | tail -5 >&2
fi

exit 0
