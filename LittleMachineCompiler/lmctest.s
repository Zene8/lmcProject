section .data
    input_buffer: resb 2
    newline_char: db 0xA
    A: dq 0
    B: dq 0

section .text
global _start
print_rax:
    push rax
    push rbx
    push rcx
    push rdx
    push rbp
    mov rbp, rsp
    mov rbx, 10
    .loop:
    xor rdx, rdx
    div rbx
    add rdx, '0'
    push rdx
    cmp rax, 0
    jne .loop
    .print:
    mov rax, 1
    mov rdi, 1
    mov rsi, rsp
    mov rdx, 1
    syscall
    pop rax
    cmp rsp, rbp
    jne .print
    mov rax, 1
    mov rdi, 1
    mov rsi, newline_char
    mov rdx, 1
    syscall
    pop rbp
    pop rdx
    pop rcx
    pop rbx
    pop rax
    ret
_start:
    mov rax, 0
    mov rdi, 0
    mov rsi, input_buffer
    mov rdx, 2
    syscall
    movzx rax, byte [input_buffer]
    sub rax, '0'
    mov [A], rax
    mov rax, 0
    mov rdi, 0
    mov rsi, input_buffer
    mov rdx, 2
    syscall
    movzx rax, byte [input_buffer]
    sub rax, '0'
    mov [B], rax
    mov rax, [A]
    add rax, [B]
    mov rdi, rax
    call print_rax
    mov rax, 60
    xor rdi, rdi
    syscall
