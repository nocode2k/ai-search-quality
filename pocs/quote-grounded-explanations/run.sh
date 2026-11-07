#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
java "$ROOT_DIR/GroundingPoc.java" "$ROOT_DIR/data/claims.tsv"
