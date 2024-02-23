#!/usr/bin/env bash

TEST_FILE="$(realpath $1)"

cd "$HOME/Code/Java/por"

OGPOR=$(./scripts/cpa.sh -config config/myAnalysis-concurrency-bdd-ogpor.properties \
	-spec default -preprocess \
	"$TEST_FILE" 2> /dev/null | grep 'Verification result:' | awk '{ print $3 }')

if [[ $OGPOR == "TRUE." ]]; then
	OGPOR="\033[32m$OGPOR\033[0m" 
elif [[ $OGPOR == "FALSE." ]]; then
	OGPOR="\033[31m$OGPOR\033[0m" 
else
	OGPOR="ERROR."
fi

PCDPOR=$(./scripts/cpa.sh -config config/myAnalysis-concurrency-bdd-pcdpor.properties \
	-spec default -preprocess \
	"$TEST_FILE" 2> /dev/null | grep 'Verification result:' | awk '{ print $3 }')

if [[ $PCDPOR == "TRUE." ]]; then
	PCDPOR="\033[32m$PCDPOR\033[0m" 
elif [[ $PCDPOR == "FALSE." ]]; then
	PCDPOR="\033[31m$PCDPOR\033[0m" 
else
	PCDPOR="ERROR."
fi

printf "OGPOR: \t\t$OGPOR\n"
printf "PCDPOR: \t$PCDPOR\n"
