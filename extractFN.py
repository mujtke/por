#!/usr/bin/python3
# Extract the false negatives from the data extracted from .html file.
# .html file locates in directory './results/20xx-xx-xx/'
# Use this script after the generation of the report.

import os
import json
import sys
import re

try:
    dataDir = sys.argv[1]
except IndexError:
    print("Report dir not specified!")
    exit(0)

dataDir = dataDir.rstrip('/')
if not os.path.exists(dataDir):
    raise FileExistsError(dataDir + " doesn't exist!")

dataJson = dataDir + "/data.json"
if not os.path.exists(dataJson):
    if (os.system('touch ' + dataJson) != 0):
        raise FileExistsError(f"creating data file {dataJson} failed!")

if (os.system(f'grep \'"rows":\' {dataDir}/*.html | sed \'s/^ *"rows": *\\(.*\\),$/\\1/\' > {dataJson}') != 0):
    raise FileExistsError(f"extracting data from .html file failed!")

if not os.path.exists(dataDir + "/FN.set"):
    os.system("touch " + dataDir + "/FN.set")
fnSet = open(dataDir + "/FN.set", 'w')

data = json.load(open(dataJson))
for row in data:
    wrong = filter(lambda x : x['category'] == 'wrong', row['results'])
    if (list(wrong)):
        print(row["href"])
        fnSet.write(row["href"] + "\n")

setFile = (dataDir + "/FN.set").replace('/', '\/')
os.system(f'sed -i .bak \'s/\(<includesfile>\).*\(<\/includesfile>\)/\\1{setFile}\\2/\' OGPOR-FN.xml')
