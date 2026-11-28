#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
output="$(mktemp)"
trap 'rm -f "$output"' EXIT
java src/AgentToolIdempotencyExperiment.java data/logical-operations.csv > "$output"
grep -q '"sideEffects": 60' "$output"
grep -q '"sideEffects": 20' "$output"
grep -q '"consistentResultRate": 1.0000' "$output"
echo "verification passed"
