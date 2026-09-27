#!/usr/bin/env bash
./evskript.sh

for m in $(seq 4); do
for n in $(seq 2); do

cd Plot_${m}_${n}/
cat Ev*.txt > all.txt
../shorten
cd ..

done
done
