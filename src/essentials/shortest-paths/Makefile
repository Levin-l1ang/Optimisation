-include ../settings/Makefile.env

.PHONY: all clean shortest_paths_java

all: shortest_paths_cc shortest_paths_sh

shortest_paths_sh: fheap.o shortest_paths.o
shortest_paths_cc: fheap.o shortest_paths.o

shortest_paths_sh_test: shortest_paths_sh
	./shortest_paths_sh -n nodes.csv -e edges.csv -d distances.csv -p paths.csv

shortest_paths_sh_test_undirected: shortest_paths_sh
	./shortest_paths_sh -n nodes.csv -e edges.csv -d distances.csv -p paths.csv -u

shortest_paths.o: fheap.o

shortest_paths_java:
	ant

shortest_paths_java_test:
	make shortest_paths_java
	java -classpath ../../../libs/jgrapht/jgrapht-core-1.1.0.jar:./src ShortestPathsTest nodes.csv edges.csv distances.csv paths.csv

clean:
	rm -f *.o shortest_paths_sh shortest_paths_cc distances.csv paths.csv
	find src -name "*.class" -exec rm \{\} \;
