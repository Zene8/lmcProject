import * as vscode from 'vscode';

export class LMCInterpreter {
    private memory: number[];
    private programCounter: number;
    private accumulator: number;
    private labels: Map<string, number>;
    private programLines: string[];
    private inputQueue: number[];
    private outputChannel: vscode.OutputChannel;
    private isHalted: boolean;
    private panel: vscode.WebviewPanel | undefined;

    // LMC instruction opcodes (simplified for interpreter)
    private static readonly OPCODE_MAP: Map<string, number> = new Map([
        ["ADD", 100],
        ["SUB", 200],
        ["STA", 300],
        ["LDA", 500],
        ["BRA", 600],
        ["BRZ", 700],
        ["BRP", 800],
        ["INP", 901],
        ["OUT", 902],
        ["HLT", 0],
        ["DAT", 0]
    ]);

    constructor(lmcCode: string, outputChannel: vscode.OutputChannel) {
        this.memory = new Array(100).fill(0); // LMC has 100 memory locations (00-99)
        this.programCounter = 0;
        this.accumulator = 0;
        this.labels = new Map<string, number>();
        this.programLines = lmcCode.split('\n');
        this.inputQueue = [];
        this.outputChannel = outputChannel;
        this.isHalted = false;

        this.parseLabelsAndData();
    }

    public setWebViewPanel(panel: vscode.WebviewPanel) {
        this.panel = panel;
    }

    private updateWebView() {
        if (this.panel) {
            this.panel.webview.postMessage({
                command: 'update',
                memory: this.getMemory(),
                pc: this.getProgramCounter(),
                acc: this.getAccumulator(),
            });
        }
    }

    private parseLabelsAndData() {
        const labelPattern = /^\s*([A-Za-z_][A-Za-z0-9_]*):/;

        for (let i = 0; i < this.programLines.length; i++) {
            const line = this.programLines[i];
            const trimmedLine = line.trim();
            if (trimmedLine.length === 0 || trimmedLine.startsWith('//')) continue;

            let parts = trimmedLine.split(/\s+/);
            if (parts.length === 0) continue;

            const labelMatch = line.match(labelPattern);
            let hasLabel = false;
            if (labelMatch) {
                const labelName = labelMatch[1];
                this.labels.set(labelName, i); // Store line number for label
                hasLabel = true;
                // Remove the label part from the line for further processing
                parts = trimmedLine.replace(labelMatch[0], '').trim().split(/\s+/);
                if (parts.length === 0) continue; // Line was just a label
            }

            // Handle DAT instruction and store in memory
            if (parts.length >= 1 && parts[0].toUpperCase() === "DAT") {
                if (parts.length >= 2) {
                    try {
                        const value = parseInt(parts[1]);
                        this.memory[i] = value;
                    } catch (e: any) {
                        this.outputChannel.appendLine(`Error: Invalid data value on line ${i + 1}: ${e.message}`);
                        this.isHalted = true;
                    }
                } else {
                    // DAT without an explicit value defaults to 0
                    this.memory[i] = 0;
                }
            } else if (parts.length >= 1 && LMCInterpreter.isInstruction(parts[0])) {
                // Store instruction opcode in memory
                const instruction = parts[0].toUpperCase();
                const opcode = LMCInterpreter.OPCODE_MAP.get(instruction) || 0; // Default to 0 for HLT/DAT
                let operand = 0;

                if (parts.length > 1) {
                    try {
                        operand = parseInt(parts[1]);
                    } catch (e) {
                        // If operand is a label, its address will be resolved during execution
                        // For now, store 0, and resolve during execution
                        operand = 0;
                    }
                }
                this.memory[i] = opcode + operand;
            }
        }
    }

    private static isInstruction(s: string): boolean {
        return LMCInterpreter.OPCODE_MAP.has(s.toUpperCase());
    }

    public async run(): Promise<void> {
        while (this.programCounter < this.programLines.length && !this.isHalted) {
            await this.step();
        }
    }

    public async runSlow(delay: number): Promise<void> {
        if (this.isHalted) return;

        await this.step();
        this.updateWebView();

        if (!this.isHalted) {
            setTimeout(() => this.runSlow(delay), delay);
        }
    }

