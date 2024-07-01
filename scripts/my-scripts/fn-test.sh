#!/usr/bin/env bash

# Usage: ./scripts/my-scripts/fn-test.sh FN.set

CONFIG_FILE="config/myAnalysis-concurrency-bdd-ogpor-no-out.properties"
# ARGS="-spec default -preprocess"
ARGS="-spec default"
EXECUTABLE="./scripts/cpa.sh"
FORMATTER="%-50s%-10s%-10s\n"
printf "$FORMATTER" "File" "Expected" "Result"
while read line;
do
	YML="$line"
	PREFIX="${YML%/*}"
	FILE="${PREFIX}/$(awk '/.*input_files.*$/ { print $2 }' $YML | tr -d "'")"
	EXPECT="$(awk '/.*expected.*$/ { print $2 }' $YML)"
	RESULT="$($EXECUTABLE "$CONFIG_FILE" $ARGS "$FILE" 2> /dev/null | grep 'Verification result:' | awk '{ print $3}')"
	printf "$FORMATTER" "FILE" "$EXPECT" "$RESULT"
done < "$1"
