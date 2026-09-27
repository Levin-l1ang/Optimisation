#!/usr/bin/env bash
set -e
echo "Running python core unit tests"
cd ../src/core/python
python3 -m unittest
echo "Running java core unit tests"
cd ../java
ant -q build-tests
java -cp build/:../../../libs/junit/junit-4.13.2.jar:../../../libs/hamcrest/hamcrest-core-1.3.jar net.lintim.main.TestRunner
echo "Running line concept evaluation unit tests"
cd ../..
bash line-planning/evaluation/run_tests.sh
echo "Running timetable evaluation unit tests"
cd timetabling/periodic/capacitated_evaluation
ant -q build-tests
java -cp ${CLASSPATH}:build/:../../../../libs/junit/junit-4.13.2.jar:../../../../libs/hamcrest/hamcrest-core-1.3.jar:../../../core/java/build/ net.lintim.main.TestRunner
