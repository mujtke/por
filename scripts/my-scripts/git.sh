#!/usr/bin/env bash

git status

if [ $? -eq 0 ]; then
	for f in $(git status -s | egrep '(^ M|^MM|^\?\?|^ A|^AA)' | awk '{ print $2 }'); do
		git add $f
	done
fi

git status -s

if [ $? -eq 0 ]; then
	read -p 'Enter the comment:'
	git commit -m "$REPLY"
fi
