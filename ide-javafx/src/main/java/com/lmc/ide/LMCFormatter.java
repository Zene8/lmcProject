package com.lmc.ide;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LMCFormatter {
    private static final Pattern LINE_PATTERN = Pattern.compile(
            "^\\s*" +
                    "(?:([A-Za-z_][A-Za-z0-9_]*):\\s*)?" +
                    "(INP|OUT|LDA|STA|ADD|SUB|BRA|BRZ|BRP|HLT|DAT)" +
                    "(?:\\s+([A-Za-z_][A-Za-z0-9_]*|\\d+))?" +
                    "(\\s*//.*)?" +
                    "\\s*$",
            Pattern.CASE_INSENSITIVE);

    public static String format(String lmcCode) {
        StringBuilder formattedCode = new StringBuilder();
        String[] lines = lmcCode.split("\\r?\\n");
        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("//") || trimmedLine.startsWith("#")) {
                formattedCode.append(trimmedLine).append("\n");
                continue;
            }
            Matcher matcher = LINE_PATTERN.matcher(trimmedLine);
            if (matcher.matches()) {
                String label = matcher.group(1);
                String instruction = matcher.group(2);
                String operand = matcher.group(3);
                String comment = matcher.group(4);
                StringBuilder currentLine = new StringBuilder();
                if (label != null) {
                    currentLine.append(String.format("%-8s", label.toUpperCase() + ":"));
                } else {
                    currentLine.append("\t");
                }
                currentLine.append(instruction.toUpperCase());
                if (operand != null) {
                    currentLine.append("\t").append(operand.toUpperCase());
                }
                if (comment != null) {
                    currentLine.append("\t").append(comment.trim());
                }
                formattedCode.append(currentLine.toString()).append("\n");
            } else {
                formattedCode.append(trimmedLine).append("\n");
            }
        }
        return formattedCode.toString();
    }
}
