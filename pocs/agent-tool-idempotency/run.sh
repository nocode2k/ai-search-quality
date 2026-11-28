#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p results
java src/AgentToolIdempotencyExperiment.java data/logical-operations.csv | tee results/latest.json
