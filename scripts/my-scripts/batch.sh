#!/usr/bin/env bash

if [[ ! -d "$1" ]]; then
	echo "Dir $1 does not exist!"
fi

targetDir="$1"
GREEN="\033[32m"
YELLOW="\033[33m"
CLEAR="\033[0m"
BOLD="\033[1m"

# echo -e "\033[32mFile\t\t\t\t\033[33mResult\033[0m"
printf "${GREEN}${BOLD}%-30s${YELLOW}%-10s${CLEAR}%-10s\n" "File" "Result" "Time"

function runTask() {
	TEST_FILE="$1"
	printf "%-30s" "$(basename ${TEST_FILE})"
	cd "$HOME/Code/Java/por"
	RESULT=$(./scripts/cpa.sh -config config/myAnalysis-concurrency-bdd-ogpor.properties \
	-spec default -preprocess \
	"$TEST_FILE" 2> /dev/null | grep 'Verification result:' | awk '{ print $3 }')
		if [[ "$RESULT" =~ FALSE.* || "$RESULT" == TRUE.* ]]; then
		#echo "$RESULT"
		printf "%-10s\n" "$RESULT"
	else
		#echo "UNKNOWN"
		printf "%-10s\n" "UNKNOWN"
	fi
}

for file in $(find "$targetDir" -iname '*.c'); do
	#echo -n "$(basename $file): "
	runTask "$file"
done
