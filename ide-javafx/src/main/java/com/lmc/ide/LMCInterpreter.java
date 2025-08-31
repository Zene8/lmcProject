package com.lmc.ide;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class LMCInterpreter {
    public enum ExecutionState {
        RUNNING, HALTED, STOPPED, AWAITING_INPUT, ERROR, BREAKPOINT_HIT
    }

    private static final int MEMORY_SIZE = 100;
    private int[] memory = new int[MEMORY_SIZE];
    private int programCounter = 0;
    private int accumulator = 0;
    private int lastAccessedAddress = -1;
    private InputProvider inputProvider;
    private StringBuilder outputBuffer = new StringBuilder();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private String errorMessage = null;
    private Integer inputValue = null;
    private Set<Integer> breakpoints = new HashSet<>();
    private LMCParser.AssembledCode assembledCode;

    public LMCParser.AssembledCode getAssembledCode() {
        return assembledCode;
    }

    public void load(LMCParser.AssembledCode assembledCode, InputProvider provider) {
        this.inputProvider = provider;
        this.assembledCode = assembledCode;
        this.outputBuffer.setLength(0);
        this.errorMessage = null;
        this.programCounter = 0;
        this.accumulator = 0;
        this.lastAccessedAddress = -1;
        this.inputValue = null;
        Arrays.fill(memory, 0);
        for (Map.Entry<Integer, Integer> entry : assembledCode.memoryMap.entrySet()) {
            if (entry.getKey() >= 0 && entry.getKey() < MEMORY_SIZE) {
                memory[entry.getKey()] = entry.getValue();
            }
        }
    }

    public void start() {
        this.running.set(true);
    }

    public void stop() {
        this.running.set(false);
    }

    public void setInputValue(int value) {
        this.inputValue = value;
    }

    public void setBreakpoints(Set<Integer> breakpoints) {
        this.breakpoints = new HashSet<>(breakpoints);
    }

    public ExecutionState step() {
        if (!running.get())
            return ExecutionState.STOPPED;
        if (breakpoints.contains(programCounter))
            return ExecutionState.BREAKPOINT_HIT;
        if (programCounter < 0 || programCounter >= MEMORY_SIZE) {
            errorMessage = "Program counter out of bounds: " + programCounter;
            return ExecutionState.ERROR;
        }
        lastAccessedAddress = -1;
        int instruction = memory[programCounter];
        int opcode = instruction / 100;
        int operand = instruction % 100;
        int nextPC = programCounter + 1;
        try {
            switch (opcode) {
                case 1:
                    accumulator += read(operand);
                    break;
                case 2:
                    accumulator -= read(operand);
                    break;
                case 3:
                    write(operand, accumulator);
                    break;
                case 5:
                    accumulator = read(operand);
                    break;
                case 6:
                    nextPC = operand;
                    break;
                case 7:
                    if (accumulator == 0)
                        nextPC = operand;
                    break;
                case 8:
                    if (accumulator >= 0)
                        nextPC = operand;
                    break;
                case 9:
                    if (operand == 1) { // INP
                        if (inputValue != null) {
                            accumulator = inputValue;
                            inputValue = null;
                        } else {
                            inputProvider.requestInput().thenAccept(this::setInputValue);
                            return ExecutionState.AWAITING_INPUT;
                        }
                    } else if (operand == 2) { // OUT
                        outputBuffer.append(accumulator).append("\n");
                    }
                    break;
                case 0:
                    if (instruction == 0)
                        return ExecutionState.HALTED;
                    throw new RuntimeException("Attempted to execute data as instruction");
                default:
                    throw new RuntimeException("Unknown instruction opcode: " + opcode);
            }
        } catch (Exception e) {
            errorMessage = e.getMessage();
            return ExecutionState.ERROR;
        }
        programCounter = nextPC;
        return ExecutionState.RUNNING;
    }

    private int read(int address) {
        if (address < 0 || address >= MEMORY_SIZE)
            throw new RuntimeException("Memory address out of bounds: " + address);
        lastAccessedAddress = address;
        return memory[address];
    }

    private void write(int address, int value) {
        if (address < 0 || address >= MEMORY_SIZE)
            throw new RuntimeException("Memory address out of bounds: " + address);
        lastAccessedAddress = address;
        memory[address] = value;
    }

    public int getProgramCounter() {
        return programCounter;
    }

    public int getLastAccessedAddress() {
        return lastAccessedAddress;
    }

    public int[] getMemory() {
        return memory;
    }

    public String getOutput() {
        return outputBuffer.toString();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void resetProgram() {
        this.programCounter = 0;
        this.accumulator = 0;
        this.lastAccessedAddress = -1;
        this.outputBuffer.setLength(0);
        this.errorMessage = null;
        this.inputValue = null;
        this.running.set(false);
        Arrays.fill(memory, 0);
        if (assembledCode != null) {
            for (Map.Entry<Integer, Integer> entry : assembledCode.memoryMap.entrySet()) {
                if (entry.getKey() >= 0 && entry.getKey() < MEMORY_SIZE) {
                    memory[entry.getKey()] = entry.getValue();
                }
            }
        }
    }
