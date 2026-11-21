#!/usr/bin/env bash
# Compiles the API into backend/out. The two jars in backend/lib are the only dependencies.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out -cp "lib/*" $(find src -name "*.java")
echo "Built to $(pwd)/out"
