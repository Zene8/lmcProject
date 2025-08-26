CC = gcc
CFLAGS = -Wall -g

all: compiler

compiler: compiler.o lexer.o parser.o codegen.o
	$(CC) $(CFLAGS) -o compiler compiler.o lexer.o parser.o codegen.o

compiler.o: compiler.c
	$(CC) $(CFLAGS) -c compiler.c

lexer.o: lexer.c lexer.h
	$(CC) $(CFLAGS) -c lexer.c

parser.o: parser.c parser.h
	$(CC) $(CFLAGS) -c parser.c

codegen.o: codegen.c codegen.h
	$(CC) $(CFLAGS) -c codegen.c

clean:
	rm -f *.o compiler