    public async step(): Promise<void> {
        if (this.isHalted) return;

        if (this.programCounter >= this.programLines.length) {
            this.outputChannel.appendLine("Error: Program Counter out of bounds.");
            this.isHalted = true;
            return;
        }

        let line = this.programLines[this.programCounter].trim();
        if (line.length === 0 || line.startsWith('//')) {
            this.programCounter++;
            return;
        }

        let parts = line.split(/\s+/);
        if (parts.length === 0) {
            this.programCounter++;
            return;
        }

        // Skip label if present (already handled in parseLabelsAndData)
        const potentialInstruction = parts[0];
        if (!LMCInterpreter.isInstruction(potentialInstruction) && this.labels.has(potentialInstruction) && parts.length > 1) {
            parts = parts.slice(1);
        } else if (!LMCInterpreter.isInstruction(potentialInstruction) && this.labels.has(potentialInstruction) && parts.length === 1) {
            this.programCounter++;
            return;
        }

        const instruction = parts[0].toUpperCase();
        let operand = 0; // Default for instructions without operand
        if (parts.length > 1) {
            try {
                operand = parseInt(parts[1]);
            } catch (e) {
                // If not a number, it must be a label
                if (this.labels.has(parts[1])) {
                    operand = this.labels.get(parts[1])!;
                } else {
                    this.outputChannel.appendLine(`Error: Undefined label or invalid operand on line ${this.programCounter + 1}`);
                    this.isHalted = true;
                    return;
                }
            }
        }

        try {
            switch (instruction) {
                case "INP":
                    const input = await vscode.window.showInputBox({ prompt: 'Enter an integer for INP:' });
                    if (input === undefined) { // User cancelled input
                        this.outputChannel.appendLine("Program terminated by user (INP cancelled).");
                        this.isHalted = true;
                        return;
                    }
                    const numInput = parseInt(input);
                    if (isNaN(numInput)) {
                        this.outputChannel.appendLine("Error: Invalid input. Please enter an integer.");
                        this.isHalted = true;
                        return;
                    }
                    this.accumulator = numInput;
                    this.outputChannel.appendLine(`Input: ${this.accumulator}`);
                    break;
                case "OUT":
                    this.outputChannel.appendLine(`Output: ${this.accumulator}`);
                    break;
                case "LDA":
                    this.accumulator = this.memory[operand];
                    break;
                case "STA":
                    this.memory[operand] = this.accumulator;
                    break;
                case "ADD":
                    this.accumulator += this.memory[operand];
                    break;
                case "SUB":
                    this.accumulator -= this.memory[operand];
                    break;
                case "BRA":
                    this.programCounter = operand - 1; // -1 because programCounter increments after switch
                    break;
                case "BRZ":
                    if (this.accumulator === 0) {
                        this.programCounter = operand - 1;
                    }
                    break;
                case "BRP":
                    if (this.accumulator >= 0) {
                        this.programCounter = operand - 1;
                    }
                    break;
                case "HLT":
                    this.outputChannel.appendLine("Program Halted.");
                    this.isHalted = true;
                    return;
                case "DAT":
                    // Handled during parsing phase
                    break;
                default:
                    this.outputChannel.appendLine(`Error: Unknown instruction ${instruction} on line ${this.programCounter + 1}`);
                    this.isHalted = true;
                    return;
            }
        } catch (e: any) {
            this.outputChannel.appendLine(`Runtime Error: ${e.message} at address ${operand} on line ${this.programCounter + 1}`);
            this.isHalted = true;
            return;
        }

        this.programCounter++;
        this.updateWebView();
    }

    public getHalted(): boolean {
        return this.isHalted;
    }

    public getMemory(): number[] {
        return [...this.memory]; // Return a copy to prevent external modification
    }

    public getAccumulator(): number {
        return this.accumulator;
    }

    public getProgramCounter(): number {
        return this.programCounter;
    }

    public getLabels(): Map<string, number> {
        return new Map(this.labels); // Return a copy
    }
}