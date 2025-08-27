package com.lmc.ide;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class LMCInterpreter {

    private static final int MEMORY_SIZE = 100;
    private static final int ACC_MIN = -999;
    private static final int ACC_MAX = 999;

    private int[] memory;
    private int programCounter;
    private int accumulator;
    private InputProvider inputProvider;
    private StringBuilder outputBuffer;
    private final AtomicBoolean running = new AtomicBoolean(false); // FIX: For stopping execution

    public LMCInterpreter() {
        memory = new int[MEMORY_SIZE];
        outputBuffer = new StringBuilder();
    }

    public void stop() {
        running.set(false);
    }

    public String run(String lmcCode, InputProvider inputProvider)
            throws LMCParser.LMCParseException, LMCRuntimeException {
        this.inputProvider = inputProvider;
        this.outputBuffer.setLength(0);
        this.running.set(true); // Start execution

        LMCParser parser = new LMCParser();
        Map<Integer, Integer> assembledMemory = parser.parse(lmcCode);

        // Initialize memory
        for (int i = 0; i < MEMORY_SIZE; i++) {
            memory[i] = 0;
        }
        for (Map.Entry<Integer, Integer> entry : assembledMemory.entrySet()) {
            int address = entry.getKey();
            if (address < 0 || address >= MEMORY_SIZE) {
                throw new LMCRuntimeException("Memory address out of bounds: " + address);
            }
            memory[address] = entry.getValue();
        }

        programCounter = 0;
        accumulator = 0;

        // FIX: Loop now checks the 'running' flag instead of a cycle count
        while (programCounter < MEMORY_SIZE && running.get()) {
            int instruction = safeRead(programCounter);
            int opcode = instruction / 100;
            int operand = instruction % 100;

            programCounter++; // Pre-increment PC

            switch (opcode) {
                case 1: // ADD
                    accumulator += safeRead(operand);
                    break;
                case 2: // SUB
                    accumulator -= safeRead(operand);
                    break;
                case 3: // STA
                    safeWrite(operand, accumulator);
                    break;
                case 5: // LDA
                    accumulator = safeRead(operand);
                    break;
                case 6: // BRA
                    programCounter = operand;
                    break;
                case 7: // BRZ
                    if (accumulator == 0)
                        programCounter = operand;
                    break;
                case 8: // BRP
                    if (accumulator >= 0)
                        programCounter = operand;
                    break;
                case 9: // INP / OUT
                    switch (operand) {
                        case 1: // INP
                            if (!running.get())
                                return "Execution stopped by user.";
                            accumulator = inputProvider.requestInput().join();
                            break;
                        case 2: // OUT
                            outputBuffer.append(accumulator).append("\n");
                            break;
                        default:
                            throw new LMCRuntimeException("Unknown I/O instruction: 9" + operand);
                    }
                    break;
                case 0: // HLT or DAT
                    if (instruction == 0) {
                        running.set(false);
                        return outputBuffer.toString().trim(); // Halt
                    } else {
                        throw new LMCRuntimeException("Attempted to execute data as instruction at address "
                                + (programCounter - 1) + ": " + instruction);
                    }
                default:
                    throw new LMCRuntimeException("Unknown instruction at address "
                            + (programCounter - 1) + ": " + instruction);
            }
            accumulator = Math.max(ACC_MIN, Math.min(ACC_MAX, accumulator));
        }

        if (!running.get()) {
            return outputBuffer.append("\nExecution stopped by user.").toString().trim();
        }
        throw new LMCRuntimeException("Program exceeded memory bounds or did not halt.");
    }

    private int safeRead(int address) throws LMCRuntimeException {
        if (address < 0 || address >= MEMORY_SIZE) {
            throw new LMCRuntimeException("Invalid memory read at address: " + address);
        }
        return memory[address];
    }

    private void safeWrite(int address, int value) throws LMCRuntimeException {
        if (address < 0 || address >= MEMORY_SIZE) {
            throw new LMCRuntimeException("Invalid memory write at address: " + address);
        }
        memory[address] = value;
    }

    public static class LMCRuntimeException extends Exception {
        public LMCRuntimeException(String message) {
            super(message);
        }
    }
}