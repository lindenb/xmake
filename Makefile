.SHELL=/bin/bash
.PHONY:all test doc/javadoc/index.html javadoc

all: test

test: dist/xmake.jar
	find ./src/test/resources/xmakefiles -type f -name "xmakefile*.xml" -exec java -cp $< com.github.lindenb.xmake.XMake -d --report '{}.html' -f '{}' ';'

dist/xmake.jar : $(addsuffix .java,$(addprefix  src/main/java/com/github/lindenb/xmake/,XMake))
	mkdir -p tmp dist
	javac -d tmp -sourcepath src/main/java $<
	jar cvf dist/xmake.jar -C tmp .
	rm -rf tmp

javadoc: doc/javadoc/index.html 
doc/javadoc/index.html :
	rm -rf $@
	mkdir -p $(dir $@)
	javadoc -d $(dir $@) -windowtitle "XMake" -use -author -private -sourcepath src/main/java ./src/main/java/com/github/lindenb/xmake/XMake.java