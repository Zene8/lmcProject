package com.lmc.ide;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;
import org.fxmisc.richtext.CodeArea;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LMCIDEFeatures {

    private final CodeArea codeArea;
    private final LMCParser lmcParser;
    private final Label memoryUsageLabel;
    private boolean autocorrectEnabled;
    private boolean autoFormattingEnabled;
    private boolean errorHighlightingEnabled;

    private final Tooltip tooltip = new Tooltip();
    private final PauseTransition tooltipDelay = new PauseTransition(Duration.millis(500));
    private double lastMouseX;
    private double lastMouseY;

    private final ContextMenu suggestionsPopup = new ContextMenu();

    private static final List<String> LMC_INSTRUCTIONS = Arrays.asList(
            "INP", "OUT", "LDA", "STA", "ADD", "SUB", "BRA", "BRZ", "BRP", "HLT", "DAT");

    public LMCIDEFeatures(CodeArea codeArea, LMCParser lmcParser, Label memoryUsageLabel,
            boolean autocorrectEnabled, boolean autoFormattingEnabled, boolean errorHighlightingEnabled) {
        this.codeArea = codeArea;
        this.lmcParser = lmcParser;
        this.memoryUsageLabel = memoryUsageLabel;
        this.autocorrectEnabled = autocorrectEnabled;
        this.autoFormattingEnabled = autoFormattingEnabled;
        this.errorHighlightingEnabled = errorHighlightingEnabled;

        setupAutocomplete();
        setupErrorHighlighting();
        setupMemoryUsageTracking();
        setupInstructionTooltips();
        setupLabelNavigation();
        setupPasteFormatting();
    }

    private void setupAutocomplete() {
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            String currentWord = getCurrentWord();
            if (currentWord.isEmpty()) {
                suggestionsPopup.hide();
                return;
            }

            List<MenuItem> suggestions = LMC_INSTRUCTIONS.stream()
                    .filter(instr -> instr.startsWith(currentWord.toUpperCase()))
                    .map(instr -> {
                        MenuItem item = new MenuItem(instr);
                        item.setOnAction(e -> completeInstruction(instr));
                        return item;
                    })
                    .collect(Collectors.toList());

            if (suggestions.isEmpty()) {
                suggestionsPopup.hide();
            } else {
                suggestionsPopup.getItems().setAll(suggestions);
                Point2D caretPos = codeArea.getCaretBounds()
                        .map(bounds -> new Point2D(bounds.getMinX(), bounds.getMaxY())).orElse(new Point2D(0, 0));
                suggestionsPopup.show(codeArea, caretPos.getX(), caretPos.getY());
            }
        });

        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (suggestionsPopup.isShowing() && e.getCode() == KeyCode.TAB) {
                if (!suggestionsPopup.getItems().isEmpty()) {
                    suggestionsPopup.getItems().get(0).fire();
                    e.consume();
                }
            }
        });
    }

    private String getCurrentWord() {
        int caretPos = codeArea.getCaretPosition();
        String text = codeArea.getText();
        int start = caretPos - 1;
        while (start >= 0 && Character.isLetterOrDigit(text.charAt(start))) {
            start--;
        }
        return text.substring(start + 1, caretPos);
    }

    private void completeInstruction(String instruction) {
        int caretPos = codeArea.getCaretPosition();
        String text = codeArea.getText();
        int start = caretPos - 1;
        while (start >= 0 && Character.isLetterOrDigit(text.charAt(start))) {
            start--;
        }
        codeArea.replaceText(start + 1, caretPos, instruction + " ");
        suggestionsPopup.hide();
    }

    private void setupErrorHighlighting() {
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (!errorHighlightingEnabled) {
                clearParagraphStyles();
                return;
            }
            clearParagraphStyles();
            try {
                lmcParser.parse(newText);
            } catch (LMCParser.LMCParseException e) {
                Platform.runLater(() -> {
                    int lineNumber = e.getLineNumber() - 1;
                    if (lineNumber >= 0 && lineNumber < codeArea.getParagraphs().size()) {
                        codeArea.setParagraphStyle(lineNumber, Collections.singleton("error-line"));
                    }
                });
            }
        });
    }

    private void setupMemoryUsageTracking() {
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            try {
                // FIX: Handle the AssembledCode object returned by the parser
                LMCParser.AssembledCode assembledCode = lmcParser.parse(newText);
                Map<Integer, Integer> memory = assembledCode.memoryMap;
                int used = memory.size();
                Platform.runLater(() -> memoryUsageLabel.setText("Memory Used: " + used + " / 100"));
            } catch (LMCParser.LMCParseException e) {
                Platform.runLater(() -> memoryUsageLabel.setText("Memory Used: Error"));
            }
        });
    }

    private void setupInstructionTooltips() {
        tooltipDelay.setOnFinished(actionEvent -> {
            int pos = codeArea.hit(lastMouseX, lastMouseY).getInsertionIndex();
            String word = extractWordAt(pos);

            if (LMC_INSTRUCTIONS.contains(word.toUpperCase())) {
                tooltip.setText(getInstructionHelp(word));
                tooltip.show(codeArea.getScene().getWindow(),
                        codeArea.localToScreen(0, 0).getX() + lastMouseX + 15,
                        codeArea.localToScreen(0, 0).getY() + lastMouseY + 15);
            } else {
                tooltip.hide();
            }
        });

        codeArea.setOnMouseMoved(mouseEvent -> {
            lastMouseX = mouseEvent.getX();
            lastMouseY = mouseEvent.getY();
            tooltipDelay.playFromStart();
        });

        codeArea.setOnMouseExited(mouseEvent -> {
            tooltip.hide();
            tooltipDelay.stop();
        });
    }

    private void setupLabelNavigation() {
        codeArea.setOnMouseClicked(event -> {
            if (event.isControlDown()) {
                String label = extractWordAt(codeArea.getCaretPosition());
                int line = findLabelDefinition(label);
                if (line >= 0) {
                    codeArea.moveTo(line, 0);
                    codeArea.requestFollowCaret();
                }
            }
        });
    }

    private void setupPasteFormatting() {
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.V) {
                Platform.runLater(() -> {
                    if (autoFormattingEnabled) {
                        String formatted = LMCFormatter.format(codeArea.getText());
                        codeArea.replaceText(formatted);
                    }
                });
            }
        });
    }

    private String extractWordAt(int pos) {
        String text = codeArea.getText();
        if (pos < 0 || pos >= text.length()) {
            return "";
        }

        int start = pos;
        int end = pos;

        while (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) {
            start--;
        }

        while (end < text.length() && Character.isLetterOrDigit(text.charAt(end))) {
            end++;
        }

        return text.substring(start, end);
    }

    private int findLabelDefinition(String label) {
        String[] lines = codeArea.getText().split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith(label.toUpperCase() + ":")) {
                return i;
            }
        }
        return -1;
    }

    private String getInstructionHelp(String instruction) {
        switch (instruction.toUpperCase()) {
            case "INP":
                return "INP: Reads a number from input into the Accumulator.";
            case "OUT":
                return "OUT: Writes the value from the Accumulator to output.";
            case "LDA":
                return "LDA (Load): Loads the value from a memory address into the Accumulator.";
            case "STA":
                return "STA (Store): Stores the Accumulator value into a memory address.";
            case "ADD":
                return "ADD: Adds a value from memory to the Accumulator.";
            case "SUB":
                return "SUB: Subtracts a value from memory from the Accumulator.";
            case "BRA":
                return "BRA (Branch Always): Jumps to the specified label.";
            case "BRZ":
                return "BRZ (Branch if Zero): Jumps to a label if the Accumulator is 0.";
            case "BRP":
                return "BRP (Branch if Positive): Jumps to a label if the Accumulator is >= 0.";
            case "HLT":
                return "HLT (Halt): Stops the program.";
            case "DAT":
                return "DAT (Data): Reserves a memory location, optionally with an initial value.";
            default:
                return "Unknown instruction.";
        }
    }

    private void clearParagraphStyles() {
        for (int i = 0; i < codeArea.getParagraphs().size(); i++) {
            codeArea.setParagraphStyle(i, Collections.emptyList());
        }
    }

    public void setAutocorrectEnabled(boolean autocorrectEnabled) {
        this.autocorrectEnabled = autocorrectEnabled;
    }

    public void setAutoFormattingEnabled(boolean autoFormattingEnabled) {
        this.autoFormattingEnabled = autoFormattingEnabled;
    }

    public void setErrorHighlightingEnabled(boolean errorHighlightingEnabled) {
        this.errorHighlightingEnabled = errorHighlightingEnabled;
        if (codeArea.getText() != null && !codeArea.getText().isEmpty()) {
            codeArea.replaceText(0, codeArea.getText().length(), codeArea.getText());
        }
    }

    public String autocorrectInstruction(String instruction) {
        String bestMatch = instruction;
        int minDistance = Integer.MAX_VALUE;

        for (String validInstruction : LMC_INSTRUCTIONS) {
            int distance = levenshteinDistance(instruction.toUpperCase(), validInstruction);
            if (distance < minDistance) {
                minDistance = distance;
                bestMatch = validInstruction;
            }
        }
        return (minDistance <= 2) ? bestMatch : instruction;
    }

    public String autocorrectCode(String code) {
        StringBuilder correctedCode = new StringBuilder();
        String[] lines = code.split("\\r?\\n");

        for (String line : lines) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length > 0 && !parts[0].isEmpty()) {
                int instructionIndex = 0;
                if (parts.length > 1 && parts[0].endsWith(":")) {
                    instructionIndex = 1;
                }

                if (instructionIndex < parts.length) {
                    String originalInstruction = parts[instructionIndex];
                    String correctedInstruction = autocorrectInstruction(originalInstruction);

                    if (!originalInstruction.equals(correctedInstruction)) {
                        parts[instructionIndex] = correctedInstruction;
                        correctedCode.append(String.join(" ", parts)).append("\n");
                    } else {
                        correctedCode.append(line).append("\n");
                    }
                } else {
                    correctedCode.append(line).append("\n");
                }
            } else {
                correctedCode.append(line).append("\n");
            }
        }
        return correctedCode.toString();
    }

    private int levenshteinDistance(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0)
                    costs[j] = j;
                else if (j > 0) {
                    int newValue = costs[j - 1];
                    if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                        newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                    }
                    costs[j - 1] = lastValue;
                    lastValue = newValue;
                }
            }
            if (i > 0)
                costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }
}
