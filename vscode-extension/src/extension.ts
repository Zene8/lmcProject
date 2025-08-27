import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
// import { exec } from 'child_process'; // No longer needed
import { checkLmcSyntax } from './lmcSyntaxChecker'; // Import the syntax checker
import { LMCInterpreter } from './lmcInterpreter'; // Import the LMC Interpreter

const LMC_KEYWORDS = [
    "ADD", "SUB", "STA", "LDA", "BRA", "BRZ", "BRP", "INP", "OUT", "HLT", "DAT"
];

function parseLabels(document: vscode.TextDocument): Map<string, number> {
    const labels = new Map<string, number>();
    for (let i = 0; i < document.lineCount; i++) {
        const line = document.lineAt(i).text.trim();
        if (line.length === 0 || line.startsWith('//')) {
            continue;
        }

        const parts = line.split(/\s+/);
        if (parts.length === 0) {
            continue;
        }

        // A label is a word at the beginning of the line that is not an instruction
        if (!LMC_KEYWORDS.includes(parts[0].toUpperCase())) {
            labels.set(parts[0], i); // Store label and its line number
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
}

export function deactivate() {
    // Clean up diagnostic collection when extension is deactivated
    // This is handled automatically by pushing to context.subscriptions
}
