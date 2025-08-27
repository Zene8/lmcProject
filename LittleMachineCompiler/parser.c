#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "parser.h"

static Token *tokens;
static int current = 0;

// Helper to advance past newlines
static void consume_newlines() {
    while (tokens[current].type == TOKEN_NEWLINE) {
        current++;
    }
}

Program *parse(Token *tkns) {
    tokens = tkns;
    Program *program = malloc(sizeof(Program));
    program->statements = NULL;
    program->num_statements = 0;

    char *pending_label = NULL; // To handle labels on their own line

    while (tokens[current].type != TOKEN_EOF) {
        consume_newlines(); // Skip any leading newlines

        if (tokens[current].type == TOKEN_EOF) break; // Check EOF again after consuming newlines

        Statement stmt;
        stmt.label = NULL;
        stmt.operand.lexeme = NULL;
        stmt.instruction.lexeme = NULL; // Ensure instruction is null if not found

        // Check for a label at the beginning of the line
        if (tokens[current].type == TOKEN_LABEL) {
            pending_label = tokens[current].lexeme;
            current++;
            consume_newlines(); // Consume newlines after a label if it's on its own line
        }

        // Check for instruction or DAT
        if (tokens[current].type == TOKEN_INSTRUCTION || tokens[current].type == TOKEN_DAT) {
            stmt.instruction = tokens[current];
            current++;

            // Assign pending label if exists
            if (pending_label != NULL) {
                stmt.label = pending_label;
                pending_label = NULL; // Consume the pending label
            }

            // Check for operand if required by instruction/DAT
            // This assumes all instructions/DAT can have an operand.
            // A more robust parser would check instruction type for operand requirement.
            if (tokens[current].type == TOKEN_NUMBER || tokens[current].type == TOKEN_LABEL) {
                stmt.operand = tokens[current];
                current++;
            }

            // Add the statement
            program->statements = realloc(program->statements, sizeof(Statement) * (program->num_statements + 1));
            program->statements[program->num_statements++] = stmt;

        } else {
            // If we reached here, it means there was no instruction/DAT token.
            // This could be an empty line (already handled by consume_newlines),
            // or an unrecognized token.
            // For now, we'll just advance past it to prevent infinite loops.
            // A more robust parser would report an error here.
            current++;
        }

        // Consume any remaining tokens on the line until a newline or EOF
        while (tokens[current].type != TOKEN_NEWLINE && tokens[current].type != TOKEN_EOF) {
            current++;
        }
    }

    return program;
}
