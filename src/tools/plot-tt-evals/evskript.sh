for m in $(seq 4); do
for n in $(seq 2); do
for i in $(seq 9); do

mv Plot_${m}_${n}/Ev${i}.txt Plot_${m}_${n}/Ev0${i}.txt

done
done
done
