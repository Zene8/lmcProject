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

    const definedLabels = new Map<string, number>();
    const usedLabels = new Map<string, vscode.Range[]>();

    // First pass: Identify labels and basic instruction format errors
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

        const label = match[1];
        const instruction = match[2];
        const operandStr = match[3];

        // Check for label definition
        if (label) {
            if (definedLabels.has(label)) {
                diagnostics.push(createDiagnostic(
                    new vscode.Range(i, line.indexOf(label), i, line.indexOf(label) + label.length),
                    `Duplicate label definition: '${label}'.`,
                    vscode.DiagnosticSeverity.Error
                ));
            } else {
                definedLabels.set(label, i);
            }
        }

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

        // Check operand requirements and collect used labels
        if (INSTRUCTIONS_NO_OPERAND.includes(instruction)) {
            if (operandStr !== undefined) {
                diagnostics.push(createDiagnostic(
                    new vscode.Range(i, line.indexOf(instruction), i, line.length),
                    `Instruction '${instruction}' does not take an operand.`,
                    vscode.DiagnosticSeverity.Error
                ));
            }
        } else if (INSTRUCTIONS_NO_OPERAND.includes(instruction)) {
            if (operandStr !== undefined) {
                diagnostics.push(createDiagnostic(
                    new vscode.Range(i, line.indexOf(instruction), i, line.length),
                    `Instruction '${instruction}' does not take an operand.`,
                    vscode.DiagnosticSeverity.Error
                ));
            }
        } else if (INSTRUCTIONS_WITH_OPERAND.includes(instruction) || instruction === DATA_INSTRUCTION) {
            if (operandStr === undefined) {
                diagnostics.push(createDiagnostic(
                    new vscode.Range(i, line.indexOf(instruction), i, line.length),
                    `Instruction '${instruction}' requires an operand.`,
                    vscode.DiagnosticSeverity.Error
                ));
            } else {
                if (isNaN(parseInt(operandStr))) {
                    // It's a label, collect it for second pass validation
                    const range = new vscode.Range(i, line.indexOf(operandStr), i, line.indexOf(operandStr) + operandStr.length);
                    if (!usedLabels.has(operandStr)) {
                        usedLabels.set(operandStr, []);
                    }
                    usedLabels.get(operandStr)?.push(range);
                }
            }
        }
    }

    // Second pass: Check for undefined labels
    usedLabels.forEach((ranges, usedLabel) => {
        if (!definedLabels.has(usedLabel)) {
            ranges.forEach(range => {
                diagnostics.push(createDiagnostic(
                    range,
                    `Undefined label: '${usedLabel}'.`,
                    vscode.DiagnosticSeverity.Error
                ));
            });
        }
    });

    return diagnostics;
}


function createDiagnostic(range: vscode.Range, message: string, severity: vscode.DiagnosticSeverity): vscode.Diagnostic {
    const diagnostic = new vscode.Diagnostic(range, message, severity);
    diagnostic.source = 'LMC Syntax Checker';
    return diagnostic;
}