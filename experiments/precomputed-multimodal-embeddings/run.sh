#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
java --source 17 "$script_dir/src/PrecomputedEmbeddingExperiment.java" \
  "$script_dir/data/products.csv" "$script_dir/data/queries.csv"

