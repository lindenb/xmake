.PHONY:all

all:
	mkdir -p tmp dist
	javac -d tmp -sourcepath src/main/java src/main/java/com/github/lindenb/xmake/XMake.java
	jar cvf dist/xmake.jar -C tmp .
	rm -rf tmp
	java -cp dist/xmake.jar com.github.lindenb.xmake.XMake makefile.xml
