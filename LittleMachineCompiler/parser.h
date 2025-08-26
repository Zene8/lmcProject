#ifndef PARSER_H
#define PARSER_H

#include "lexer.h"

// Represents a single instruction
typedef struct {
    Token instruction;
    Token operand;
    char *label;
} Statement;

// Represents the entire program
typedef struct {
    Statement *statements;
    int num_statements;
} Program;

Program *parse(Token *tokens);

#endif // PARSER_H
