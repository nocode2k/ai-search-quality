#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
java SearchContractExperiment.java contracts/search-contract.tsv
