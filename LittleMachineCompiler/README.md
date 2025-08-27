# Little Machine Compiler Project

This project aims to create a comprehensive development environment for the Little Machine Code language.

## 1. Little Machine Code Language Specification

Little Machine Code (LMC) is a simplified instruction set used for educational purposes. It models a simple von Neumann architecture computer.

### Registers

*   **Accumulator (A):** A single general-purpose register.
*   **Program Counter (PC):** Holds the address of the next instruction to be executed.

### Instruction Set

The LMC instruction set consists of the following instructions:

| Opcode | Mnemonic | Instruction                                               |
| :----- | :------- | :-------------------------------------------------------- |
| 1xx    | `ADD`    | Add the value from memory location xx to the accumulator.      |
| 2xx    | `SUB`    | Subtract the value from memory location xx from the accumulator. |
| 3xx    | `STA`    | Store the value of the accumulator in memory location xx.      |
| 5xx    | `LDA`    | Load the value from memory location xx into the accumulator.   |
| 6xx    | `BRA`    | Branch (unconditionally) to the address xx.               |
| 7xx    | `BRZ`    | Branch to address xx if the accumulator is zero.          |
| 8xx    | `BRP`    | Branch to address xx if the accumulator is positive or zero. |
| 901    | `INP`    | Input a value and store it in the accumulator.            |
| 902    | `OUT`    | Output the value of the accumulator.                      |
| 000    | `HLT`    | Halt the program.                                         |
|        | `DAT`    | A data location (assembles to a word of data).            |

## 2. Compiler

The compiler will be written in C and will translate Little Machine Code into a target assembly language (e.g., x86-64).

### Compiler Stages

1.  **Lexical Analysis (Lexing):** Break the source code into a stream of tokens.
2.  **Syntactic Analysis (Parsing):** Build a parse tree from the token stream to represent the program's structure.
3.  **Semantic Analysis:** Check the parse tree for semantic errors (e.g., undefined labels).
4.  **Code Generation:** Generate the target assembly code from the parse tree.

For detailed setup and testing instructions, please refer to the main project README.md.

## 3. Simple IDE

A simple, terminal-based IDE will be created with the following features:

*   **Text Editor:** A simple text editor for writing LMC code.
*   **Intellisense/Autocomplete:** Suggest instructions and labels.
*   **Syntax Highlighting:** Highlight different parts of the code (instructions, labels, comments).
*   **Build & Run:** Compile and run the LMC code.

## 4. VSCode Extension

A VSCode extension will be developed to provide a modern development experience for LMC.

### Features

*   **Syntax Highlighting:** For `.lmc` files.
*   **Intellisense:** Autocomplete for instructions and labels.
*   **Diagnostics:** Real-time error checking and warnings.
*   **Code Snippets:** For common LMC code patterns.
*   **Build Tasks:** To compile and run LMC code from within VSCode.
