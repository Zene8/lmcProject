package com.lmc.ide;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LMCParser {
    public static class AssembledCode {
        public final Map<Integer, Integer> memoryMap;
        public final Map<Integer, Integer> addressToLineMap;
        public final Map<Integer, String> instructions;
        public final Map<Integer, String> addressToLabelMap;

        AssembledCode(Map<Integer, Integer> memoryMap, Map<Integer, Integer> addressToLineMap,
                Map<Integer, String> instructions, Map<Integer, String> addressToLabelMap) {
            this.memoryMap = Collections.unmodifiableMap(memoryMap);
            this.addressToLineMap = Collections.unmodifiableMap(addressToLineMap);
            this.instructions = Collections.unmodifiableMap(instructions);
            this.addressToLabelMap = Collections.unmodifiableMap(addressToLabelMap);
        }
    }

    private static final int INP = 901, OUT = 902, ADD = 100, SUB = 200, STA = 300, LDA = 500, BRA = 600, BRZ = 700,
            BRP = 800, HLT = 0;
    private static final Set<String> INSTRUCTIONS_WITH_OPERAND = Set.of("ADD", "SUB", "STA", "LDA", "BRA", "BRZ",
            "BRP");
    private static final Pattern LINE_PATTERN = Pattern.compile(
            "^(?:([A-Z_][A-Z0-9_]*):?\\s*)?(ADD|SUB|STA|LDA|BRA|BRZ|BRP|INP|OUT|HLT|DAT)(?:\\s+([A-Z_][A-Z0-9_]*|\\d+))?\\s*(?://.*)?$",
            Pattern.CASE_INSENSITIVE);

    public AssembledCode parse(String code) throws LMCParseException {
        Map<String, Integer> symbolTable = new HashMap<>();
        List<String[]> parsedLines = new ArrayList<>();
        Map<Integer, Integer> memoryMap = new HashMap<>();
        Map<Integer, Integer> addressToLineMap = new HashMap<>();
        Map<Integer, String> instructionsMap = new HashMap<>();
        int currentAddress = 0;
        String[] lines = code.split("\\r?\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.matches("^\\s*(//|#).*"))
                continue;
            Matcher matcher = LINE_PATTERN.matcher(line);
            if (!matcher.matches())
                throw new LMCParseException("Syntax error: Invalid line format.", i + 1);
            addressToLineMap.put(currentAddress, i);
            String label = matcher.group(1);
            if (label != null) {
                label = label.toUpperCase().replace(":", "");
                if (symbolTable.containsKey(label))
                    throw new LMCParseException("Duplicate label: " + label, i + 1);
                symbolTable.put(label, currentAddress);
            }
            parsedLines.add(new String[] { matcher.group(2).toUpperCase(), matcher.group(3) });
            currentAddress++;
        }
        currentAddress = 0;
        for (int i = 0; i < parsedLines.size(); i++) {
            String[] parts = parsedLines.get(i);
            String instruction = parts[0];
            String operand = parts[1];
            int operandValue = 0;
            if ("DAT".equals(instruction)) {
                if (operand != null && !operand.isEmpty()) {
                    try {
                        operandValue = Integer.parseInt(operand);
                    } catch (NumberFormatException e) {
                        if (symbolTable.containsKey(operand.toUpperCase())) {
                            operandValue = symbolTable.get(operand.toUpperCase());
                        } else {
                            throw new LMCParseException("Unknown label for DAT: " + operand, i + 1);
                        }
                    }
                }
                memoryMap.put(currentAddress, operandValue);
            } else {
                int opcode = getOpcode(instruction, i + 1);
                if (INSTRUCTIONS_WITH_OPERAND.contains(instruction)) {
                    if (operand == null || operand.isEmpty())
                        throw new LMCParseException("Missing operand for instruction: " + instruction, i + 1);
                    try {
                        operandValue = Integer.parseInt(operand);
                    } catch (NumberFormatException e) {
                        if (symbolTable.containsKey(operand.toUpperCase())) {
                            operandValue = symbolTable.get(operand.toUpperCase());
                        } else {
                            throw new LMCParseException("Unknown label or invalid operand: " + operand, i + 1);
                        }
                    }
                    memoryMap.put(currentAddress, opcode + operandValue);
                    instructionsMap.put(currentAddress, instruction + " " + operand);
                } else {
                    if (operand != null && !operand.isEmpty())
                        throw new LMCParseException("Instruction " + instruction + " does not take an operand.", i + 1);
                    memoryMap.put(currentAddress, opcode);
                    instructionsMap.put(currentAddress, instruction);
                }
            }
            currentAddress++;
        }
        return new AssembledCode(memoryMap, addressToLineMap, instructionsMap, new HashMap<>());
    }

    private int getOpcode(String instruction, int line) throws LMCParseException {
        switch (instruction) {
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
