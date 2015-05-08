.SHELL=/bin/bash
.PHONY:all test

all: test

test: dist/xmake.jar
	find ./src/test/resources/xmakefiles -type f -name "xmakefile*.xml" -exec java -cp $< com.github.lindenb.xmake.XMake '{}' ';'

dist/xmake.jar : $(addsuffix .java,$(addprefix  src/main/java/com/github/lindenb/xmake/,XMake AbstractEval Context DefaultContext DefaultRule DefaultVariable DepFile EvalException Eval NilAppendable Rule Shell Status StreamConsummer SystemVariable Variable))
	mkdir -p tmp dist
	javac -d tmp -sourcepath src/main/java $<
	jar cvf dist/xmake.jar -C tmp .
	rm -rf tmp
	
