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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;

public class LMCIDEFeatures {

    private final UIController uiController;
    private final LMCParser lmcParser;
    private final Label memoryUsageLabel;
    private boolean autocorrectEnabled;
    private boolean autoFormattingEnabled;
    private boolean errorHighlightingEnabled;

    private final Tooltip tooltip = new Tooltip();
    private final PauseTransition tooltipDelay = new PauseTransition(Duration.millis(500));
    private double lastMouseX, lastMouseY;
    private final ContextMenu suggestionsPopup = new ContextMenu();
    private Set<Integer> breakpoints = new HashSet<>();
    private CompletableFuture<Integer> pendingInputRequest;

    private static final List<String> LMC_INSTRUCTIONS = Arrays.asList(
            "INP", "OUT", "LDA", "STA", "ADD", "SUB", "BRA", "BRZ", "BRP", "HLT", "DAT");

    private static final Map<String, String> TYPO_CORRECTIONS = new HashMap<>();

    static {
        TYPO_CORRECTIONS.put("INPT", "INP");
        TYPO_CORRECTIONS.put("OUTT", "OUT");
        TYPO_CORRECTIONS.put("LOD", "LDA");
        TYPO_CORRECTIONS.put("STOR", "STA");
        TYPO_CORRECTIONS.put("AD", "ADD");
        TYPO_CORRECTIONS.put("SUBT", "SUB");
        TYPO_CORRECTIONS.put("BRNCH", "BRA");
        TYPO_CORRECTIONS.put("BRZRO", "BRZ");
        TYPO_CORRECTIONS.put("BRPOS", "BRP");
        TYPO_CORRECTIONS.put("HALT", "HLT");
        TYPO_CORRECTIONS.put("DATA", "DAT");
    }

    public LMCIDEFeatures(UIController uiController, LMCParser lmcParser, Label memoryUsageLabel,
            boolean autocorrectEnabled, boolean autoFormattingEnabled, boolean errorHighlightingEnabled) {
        this.uiController = uiController;
        this.lmcParser = lmcParser;
        this.memoryUsageLabel = memoryUsageLabel;
        this.autocorrectEnabled = autocorrectEnabled;
        this.autoFormattingEnabled = autoFormattingEnabled;
        this.errorHighlightingEnabled = errorHighlightingEnabled;

        setupAllFeatures();
    }

    private void setupAllFeatures() {
        uiController.getEditorTabPane().getSelectionModel().selectedItemProperty()
                .addListener((obs, oldTab, newTab) -> {
                    if (newTab != null) {
                        CodeArea codeArea = uiController.getCurrentCodeArea();
                        if (codeArea != null) {
                            setupAutocomplete(codeArea);
                            setupErrorHighlighting(codeArea);
                            setupMemoryUsageTracking(codeArea);
                            setupInstructionTooltips(codeArea);
                            setupLabelNavigation(codeArea);
                            setupPasteFormatting(codeArea);
                        }
                    }
                });
    }

    private void setupAutocomplete(CodeArea codeArea) {
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            String currentWord = getCurrentWord(codeArea);
            if (currentWord.isEmpty()) {
                suggestionsPopup.hide();
                return;
            }
            List<MenuItem> suggestions = LMC_INSTRUCTIONS.stream()
                    .filter(instr -> instr.startsWith(currentWord.toUpperCase()))
                    .map(instr -> {
                        MenuItem item = new MenuItem(instr);
                        item.setOnAction(e -> completeInstruction(codeArea, instr));
                        return item;
                    })
                    .collect(Collectors.toList());
            if (suggestions.isEmpty()) {
                suggestionsPopup.hide();
            } else {
                suggestionsPopup.getItems().setAll(suggestions);
                Point2D caretPos = codeArea.getCaretBounds().map(b -> new Point2D(b.getMinX(), b.getMaxY()))
                        .orElse(new Point2D(0, 0));
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

    private String getCurrentWord(CodeArea codeArea) {
        int caretPos = codeArea.getCaretPosition();
        String text = codeArea.getText();
        int start = caretPos - 1;
        while (start >= 0 && Character.isLetterOrDigit(text.charAt(start))) {
            start--;
        }
        return text.substring(start + 1, caretPos);
    }

    private void completeInstruction(CodeArea codeArea, String instruction) {
        int caretPos = codeArea.getCaretPosition();
        String text = codeArea.getText();
        int start = caretPos - 1;
        while (start >= 0 && Character.isLetterOrDigit(text.charAt(start))) {
            start--;
        }
        codeArea.replaceText(start + 1, caretPos, instruction + " ");
        suggestionsPopup.hide();
    }

    private void setupErrorHighlighting(CodeArea codeArea) {
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            // FIX: Clear previous errors before re-parsing
            lineErrors.clear();

            if (!errorHighlightingEnabled) {
                clearParagraphStyles(codeArea);
                return;
            }
            clearParagraphStyles(codeArea);
            try {
                lmcParser.parse(newText);
            } catch (LMCParser.LMCParseException e) {
                Platform.runLater(() -> {
                    int lineNumber = e.getLineNumber() - 1; // Convert to 0-based index for CodeArea
                    if (lineNumber >= 0 && lineNumber < codeArea.getParagraphs().size()) {
                        codeArea.setParagraphStyle(lineNumber, Collections.singleton("error-line"));
                        lineErrors.put(e.getLineNumber(), e.getMessage()); // Store with 1-based line number for UI
                    }
                });
            }
        });
    }

