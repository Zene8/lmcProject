section .data
    FIRST: dw 0
    SECOND: dw 0

section .text
global _start
print_rax:
    push rax
    push rbx
    push rcx
    push rdx
    mov rcx, rsp
    mov rbx, 10
    .loop:
    xor rdx, rdx
    div rbx
    add rdx, '0'
    push rdx
    inc rcx
    cmp rax, 0
    jne .loop
    .print:
    dec rcx
    mov rax, 1
    mov rdi, 1
    mov rsi, rsp
    mov rdx, 1
    syscall
    pop rax
    cmp rcx, rsp
    jne .print
    pop rdx
    pop rcx
    pop rbx
    pop rax
    ret
_start:
START:
    mov rdi, rax
    call print_rax
    mov rax, 60
    xor rdi, rdi
    syscall
