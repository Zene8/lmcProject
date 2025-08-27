package com.lmc.ide;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LMCParser {

    // Inner class to hold the result of parsing
    public static class AssembledCode {
        public final Map<Integer, Integer> memoryMap;
        public final Map<Integer, Integer> addressToLineMap;

        AssembledCode(Map<Integer, Integer> memoryMap, Map<Integer, Integer> addressToLineMap) {
            this.memoryMap = Collections.unmodifiableMap(memoryMap);
            this.addressToLineMap = Collections.unmodifiableMap(addressToLineMap);
        }
    }

    private static final int INP = 901;
    private static final int OUT = 902;
    private static final int ADD = 100;
    private static final int SUB = 200;
    private static final int STA = 300;
    private static final int LDA = 500;
    private static final int BRA = 600;
    private static final int BRZ = 700;
    private static final int BRP = 800;
    private static final int HLT = 0;

    private static final Set<String> INSTRUCTIONS_WITH_OPERAND = Set.of(
            "ADD", "SUB", "STA", "LDA", "BRA", "BRZ", "BRP");

    private static final Pattern LINE_PATTERN = Pattern.compile(
            "^(?:([A-Z_][A-Z0-9_]*):?\\s*)?" +
                    "(ADD|SUB|STA|LDA|BRA|BRZ|BRP|INP|OUT|HLT|DAT)" +
                    "(?:\\s+([A-Z_][A-Z0-9_]*|\\d+))?" +
                    "\\s*(?://.*)?$",
            Pattern.CASE_INSENSITIVE);

    public AssembledCode parse(String code) throws LMCParseException {
        Map<String, Integer> symbolTable = new HashMap<>();
        List<String[]> parsedLines = new ArrayList<>();
        Map<Integer, Integer> memoryMap = new HashMap<>();
        Map<Integer, Integer> addressToLineMap = new HashMap<>();

        int currentAddress = 0;
        String[] lines = code.split("\\r?\\n");

        // Pass 1: Build symbol table and address-to-line map
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.matches("^\\s*(//|#).*")) {
                continue;
            }

            Matcher matcher = LINE_PATTERN.matcher(line);
            if (!matcher.matches()) {
                throw new LMCParseException("Syntax error: Invalid line format.", i + 1);
            }

            addressToLineMap.put(currentAddress, i);

            String label = matcher.group(1);
            String instruction = matcher.group(2);
            String operand = matcher.group(3);

            if (label != null) {
                label = label.toUpperCase().replace(":", "");
                if (symbolTable.containsKey(label)) {
                    throw new LMCParseException("Duplicate label: " + label, i + 1);
                }
                symbolTable.put(label, currentAddress);
            }
            parsedLines.add(new String[] { instruction.toUpperCase(), operand });
            currentAddress++;
        }

        currentAddress = 0;

        // Pass 2: Assemble instructions
        for (int i = 0; i < parsedLines.size(); i++) {
            String[] parts = parsedLines.get(i);
            String instruction = parts[0];
            String operand = parts[1];
            int operandValue = 0;

            if ("DAT".equalsIgnoreCase(instruction)) {
                if (operand != null && !operand.isEmpty()) {
                    try {
                        operandValue = Integer.parseInt(operand);
                    } catch (NumberFormatException e) {
                        String upperOperand = operand.toUpperCase();
                        if (symbolTable.containsKey(upperOperand)) {
                            operandValue = symbolTable.get(upperOperand);
                        } else {
                            throw new LMCParseException("Unknown label for DAT: " + operand, i + 1);
                        }
                    }
                }
                memoryMap.put(currentAddress, operandValue);
            } else {
                int opcode = getOpcode(instruction, i + 1);
                if (INSTRUCTIONS_WITH_OPERAND.contains(instruction)) {
                    if (operand == null || operand.isEmpty()) {
                        throw new LMCParseException("Missing operand for instruction: " + instruction, i + 1);
                    }
                    try {
                        operandValue = Integer.parseInt(operand);
                    } catch (NumberFormatException e) {
                        String upperOperand = operand.toUpperCase();
                        if (symbolTable.containsKey(upperOperand)) {
                            operandValue = symbolTable.get(upperOperand);
                        } else {
                            throw new LMCParseException("Unknown label or invalid operand: " + operand, i + 1);
                        }
                    }
                    memoryMap.put(currentAddress, opcode + operandValue);
                } else {
                    if (operand != null && !operand.isEmpty()) {
                        throw new LMCParseException("Instruction " + instruction + " does not take an operand.", i + 1);
                    }
                    memoryMap.put(currentAddress, opcode);
                }
            }
            currentAddress++;
        }
        return new AssembledCode(memoryMap, addressToLineMap);
    }

    private int getOpcode(String instruction, int line) throws LMCParseException {
        switch (instruction.toUpperCase()) {
            case "INP":
                return INP;
            case "OUT":
                return OUT;
            case "ADD":
                return ADD;
            case "SUB":
                return SUB;
            case "STA":
                return STA;
            case "LDA":
                return LDA;
            case "BRA":
                return BRA;
            case "BRZ":
                return BRZ;
            case "BRP":
                return BRP;
            case "HLT":
                return HLT;
            default:
                throw new LMCParseException("Unknown instruction: " + instruction, line);
        }
    }

    public static class LMCParseException extends Exception {
        private final int lineNumber;

        public LMCParseException(String message, int lineNumber) {
            super("Line " + lineNumber + ": " + message);
            this.lineNumber = lineNumber;
        }

        public int getLineNumber() {
            return lineNumber;
        }
    }
}
