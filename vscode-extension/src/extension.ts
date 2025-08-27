import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
// import { exec } from 'child_process'; // No longer needed
import { checkLmcSyntax } from './lmcSyntaxChecker'; // Import the syntax checker
import { LMCInterpreter } from './lmcInterpreter'; // Import the LMC Interpreter
import { formatLmcDocument } from './lmcFormatter'; // Import the LMC Formatter

const LMC_KEYWORDS = [
    "ADD", "SUB", "STA", "LDA", "BRA", "BRZ", "BRP", "INP", "OUT", "HLT", "DAT"
];

function parseLabels(document: vscode.TextDocument): Map<string, number> {
    const labels = new Map<string, number>();
    const labelPattern = /^\s*([A-Za-z_][A-Za-z0-9_]*):/;

    for (let i = 0; i < document.lineCount; i++) {
        const line = document.lineAt(i).text;
        const match = line.match(labelPattern);
        if (match) {
            const labelName = match[1];
            labels.set(labelName, i); // Store label and its line number
        }
    }
    return labels;
}

export function activate(context: vscode.ExtensionContext) {
  console.log('Congratulations, your extension "lmc-ide-extension" is now active!');

  // Create a diagnostic collection for LMC syntax errors
  const lmcDiagnostics = vscode.languages.createDiagnosticCollection('lmc');
  context.subscriptions.push(lmcDiagnostics);

  // Function to update diagnostics
  function updateDiagnostics(document: vscode.TextDocument) {
    if (document.languageId === 'lmc') {
      const diagnostics = checkLmcSyntax(document);
      lmcDiagnostics.set(document.uri, diagnostics);
    } else {
      lmcDiagnostics.delete(document.uri);
    }
  }

  // Run diagnostics when document is opened or changed
  vscode.workspace.onDidOpenTextDocument(updateDiagnostics, null, context.subscriptions);
  vscode.workspace.onDidChangeTextDocument(event => updateDiagnostics(event.document), null, context.subscriptions);
  vscode.workspace.onDidCloseTextDocument(document => lmcDiagnostics.delete(document.uri), null, context.subscriptions);

  // Initial check for all open LMC documents
  vscode.workspace.textDocuments.forEach(updateDiagnostics);


  // Register "Run LMC Code" command
  let runDisposable = vscode.commands.registerCommand('lmc-ide-extension.runLmcCode', async () => { // Added 'async'
    const editor = vscode.window.activeTextEditor;
    if (!editor) {
      vscode.window.showInformationMessage('No active text editor found.');
      return;
    }

    const document = editor.document;
    if (document.languageId !== 'lmc') {
      vscode.window.showInformationMessage('Active file is not an LMC file.');
      return;
    }

    // Before running, check for syntax errors
    const diagnostics = checkLmcSyntax(document);
    if (diagnostics.length > 0) {
        vscode.window.showErrorMessage('Cannot run LMC code due to syntax errors. Please fix them first.');
        lmcDiagnostics.set(document.uri, diagnostics); // Ensure diagnostics are shown
        return;
    }


    const lmcCode = document.getText();
    const outputChannel = vscode.window.createOutputChannel('LMC Output'); // Changed name
    outputChannel.show();
    outputChannel.appendLine('Running LMC Code...');

    // Use the LMCInterpreter
    const interpreter = new LMCInterpreter(lmcCode, outputChannel);
    await interpreter.run(); // Await the interpreter to finish

    if (interpreter.getHalted()) {
        outputChannel.appendLine('Program execution finished.');
    } else {
        outputChannel.appendLine('Program execution completed without HLT instruction.');
    }

    // Display final state of registers and memory
    outputChannel.appendLine('\n--- Final State ---');
    outputChannel.appendLine(`Accumulator: ${interpreter.getAccumulator()}`);
    outputChannel.appendLine(`Program Counter: ${interpreter.getProgramCounter()}`);
    outputChannel.appendLine('Memory:');

    const memory = interpreter.getMemory();
    const labels = interpreter.getLabels();
    const addressToLabel = new Map<number, string>();
    labels.forEach((address, label) => addressToLabel.set(address, label));

    for (let i = 0; i < memory.length; i += 10) {
        let line = `${String(i).padStart(2, '0')}-${String(i + 9).padStart(2, '0')}: `;
        for (let j = 0; j < 10; j++) {
            const currentAddress = i + j;
            if (currentAddress < memory.length) {
                const labelAtAddress = addressToLabel.get(currentAddress);
                if (labelAtAddress) {
                    line += `${labelAtAddress}(${String(memory[currentAddress]).padStart(4, '0')}) `;
                } else {
                    line += `${String(memory[currentAddress]).padStart(4, '0')} `;
                }
            }
        }
        outputChannel.appendLine(line);
    }
  });

  context.subscriptions.push(runDisposable);

  // Register CompletionItemProvider for LMC keywords and labels
  const provider = vscode.languages.registerCompletionItemProvider('lmc', {
    provideCompletionItems(document: vscode.TextDocument, position: vscode.Position) {
      const linePrefix = document.lineAt(position).text.substr(0, position.character);
      const lastWord = linePrefix.match(/\b(\w+)$/); // Match the last word

      if (!lastWord) {
        return undefined; // No word to complete
      }

      const completions: vscode.CompletionItem[] = [];

      // Add LMC keywords
      LMC_KEYWORDS.forEach(keyword => {
        const item = new vscode.CompletionItem(keyword, vscode.CompletionItemKind.Keyword);
        item.insertText = keyword; // Ensure the full keyword is inserted
        completions.push(item);
      });

      // Add labels from the document
      const labels = parseLabels(document);
      labels.forEach((lineNumber, labelName) => {
        const item = new vscode.CompletionItem(labelName, vscode.CompletionItemKind.Reference);
        item.insertText = labelName;
        completions.push(item);
      });

      return completions;
    }
  });

  context.subscriptions.push(provider);

  // Register DocumentFormattingEditProvider
  context.subscriptions.push(
    vscode.languages.registerDocumentFormattingEditProvider('lmc', {
      provideDocumentFormattingEdits(document: vscode.TextDocument): vscode.TextEdit[] {
        return formatLmcDocument(document);
      }
    })
  );
}

export function deactivate() {
    // Clean up diagnostic collection when extension is deactivated
    // This is handled automatically by pushing to context.subscriptions
}
