#!/bin/bash

f=$1

if [ ! -n "${f}" ];then
	echo "No input file, exit."
	exit 0
elif [ ! -e ${f} ];then
	echo "file ${f} doesn't exist, exit."
	exit 0
fi

gcc -E ${f} -o ${f%.c}.i

if [ $? -eq 0 ];then
	echo "gcc -E ${f} -o ${f%.c}.i"
fi