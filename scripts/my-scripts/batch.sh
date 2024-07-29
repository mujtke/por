#!/usr/bin/env bash

ARCH=$(arch)
if [ "$ARCH" == "arm64" ]; then
	sig="KILL"
elif [ "$ARCH" == "x86_64" ]; then
	sig=9
else
	echo "Unknown architecture." && exit 0
fi

taskNum=0
function CtrlC_Handler {
	PID=$(pgrep -n java ogpor)
	kill -s $ARCH $PID > /dev/null 2>&1
	((taskNum--))
	if [ $taskNum -lt 0 ]; then
		exit 0
	fi
}

function CtrlD_Handler {
	echo "ctrl_d(or ctrl_\ on macos) pressed, quit."
	taskNum=0
	CtrlC_Handler
}

trap CtrlC_Handler SIGINT
trap CtrlD_Handler SIGQUIT

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

function pass() {
	for n in {8,}; do
		reg=".*$n.*"
		if [[ "$1" =~ $reg ]]; then
			return 0
		fi
	done
	return 0
}

taskNum=$(find -s "$targetDir" -iname '*.c' | wc -l | tr -d ' ')
for file in $(find -s "$targetDir" -iname '*.c'); do
	# Ignore some files.
	pass "$file"
	if [ $? -eq 1 ]; then
		continue
	fi
	#echo -n "$(basename $file): "
	# Use .i file if existed.
	if [ -e "${file%.c}.i" ]; then
		file="${file%.c}.i"
	fi
	runTask "$file"
done
