import * as vscode from 'vscode';

const LMC_INSTRUCTIONS = new Set([
    "INP", "OUT", "LDA", "STA", "ADD", "SUB", "BRA", "BRZ", "BRP", "HLT", "DAT"
]);

// Pattern to match a line: [LABEL] INSTRUCTION [OPERAND] [// COMMENT]
const LMC_LINE_PATTERN = new RegExp(
    "^\\s*" +                               // Optional leading whitespace
    "(?:([A-Za-z_][A-Za-z0-9_]*)\\s*:\s*)?" + // Optional Label (Group 1) with optional colon and whitespace
    "([A-Za-z]{3})" +                      // Instruction (Group 2)
    "(?:\\s+([A-Za-z_][A-Za-z0-9_]*|\\d+))?" + // Optional Operand (Group 3: label or number)
    "(?:\\s*//.*)?" +                     // Optional Comment
    "\\s*$"                                // Optional trailing whitespace and end of line
);

export function formatLmcDocument(document: vscode.TextDocument): vscode.TextEdit[] {
    const edits: vscode.TextEdit[] = [];
    const lines = document.getText().split(/\r?\n/);

    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        const trimmedLine = line.trim();

        if (trimmedLine.length === 0) {
            continue; // Skip empty lines
        }

        // Preserve full-line comments
        if (trimmedLine.startsWith("//") || trimmedLine.startsWith(";")) {
            // Ensure comments are not indented if they are full-line comments
            if (line !== trimmedLine) {
                edits.push(vscode.TextEdit.replace(
                    new vscode.Range(i, 0, i, line.length),
                    trimmedLine
                ));
            }
            continue;
        }

        const match = trimmedLine.match(LMC_LINE_PATTERN);
        if (match) {
            let formattedLine = "";
            const label = match[1];
            const instruction = match[2];
            const operand = match[3];
            const commentIndex = trimmedLine.indexOf("//");
            const comment = commentIndex !== -1 ? trimmedLine.substring(commentIndex) : null;

            // Auto capitalize label and append
            if (label) {
                formattedLine += label.toUpperCase() + ":\t";
            }

            // Auto capitalize instruction and append
            formattedLine += instruction.toUpperCase();

            // Append operand if present
            if (operand) {
                formattedLine += "\t" + operand.toUpperCase();
            }

            // Append comment if present
            if (comment) {
                // Align comments if there's an instruction/operand
                if (label || instruction || operand) {
                    formattedLine += "\t"; // Add a tab for alignment
                }
                formattedLine += comment;
            }

            // Apply basic indentation for lines without labels
            if (!label && formattedLine.length > 0 && !formattedLine.startsWith("//") && !formattedLine.startsWith(";")) {
                formattedLine = "\t" + formattedLine; // Indent with one tab
            }

            if (formattedLine !== line) {
                edits.push(vscode.TextEdit.replace(
                    new vscode.Range(i, 0, i, line.length),
                    formattedLine
                ));
            }
        } else {
            // If line doesn't match pattern, just ensure it's trimmed
            if (line !== trimmedLine) {
                edits.push(vscode.TextEdit.replace(
                    new vscode.Range(i, 0, i, line.length),
                    trimmedLine
                ));
            }
        }
    }

    return edits;
}
