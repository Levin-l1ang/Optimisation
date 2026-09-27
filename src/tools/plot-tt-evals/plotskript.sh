for m in $(seq 4); do
for n in $(seq 2); do

cp ../Global-${m}-${n}.cnf ../Global-Config.cnf
make periodic-timetable
mv timetable* Plot_skript/Plot_${m}_${n}/
for i in $(seq 60); do
	cp Plot_skript/Plot_${m}_${n}/timetable${i} timetabling/Timetable-periodic.tim
	make eval-timetable
	cp timetabling/Evaluation.txt Plot_skript/Plot_${m}_${n}/Ev${i}.txt
done

done
done