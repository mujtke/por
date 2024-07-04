#!/usr/bin/env bash

if [[ ! -d "$1" ]]; then
	echo "Dir $1 does not exist!"
fi

fullPath="$(realpath $0)"
fullPath="${fullPath%/*}"
fullPath="${fullPath%/*}"
fullPath="${fullPath%/*}"
workDir="$fullPath"
[[ ! -d "$workDir" ]] && echo "Directory $workDir does not exist!" && exit 0

targetDir="$1"
GREEN="\033[32m"
YELLOW="\033[33m"
CLEAR="\033[0m"
BOLD="\033[1m"

# echo -e "\033[32mFile\t\t\t\t\033[33mResult\033[0m"
printf "${GREEN}${BOLD}%-30s${CLEAR}%-10s${YELLOW}%-10s${CLEAR}%-10s\n" "File" "LOC" "Result" "Time"

function runTask() {
	TEST_FILE="$1"
	printf "%-30s" "$(basename ${TEST_FILE})"
	printf "%-10s" "$(grep -v -E '^//|^$|^[\s\t ]*$' "$TEST_FILE" | wc -l | tr -d ' ')"
	cd "$workDir"
	RESULT=$(./scripts/cpa.sh -config config/myAnalysis-concurrency-bdd-ogpor-no-out.properties \
	-spec default -preprocess \
	"$TEST_FILE" 2> /dev/null | grep 'Verification result:' | awk '{ print $3 }')
	if [[ "$RESULT" =~ FALSE.* || "$RESULT" == TRUE.* ]]; then
		printf "%-10s\n" "$RESULT"
	else
		printf "%-10s\n" "UNKNOWN"
	fi
}

for file in $(find -s "$targetDir" -iname '*.c'); do
	#echo -n "$(basename $file): "
	# Use .i file if existed.
	if [ -e "${file%.c}.i" ]; then
		file="${file%.c}.i"
	fi
	runTask "$file"
done
