import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import { exec } from 'child_process';
import { checkLmcSyntax } from './lmcSyntaxChecker'; // Import the syntax checker

const LMC_KEYWORDS = [
    "ADD", "SUB", "STA", "LDA", "BRA", "BRZ", "BRP", "INP", "OUT", "HLT", "DAT"
];

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
  let runDisposable = vscode.commands.registerCommand('lmc-ide-extension.runLmcCode', () => {
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
    const outputChannel = vscode.window.createOutputChannel('LMC Compiler Output');
    outputChannel.show();
    outputChannel.appendLine('Running LMC Code...');

    // Create a temporary file for the LMC code
    const tempDir = path.join(context.extensionPath, 'temp');
    if (!fs.existsSync(tempDir)) {
      fs.mkdirSync(tempDir);
    }
    const tempFilePath = path.join(tempDir, `input_${Date.now()}.lmc`);

    fs.writeFile(tempFilePath, lmcCode, (err) => {
      if (err) {
        outputChannel.appendLine(`Error creating temporary file: ${err.message}`);
        vscode.window.showErrorMessage(`Error creating temporary file: ${err.message}`);
        return;
      }

      // Assuming the compiler executable is in the root of the LMC project
      // This path needs to be adjusted based on where the compiler actually resides
      // For now, let's assume it's in a 'compiler' directory relative to the extension root
      const compilerPath = path.join(context.extensionPath, '..', '..', 'LittleMachineCompiler', 'compiler'); // Adjust this path as needed

      exec(`${compilerPath} ${tempFilePath}`, (error, stdout, stderr) => {
        if (error) {
          outputChannel.appendLine(`Compiler Error: ${error.message}`);
          vscode.window.showErrorMessage(`LMC Compiler Error: ${error.message}`);
        }
        if (stdout) {
          outputChannel.appendLine('Compiler Output:\n' + stdout);
        }
        if (stderr) {
          outputChannel.appendLine('Compiler Stderr:\n' + stderr);
        }

        // Clean up temporary file
        fs.unlink(tempFilePath, (unlinkErr) => {
          if (unlinkErr) {
            console.error(`Error deleting temporary file: ${unlinkErr.message}`);
          }
        });
      });
    });
  });

  context.subscriptions.push(runDisposable);

  // Register CompletionItemProvider for LMC keywords
  const provider = vscode.languages.registerCompletionItemProvider('lmc', {
    provideCompletionItems(document: vscode.TextDocument, position: vscode.Position) {
      const linePrefix = document.lineAt(position).text.substr(0, position.character);
      const lastWord = linePrefix.match(/\b(\w+)$/); // Match the last word

      if (!lastWord) {
        return undefined; // No word to complete
      }

      const completions = LMC_KEYWORDS.map(keyword => {
        const item = new vscode.CompletionItem(keyword, vscode.CompletionItemKind.Keyword);
        item.insertText = keyword; // Ensure the full keyword is inserted
        return item;
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