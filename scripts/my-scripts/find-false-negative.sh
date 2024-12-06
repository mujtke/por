#!/usr/bin/env bash

TEST_FILE="$(realpath $1)"

# cd "$HOME/Code/Java/por"
cd "$PWD"

OGPOR_OUT=$(./scripts/cpa.sh -config config/myAnalysis-concurrency-bdd-ogpor-no-out.properties \
	-spec default -preprocess -stats "$TEST_FILE" 2> /dev/null &)
OGPOR_PID=$!

PCDPOR_OUT=$(./scripts/cpa.sh -config config/myAnalysis-concurrency-bdd-pcdpor-no-out.properties \
	-spec default -preprocess -stats "$TEST_FILE" 2> /dev/null &)
PCDPOR_PID=$!

wait $OGPOR_PID $PCDPOR_PID

OGPOR_RESULT=$(grep 'Verification result:' <<< $OGPOR_OUT | awk '{ print $3 }')
OGPOR_STATES_NUM=$(grep 'explored states:' <<< $OGPOR_OUT | awk '{ print $3 }')
OGPOR_OG_NUM=$(grep -i 'number of og' <<< $OGPOR_OUT | awk '{ print $4 }')
PCDPOR_RESULT=$(grep 'Verification result:' <<< $PCDPOR_OUT | awk '{ print $3 }')
PCDPOR_STATES_NUM=$(grep 'explored states:' <<< $PCDPOR_OUT | awk '{ print $3 }')

if [[ $OGPOR == "TRUE." ]]; then
	OGPOR="\033[32m$OGPOR\033[0m" 
elif [[ $OGPOR == "FALSE." ]]; then
	OGPOR="\033[31m$OGPOR\033[0m" 
else
	OGPOR="ERROR."
fi

if [[ $PCDPOR == "TRUE." ]]; then
	PCDPOR="\033[32m$PCDPOR\033[0m" 
elif [[ $PCDPOR == "FALSE." ]]; then
	PCDPOR="\033[31m$PCDPOR\033[0m" 
else
	PCDPOR="ERROR."
fi

printf "       %-8s%-18s%-18s\n" "Result" "Explored States" "Number of OG"
printf "OGPOR  %-8s%-18s%-18s\n" "$OGPOR_RESULT" "$OGPOR_STATES_NUM" "$OGPOR_OG_NUM"
printf "PCDPOR %-8s%-18s\n" "$PCDPOR_RESULT" "$PCDPOR_STATES_NUM"
