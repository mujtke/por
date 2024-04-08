#!/usr/bin/env bash

if [[ ! -d "$1" ]]; then
	echo "Dir $1 does not exist!"
fi

targetDir="$1"

echo -e "\033[32mFile\t\t\t\t\t\t\t\t\033[33mResult\033[0m"

function runTask() {
	TEST_FILE="$1"
	cd "$HOME/Code/Java/por"
	RESULT=$(./scripts/cpa.sh -config config/myAnalysis-concurrency-bdd-ogpor.properties \
	-spec default -preprocess \
	"$TEST_FILE" 2> /dev/null | grep 'Verification result:' | awk '{ print $3 }')
		if [[ "$RESULT" =~ FALSE.* || "$RESULT" == TRUE.* ]]; then
		echo "$RESULT"
	else
		echo "UNKNOWN"
	fi
}

for file in $(find "$targetDir" -iname '*.c'); do
	echo -n "$(basename $file): "
	runTask "$file"
done

