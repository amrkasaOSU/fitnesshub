#!/bin/bash
# Starts the backend with everything in .env loaded.
# Keeps secrets (OPENAI_API_KEY etc.) in a gitignored file instead of shell history.
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Portable Maven/Node used during initial development, if the system has none.
if ! command -v mvn >/dev/null 2>&1 && [ -f "$HOME/.fitnesshub-tools/env.sh" ]; then
  . "$HOME/.fitnesshub-tools/env.sh"
fi

if [ -f "$ROOT/.env" ]; then
  set -a
  . "$ROOT/.env"
  set +a
else
  echo "No .env found - copy .env.example to .env first." >&2
  exit 1
fi

if [ -z "$OPENAI_API_KEY" ]; then
  echo "note: OPENAI_API_KEY is blank - the AI assistant will report itself as not configured."
fi

cd "$ROOT/backend"
exec mvn spring-boot:run
