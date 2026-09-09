#!/bin/bash
# Dev convenience: makes sure `node`/`npm` are on PATH before starting the frontend.
# Falls back to a portable Node install used during initial development if the
# system has none on PATH.
if ! command -v node >/dev/null 2>&1 && [ -x "$HOME/.fitnesshub-tools/node/bin/node" ]; then
  export PATH="$HOME/.fitnesshub-tools/node/bin:$PATH"
fi
cd "$(dirname "$0")/../frontend"
exec npm run dev
