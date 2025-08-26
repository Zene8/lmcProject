import * as vscode from 'vscode';

// Define valid LMC instructions and their operand requirements
const INSTRUCTIONS_NO_OPERAND = ["INP", "OUT", "HLT"];
const INSTRUCTIONS_WITH_OPERAND = ["ADD", "SUB", "STA", "LDA", "BRA", "BRZ", "BRP"];
const DATA_INSTRUCTION = "DAT";

// Regex to match an LMC instruction line: LABEL? INSTRUCTION OPERAND? COMMENT?
const LMC_LINE_PATTERN = new RegExp(
    "^(?:\\s*([A-Z][A-Z0-9]*):)?\\s*([A-Z]{3})(?:\\s+(-?\\d+))?\\s*(?:;.*)?$"
);

export function checkLmcSyntax(document: vscode.TextDocument): vscode.Diagnostic[] {
    const diagnostics: vscode.Diagnostic[] = [];
    const lines = document.getText().split(/\r?\n/);

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim();
        if (line.length === 0 || line.startsWith(";")) {
            continue; // Skip empty lines and full-line comments
        }

        const match = line.match(LMC_LINE_PATTERN);
        if (!match) {
            diagnostics.push(createDiagnostic(
                new vscode.Range(i, 0, i, line.length),
                "Syntax error. Invalid line format.",
                vscode.DiagnosticSeverity.Error
            ));
            continue;
        }

        // const label = match[1]; // Not used for syntax checking, but available
        const instruction = match[2];
        const operandStr = match[3];

        // Check if instruction is valid
        if (!INSTRUCTIONS_NO_OPERAND.includes(instruction) &&
            !INSTRUCTIONS_WITH_OPERAND.includes(instruction) &&
            instruction !== DATA_INSTRUCTION) {
            diagnostics.push(createDiagnostic(
                new vscode.Range(i, line.indexOf(instruction), i, line.indexOf(instruction) + instruction.length),
                `Unknown instruction '${instruction}'.`,
                vscode.DiagnosticSeverity.Error
            ));
            continue;
        }

        // Check operand requirements
        if (INSTRUCTIONS_NO_OPERAND.includes(instruction)) {
            if (operandStr !== undefined) {
                diagnostics.push(createDiagnostic(
                    new vscode.Range(i, line.indexOf(instruction), i, line.length),
                    `Instruction '${instruction}' does not take an operand.`,
                    vscode.DiagnosticSeverity.Error
                ));
            }
        } else if (INSTRUCTIONS_WITH_OPERAND.includes(instruction)) {
            if (operandStr === undefined) {
                diagnostics.push(createDiagnostic(
                    new vscode.Range(i, line.indexOf(instruction), i, line.length),
                    `Instruction '${instruction}' requires an operand.`,
                    vscode.DiagnosticSeverity.Error
                ));
            } else {
                if (isNaN(parseInt(operandStr))) {
                    diagnostics.push(createDiagnostic(
                        new vscode.Range(i, line.indexOf(operandStr), i, line.indexOf(operandStr) + operandStr.length),
                        `Invalid operand for '${instruction}'. Expected a number.`, 
                        vscode.DiagnosticSeverity.Error
                    ));
                }
            }
        } else if (instruction === DATA_INSTRUCTION) { // DAT instruction
            if (operandStr === undefined) {
                diagnostics.push(createDiagnostic(
                    new vscode.Range(i, line.indexOf(instruction), i, line.length),
                    `'DAT' instruction requires a data value operand.`,
                    vscode.DiagnosticSeverity.Error
                ));
            } else {
                if (isNaN(parseInt(operandStr))) {
                    diagnostics.push(createDiagnostic(
                        new vscode.Range(i, line.indexOf(operandStr), i, line.indexOf(operandStr) + operandStr.length),
                        `Invalid data value for 'DAT'. Expected a number.`, 
                        vscode.DiagnosticSeverity.Error
                    ));
                }
            }
        }
    }

    return diagnostics;
}

function createDiagnostic(range: vscode.Range, message: string, severity: vscode.DiagnosticSeverity): vscode.Diagnostic {
    const diagnostic = new vscode.Diagnostic(range, message, severity);
    diagnostic.source = 'LMC Syntax Checker';
    return diagnostic;
}