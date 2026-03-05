#!/bin/bash
# Auto-regenerate docs/db-schema.md when SQL migration files are changed.
# Triggered by Claude Code PostToolUse hook on Edit|Write.

INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

if [[ "$FILE_PATH" == *"db/migration"* ]]; then
    cd "$(git rev-parse --show-toplevel)" || exit 0
    echo "Regenerating docs/db-schema.md..." >&2
    ./gradlew generateDbSchema --quiet 2>&1 | tail -3 >&2
fi

exit 0
