-include ../settings/Makefile.env

.PHONY: clean all

../string-helper/string_helper.o:
	make -C ../string-helper string_helper.o
	
all: statistic_cmd

statistic_cmd: ../string-helper/string_helper.o statistic.o

statistic_java:
	ant
clean: 
	rm -f statistic.o statistic_cmd
