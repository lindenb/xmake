.SHELL=/bin/bash
.PHONY:all test

all: test

test: dist/xmake.jar
	find ./src/test/resources/xmakefiles -type f -name "xmakefile*.xml" -exec java -cp $< com.github.lindenb.xmake.XMake -f '{}' ';'

dist/xmake.jar : $(addsuffix .java,$(addprefix  src/main/java/com/github/lindenb/xmake/,XMake))
	mkdir -p tmp dist
	javac -d tmp -sourcepath src/main/java $<
	jar cvf dist/xmake.jar -C tmp .
	rm -rf tmp
	
