#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include "lexer.h"

static int line = 1;
static const char *source;
static Token *tokens;
static int num_tokens = 0;

static Token create_token(TokenType type, const char *lexeme) {
    Token token;
    token.type = type;
    token.lexeme = strdup(lexeme);
    token.line = line;
    return token;
}

static void add_token(TokenType type, const char *lexeme) {
    tokens = realloc(tokens, sizeof(Token) * (num_tokens + 1));
    tokens[num_tokens++] = create_token(type, lexeme);
}

static int is_digit(char c) {
    return c >= '0' && c <= '9';
}

static int is_alpha(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
}

static void number() {
    const char *start = source;
    while (is_digit(*source)) {
        source++;
    }
    char *lexeme = malloc(source - start + 1);
    strncpy(lexeme, start, source - start);
    lexeme[source - start] = '\0';
    add_token(TOKEN_NUMBER, lexeme);
}

static void identifier() {
    const char *start = source;
    while (is_alpha(*source) || is_digit(*source)) {
        source++;
    }
    char *lexeme = malloc(source - start + 1);
    strncpy(lexeme, start, source - start);
    lexeme[source - start] = '\0';

    TokenType type = TOKEN_LABEL;
    if (strcmp(lexeme, "ADD") == 0 || strcmp(lexeme, "SUB") == 0 || strcmp(lexeme, "STA") == 0 ||
        strcmp(lexeme, "LDA") == 0 || strcmp(lexeme, "BRA") == 0 || strcmp(lexeme, "BRZ") == 0 ||
        strcmp(lexeme, "BRP") == 0 || strcmp(lexeme, "INP") == 0 || strcmp(lexeme, "OUT") == 0 ||
        strcmp(lexeme, "HLT") == 0 || strcmp(lexeme, "DAT") == 0) {
        type = TOKEN_INSTRUCTION;
        if (strcmp(lexeme, "DAT") == 0) {
            type = TOKEN_DAT;
        }
    }

    add_token(type, lexeme);
}

Token *scan_tokens(const char *src) {
    source = src;
    tokens = NULL;
    num_tokens = 0;

    while (*source != '\0') {
        switch (*source) {
            case ' ':
            case '\r':
            case '\t':
                source++;
                break;
            case '\n':
                add_token(TOKEN_NEWLINE, "\n");
                line++;
                source++;
                break;
            case '/': // Handle comments
                if (*(source + 1) == '/') {
                    while (*source != '\n' && *source != '\0') {
                        source++;
                    }
                } else {
                    // Handle other characters starting with / if any
                    source++;
                }
                break;
            default:
                if (is_digit(*source)) {
                    number();
                } else if (is_alpha(*source)) {
                    identifier();
                }
                else {
                    // Unrecognized character, skip for now
                    source++;
                }
                break;
        }
    }

    add_token(TOKEN_EOF, "");
    return tokens;
}