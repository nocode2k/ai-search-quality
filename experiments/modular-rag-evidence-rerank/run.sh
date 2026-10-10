#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
java --source 17 "$script_dir/src/ModularRagEvidenceExperiment.java"
