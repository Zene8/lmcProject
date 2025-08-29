import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import { LMCInterpreter } from './lmcInterpreter';
import { checkLmcSyntax } from './lmcSyntaxChecker';
import { formatLmcDocument } from './lmcFormatter';

let interpreter: LMCInterpreter | null = null;
let debuggerPanel: vscode.WebviewPanel | null = null;
let referencePanel: vscode.WebviewPanel | null = null;

const LMC_KEYWORDS = [
    "ADD", "SUB", "STA", "LDA", "BRA", "BRZ", "BRP", "INP", "OUT", "HLT", "DAT"
];

const LMC_INSTRUCTION_DETAILS = new Map<string, string>([
    ['ADD', '**ADD (Add)**\n\nAdds the value from the specified memory address to the accumulator. Opcode: 1xx'],
    ['SUB', '**SUB (Subtract)**\n\nSubtracts the value from the specified memory address from the accumulator. Opcode: 2xx'],
    ['STA', '**STA (Store)**\n\nStores the value from the accumulator to the specified memory address. Opcode: 3xx'],
    ['LDA', '**LDA (Load)**\n\nLoads the value from the specified memory address into the accumulator. Opcode: 5xx'],
    ['BRA', '**BRA (Branch Always)**\n\nBranches to the specified memory address unconditionally. Opcode: 6xx'],
    ['BRZ', '**BRZ (Branch if Zero)**\n\nBranches to the specified memory address if the accumulator is zero. Opcode: 7xx'],
    ['BRP', '**BRP (Branch if Positive)**\n\nBranches to the specified memory address if the accumulator is positive or zero. Opcode: 8xx'],
    ['INP', '**INP (Input)**\n\nTakes user input and stores it in the accumulator. Opcode: 901'],
    ['OUT', '**OUT (Output)**\n\nOutputs the value from the accumulator. Opcode: 902'],
    ['HLT', '**HLT (Halt)**\n\nHalts the program. Opcode: 000'],
    ['DAT', '**DAT (Data)**\n\nReserves a memory location and initializes it with a value.']
]);

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

    const lmcDiagnostics = vscode.languages.createDiagnosticCollection('lmc');
    context.subscriptions.push(lmcDiagnostics);

    function updateDiagnostics(document: vscode.TextDocument) {
        if (document.languageId === 'lmc') {
            const diagnostics = checkLmcSyntax(document);
            lmcDiagnostics.set(document.uri, diagnostics);
        } else {
            lmcDiagnostics.delete(document.uri);
        }
    }

    vscode.workspace.onDidOpenTextDocument(updateDiagnostics, null, context.subscriptions);
    vscode.workspace.onDidChangeTextDocument(event => updateDiagnostics(event.document), null, context.subscriptions);
    vscode.workspace.onDidCloseTextDocument(document => lmcDiagnostics.delete(document.uri), null, context.subscriptions);
    vscode.workspace.textDocuments.forEach(updateDiagnostics);

    context.subscriptions.push(vscode.commands.registerCommand('lmc-ide-extension.runLmcCode', () => runLmcCode(false)));
    context.subscriptions.push(vscode.commands.registerCommand('lmc-ide-extension.runLmcCodeSlow', () => runLmcCode(true)));
    context.subscriptions.push(vscode.commands.registerCommand('lmc-ide-extension.debugLmcCode', debugLmcCode));
    context.subscriptions.push(vscode.commands.registerCommand('lmc-ide-extension.stepOver', () => interpreter?.stepOver()));
    context.subscriptions.push(vscode.commands.registerCommand('lmc-ide-extension.resume', () => interpreter?.resume()));
    context.subscriptions.push(vscode.commands.registerCommand('lmc-ide-extension.stop', stopInterpreter));
    context.subscriptions.push(vscode.commands.registerCommand('lmc-ide-extension.showReference', createReferencePanel));

    const hoverProvider = vscode.languages.registerHoverProvider('lmc', {
        provideHover(document, position, token) {
            const range = document.getWordRangeAtPosition(position);
            const word = document.getText(range);
            if (LMC_INSTRUCTION_DETAILS.has(word.toUpperCase())) {
                return new vscode.Hover(LMC_INSTRUCTION_DETAILS.get(word.toUpperCase())!);
            }
        }
    });
    context.subscriptions.push(hoverProvider);

    const completionProvider = vscode.languages.registerCompletionItemProvider('lmc', {
        provideCompletionItems(document: vscode.TextDocument, position: vscode.Position): vscode.ProviderResult<vscode.CompletionItem[] | vscode.CompletionList> {
            const linePrefix = document.lineAt(position).text.substr(0, position.character);
            const lastWord = linePrefix.match(/\b(\w+)$/);

            if (!lastWord) {
                return undefined;
            }

            const completions: vscode.CompletionItem[] = [];

            LMC_KEYWORDS.forEach(keyword => {
                const item = new vscode.CompletionItem(keyword, vscode.CompletionItemKind.Keyword);
                item.insertText = new vscode.SnippetString(`${keyword} $0`);
                completions.push(item);
            });

            const labels = parseLabels(document);
            labels.forEach((lineNumber, labelName) => {
                const item = new vscode.CompletionItem(labelName, vscode.CompletionItemKind.Reference);
                item.insertText = labelName;
                completions.push(item);
            });

            return completions;
        }
    });
    context.subscriptions.push(completionProvider);

    context.subscriptions.push(
        vscode.languages.registerDocumentFormattingEditProvider('lmc', {
            provideDocumentFormattingEdits(document: vscode.TextDocument): vscode.TextEdit[] {
                return formatLmcDocument(document);
            }
        })
    );
}

