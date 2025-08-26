#include <stdio.h>
#include <stdlib.h>
#include "lexer.h"
#include "parser.h"
#include "codegen.h"

int main(int argc, char *argv[]) {
    if (argc != 3) {
        printf("Usage: %s <source_file> <output_file>\n", argv[0]);
        return 1;
    }

    char *source_filename = argv[1];
    char *output_filename = argv[2];

    FILE *source_file = fopen(source_filename, "r");
    if (source_file == NULL) {
        perror("Error opening source file");
        return 1;
    }

    fseek(source_file, 0, SEEK_END);
    long file_size = ftell(source_file);
    fseek(source_file, 0, SEEK_SET);

    char *source_code = malloc(file_size + 1);
    fread(source_code, 1, file_size, source_file);
    source_code[file_size] = '\0';

    fclose(source_file);

    Token *tokens = scan_tokens(source_code);
    Program *program = parse(tokens);
    generate_code(program, output_filename);

    // Free memory
    for (int i = 0; tokens[i].type != TOKEN_EOF; i++) {
        free(tokens[i].lexeme);
    }
    free(tokens);
    free(program->statements);
    free(program);
    free(source_code);

    return 0;
}

