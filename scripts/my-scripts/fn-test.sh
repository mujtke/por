#!/usr/bin/env bash

# Usage: ./scripts/my-scripts/fn-test.sh FN.set

CONFIG_FILE="config/myAnalysis-concurrency-bdd-ogpor-no-out.properties"
# ARGS="-spec default -preprocess"
ARGS="-spec default"
EXECUTABLE="./scripts/cpa.sh"
FORMATTER="%-10s%-10s%-100s\n"
printf "$FORMATTER" "Expected" "Result" "File" 
while read line;
do
	YML="$line"
	PREFIX="${YML%/*}"
	FILE="${PREFIX}/$(awk '/.*input_files.*$/ { print $2 }' $YML | tr -d "'")"
	EXPECT="$(awk '/.*expected.*$/ { print $2 }' $YML)"
	RESULT="$($EXECUTABLE -config "$CONFIG_FILE" $ARGS "$FILE" 2> /dev/null | grep 'Verification result:' | awk '{ print $3}')"
	if [ "$RESULT" == "" ]; then
		RESULT="UNKNOWN"
	fi
	printf "$FORMATTER" "$EXPECT" "$RESULT" "$FILE"
done < "$1"
