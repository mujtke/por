#!/usr/bin/env bash

TEST_FILE="$1"

cd "$HOME/Code/Java/por"

./scripts/cpa.sh -config -config config/myAnalysis-concurrency-bdd-pcdpor.properties -spec default -preprocess "$TEST_FILE"
