package com.lmc.ide;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LMCParser {

    private static final String[] LMC_INSTRUCTIONS = new String[]{
            "INP", "OUT", "LDA", "STA", "ADD", "SUB", "BRA", "BRZ", "BRP", "HLT", "DAT"
    };

    // Instructions that require an operand
    private static final List<String> INSTRUCTIONS_WITH_OPERAND = Arrays.asList(
            "LDA", "STA", "ADD", "SUB", "BRA", "BRZ", "BRP", "DAT"
    );

    // Pattern to match a line: [LABEL] INSTRUCTION [OPERAND] [// COMMENT]
    private static final Pattern LINE_PATTERN = Pattern.compile(
            "^\\s*" +                               // Optional leading whitespace
            "(?:([A-Za-z_][A-Za-z0-9_]*)\\s+)?" + // Optional Label (Group 1)
            "([A-Za-z]{3})" +                      // Instruction (Group 2)
            "(?:\\s+([A-Za-z_][A-Za-z0-9_]*|\\d+))?" + // Optional Operand (Group 3: label or number)
            "(?:\\s*//.*)?" +                     // Optional Comment
            "\\s*$"                                // Optional trailing whitespace and end of line
    );

    public static class ParseError {
        public final int lineNumber;
        public final String message;

        public ParseError(int lineNumber, String message) {
            this.lineNumber = lineNumber;
            this.message = message;
        }

        @Override
        public String toString() {
            return "Line " + (lineNumber + 1) + ": " + message;
        }
    }

    public List<ParseError> parse(String lmcCode) {
        List<ParseError> errors = new ArrayList<>();
        String[] lines = lmcCode.split("\n");

        Map<String, Integer> definedLabels = new HashMap<>();
        Map<String, List<Integer>> usedLabels = new HashMap<>();

        // First pass: Identify labels and basic instruction format errors
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("//")) continue;

            Matcher matcher = LINE_PATTERN.matcher(line);
            if (!matcher.matches()) {
                errors.add(new ParseError(i, "Syntax error: Invalid line format."));
                continue;
            }

            String label = matcher.group(1);
            String instruction = matcher.group(2);
            String operand = matcher.group(3);

            // Check for label definition
            if (label != null) {
                if (definedLabels.containsKey(label)) {
                    errors.add(new ParseError(i, "Duplicate label definition: " + label + "."));
                } else {
                    definedLabels.put(label, i);
                }
            }

            // Check if instruction is valid
            if (!Arrays.asList(LMC_INSTRUCTIONS).contains(instruction.toUpperCase())) {
                errors.add(new ParseError(i, "Unknown instruction: " + instruction + "."));
                continue;
            }

            // Check operand presence for instructions that require it
            if (INSTRUCTIONS_WITH_OPERAND.contains(instruction.toUpperCase())) {
                if (operand == null) {
                    errors.add(new ParseError(i, "Missing operand for instruction: " + instruction + "."));
                }
            } else if (operand != null) {
                // Instructions that do not take an operand but have one
                if (!instruction.equalsIgnoreCase("DAT")) { // DAT can have an operand
                    errors.add(new ParseError(i, "Unexpected operand for instruction: " + instruction + "."));
                }
            }
        }

        // Second pass: Check for undefined labels
        for (Map.Entry<String, List<Integer>> entry : usedLabels.entrySet()) {
            String usedLabel = entry.getKey();
            if (!definedLabels.containsKey(usedLabel)) {
                for (Integer lineNumber : entry.getValue()) {
                    errors.add(new ParseError(lineNumber, "Undefined label: " + usedLabel + "."));
                }
            }
        }

        return errors;
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        return str.matches("-?\\d+");
    }
}