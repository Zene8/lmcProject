#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "parser.h"

static Token *tokens;
static int current = 0;

static Statement parse_statement() {
    Statement stmt;
    stmt.label = NULL;
    stmt.operand.lexeme = NULL;

    if (tokens[current].type == TOKEN_LABEL) {
        stmt.label = tokens[current].lexeme;
        current++;
    }

    if (tokens[current].type == TOKEN_INSTRUCTION || tokens[current].type == TOKEN_DAT) {
        stmt.instruction = tokens[current];
        current++;
        if (tokens[current].type == TOKEN_NUMBER || tokens[current].type == TOKEN_LABEL) {
            stmt.operand = tokens[current];
            current++;
        }
    }
    return stmt;
}

Program *parse(Token *tkns) {
    tokens = tkns;
    Program *program = malloc(sizeof(Program));
    program->statements = NULL;
    program->num_statements = 0;

    while (tokens[current].type != TOKEN_EOF) {
        int prev_current = current;
        Statement stmt = parse_statement();
        program->statements = realloc(program->statements, sizeof(Statement) * (program->num_statements + 1));
        program->statements[program->num_statements++] = stmt;

        if (tokens[current].type == TOKEN_NEWLINE) {
            current++;
        }

        if (current == prev_current) {
            current++;
        }
    }

    return program;
}
