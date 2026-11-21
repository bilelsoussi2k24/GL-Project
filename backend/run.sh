#!/usr/bin/env bash
# Builds if needed, then starts the API on http://localhost:8080.
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -f out/Main.class ]; then
  ./build.sh
fi
exec java -cp "out:lib/*" Main
