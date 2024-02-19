#!/usr/bin/env bash

git status

if [ $? -eq 0 ]; then
	for f in $(git status -s | egrep '(^ M|^\?\?|^ A)' | awk '{ print $2 }'); do
		git add $f
	done
fi

if [ $? -eq 0 ]; then
	read -p 'Enter the comment:'
fi
