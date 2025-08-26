#ifndef LEXER_H
#define LEXER_H

typedef enum {
    TOKEN_INSTRUCTION,
    TOKEN_NUMBER,
    TOKEN_LABEL,
    TOKEN_DAT,
    TOKEN_NEWLINE,
    TOKEN_EOF
} TokenType;

typedef struct {
    TokenType type;
    char *lexeme;
    int line;
} Token;

Token *scan_tokens(const char *src);

#endif // LEXER_H