    private void setupMemoryUsageTracking(CodeArea codeArea) {
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            try {
                LMCParser.AssembledCode assembledCode = lmcParser.parse(newText);
                int used = assembledCode.memoryMap.size();
                Platform.runLater(() -> memoryUsageLabel.setText("Memory Used: " + used + " / 100"));
            } catch (LMCParser.LMCParseException e) {
                Platform.runLater(() -> memoryUsageLabel.setText("Memory Used: Error"));
            }
        });
    }

    private void setupInstructionTooltips(CodeArea codeArea) {
        tooltipDelay.setOnFinished(e -> {
            int pos = codeArea.hit(lastMouseX, lastMouseY).getInsertionIndex();
            String word = extractWordAt(codeArea, pos);
            if (LMC_INSTRUCTIONS.contains(word.toUpperCase())) {
                tooltip.setText(getInstructionHelp(word));
                tooltip.show(codeArea.getScene().getWindow(),
                        codeArea.localToScreen(0, 0).getX() + lastMouseX + 15,
                        codeArea.localToScreen(0, 0).getY() + lastMouseY + 15);
            } else {
                tooltip.hide();
            }
        });
        codeArea.setOnMouseMoved(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            tooltipDelay.playFromStart();
        });
        codeArea.setOnMouseExited(e -> {
            tooltip.hide();
            tooltipDelay.stop();
        });
    }

    private void setupLabelNavigation(CodeArea codeArea) {
        codeArea.setOnMouseClicked(event -> {
            if (event.isControlDown()) {
                String label = extractWordAt(codeArea, codeArea.getCaretPosition());
                int line = findLabelDefinition(codeArea, label);
                if (line >= 0) {
                    codeArea.moveTo(line, 0);
                    codeArea.requestFollowCaret();
                }
            }
        });
    }

    private void setupPasteFormatting(CodeArea codeArea) {
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

    private String extractWordAt(CodeArea codeArea, int pos) {
        String text = codeArea.getText();
        if (pos < 0 || pos >= text.length())
            return "";
        int start = pos, end = pos;
        while (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1)))
            start--;
        while (end < text.length() && Character.isLetterOrDigit(text.charAt(end)))
            end++;
        return text.substring(start, end);
    }

    private int findLabelDefinition(CodeArea codeArea, String label) {
        String[] lines = codeArea.getText().split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith(label.toUpperCase() + " ")) // LMC labels are followed by a space
                return i;
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

    private void clearParagraphStyles(CodeArea codeArea) {
        for (int i = 0; i < codeArea.getParagraphs().size(); i++) {
            codeArea.setParagraphStyle(i, Collections.emptyList());
        }
    }

    public void setAutocorrectEnabled(boolean b) {
        this.autocorrectEnabled = b;
    }

    public void setAutoFormattingEnabled(boolean b) {
        this.autoFormattingEnabled = b;
    }

    public void setErrorHighlightingEnabled(boolean b) {
        this.errorHighlightingEnabled = b;
    }

    public Set<Integer> getBreakpoints() {
        return breakpoints;
    }

    public void setPendingInputRequest(CompletableFuture<Integer> req) {
        this.pendingInputRequest = req;
    }

    public CompletableFuture<Integer> getPendingInputRequest() {
        return pendingInputRequest;
    }

    private Map<Integer, String> lineErrors = new HashMap<>();

    public Map<Integer, String> getLineErrors() {
        return lineErrors;
    }

    // --- START: NEWLY IMPLEMENTED FIND/REPLACE METHODS ---

    public void findNext(String textToFind, boolean forward) {
        CodeArea codeArea = uiController.getCurrentCodeArea();
        if (codeArea == null || textToFind == null || textToFind.isEmpty()) {
            return;
        }
        String content = codeArea.getText();
        int caretPos = codeArea.getCaretPosition();
        int nextMatch = -1;

        if (forward) {
            nextMatch = content.indexOf(textToFind, caretPos);
            // Wrap around search if not found from caret position
            if (nextMatch == -1) {
                nextMatch = content.indexOf(textToFind, 0);
            }
        } else { // Searching backward
            nextMatch = content.lastIndexOf(textToFind, caretPos - 1);
            // Wrap around search if not found from caret position
            if (nextMatch == -1) {
                nextMatch = content.lastIndexOf(textToFind, content.length());
            }
        }

        if (nextMatch != -1) {
            codeArea.selectRange(nextMatch, nextMatch + textToFind.length());
            codeArea.requestFollowCaret();
        } else {
            uiController.setStatusBarMessage("'" + textToFind + "' not found.");
        }
    }

    public void replaceNext() {
        CodeArea codeArea = uiController.getCurrentCodeArea();
        if (codeArea == null)
            return;

        String textToFind = uiController.getReplaceFindPopupText();
        String replacementText = uiController.getReplaceWithPopupText();

        if (textToFind == null || textToFind.isEmpty())
            return;

        // If the selected text matches, replace it and find the next one
        if (codeArea.getSelectedText().equalsIgnoreCase(textToFind)) {
            codeArea.replaceSelection(replacementText);
        }

        // Find the next occurrence
        findNext(textToFind, true);
    }

    public void replaceAll() {
        CodeArea codeArea = uiController.getCurrentCodeArea();
        if (codeArea == null)
            return;

        String textToFind = uiController.getReplaceFindPopupText();
        String replacementText = uiController.getReplaceWithPopupText();

        if (textToFind == null || textToFind.isEmpty())
            return;

        String newText = codeArea.getText().replaceAll("(?i)" + textToFind, replacementText);
        codeArea.replaceText(newText);
        uiController.setStatusBarMessage("Replaced all occurrences.");
    }

    // --- END: NEWLY IMPLEMENTED FIND/REPLACE METHODS ---
}