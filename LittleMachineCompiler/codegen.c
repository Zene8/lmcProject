#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "codegen.h"

static FILE *output_file;

static void generate_statement(Statement stmt) {
    if (stmt.label != NULL) {
        fprintf(output_file, "%s:\n", stmt.label);
    }

    if (strcmp(stmt.instruction.lexeme, "ADD") == 0) {
        fprintf(output_file, "    add rax, [%s]\n", stmt.operand.lexeme);
    } else if (strcmp(stmt.instruction.lexeme, "SUB") == 0) {
        fprintf(output_file, "    sub rax, [%s]\n", stmt.operand.lexeme);
    } else if (strcmp(stmt.instruction.lexeme, "STA") == 0) {
        fprintf(output_file, "    mov [%s], rax\n", stmt.operand.lexeme);
    } else if (strcmp(stmt.instruction.lexeme, "LDA") == 0) {
        fprintf(output_file, "    mov rax, [%s]\n", stmt.operand.lexeme);
    } else if (strcmp(stmt.instruction.lexeme, "BRA") == 0) {
        fprintf(output_file, "    jmp %s\n", stmt.operand.lexeme);
    } else if (strcmp(stmt.instruction.lexeme, "BRZ") == 0) {
        fprintf(output_file, "    cmp rax, 0\n");
        fprintf(output_file, "    je %s\n", stmt.operand.lexeme);
    } else if (strcmp(stmt.instruction.lexeme, "BRP") == 0) {
        fprintf(output_file, "    cmp rax, 0\n");
        fprintf(output_file, "    jge %s\n", stmt.operand.lexeme);
    } else if (strcmp(stmt.instruction.lexeme, "INP") == 0) {
        // For simplicity, we'll just read a single character from stdin
        fprintf(output_file, "    mov rax, 0\n");
        fprintf(output_file, "    mov rdi, 0\n");
        fprintf(output_file, "    mov rsi, input_buffer\n");
        fprintf(output_file, "    mov rdx, 2\n"); // Read one char and a newline
        fprintf(output_file, "    syscall\n");
        fprintf(output_file, "    movzx rax, byte [input_buffer]\n");
        fprintf(output_file, "    sub rax, '0'\n");
    } else if (strcmp(stmt.instruction.lexeme, "OUT") == 0) {
        // For simplicity, we'll just print the accumulator to stdout
        fprintf(output_file, "    mov rdi, rax\n");
        fprintf(output_file, "    call print_rax\n");
    } else if (strcmp(stmt.instruction.lexeme, "HLT") == 0) {
        fprintf(output_file, "    mov rax, 60\n");
        fprintf(output_file, "    xor rdi, rdi\n");
        fprintf(output_file, "    syscall\n");
    }
}

void generate_code(Program *program, const char *output_filename) {
    output_file = fopen(output_filename, "w");
    if (output_file == NULL) {
        perror("Error opening output file");
        return;
    }

    fprintf(output_file, "section .data\n");
    fprintf(output_file, "    input_buffer: resb 2\n");
    fprintf(output_file, "    newline_char: db 0xA\n"); // ASCII for newline
    for (int i = 0; i < program->num_statements; i++) {
        if (program->statements[i].instruction.type == TOKEN_DAT) {
            fprintf(output_file, "    %s: dq %s\n", program->statements[i].label, program->statements[i].operand.lexeme);
        }
    }

    fprintf(output_file, "\nsection .text\n");
    fprintf(output_file, "global _start\n");

    // Helper function to print rax
    fprintf(output_file, "print_rax:\n");
    fprintf(output_file, "    push rax\n");
    fprintf(output_file, "    push rbx\n");
    fprintf(output_file, "    push rcx\n");
    fprintf(output_file, "    push rdx\n");
    fprintf(output_file, "    push rbp\n"); // Save rbp
    fprintf(output_file, "    mov rbp, rsp\n"); // Save stack pointer before pushing digits
    fprintf(output_file, "    mov rbx, 10\n");
    fprintf(output_file, "    .loop:\n");
    fprintf(output_file, "    xor rdx, rdx\n");
    fprintf(output_file, "    div rbx\n");
    fprintf(output_file, "    add rdx, '0'\n");
    fprintf(output_file, "    push rdx\n");
    fprintf(output_file, "    cmp rax, 0\n");
    fprintf(output_file, "    jne .loop\n");
    fprintf(output_file, "    .print:\n");
    fprintf(output_file, "    mov rax, 1\n");
    fprintf(output_file, "    mov rdi, 1\n");
    fprintf(output_file, "    mov rsi, rsp\n");
    fprintf(output_file, "    mov rdx, 1\n");
    fprintf(output_file, "    syscall\n");
    fprintf(output_file, "    pop rax\n"); // Pop the digit that was just printed
    fprintf(output_file, "    cmp rsp, rbp\n"); // Compare current stack pointer with saved stack pointer
    fprintf(output_file, "    jne .print\n");
    fprintf(output_file, "    mov rax, 1\n");
    fprintf(output_file, "    mov rdi, 1\n");
    fprintf(output_file, "    mov rsi, newline_char\n");
    fprintf(output_file, "    mov rdx, 1\n");
    fprintf(output_file, "    syscall\n");
    fprintf(output_file, "    pop rbp\n"); // Restore rbp
    fprintf(output_file, "    pop rdx\n");
    fprintf(output_file, "    pop rcx\n");
    fprintf(output_file, "    pop rbx\n");
    fprintf(output_file, "    pop rax\n");
    fprintf(output_file, "    ret\n");

    fprintf(output_file, "_start:\n");

    for (int i = 0; i < program->num_statements; i++) {
        if (program->statements[i].instruction.type != TOKEN_DAT) {
            generate_statement(program->statements[i]);
        }
    }

    fclose(output_file);
}