async function runLmcCode(slowMode: boolean) {
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

    const diagnostics = checkLmcSyntax(document);
    if (diagnostics.length > 0) {
        vscode.window.showErrorMessage('Cannot run LMC code due to syntax errors. Please fix them first.');
        return;
    }

    const lmcCode = document.getText();
    const outputChannel = vscode.window.createOutputChannel('LMC Output');
    outputChannel.show();
    outputChannel.appendLine(`Running LMC Code${slowMode ? ' in Slow Mode' : ''}...`);

    interpreter = new LMCInterpreter(lmcCode, outputChannel);

    if (slowMode) {
        createDebuggerPanel();
        interpreter.setWebViewPanel(debuggerPanel!); 
        await interpreter.runSlow(500);
    } else {
        await interpreter.run();
    }

    if (interpreter.getHalted()) {
        outputChannel.appendLine('Program execution finished.');
    } else {
        outputChannel.appendLine('Program execution completed without HLT instruction.');
    }

    outputChannel.appendLine('\n--- Final State ---');
    outputChannel.appendLine(`Accumulator: ${interpreter.getAccumulator()}`);
    outputChannel.appendLine(`Program Counter: ${interpreter.getProgramCounter()}`);
}

function debugLmcCode() {
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

    const diagnostics = checkLmcSyntax(document);
    if (diagnostics.length > 0) {
        vscode.window.showErrorMessage('Cannot debug LMC code due to syntax errors. Please fix them first.');
        return;
    }

    const lmcCode = document.getText();
    const outputChannel = vscode.window.createOutputChannel('LMC Output');
    outputChannel.show();
    outputChannel.appendLine('Starting LMC debugger...');

    interpreter = new LMCInterpreter(lmcCode, outputChannel);
    interpreter.pause();

    createDebuggerPanel();
    interpreter.setWebViewPanel(debuggerPanel!);
    interpreter.updateWebView();
}

function createDebuggerPanel() {
    if (debuggerPanel) {
        debuggerPanel.reveal(vscode.ViewColumn.Two);
    } else {
        debuggerPanel = vscode.window.createWebviewPanel(
            'lmcDebugger',
            'LMC Debugger',
            vscode.ViewColumn.Two,
            {
                enableScripts: true
            }
        );

        debuggerPanel.webview.html = getDebuggerWebviewContent();

        debuggerPanel.onDidDispose(() => {
            debuggerPanel = null;
            stopInterpreter();
        });
    }
}

function createReferencePanel() {
    if (referencePanel) {
        referencePanel.reveal(vscode.ViewColumn.Two);
    } else {
        referencePanel = vscode.window.createWebviewPanel(
            'lmcReference',
            'LMC Reference',
            vscode.ViewColumn.Two,
            {
                enableScripts: true
            }
        );

        referencePanel.webview.html = getReferenceWebviewContent();

        referencePanel.onDidDispose(() => {
            referencePanel = null;
        });
    }
}

function stopInterpreter() {
    if (interpreter) {
        interpreter.pause();
        interpreter = null;
    }
    if (debuggerPanel) {
        debuggerPanel.dispose();
    }
}

function getDebuggerWebviewContent(): string {
    const htmlPath = path.resolve(path.join(__dirname, '..', 'src', 'debugger.html'));
    return fs.readFileSync(htmlPath, 'utf-8');
}

function getReferenceWebviewContent(): string {
    const htmlPath = path.resolve(path.join(__dirname, '..', 'src', 'reference.html'));
    return fs.readFileSync(htmlPath, 'utf-8');
}

export function deactivate() {
    stopInterpreter();
}